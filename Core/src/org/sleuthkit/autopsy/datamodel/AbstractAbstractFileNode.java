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
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.Score;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.datamodel.Bundle.*;
import static org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode.AbstractFilePropertyType.*;
import org.sleuthkit.autopsy.datamodel.SCOAndTranslationTask.SCOResults;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;

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

    private static final ExecutorService pool;
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
        pool = Executors.newFixedThreadPool(MAX_POOL_SIZE);
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
     * Currently, we have two property tasks in the background:
     *
     * 1) Retreiving the translation of the file name 2) Getting the SCO column
     * properties from the databases
     */
    enum NodeSpecificEvents {
        TRANSLATION_AVAILABLE,
        DABABASE_CONTENT_AVAILABLE;
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
                //No need to do any asynchrony around these events, they are so infrequent
                //and user driven that we can just keep a simple blocking approach, where we
                //go out to the database ourselves!
                List<ContentTag> tags = PropertyUtil.getContentTagsFromDatabase(content);
                
                updateProperty(new FileProperty(SCORE.toString()) {
                    Pair<Score, String> scorePropertyAndDescription
                            = PropertyUtil.getScorePropertyAndDescription(content, tags);

                    @Override
                    public Object getPropertyValue() {
                        return scorePropertyAndDescription.getLeft();
                    }

                    @Override
                    public String getDescription() {
                        return scorePropertyAndDescription.getRight();
                    }
                }, new FileProperty(COMMENT.toString()) {
                    @Override
                    public Object getPropertyValue() {
                        //Null out the correlation attribute because we are only 
                        //concerned with changes to the content tag, not the CR!
                        return PropertyUtil.getCommentProperty(tags, null);
                    }
                });
            }
        } else if (eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
            ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
            if (event.getDeletedTagInfo().getContentID() == content.getId()) {
                //No need to do any asynchrony around these events, they are so infrequent
                //and user driven that we can just keep a simple blocking approach, where we
                //go out to the database ourselves!
                List<ContentTag> tags = PropertyUtil.getContentTagsFromDatabase(content);
                
                updateProperty(new FileProperty(SCORE.toString()) {
                    Pair<Score, String> scorePropertyAndDescription
                            = PropertyUtil.getScorePropertyAndDescription(content, tags);

                    @Override
                    public Object getPropertyValue() {
                        return scorePropertyAndDescription.getLeft();
                    }

                    @Override
                    public String getDescription() {
                        return scorePropertyAndDescription.getRight();
                    }
                }, new FileProperty(COMMENT.toString()) {
                    @Override
                    public Object getPropertyValue() {
                        //Null out the correlation attribute because we are only 
                        //concerned with changes to the content tag, not the CR!
                        return PropertyUtil.getCommentProperty(tags, null);
                    }
                });
            }
        } else if (eventType.equals(Case.Events.CR_COMMENT_CHANGED.toString())) {
            CommentChangedEvent event = (CommentChangedEvent) evt;
            if (event.getContentID() == content.getId()) {
                //No need to do any asynchrony around these events, they are so infrequent
                //and user driven that we can just keep a simple blocking approach, where we
                //go out to the database ourselves!
                updateProperty(new FileProperty(COMMENT.toString()) {
                    @Override
                    public Object getPropertyValue() {
                        List<ContentTag> tags = PropertyUtil.getContentTagsFromDatabase(content);
                        CorrelationAttributeInstance attribute = PropertyUtil.getCorrelationAttributeInstance(content);
                        return PropertyUtil.getCommentProperty(tags, attribute);
                    }
                });
            }
        } else if (eventType.equals(NodeSpecificEvents.TRANSLATION_AVAILABLE.toString())) {
            updateProperty(new FileProperty(TRANSLATION.toString()) {
                @Override
                public Object getPropertyValue() {
                    return evt.getNewValue();
                }
            });
        } else if (eventType.equals(NodeSpecificEvents.DABABASE_CONTENT_AVAILABLE.toString())) {
            SCOResults results = (SCOResults) evt.getNewValue();
            updateProperty(new FileProperty(SCORE.toString()) {
                @Override
                public Object getPropertyValue() {
                    return results.getScore();
                }

                @Override
                public String getDescription() {
                    return results.getScoreDescription();
                }
            }, new FileProperty(COMMENT.toString()) {
                @Override
                public Object getPropertyValue() {
                    return results.getComment();
                }
            }, new FileProperty(OCCURRENCES.toString()) {
                @Override
                public Object getPropertyValue() {
                    return results.getCount();
                }

                @Override
                public String getDescription() {
                    return results.getCountDescription();
                }
            });
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
     * Returns a blank sheet to the caller, useful for giving subclasses the 
     * ability to override createSheet() with their own implementation.
     * 
     * @return 
     */
    protected Sheet getBlankSheet() {
        return super.createSheet();
    }

    /**
     * Updates the values of the properties in the current property sheet with
     * the new properties being passed in! Only if that property exists in the
     * current sheet will it be applied. That way, we allow for subclasses to
     * add their own (or omit some!) properties and we will not accidentally
     * disrupt their UI.
     *
     * Race condition if not synchronized. Only one update should be applied at a time.
     * The timing of currSheetSet.getProperties() could result in wrong/stale data
     * being shown!
     *
     * @param newProps New file property instances to be updated in the current
     *                 sheet.
     */
    private synchronized void updateProperty(FileProperty... newProps) {

        //Refresh ONLY those properties in the sheet currently. Subclasses may have 
        //only added a subset of our properties or their own props! Let's keep their UI correct.
        Sheet currSheet = this.getSheet();
        Sheet.Set currSheetSet = currSheet.get(Sheet.PROPERTIES);
        Property<?>[] currProps = currSheetSet.getProperties();

        for (int i = 0; i < currProps.length; i++) {
            for (FileProperty property : newProps) {
                if (currProps[i].getName().equals(property.getPropertyName())) {
                    currProps[i] = new NodeProperty<>(
                            property.getPropertyName(),
                            property.getPropertyName(),
                            property.getDescription(),
                            property.getPropertyValue());
                }
            }
        }

        currSheetSet.put(currProps);
        currSheet.put(currSheetSet);

        //setSheet() will notify Netbeans to update this node in the UI!
        this.setSheet(currSheet);
    }

    /*
     * This is called when the node is first initialized. Any new updates or changes 
     * happen by directly manipulating the sheet. That means we can fire off background
     * events everytime this method is called and not worry about duplicated jobs!
     */
    @Override
    protected synchronized Sheet createSheet() {
        Sheet sheet = getBlankSheet();
        Sheet.Set sheetSet = Sheet.createPropertiesSet();
        sheet.put(sheetSet);

        //This will fire off fresh background tasks.
        List<FileProperty> newProperties = getProperties();

        //Add only the enabled properties to the sheet!
        for (FileProperty property : newProperties) {
            if (property.isEnabled()) {
                sheetSet.put(new NodeProperty<>(
                        property.getPropertyName(),
                        property.getPropertyName(),
                        property.getDescription(),
                        property.getPropertyValue()));
            }
        }

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
     * Creates a list of properties for this file node. Each property has its
     * own strategy for producing a value, its own description, name, and
     * ability to be disabled. The FileProperty abstract class provides a
     * wrapper for all of these characteristics. Additionally, with a return
     * value of a list, any children classes of this node may reorder or omit
     * any of these properties as they see fit for their use case.
     *
     * @return List of file properties associated with this file node's content.
     */
    List<FileProperty> getProperties() {
        List<FileProperty> properties = new ArrayList<>();

        properties.add(new FileProperty(NAME.toString()) {
            @Override
            public Object getPropertyValue() {
                return getContentDisplayName(content);
            }
        });
        
        //Initialize dummy place holder properties! These obviously do no work
        //to get their property values, but at the bottom we kick off a background
        //task that promises to update these values.
        final String NO_OP = "";
        properties.add(new FileProperty(TRANSLATION.toString()) {
            @Override
            public Object getPropertyValue() {
                return NO_OP;
            }

            @Override
            public boolean isEnabled() {
                return UserPreferences.displayTranslationFileNames();
            }
        });

        properties.add(new FileProperty(SCORE.toString()) {
            @Override
            public Object getPropertyValue() {
                return NO_OP;
            }
        });
        properties.add(new FileProperty(COMMENT.toString()) {
            @Override
            public Object getPropertyValue() {
                return NO_OP;
            }

            @Override
            public boolean isEnabled() {
                return !UserPreferences.hideCentralRepoCommentsAndOccurrences();
            }
        });
        properties.add(new FileProperty(OCCURRENCES.toString()) {
            @Override
            public Object getPropertyValue() {
                return NO_OP;
            }

            @Override
            public boolean isEnabled() {
                return !UserPreferences.hideCentralRepoCommentsAndOccurrences();
            }
        });
        properties.add(new FileProperty(LOCATION.toString()) {
            @Override
            public Object getPropertyValue() {
                return getContentPath(content);
            }
        });
        properties.add(new FileProperty(MOD_TIME.toString()) {
            @Override
            public Object getPropertyValue() {
                return ContentUtils.getStringTime(content.getMtime(), content);
            }
        });
        properties.add(new FileProperty(CHANGED_TIME.toString()) {
            @Override
            public Object getPropertyValue() {
                return ContentUtils.getStringTime(content.getCtime(), content);
            }
        });
        properties.add(new FileProperty(ACCESS_TIME.toString()) {
            @Override
            public Object getPropertyValue() {
                return ContentUtils.getStringTime(content.getAtime(), content);
            }
        });
        properties.add(new FileProperty(CREATED_TIME.toString()) {
            @Override
            public Object getPropertyValue() {
                return ContentUtils.getStringTime(content.getCrtime(), content);
            }
        });
        properties.add(new FileProperty(SIZE.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getSize();
            }
        });
        properties.add(new FileProperty(FLAGS_DIR.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getDirFlagAsString();
            }
        });
        properties.add(new FileProperty(FLAGS_META.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getMetaFlagsAsString();
            }
        });
        properties.add(new FileProperty(MODE.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getModesAsString();
            }
        });
        properties.add(new FileProperty(USER_ID.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getUid();
            }
        });
        properties.add(new FileProperty(GROUP_ID.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getGid();
            }
        });
        properties.add(new FileProperty(META_ADDR.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getMetaAddr();
            }
        });
        properties.add(new FileProperty(ATTR_ADDR.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getAttrType().getValue() + "-" + content.getAttributeId();
            }
        });
        properties.add(new FileProperty(TYPE_DIR.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getDirType().getLabel();
            }
        });
        properties.add(new FileProperty(TYPE_META.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getMetaType().toString();
            }
        });
        properties.add(new FileProperty(KNOWN.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getKnown().getName();
            }
        });
        properties.add(new FileProperty(MD5HASH.toString()) {
            @Override
            public Object getPropertyValue() {
                return StringUtils.defaultString(content.getMd5Hash());
            }
        });
        properties.add(new FileProperty(ObjectID.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getId();
            }
        });
        properties.add(new FileProperty(MIMETYPE.toString()) {
            @Override
            public Object getPropertyValue() {
                return StringUtils.defaultString(content.getMIMEType());
            }
        });
        properties.add(new FileProperty(EXTENSION.toString()) {
            @Override
            public Object getPropertyValue() {
                return content.getNameExtension();
            }
        });

        //Submit the database queries ASAP! We want updated SCO columns
        //without blocking the UI as soon as we can get it! Keep all weak references
        //so this task doesn't block the ability of this node to be GC'd. Handle potentially
        //null reference values in the Task!
        pool.submit(new SCOAndTranslationTask(new WeakReference<>(content), weakPcl));
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

    /**
     * Fill map with AbstractFile properties
     *
     * @param map     map with preserved ordering, where property names/values
     *                are put
     * @param content The content to get properties for.
     */
    @Deprecated
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
