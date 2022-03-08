/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.CommentChangedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.HasCommentStatus;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.datamodel.Bundle.*;
import static org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode.AbstractFilePropertyType.*;
import org.sleuthkit.autopsy.datamodel.BaseChildFactory.NoSuchEventBusException;
import org.sleuthkit.autopsy.datamodel.BaseChildFactory.RefreshKeysEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.CONTENT_CHANGED;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.texttranslation.utils.FileNameTranslationUtil;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * An abstract node that encapsulates AbstractFile data
 *
 * @param <T> type of the AbstractFile to encapsulate
 */
public abstract class AbstractAbstractFileNode<T extends AbstractFile> extends AbstractContentNode<T> {

    private static final Logger logger = Logger.getLogger(AbstractAbstractFileNode.class.getName());

    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.CURRENT_CASE,
            Case.Events.CONTENT_TAG_ADDED, Case.Events.CONTENT_TAG_DELETED, Case.Events.CR_COMMENT_CHANGED);
    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(CONTENT_CHANGED);

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
                IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, weakPcl);
            }
        }
        
        // Add listener if this is the carved files base directory.
        if (VirtualDirectory.NAME_CARVED.equals(abstractFile.getName())) {
            IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, weakPcl);
        }

        try {
            //See JIRA-5971
            //Attempt to cache file path during construction of this UI component.
            this.content.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Failed attempt to cache the "
                    + "unique path of the abstract file instance. Name: %s (objID=%d)",
                    this.content.getName(), this.content.getId()), ex);
        }

        if (TextTranslationService.getInstance().hasProvider() && UserPreferences.displayTranslatedFileNames()) {
            backgroundTasksPool.submit(new TranslationTask(
                    new WeakReference<>(this), weakPcl));
        }

        // Listen for case events so that we can detect when the case is closed
        // or when tags are added.
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
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
                    // We only want to refresh our parents children if we are in the
                    // data sources branch of the tree. The parent nodes in other
                    // branches of the tree (e.g. File Types and Deleted Files) do
                    // not need to be refreshed.
                    if (VirtualDirectory.NAME_CARVED.equals(getContent().getName())) {
                        // If the current node is the carved files directory, we need to refresh it
                        BaseChildFactory.post(getName(), new RefreshKeysEvent());
                    } else {
                        // Otherwise we need to refresh the parent node
                        BaseChildFactory.post(getParentNode().getName(), new RefreshKeysEvent());
                    }
                } catch (NullPointerException ex) {
                    // Skip
                } catch (NoSuchEventBusException ex) {
                    logger.log(Level.WARNING, "Failed to post key refresh event", ex); //NON-NLS
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

                Pair<Score, String> scorePropAndDescr = getScorePropertyAndDescription();
                Score value = scorePropAndDescr.getLeft();
                String descr = scorePropAndDescr.getRight();
                List<CorrelationAttributeInstance> listWithJustFileAttr = new ArrayList<>();
                CorrelationAttributeInstance corrInstance = CorrelationAttributeUtil.getCorrAttrForFile(content);
                if (corrInstance != null) {
                    listWithJustFileAttr.add(corrInstance);
                }
                updateSheet(new NodeProperty<>(SCORE.toString(), SCORE.toString(), descr, value),
                        new NodeProperty<>(COMMENT.toString(), COMMENT.toString(), NO_DESCR, getCommentProperty(getAllTagsFromDatabase(), listWithJustFileAttr))
                );
            }
        } else if (eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
            ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
            if (event.getDeletedTagInfo().getContentID() == content.getId()) {
                List<Tag> tags = getAllTagsFromDatabase();
                Pair<Score, String> scorePropAndDescr = getScorePropertyAndDescription();
                Score value = scorePropAndDescr.getLeft();
                String descr = scorePropAndDescr.getRight();
                List<CorrelationAttributeInstance> listWithJustFileAttr = new ArrayList<>();
                CorrelationAttributeInstance corrInstance = CorrelationAttributeUtil.getCorrAttrForFile(content);
                if (corrInstance != null) {
                    listWithJustFileAttr.add(corrInstance);
                }
                updateSheet(new NodeProperty<>(SCORE.toString(), SCORE.toString(), descr, value),
                        new NodeProperty<>(COMMENT.toString(), COMMENT.toString(), NO_DESCR, getCommentProperty(getAllTagsFromDatabase(), listWithJustFileAttr))
                );
            }
        } else if (eventType.equals(Case.Events.CR_COMMENT_CHANGED.toString())) {
            CommentChangedEvent event = (CommentChangedEvent) evt;
            if (event.getContentID() == content.getId()) {
                List<CorrelationAttributeInstance> listWithJustFileAttr = new ArrayList<>();
                CorrelationAttributeInstance corrInstance = CorrelationAttributeUtil.getCorrAttrForFile(content);
                if (corrInstance != null) {
                    listWithJustFileAttr.add(corrInstance);
                }
                updateSheet(new NodeProperty<>(COMMENT.toString(), COMMENT.toString(), NO_DESCR, getCommentProperty(getAllTagsFromDatabase(), listWithJustFileAttr)));
            }
        } else if (eventType.equals(NodeSpecificEvents.TRANSLATION_AVAILABLE.toString())) {
            this.setDisplayName(evt.getNewValue().toString());
            //Set the tooltip
            this.setShortDescription(content.getName());
            updateSheet(new NodeProperty<>(ORIGINAL_NAME.toString(), ORIGINAL_NAME.toString(), NO_DESCR, content.getName()));
        } else if (eventType.equals(NodeSpecificEvents.SCO_AVAILABLE.toString()) && !UserPreferences.getHideSCOColumns()) {
            SCOData scoData = (SCOData) evt.getNewValue();
            if (scoData.getScoreAndDescription() != null) {
                updateSheet(new NodeProperty<>(SCORE.toString(), SCORE.toString(), scoData.getScoreAndDescription().getRight(), scoData.getScoreAndDescription().getLeft()));
            }
            if (scoData.getComment() != null) {
                updateSheet(new NodeProperty<>(COMMENT.toString(), COMMENT.toString(), NO_DESCR, scoData.getComment()));
            }
            if (scoData.getCountAndDescription() != null) {
                updateSheet(new NodeProperty<>(OCCURRENCES.toString(), OCCURRENCES.toString(), scoData.getCountAndDescription().getRight(), scoData.getCountAndDescription().getLeft()));
            }
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

        return sheet;
    }

    @NbBundle.Messages({"AbstractAbstractFileNode.nameColLbl=Name",
        "AbstractAbstractFileNode.originalName=Original Name",
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
        "AbstractAbstractFileNode.sha256HashColLbl=SHA-256 Hash",
        "AbstractAbstractFileNode.objectId=Object ID",
        "AbstractAbstractFileNode.mimeType=MIME Type",
        "AbstractAbstractFileNode.extensionColLbl=Extension"})
    public enum AbstractFilePropertyType {

        NAME(AbstractAbstractFileNode_nameColLbl()),
        ORIGINAL_NAME(AbstractAbstractFileNode_originalName()),
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
        SHA256HASH(AbstractAbstractFileNode_sha256HashColLbl()),
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

        if (TextTranslationService.getInstance().hasProvider() && UserPreferences.displayTranslatedFileNames()) {
            properties.add(new NodeProperty<>(ORIGINAL_NAME.toString(), ORIGINAL_NAME.toString(), NO_DESCR, ""));
        }

        // Create place holders for S C O 
        if (!UserPreferences.getHideSCOColumns()) {
            properties.add(new NodeProperty<>(SCORE.toString(), SCORE.toString(), VALUE_LOADING, ""));
            properties.add(new NodeProperty<>(COMMENT.toString(), COMMENT.toString(), VALUE_LOADING, ""));
            if (CentralRepository.isEnabled()) {
                properties.add(new NodeProperty<>(OCCURRENCES.toString(), OCCURRENCES.toString(), VALUE_LOADING, ""));
            }
            // Get the SCO columns data in a background task
            backgroundTasksPool.submit(new GetSCOTask(
                    new WeakReference<>(this), weakPcl));
        }

        properties.add(new NodeProperty<>(MOD_TIME.toString(), MOD_TIME.toString(), NO_DESCR, TimeZoneUtils.getFormattedTime(content.getMtime())));
        properties.add(new NodeProperty<>(CHANGED_TIME.toString(), CHANGED_TIME.toString(), NO_DESCR, TimeZoneUtils.getFormattedTime(content.getCtime())));
        properties.add(new NodeProperty<>(ACCESS_TIME.toString(), ACCESS_TIME.toString(), NO_DESCR, TimeZoneUtils.getFormattedTime(content.getAtime())));
        properties.add(new NodeProperty<>(CREATED_TIME.toString(), CREATED_TIME.toString(), NO_DESCR, TimeZoneUtils.getFormattedTime(content.getCrtime())));
        properties.add(new NodeProperty<>(SIZE.toString(), SIZE.toString(), NO_DESCR, content.getSize()));
        properties.add(new NodeProperty<>(FLAGS_DIR.toString(), FLAGS_DIR.toString(), NO_DESCR, content.getDirFlagAsString()));
        properties.add(new NodeProperty<>(FLAGS_META.toString(), FLAGS_META.toString(), NO_DESCR, content.getMetaFlagsAsString()));
        properties.add(new NodeProperty<>(KNOWN.toString(), KNOWN.toString(), NO_DESCR, content.getKnown().getName()));
        properties.add(new NodeProperty<>(LOCATION.toString(), LOCATION.toString(), NO_DESCR, getContentPath(content)));
        properties.add(new NodeProperty<>(MD5HASH.toString(), MD5HASH.toString(), NO_DESCR, StringUtils.defaultString(content.getMd5Hash())));
        properties.add(new NodeProperty<>(SHA256HASH.toString(), SHA256HASH.toString(), NO_DESCR, StringUtils.defaultString(content.getSha256Hash())));
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
        "AbstractAbstractFileNode.createSheet.count.hashLookupNotRun.description=Hash lookup had not been run on this file when the column was populated",
        "# {0} - occurrenceCount",
        "AbstractAbstractFileNode.createSheet.count.description=There were {0} datasource(s) found with occurrences of the MD5 correlation value"})
    @Override
    protected Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attributeInstance, String defaultDescription) {
        Long count = -1L;  //The column renderer will not display negative values, negative value used when count unavailble to preserve sorting
        String description = defaultDescription;
        try {
            //don't perform the query if there is no correlation value
            if (attributeInstance != null && StringUtils.isNotBlank(attributeInstance.getCorrelationValue())) {
                count = CentralRepository.getInstance().getCountCasesWithOtherInstances(attributeInstance);
                description = Bundle.AbstractAbstractFileNode_createSheet_count_description(count);
            } else if (attributeInstance != null) {
                description = Bundle.AbstractAbstractFileNode_createSheet_count_hashLookupNotRun_description();
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error getting count of datasources with correlation attribute", ex);
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, "Unable to normalize data to get count of datasources with correlation attribute", ex);
        }
        return Pair.of(count, description);
    }

    @NbBundle.Messages({
        "AbstractAbstractFileNode.createSheet.comment.displayName=C"})
    @Override
    protected HasCommentStatus getCommentProperty(List<Tag> tags, List<CorrelationAttributeInstance> attributes) {
        DataResultViewerTable.HasCommentStatus status = !tags.isEmpty() ? DataResultViewerTable.HasCommentStatus.TAG_NO_COMMENT : DataResultViewerTable.HasCommentStatus.NO_COMMENT;
        for (Tag tag : tags) {
            if (!StringUtils.isBlank(tag.getComment())) {
                //if the tag is null or empty or contains just white space it will indicate there is not a comment
                status = DataResultViewerTable.HasCommentStatus.TAG_COMMENT;
                break;
            }
        }
        /*
         * Is there a comment in the CR for anything that matches the value and
         * type of the specified attributes.
         */
        try {
            if (CentralRepoDbUtil.commentExistsOnAttributes(attributes)) {
                if (status == DataResultViewerTable.HasCommentStatus.TAG_COMMENT) {
                    status = DataResultViewerTable.HasCommentStatus.CR_AND_TAG_COMMENTS;
                } else {
                    status = DataResultViewerTable.HasCommentStatus.CR_COMMENT;
                }
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Attempted to Query CR for presence of comments in a file node and was unable to perform query, comment column will only reflect caseDB", ex);
        }
        return status;
    }

    /**
     * Translates the name of the file this node represents. An empty string
     * will be returned if the translation fails for any reason.
     *
     * @return The translated file name or the empty string.
     */
    String getTranslatedFileName() {
        try {
            return FileNameTranslationUtil.translate(content.getName());
        } catch (NoServiceProviderException | TranslationException ex) {
            logger.log(Level.WARNING, MessageFormat.format("Error translating file name (objID={0}))", content.getId()), ex);
            return "";
        }
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

    @Override
    protected List<Tag> getAllTagsFromDatabase() {
        return new ArrayList<>(getContentTagsFromDatabase());
    }

    static String getContentPath(AbstractFile file) {
        try {
            return file.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Except while calling Content.getUniquePath() on " + file.getName(), ex); //NON-NLS
            return "";            //NON-NLS
        }
    }

    static String getContentDisplayName(AbstractFile file) {
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
     * TODO JIRA-4421: Deprecate this method and resolve warnings that appear in
     * other locations.
     */
    static public void fillPropertyMap(Map<String, Object> map, AbstractFile content) {
        map.put(NAME.toString(), getContentDisplayName(content));
        map.put(LOCATION.toString(), getContentPath(content));
        map.put(MOD_TIME.toString(), TimeZoneUtils.getFormattedTime(content.getMtime()));
        map.put(CHANGED_TIME.toString(), TimeZoneUtils.getFormattedTime(content.getCtime()));
        map.put(ACCESS_TIME.toString(), TimeZoneUtils.getFormattedTime(content.getAtime()));
        map.put(CREATED_TIME.toString(), TimeZoneUtils.getFormattedTime(content.getCrtime()));
        map.put(SIZE.toString(), content.getSize());
        map.put(FLAGS_DIR.toString(), content.getDirFlagAsString());
        map.put(FLAGS_META.toString(), content.getMetaFlagsAsString());
        map.put(KNOWN.toString(), content.getKnown().getName());
        map.put(MD5HASH.toString(), StringUtils.defaultString(content.getMd5Hash()));
        map.put(SHA256HASH.toString(), StringUtils.defaultString(content.getSha256Hash()));
        map.put(MIMETYPE.toString(), StringUtils.defaultString(content.getMIMEType()));
        map.put(EXTENSION.toString(), content.getNameExtension());
    }
}
