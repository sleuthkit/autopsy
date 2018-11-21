/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.datamodel;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.CommentChangedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.HasCommentStatus;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.Score;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.datamodel.Bundle.*;
import static org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode.AbstractFilePropertyType.*;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * An abstract node that encapsulates AbstractFile data
 *
 * @param <T> type of the AbstractFile to encapsulate
 */
public abstract class AbstractAbstractFileNode<T extends AbstractFile> extends AbstractContentNode<T> {

    private static final Logger logger = Logger.getLogger(AbstractAbstractFileNode.class.getName());
    @NbBundle.Messages("AbstractAbstractFileNode.addFileProperty.desc=no description")
    private static final String NO_DESCR = AbstractAbstractFileNode_addFileProperty_desc();

    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.CURRENT_CASE,
            Case.Events.CONTENT_TAG_ADDED, Case.Events.CONTENT_TAG_DELETED, Case.Events.CR_COMMENT_CHANGED);

    private static final ExecutorService translationPool;
    private static final Integer MAX_POOL_SIZE = 10;

    /**
     * @param abstractFile file to wrap
     */
    AbstractAbstractFileNode(T abstractFile) {
        super(abstractFile);
        String ext = abstractFile.getNameExtension();
        if (StringUtils.isNotBlank(ext)) {
            ext = "." + ext;
            // If this is an archive file we will listen for ingest events
            // that will notify us when new content has been identified.
            if (FileTypeExtensions.getArchiveExtensions().contains(ext)) {
                IngestManager.getInstance().addIngestModuleEventListener(weakPcl);
            }
        }
        // Listen for case events so that we can detect when the case is closed
        // or when tags are added.
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
    }

    static {
        //Initialize this pool only once! This will be used by every instance of AAFN
        //to do their heavy duty SCO column and translation updates.
        translationPool = Executors.newFixedThreadPool(MAX_POOL_SIZE, 
                new ThreadFactoryBuilder().setNameFormat("translation-task-thread-%d").build());
    }

    /**
     * The finalizer removes event listeners as the BlackboardArtifactNode is
     * being garbage collected. Yes, we know that finalizers are considered to
     * be "bad" but since the alternative also relies on garbage collection
     * being run and we know that finalize will be called when the object is
     * being GC'd it seems like this is a reasonable solution.
     *
     * @throws Throwable
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        removeListeners();
    }

    private void removeListeners() {
        IngestManager.getInstance().removeIngestModuleEventListener(weakPcl);
        Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
    }

    /**
     * Event signals to indicate the background tasks have completed processing.
     * Currently, we have one property task in the background:
     *
     * 1) Retreiving the translation of the file name
     */
    enum NodeSpecificEvents {
        TRANSLATION_AVAILABLE,
    }

    private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
        String eventType = evt.getPropertyName();

        // Is this a content changed event?
        if (eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())) {
            if ((evt.getOldValue() instanceof ModuleContentEvent) == false) {
                return;
            }
            ModuleContentEvent moduleContentEvent = (ModuleContentEvent) evt.getOldValue();
            if ((moduleContentEvent.getSource() instanceof Content) == false) {
                return;
            }
            Content newContent = (Content) moduleContentEvent.getSource();

            // Does the event indicate that content has been added to *this* file?
            if (getContent().getId() == newContent.getId()) {
                // If so, refresh our children.
                try {
                    Children parentsChildren = getParentNode().getChildren();
                    // We only want to refresh our parents children if we are in the
                    // data sources branch of the tree. The parent nodes in other
                    // branches of the tree (e.g. File Types and Deleted Files) do
                    // not need to be refreshed.
                    if (parentsChildren instanceof ContentChildren) {
                        ((ContentChildren) parentsChildren).refreshChildren();
                        parentsChildren.getNodesCount();
                    }
                } catch (NullPointerException ex) {
                    // Skip
                }
            }
        } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
            if (evt.getNewValue() == null) {
                // case was closed. Remove listeners so that we don't get called with a stale case handle
                removeListeners();
            }
            /*
             * No need to do any asynchrony around tag added, deleted or CR
             * change events, they are so infrequent and user driven that we can
             * just keep a simple blocking approach, where we go out to the
             * database ourselves.
             */
        } else if (eventType.equals(Case.Events.CONTENT_TAG_ADDED.toString())) {
            ContentTagAddedEvent event = (ContentTagAddedEvent) evt;
            if (event.getAddedTag().getContent().equals(content)) {
                List<ContentTag> tags = getContentTagsFromDatabase();
                Pair<Score, String> scorePropAndDescr = getScorePropertyAndDescription(tags);
                Score value = scorePropAndDescr.getLeft();
                String descr = scorePropAndDescr.getRight();
                CorrelationAttributeInstance attribute = getCorrelationAttributeInstance();
                updateSheet(new NodeProperty<>(SCORE.toString(),SCORE.toString(),descr,value),
                            new NodeProperty<>(COMMENT.toString(),COMMENT.toString(),NO_DESCR,getCommentProperty(tags, attribute))
                );
            }
        } else if (eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
            ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
            if (event.getDeletedTagInfo().getContentID() == content.getId()) {
                List<ContentTag> tags = getContentTagsFromDatabase();
                Pair<Score, String> scorePropAndDescr = getScorePropertyAndDescription(tags);
                Score value = scorePropAndDescr.getLeft();
                String descr = scorePropAndDescr.getRight();
                CorrelationAttributeInstance attribute = getCorrelationAttributeInstance();
                updateSheet(new NodeProperty<>(SCORE.toString(), SCORE.toString(),descr,value),
                            new NodeProperty<>(COMMENT.toString(), COMMENT.toString(),NO_DESCR,getCommentProperty(tags, attribute))
                );
            }
        } else if (eventType.equals(Case.Events.CR_COMMENT_CHANGED.toString())) {
            CommentChangedEvent event = (CommentChangedEvent) evt;
            if (event.getContentID() == content.getId()) {
                List<ContentTag> tags = getContentTagsFromDatabase();
                CorrelationAttributeInstance attribute = getCorrelationAttributeInstance();
                updateSheet(new NodeProperty<>(COMMENT.toString(), COMMENT.toString(),NO_DESCR,getCommentProperty(tags, attribute)));
            }
        /*
         * Data that was being computed in the background task. Kicked off by a
         * call to createSheet().
         */
        } else if (eventType.equals(NodeSpecificEvents.TRANSLATION_AVAILABLE.toString())) {
            updateSheet(new NodeProperty<>(TRANSLATION.toString(),TRANSLATION.toString(),NO_DESCR,evt.getNewValue()));
        }
    };
    /**
     * We pass a weak reference wrapper around the listener to the event
     * publisher. This allows Netbeans to delete the node when the user
     * navigates to another part of the tree (previously, nodes were not being
     * deleted because the event publisher was holding onto a strong reference
     * to the listener. We need to hold onto the weak reference here to support
     * unregistering of the listener in removeListeners() below.
     */
    private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);

    /**
     * Updates the values of the properties in the current property sheet with
     * the new properties being passed in. Only if that property exists in the
     * current sheet will it be applied. That way, we allow for subclasses to
     * add their own (or omit some!) properties and we will not accidentally
     * disrupt their UI.
     *
     * Race condition if not synchronized. Only one update should be applied at
     * a time.
     *
     * @param newProps New file property instances to be updated in the current
     *                 sheet.
     */
    private synchronized void updateSheet(NodeProperty<?>... newProps) {
        //Refresh ONLY those properties in the sheet currently. Subclasses may have 
        //only added a subset of our properties or their own props. Let's keep their UI correct.
        Sheet visibleSheet = this.getSheet();
        Sheet.Set visibleSheetSet = visibleSheet.get(Sheet.PROPERTIES);
        Property<?>[] visibleProps = visibleSheetSet.getProperties();
        for(NodeProperty<?> newProp: newProps) {
            for(int i = 0; i < visibleProps.length; i++) {
                if(visibleProps[i].getName().equals(newProp.getName())) {
                    visibleProps[i] = newProp;
                }
            }
        }
        visibleSheetSet.put(visibleProps);
        visibleSheet.put(visibleSheetSet);
        //setSheet() will notify Netbeans to update this node in the UI.
        this.setSheet(visibleSheet);
    }

    /*
     * This is called when the node is first initialized. Any new updates or
     * changes happen by directly manipulating the sheet. That means we can fire
     * off background events everytime this method is called and not worry about
     * duplicated jobs.
     */
    @Override
    protected synchronized Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = Sheet.createPropertiesSet();
        sheet.put(sheetSet);

        //This will fire off fresh background tasks.
        List<NodeProperty<?>> newProperties = getProperties();
        newProperties.forEach((property) -> {
            sheetSet.put(property);
        });
        
        /*
         * Submit the translation task ASAP. Keep all weak references so
         * this task doesn't block the ability of this node to be GC'd.
         */
        translationPool.submit(new TranslationTask(new WeakReference<>(this), weakPcl));

        return sheet;
    }

    @NbBundle.Messages({"AbstractAbstractFileNode.nameColLbl=Name",
        "AbstractAbstractFileNode.translateFileName=Translated Name",
        "AbstractAbstractFileNode.createSheet.score.name=S",
        "AbstractAbstractFileNode.createSheet.comment.name=C",
        "AbstractAbstractFileNode.createSheet.count.name=O",
        "AbstractAbstractFileNode.locationColLbl=Location",
        "AbstractAbstractFileNode.modifiedTimeColLbl=Modified Time",
        "AbstractAbstractFileNode.changeTimeColLbl=Change Time",
        "AbstractAbstractFileNode.accessTimeColLbl=Access Time",
        "AbstractAbstractFileNode.createdTimeColLbl=Created Time",
        "AbstractAbstractFileNode.sizeColLbl=Size",
        "AbstractAbstractFileNode.flagsDirColLbl=Flags(Dir)",
        "AbstractAbstractFileNode.flagsMetaColLbl=Flags(Meta)",
        "AbstractAbstractFileNode.modeColLbl=Mode",
        "AbstractAbstractFileNode.useridColLbl=UserID",
        "AbstractAbstractFileNode.groupidColLbl=GroupID",
        "AbstractAbstractFileNode.metaAddrColLbl=Meta Addr.",
        "AbstractAbstractFileNode.attrAddrColLbl=Attr. Addr.",
        "AbstractAbstractFileNode.typeDirColLbl=Type(Dir)",
        "AbstractAbstractFileNode.typeMetaColLbl=Type(Meta)",
        "AbstractAbstractFileNode.knownColLbl=Known",
        "AbstractAbstractFileNode.md5HashColLbl=MD5 Hash",
        "AbstractAbstractFileNode.objectId=Object ID",
        "AbstractAbstractFileNode.mimeType=MIME Type",
        "AbstractAbstractFileNode.extensionColLbl=Extension"})
    public enum AbstractFilePropertyType {

        NAME(AbstractAbstractFileNode_nameColLbl()),
        TRANSLATION(AbstractAbstractFileNode_translateFileName()),
        SCORE(AbstractAbstractFileNode_createSheet_score_name()),
        COMMENT(AbstractAbstractFileNode_createSheet_comment_name()),
        OCCURRENCES(AbstractAbstractFileNode_createSheet_count_name()),
        LOCATION(AbstractAbstractFileNode_locationColLbl()),
        MOD_TIME(AbstractAbstractFileNode_modifiedTimeColLbl()),
        CHANGED_TIME(AbstractAbstractFileNode_changeTimeColLbl()),
        ACCESS_TIME(AbstractAbstractFileNode_accessTimeColLbl()),
        CREATED_TIME(AbstractAbstractFileNode_createdTimeColLbl()),
        SIZE(AbstractAbstractFileNode_sizeColLbl()),
        FLAGS_DIR(AbstractAbstractFileNode_flagsDirColLbl()),
        FLAGS_META(AbstractAbstractFileNode_flagsMetaColLbl()),
        MODE(AbstractAbstractFileNode_modeColLbl()),
        USER_ID(AbstractAbstractFileNode_useridColLbl()),
        GROUP_ID(AbstractAbstractFileNode_groupidColLbl()),
        META_ADDR(AbstractAbstractFileNode_metaAddrColLbl()),
        ATTR_ADDR(AbstractAbstractFileNode_attrAddrColLbl()),
        TYPE_DIR(AbstractAbstractFileNode_typeDirColLbl()),
        TYPE_META(AbstractAbstractFileNode_typeMetaColLbl()),
        KNOWN(AbstractAbstractFileNode_knownColLbl()),
        MD5HASH(AbstractAbstractFileNode_md5HashColLbl()),
        ObjectID(AbstractAbstractFileNode_objectId()),
        MIMETYPE(AbstractAbstractFileNode_mimeType()),
        EXTENSION(AbstractAbstractFileNode_extensionColLbl());

        final private String displayString;

        private AbstractFilePropertyType(String displayString) {
            this.displayString = displayString;
        }

        @Override
        public String toString() {
            return displayString;
        }
    }

    /**
     * Creates and populates a list of properties for this nodes property sheet.
     */
    private List<NodeProperty<?>> getProperties() {
        List<NodeProperty<?>> properties = new ArrayList<>();
        properties.add(new NodeProperty<>(NAME.toString(), NAME.toString(), NO_DESCR, getContentDisplayName(content)));   
        /*
         * Initialize an empty place holder value. At the bottom, we kick off a
         * background task that promises to update these values.
         */
        
        if (UserPreferences.displayTranslatedFileNames()) {
            properties.add(new NodeProperty<>(TRANSLATION.toString(), TRANSLATION.toString(), NO_DESCR, ""));
        }

        //SCO column prereq info..
        List<ContentTag> tags = getContentTagsFromDatabase();
        CorrelationAttributeInstance attribute = getCorrelationAttributeInstance();
        
        Pair<DataResultViewerTable.Score, String> scoreAndDescription = getScorePropertyAndDescription(tags);
        properties.add(new NodeProperty<>(SCORE.toString(), SCORE.toString(), scoreAndDescription.getRight(), scoreAndDescription.getLeft()));
        DataResultViewerTable.HasCommentStatus comment = getCommentProperty(tags, attribute);
        properties.add(new NodeProperty<>(COMMENT.toString(), COMMENT.toString(), NO_DESCR, comment));
        if (!UserPreferences.hideCentralRepoCommentsAndOccurrences()) {
            Pair<Long, String> countAndDescription = getCountPropertyAndDescription(attribute);
            properties.add(new NodeProperty<>(OCCURRENCES.toString(), OCCURRENCES.toString(), countAndDescription.getRight(), countAndDescription.getLeft()));
        }
        properties.add(new NodeProperty<>(LOCATION.toString(), LOCATION.toString(), NO_DESCR, getContentPath(content)));
        properties.add(new NodeProperty<>(MOD_TIME.toString(), MOD_TIME.toString(), NO_DESCR, ContentUtils.getStringTime(content.getMtime(), content)));
        properties.add(new NodeProperty<>(CHANGED_TIME.toString(), CHANGED_TIME.toString(), NO_DESCR, ContentUtils.getStringTime(content.getCtime(), content)));
        properties.add(new NodeProperty<>(ACCESS_TIME.toString(), ACCESS_TIME.toString(), NO_DESCR, ContentUtils.getStringTime(content.getAtime(), content)));
        properties.add(new NodeProperty<>(CREATED_TIME.toString(), CREATED_TIME.toString(), NO_DESCR, ContentUtils.getStringTime(content.getCrtime(), content)));
        properties.add(new NodeProperty<>(SIZE.toString(), SIZE.toString(), NO_DESCR, content.getSize()));
        properties.add(new NodeProperty<>(FLAGS_DIR.toString(), FLAGS_DIR.toString(), NO_DESCR, content.getDirFlagAsString()));
        properties.add(new NodeProperty<>(FLAGS_META.toString(), FLAGS_META.toString(), NO_DESCR, content.getMetaFlagsAsString()));
        properties.add(new NodeProperty<>(MODE.toString(), MODE.toString(), NO_DESCR, content.getModesAsString()));
        properties.add(new NodeProperty<>(USER_ID.toString(), USER_ID.toString(), NO_DESCR, content.getUid()));
        properties.add(new NodeProperty<>(GROUP_ID.toString(), GROUP_ID.toString(), NO_DESCR, content.getGid()));
        properties.add(new NodeProperty<>(META_ADDR.toString(), META_ADDR.toString(), NO_DESCR, content.getMetaAddr()));
        properties.add(new NodeProperty<>(ATTR_ADDR.toString(), ATTR_ADDR.toString(), NO_DESCR, content.getAttrType().getValue() + "-" + content.getAttributeId()));
        properties.add(new NodeProperty<>(TYPE_DIR.toString(), TYPE_DIR.toString(), NO_DESCR, content.getDirType().getLabel()));
        properties.add(new NodeProperty<>(TYPE_META.toString(), TYPE_META.toString(), NO_DESCR, content.getMetaType().toString()));
        properties.add(new NodeProperty<>(KNOWN.toString(), KNOWN.toString(), NO_DESCR, content.getKnown().getName()));
        properties.add(new NodeProperty<>(MD5HASH.toString(), MD5HASH.toString(), NO_DESCR, StringUtils.defaultString(content.getMd5Hash())));
        properties.add(new NodeProperty<>(ObjectID.toString(), ObjectID.toString(), NO_DESCR, content.getId()));
        properties.add(new NodeProperty<>(MIMETYPE.toString(), MIMETYPE.toString(), NO_DESCR, StringUtils.defaultString(content.getMIMEType())));
        properties.add(new NodeProperty<>(EXTENSION.toString(), EXTENSION.toString(), NO_DESCR, content.getNameExtension()));
        
        return properties;
    }

    /**
     * Used by subclasses of AbstractAbstractFileNode to add the tags property
     * to their sheets.
     *
     * @param sheetSet the modifiable Sheet.Set returned by
     *                 Sheet.get(Sheet.PROPERTIES)
     *
     * @deprecated
     */
    @NbBundle.Messages("AbstractAbstractFileNode.tagsProperty.displayName=Tags")
    @Deprecated
    protected void addTagProperty(Sheet.Set sheetSet) {
        List<ContentTag> tags = getContentTagsFromDatabase();
        sheetSet.put(new NodeProperty<>("Tags", AbstractAbstractFileNode_tagsProperty_displayName(),
                NO_DESCR, tags.stream().map(t -> t.getName().getDisplayName())
                        .distinct()
                        .collect(Collectors.joining(", "))));
    }

    /**
     * Gets a comma-separated values list of the names of the hash sets
     * currently identified as including a given file.
     *
     * @param file The file.
     *
     * @return The CSV list of hash set names.
     *
     * @deprecated
     */
    @Deprecated
    protected static String getHashSetHitsCsvList(AbstractFile file) {
        try {
            return StringUtils.join(file.getHashSetNames(), ", ");
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.WARNING, "Error getting hashset hits: ", tskCoreException); //NON-NLS
            return "";
        }
    }
    
    @NbBundle.Messages({
        "AbstractAbstractFileNode.createSheet.count.displayName=O",
        "AbstractAbstractFileNode.createSheet.count.noCentralRepo.description=Central repository was not enabled when this column was populated",
        "AbstractAbstractFileNode.createSheet.count.hashLookupNotRun.description=Hash lookup had not been run on this file when the column was populated",
        "# {0} - occuranceCount",
        "AbstractAbstractFileNode.createSheet.count.description=There were {0} datasource(s) found with occurances of the correlation value"})
    Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attribute) {
        Long count = -1L;  //The column renderer will not display negative values, negative value used when count unavailble to preserve sorting
        String description = Bundle.AbstractAbstractFileNode_createSheet_count_noCentralRepo_description();
        try {
            //don't perform the query if there is no correlation value
            if (attribute != null && StringUtils.isNotBlank(attribute.getCorrelationValue())) {
                count = EamDb.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(attribute.getCorrelationType(), attribute.getCorrelationValue());
                description = Bundle.AbstractAbstractFileNode_createSheet_count_description(count);
            } else if (attribute != null) {
                description = Bundle.AbstractAbstractFileNode_createSheet_count_hashLookupNotRun_description();
            }
        } catch (EamDbException ex) {
            logger.log(Level.WARNING, "Error getting count of datasources with correlation attribute", ex);
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, "Unable to normalize data to get count of datasources with correlation attribute", ex);
        }

        return Pair.of(count, description);
    }
    
    @NbBundle.Messages({
        "AbstractAbstractFileNode.createSheet.score.displayName=S",
        "AbstractAbstractFileNode.createSheet.notableFile.description=File recognized as notable.",
        "AbstractAbstractFileNode.createSheet.interestingResult.description=File has interesting result associated with it.",
        "AbstractAbstractFileNode.createSheet.taggedFile.description=File has been tagged.",
        "AbstractAbstractFileNode.createSheet.notableTaggedFile.description=File tagged with notable tag.",
        "AbstractAbstractFileNode.createSheet.noScore.description=No score"})
    Pair<DataResultViewerTable.Score, String> getScorePropertyAndDescription(List<ContentTag> tags) {
        DataResultViewerTable.Score score = DataResultViewerTable.Score.NO_SCORE;
        String description = "";
        if (content.getKnown() == TskData.FileKnown.BAD) {
            score = DataResultViewerTable.Score.NOTABLE_SCORE;
            description = Bundle.AbstractAbstractFileNode_createSheet_notableFile_description();
        }
        try {
            if (score == DataResultViewerTable.Score.NO_SCORE && !content.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT).isEmpty()) {
                score = DataResultViewerTable.Score.INTERESTING_SCORE;
                description = Bundle.AbstractAbstractFileNode_createSheet_interestingResult_description();
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting artifacts for file: " + content.getName(), ex);
        }
        if (!tags.isEmpty() && (score == DataResultViewerTable.Score.NO_SCORE || score == DataResultViewerTable.Score.INTERESTING_SCORE)) {
            score = DataResultViewerTable.Score.INTERESTING_SCORE;
            description = Bundle.AbstractAbstractFileNode_createSheet_taggedFile_description();
            for (ContentTag tag : tags) {
                if (tag.getName().getKnownStatus() == TskData.FileKnown.BAD) {
                    score = DataResultViewerTable.Score.NOTABLE_SCORE;
                    description = Bundle.AbstractAbstractFileNode_createSheet_notableTaggedFile_description();
                    break;
                }
            }
        }
        return Pair.of(score, description);
    }
    
    @NbBundle.Messages({
        "AbstractAbstractFileNode.createSheet.comment.displayName=C"})
    HasCommentStatus getCommentProperty(List<ContentTag> tags, CorrelationAttributeInstance attribute) {

        DataResultViewerTable.HasCommentStatus status = !tags.isEmpty() ? DataResultViewerTable.HasCommentStatus.TAG_NO_COMMENT : DataResultViewerTable.HasCommentStatus.NO_COMMENT;

        for (ContentTag tag : tags) {
            if (!StringUtils.isBlank(tag.getComment())) {
                //if the tag is null or empty or contains just white space it will indicate there is not a comment
                status = DataResultViewerTable.HasCommentStatus.TAG_COMMENT;
                break;
            }
        }
        if (attribute != null && !StringUtils.isBlank(attribute.getComment())) {
            if (status == DataResultViewerTable.HasCommentStatus.TAG_COMMENT) {
                status = DataResultViewerTable.HasCommentStatus.CR_AND_TAG_COMMENTS;
            } else {
                status = DataResultViewerTable.HasCommentStatus.CR_COMMENT;
            }
        }
        return status;
    }
    
    /**
     * Translates this nodes content name. Doesn't attempt translation if 
     * the name is in english or if there is now translation service available.
     */
    String getTranslatedFileName() {
        //If already in complete English, don't translate.
        if (content.getName().matches("^\\p{ASCII}+$")) {
            return "";
        }
        TextTranslationService tts = TextTranslationService.getInstance();
        if (tts.hasProvider()) {
            //Seperate out the base and ext from the contents file name.
            String base = FilenameUtils.getBaseName(content.getName());
            try {
                String translation = tts.translate(base);
                String ext = FilenameUtils.getExtension(content.getName());

                //If we have no extension, then we shouldn't add the .
                String extensionDelimiter = (ext.isEmpty()) ? "" : ".";

                //Talk directly to this nodes pcl, fire an update when the translation
                //is complete. 
                if (!translation.isEmpty()) {
                    return translation + extensionDelimiter + ext;
                }
            } catch (NoServiceProviderException noServiceEx) {
                logger.log(Level.WARNING, "Translate unsuccessful because no TextTranslator "
                        + "implementation was provided.", noServiceEx.getMessage());
            } catch (TranslationException noTranslationEx) {
                logger.log(Level.WARNING, "Could not successfully translate file name "
                        + content.getName(), noTranslationEx.getMessage());
            }
        }
        return "";
    }
    
    /**
     * Get all tags from the case database that are associated with the file
     *
     * @return a list of tags that are associated with the file
     */
    List<ContentTag> getContentTagsFromDatabase() {
        List<ContentTag> tags = new ArrayList<>();
        try {
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByContent(content));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for content " + content.getName(), ex);
        }
        return tags;
    }

    CorrelationAttributeInstance getCorrelationAttributeInstance() {
        CorrelationAttributeInstance attribute = null;
        if (EamDbUtil.useCentralRepo()) {
            attribute = EamArtifactUtil.getInstanceFromContent(content);
        }
        return attribute;
    }
    
    static String getContentPath(AbstractFile file) {
        try {
            return file.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Except while calling Content.getUniquePath() on " + file.getName(), ex); //NON-NLS
            return "";            //NON-NLS
        }
    }

   static  String getContentDisplayName(AbstractFile file) {
        String name = file.getName();
        switch (name) {
            case "..":
                return DirectoryNode.DOTDOTDIR;
            case ".":
                return DirectoryNode.DOTDIR;
            default:
                return name;
        }
    }

    /**
     * Fill map with AbstractFile properties
     *
     * @param map     map with preserved ordering, where property names/values
     *                are put
     * @param content The content to get properties for.
     * 
     * TODO JIRA-4421: Deprecate this method and resolve warnings that appear
     * in other locations.
     */
    static public void fillPropertyMap(Map<String, Object> map, AbstractFile content) {
        map.put(NAME.toString(), getContentDisplayName(content));
        map.put(LOCATION.toString(), getContentPath(content));
        map.put(MOD_TIME.toString(), ContentUtils.getStringTime(content.getMtime(), content));
        map.put(CHANGED_TIME.toString(), ContentUtils.getStringTime(content.getCtime(), content));
        map.put(ACCESS_TIME.toString(), ContentUtils.getStringTime(content.getAtime(), content));
        map.put(CREATED_TIME.toString(), ContentUtils.getStringTime(content.getCrtime(), content));
        map.put(SIZE.toString(), content.getSize());
        map.put(FLAGS_DIR.toString(), content.getDirFlagAsString());
        map.put(FLAGS_META.toString(), content.getMetaFlagsAsString());
        map.put(MODE.toString(), content.getModesAsString());
        map.put(USER_ID.toString(), content.getUid());
        map.put(GROUP_ID.toString(), content.getGid());
        map.put(META_ADDR.toString(), content.getMetaAddr());
        map.put(ATTR_ADDR.toString(), content.getAttrType().getValue() + "-" + content.getAttributeId());
        map.put(TYPE_DIR.toString(), content.getDirType().getLabel());
        map.put(TYPE_META.toString(), content.getMetaType().toString());
        map.put(KNOWN.toString(), content.getKnown().getName());
        map.put(MD5HASH.toString(), StringUtils.defaultString(content.getMd5Hash()));
        map.put(ObjectID.toString(), content.getId());
        map.put(MIMETYPE.toString(), StringUtils.defaultString(content.getMIMEType()));
        map.put(EXTENSION.toString(), content.getNameExtension());
    }
}