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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
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
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.Score;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.datamodel.Bundle.*;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.HasCommentStatus;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.datamodel.CustomFileProperty;
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
        } else if (eventType.equals(Case.Events.CONTENT_TAG_ADDED.toString())) {
            ContentTagAddedEvent event = (ContentTagAddedEvent) evt;
            if (event.getAddedTag().getContent().equals(content)) {
                updateSheet();
            }
        } else if (eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
            ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
            if (event.getDeletedTagInfo().getContentID() == content.getId()) {
                updateSheet();
            }
        } else if (eventType.equals(Case.Events.CR_COMMENT_CHANGED.toString())) {
            CommentChangedEvent event = (CommentChangedEvent) evt;
            if (event.getContentID() == content.getId()) {
                updateSheet();
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

    private void updateSheet() {
        this.setSheet(createSheet());
    }
    
    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }
        
        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, getContent());
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String desc = Bundle.AbstractFsContentNode_noDesc_text();
            
            
            FileProperty p  = getFilePropertyFromDisplayName(entry.getKey());
            if(!p.getDescription(content).isEmpty()) {
                desc = p.getDescription(content);
            }
            sheetSet.put(new NodeProperty<>(entry.getKey(), entry.getKey(), desc, entry.getValue()));
        }
        
        return sheet;
    }
    
    private FileProperty getFilePropertyFromDisplayName(String displayName) {
        FileProperty p = AbstractFilePropertyType.getPropertyFromDisplayName(displayName);
        if(p != null) {
            return p;
        } else {
            Optional<Collection<? extends CustomFileProperty>> customProperties = getCustomProperties();
            if(customProperties.isPresent()) {
                for(CustomFileProperty cp : customProperties.get()) {
                    if (cp.getPropertyName().equals(displayName)) {
                        return cp;
                    }
                }   
            }
            return null;
        }
    }
    
    private static Optional<Collection<? extends CustomFileProperty>> getCustomProperties() {
        return Optional.ofNullable(Lookup.getDefault().lookupAll(CustomFileProperty.class));
    }
    
    @NbBundle.Messages({"AbstractAbstractFileNode.nameColLbl=Name",
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
    public enum AbstractFilePropertyType implements FileProperty {

        NAME(AbstractAbstractFileNode_nameColLbl()) {
            @Override
            public  Object getPropertyValue(AbstractFile content) {
                return getContentDisplayName(content);
            }
        },
        SCORE(AbstractAbstractFileNode_createSheet_score_name()) {
            Optional<Pair<String, Score>> result = Optional.empty();
            
            private void initResult(AbstractFile content) {
                scoreAndCommentTags = getContentTagsFromDatabase(content);
                result = Optional.of(getScoreProperty(content, scoreAndCommentTags));   
            }
            
           @Override
            public Object getPropertyValue(AbstractFile content) {
                if(!this.result.isPresent()){
                    initResult(content);   
                }
                return result.get().getRight();
            } 
            
            @Override
            public String getDescription(AbstractFile content) {
                if(!this.result.isPresent()){
                   initResult(content);  
                }
                return result.get().getLeft();
            }
        },
        COMMENT(AbstractAbstractFileNode_createSheet_comment_name()) {
            Optional<Pair<String, HasCommentStatus>> result = Optional.empty();
            
            private void initResult(AbstractFile content) {
                scoreAndCommentTags = getContentTagsFromDatabase(content);
                correlationAttribute = null;
                if(EamDbUtil.useCentralRepo() && 
                    !UserPreferences.hideCentralRepoCommentsAndOccurrences()){
                    if (EamDbUtil.useCentralRepo() && UserPreferences.hideCentralRepoCommentsAndOccurrences() == false) {
                        correlationAttribute = getCorrelationAttributeInstance(content);
                    }
                }  
                result = Optional.of(getCommentProperty(scoreAndCommentTags, correlationAttribute));
            }
            
            @Override
            public Object getPropertyValue(AbstractFile content) {
                if(!this.result.isPresent()){
                    initResult(content);
                }
                return result.get().getRight();
            } 
            
            @Override
            public String getDescription(AbstractFile content) {
                if(!this.result.isPresent()){
                    initResult(content);
                }
                return result.get().getLeft();
            }
        },
        COUNT(AbstractAbstractFileNode_createSheet_count_name()) {
            Optional<Pair<String, Long>> result = Optional.empty();
            
            private void initResult(AbstractFile content) {
                result = Optional.of(getCountProperty(correlationAttribute));
            }
            
            
            @Override
            public Object getPropertyValue(AbstractFile content) {
                if(!result.isPresent()){
                    initResult(content);
                }
                return result.get().getRight();
            }

            @Override
            public boolean isDisabled() {
                return !EamDbUtil.useCentralRepo() || 
                        UserPreferences.hideCentralRepoCommentsAndOccurrences();
            }

            @Override
            public String getDescription(AbstractFile content) {
                if(!result.isPresent()){
                    initResult(content);
                }
                return result.get().getLeft();
            }
            
        },
        LOCATION(AbstractAbstractFileNode_locationColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return getContentPath(content);
            }
        },
        MOD_TIME(AbstractAbstractFileNode_modifiedTimeColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return ContentUtils.getStringTime(content.getMtime(), content);
            }
        },
        CHANGED_TIME(AbstractAbstractFileNode_changeTimeColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return ContentUtils.getStringTime(content.getCtime(), content);
            }
        },
        ACCESS_TIME(AbstractAbstractFileNode_accessTimeColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return ContentUtils.getStringTime(content.getAtime(), content);
            }
        },
        CREATED_TIME(AbstractAbstractFileNode_createdTimeColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return ContentUtils.getStringTime(content.getCrtime(), content);
            }
        },
        SIZE(AbstractAbstractFileNode_sizeColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getSize();
            }
        },
        FLAGS_DIR(AbstractAbstractFileNode_flagsDirColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getDirFlagAsString();
            }
        },
        FLAGS_META(AbstractAbstractFileNode_flagsMetaColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getMetaFlagsAsString();
            }
        },
        MODE(AbstractAbstractFileNode_modeColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getModesAsString();
            }
        },
        USER_ID(AbstractAbstractFileNode_useridColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getUid();
            }
        },
        GROUP_ID(AbstractAbstractFileNode_groupidColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getGid();
            }
        },
        META_ADDR(AbstractAbstractFileNode_metaAddrColLbl()) {
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getMetaAddr();
            }
        },
        ATTR_ADDR(AbstractAbstractFileNode_attrAddrColLbl()){
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getAttrType().getValue() + "-" + content.getAttributeId();
            }
        },
        TYPE_DIR(AbstractAbstractFileNode_typeDirColLbl()){
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getDirType().getLabel();
            }
        },
        TYPE_META(AbstractAbstractFileNode_typeMetaColLbl()){
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getMetaType().toString();
            }
        },
        KNOWN(AbstractAbstractFileNode_knownColLbl()){
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getKnown().getName();
            }
        },
        MD5HASH(AbstractAbstractFileNode_md5HashColLbl()){
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return StringUtils.defaultString(content.getMd5Hash());
            }
        },
        ObjectID(AbstractAbstractFileNode_objectId()){
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getId();
            }
        },
        MIMETYPE(AbstractAbstractFileNode_mimeType()){
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return StringUtils.defaultString(content.getMIMEType());
            }
        },
        EXTENSION(AbstractAbstractFileNode_extensionColLbl()){
            @Override
            public Object getPropertyValue(AbstractFile content) {
                return content.getNameExtension();
            }
        };

        private static List<ContentTag> scoreAndCommentTags;
        private static CorrelationAttributeInstance correlationAttribute;
        final private String displayString;

        private AbstractFilePropertyType(String displayString) {
            this.displayString = displayString;
        }

        @Override
        public String toString() {
            return displayString;
        }
        
        public static FileProperty getPropertyFromDisplayName(String displayName) {
            for(FileProperty p : AbstractFilePropertyType.values()) {
                if(p.getPropertyName().equals(displayName)) {
                    return p;
                }
            }
            return null;
        }
    }

    /**
     * Fill map with AbstractFile properties
     *
     * @param map     map with preserved ordering, where property names/values
     *                are put
     * @param content The content to get properties for.
     */
    static public void fillPropertyMap(Map<String, Object> map, AbstractFile content) {
        //Load in our default properties.
        ArrayList<FileProperty> properties = new ArrayList<>();
        properties.addAll(Arrays.asList(AbstractFilePropertyType.values()));
        
        //Load in our custom properties
        Optional<Collection<? extends CustomFileProperty>> customProperties = getCustomProperties();
        if (customProperties.isPresent()) {
            customProperties.get().forEach((p) -> {
                //Inject custom properties at the desired column positions
                //Specified by the custom property itself.
                properties.add(p.getColumnPosition(), p);
            });
        }
        
        //Skip properties that are disabled, don't add them to the property map!
        properties.stream().filter(p -> !p.isDisabled()).forEach((p) -> {
            map.put(p.getPropertyName(), p.getPropertyValue(content));
        });
    }

    /**
     * Get all tags from the case database that are associated with the file
     *
     * @return a list of tags that are associated with the file
     */
    private static List<ContentTag> getContentTagsFromDatabase(AbstractFile content) {
        List<ContentTag> tags = new ArrayList<>();
        try {
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByContent(content));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for content " + content.getName(), ex);
        }
        return tags;
    }

    private static CorrelationAttributeInstance getCorrelationAttributeInstance(AbstractFile content) {
        CorrelationAttributeInstance correlationAttribute = null;
        if (EamDbUtil.useCentralRepo()) {
            correlationAttribute = EamArtifactUtil.getInstanceFromContent(content);
        }
        return correlationAttribute;
    }

    /**
     * Used by subclasses of AbstractAbstractFileNode to add the comment
     * property to their sheets.
     *
     * @param sheetSet  the modifiable Sheet.Set returned by
     *                  Sheet.get(Sheet.PROPERTIES)
     * @param tags      the list of tags associated with the file
     * @param attribute the correlation attribute associated with this file,
     *                  null if central repo is not enabled
     */
    @NbBundle.Messages({
        "AbstractAbstractFileNode.createSheet.comment.displayName=C"})
    private static Pair<String, HasCommentStatus> getCommentProperty(List<ContentTag> tags, CorrelationAttributeInstance attribute) {

        HasCommentStatus status = tags.size() > 0 ? HasCommentStatus.TAG_NO_COMMENT : HasCommentStatus.NO_COMMENT;

        for (ContentTag tag : tags) {
            if (!StringUtils.isBlank(tag.getComment())) {
                //if the tag is null or empty or contains just white space it will indicate there is not a comment
                status = HasCommentStatus.TAG_COMMENT;
                break;
            }
        }
        if (attribute != null && !StringUtils.isBlank(attribute.getComment())) {
            if (status == HasCommentStatus.TAG_COMMENT) {
                status = HasCommentStatus.CR_AND_TAG_COMMENTS;
            } else {
                status = HasCommentStatus.CR_COMMENT;
            }
        }
        return Pair.of(NO_DESCR, status);
    }

    /**
     * Used by subclasses of AbstractAbstractFileNode to add the Score property
     * to their sheets.
     *
     * @param sheetSet the modifiable Sheet.Set returned by
     *                 Sheet.get(Sheet.PROPERTIES)
     * @param tags     the list of tags associated with the file
     */
    @NbBundle.Messages({
        "AbstractAbstractFileNode.createSheet.score.displayName=S",
        "AbstractAbstractFileNode.createSheet.notableFile.description=File recognized as notable.",
        "AbstractAbstractFileNode.createSheet.interestingResult.description=File has interesting result associated with it.",
        "AbstractAbstractFileNode.createSheet.taggedFile.description=File has been tagged.",
        "AbstractAbstractFileNode.createSheet.notableTaggedFile.description=File tagged with notable tag.",
        "AbstractAbstractFileNode.createSheet.noScore.description=No score"})
    private static Pair<String, Score> getScoreProperty(AbstractFile content, List<ContentTag> tags) {
        Score score = Score.NO_SCORE;
        String description = Bundle.AbstractAbstractFileNode_createSheet_noScore_description();
        if (content.getKnown() == TskData.FileKnown.BAD) {
            score = Score.NOTABLE_SCORE;
            description = Bundle.AbstractAbstractFileNode_createSheet_notableFile_description();
        }
        try {
            if (score == Score.NO_SCORE && !content.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT).isEmpty()) {
                score = Score.INTERESTING_SCORE;
                description = Bundle.AbstractAbstractFileNode_createSheet_interestingResult_description();
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting artifacts for file: " + content.getName(), ex);
        }
        if (tags.size() > 0 && (score == Score.NO_SCORE || score == Score.INTERESTING_SCORE)) {
            score = Score.INTERESTING_SCORE;
            description = Bundle.AbstractAbstractFileNode_createSheet_taggedFile_description();
            for (ContentTag tag : tags) {
                if (tag.getName().getKnownStatus() == TskData.FileKnown.BAD) {
                    score = Score.NOTABLE_SCORE;
                    description = Bundle.AbstractAbstractFileNode_createSheet_notableTaggedFile_description();
                    break;
                }
            }
        }
        return Pair.of(description, score);
    }

    @NbBundle.Messages({
        "AbstractAbstractFileNode.createSheet.count.displayName=O",
        "AbstractAbstractFileNode.createSheet.count.noCentralRepo.description=Central repository was not enabled when this column was populated",
        "AbstractAbstractFileNode.createSheet.count.hashLookupNotRun.description=Hash lookup had not been run on this file when the column was populated",
        "# {0} - occuranceCount",
        "AbstractAbstractFileNode.createSheet.count.description=There were {0} datasource(s) found with occurances of the correlation value"})
    private static Pair<String, Long> getCountProperty(CorrelationAttributeInstance attribute) {
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
        
        return Pair.of(description, count);
    }

    /**
     * Used by subclasses of AbstractAbstractFileNode to add the tags property
     * to their sheets.
     *
     * @param sheetSet the modifiable Sheet.Set returned by
     *                 Sheet.get(Sheet.PROPERTIES)
     * @deprecated
     */
    @NbBundle.Messages("AbstractAbstractFileNode.tagsProperty.displayName=Tags")
    @Deprecated
    protected void addTagProperty(Sheet.Set sheetSet) {
        List<ContentTag> tags = new ArrayList<>();
        try {
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByContent(content));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for content " + content.getName(), ex);
        }
        sheetSet.put(new NodeProperty<>("Tags", AbstractAbstractFileNode_tagsProperty_displayName(),
                NO_DESCR, tags.stream().map(t -> t.getName().getDisplayName())
                        .distinct()
                        .collect(Collectors.joining(", "))));
    }

    private static String getContentPath(AbstractFile file) {
        try {
            return file.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Except while calling Content.getUniquePath() on " + file, ex); //NON-NLS
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
     * Gets a comma-separated values list of the names of the hash sets
     * currently identified as including a given file.
     *
     * @param file The file.
     *
     * @return The CSV list of hash set names.
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
}
