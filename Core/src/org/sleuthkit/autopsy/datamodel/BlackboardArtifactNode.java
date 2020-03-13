/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2020 Basis Technology Corp.
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.Action;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.CommentChangedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance.Type;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.Score;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.datamodel.DisplayableItemNode.findLinked;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.HasCommentStatus;
import static org.sleuthkit.autopsy.datamodel.AbstractContentNode.backgroundTasksPool;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * An AbstractNode implementation representing an artifact of any type.
 */
public class BlackboardArtifactNode extends AbstractContentNode<BlackboardArtifact> {

    private static final Logger logger = Logger.getLogger(BlackboardArtifactNode.class.getName());

    /*
     * This cache is used to avoid repeated trips to the case database for
     * Content objects that are the source of multiple artifacts.
     */
    private static final Cache<Long, Content> contentCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    /*
     * Case application events that affect the property sheets of
     * BlackboardArtifactNodes, with one exception, CURRENT_CASE (closed) events
     * which triggers content cache invalidation and unregistering of the node's
     * ProeprtyChangeListener.
     */
    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(
            Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED,
            Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED,
            Case.Events.CONTENT_TAG_ADDED,
            Case.Events.CONTENT_TAG_DELETED,
            Case.Events.CURRENT_CASE,
            Case.Events.CR_COMMENT_CHANGED);

    /*
     * Artifact types which should display the full unique path of the source
     * content in their property sheets.
     */
    private static final Integer[] SHOW_UNIQUE_PATH = new Integer[]{
        BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()
    };

    /*
     * Artifact types which should display the file metadata of the source
     * content, in these cases always a file, in their property sheets.
     */
    private static final Integer[] SHOW_FILE_METADATA = new Integer[]{
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID()
    };

    private final BlackboardArtifact artifact;
    private Content sourceContent;
    private List<NodeProperty<? extends Object>> customProperties;

    private final PropertyChangeListener appEventListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED.toString())) {
                BlackBoardArtifactTagAddedEvent event = (BlackBoardArtifactTagAddedEvent) evt;
                if (event.getAddedTag().getArtifact().equals(artifact)) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED.toString())) {
                BlackBoardArtifactTagDeletedEvent event = (BlackBoardArtifactTagDeletedEvent) evt;
                if (event.getDeletedTagInfo().getArtifactID() == artifact.getArtifactID()) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.CONTENT_TAG_ADDED.toString())) {
                ContentTagAddedEvent event = (ContentTagAddedEvent) evt;
                if (event.getAddedTag().getContent().equals(sourceContent)) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
                ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
                if (event.getDeletedTagInfo().getContentID() == sourceContent.getId()) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.CR_COMMENT_CHANGED.toString())) {
                CommentChangedEvent event = (CommentChangedEvent) evt;
                if (event.getContentID() == sourceContent.getId()) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                if (evt.getNewValue() == null) {
                    /*
                     * The case has been closed.
                     */
                    unregisterListener();
                    contentCache.invalidateAll();
                }
            } else if (eventType.equals(NodeSpecificEvents.SCO_AVAILABLE.toString()) && !UserPreferences.getHideSCOColumns()) {
                SCOData scoData = (SCOData) evt.getNewValue();
                if (scoData.getScoreAndDescription() != null) {
                    updateSheet(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_score_name(), Bundle.BlackboardArtifactNode_createSheet_score_displayName(), scoData.getScoreAndDescription().getRight(), scoData.getScoreAndDescription().getLeft()));
                }
                if (scoData.getComment() != null) {
                    updateSheet(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_comment_name(), Bundle.BlackboardArtifactNode_createSheet_comment_displayName(), NO_DESCR, scoData.getComment()));
                }
                if (scoData.getCountAndDescription() != null) {
                    updateSheet(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_count_name(), Bundle.BlackboardArtifactNode_createSheet_count_displayName(), scoData.getCountAndDescription().getRight(), scoData.getCountAndDescription().getLeft()));
                }
            }
        }
    };

    /*
     * Wrap the app event PropertyChangeListener in a weak reference that will
     * be passed to all event publishers. This allows the NetBeans
     * infrastructure to delete the node when the user navigates to another part
     * of the tree. Previously, nodes were not being deleted because the event
     * publisher was holding a strong reference to the listener. We need to hold
     * onto the weak reference here to support unregistering of the listener in
     * unregisterListener() method below.
     */
    private final PropertyChangeListener weakAppEventListener = WeakListeners.propertyChange(appEventListener, null);

    /**
     * Constructs a BlackboardArtifactNode, an AbstractNode implementation
     * representing an artifact of any type.
     *
     * @param artifact The artifact to represent.
     * @param iconPath The path to the icon for the artifact type.
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact, String iconPath) {
        super(artifact, createLookup(artifact));
        this.artifact = artifact;
        for (Content lookupContent : this.getLookup().lookupAll(Content.class)) {
            if ((lookupContent != null) && (!(lookupContent instanceof BlackboardArtifact))) {
                sourceContent = lookupContent;
                try {
                    /*
                     * Get the unique path of the source content now (it is
                     * cached in the Content object).
                     */
                    this.sourceContent.getUniquePath();
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, MessageFormat.format("Error getting the unique path of the source content (artifact objID={0}, content objID={1})", artifact.getId(), sourceContent.getId()), ex);
                }
                break;
            }
        }
        this.setName(Long.toString(artifact.getArtifactID()));
        this.setDisplayName();
        this.setIconBaseWithExtension(iconPath);
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakAppEventListener);
    }

    /**
     * Constructs a BlackboardArtifactNode, an AbstractNode implementation
     * representing an artifact of any type.
     *
     * @param artifact The artifact to represent.
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact) {
        this(artifact, ExtractedContent.getIconFilePath(artifact.getArtifactTypeID()));
    }

    /**
     * Unregisters the application event listener when the node is garbage
     * collected, if the finalizer is called.
     *
     * @throws Throwable
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        unregisterListener();
    }

    /**
     * Unregisters the application event listener.
     */
    private void unregisterListener() {
        Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakAppEventListener);
    }

    /**
     * Gets the artifact represented by this node.
     *
     * @return The artifact.
     */
    public BlackboardArtifact getArtifact() {
        return this.artifact;
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.addAll(Arrays.asList(super.getActions(context)));

        /*
         * If this artifact has a timestamp, add an action to view it in the
         * timeline.
         */
        try {
            if (ViewArtifactInTimelineAction.hasSupportedTimeStamp(artifact)) {
                actionsList.add(new ViewArtifactInTimelineAction(artifact));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting attributes of artifact (onbjID={0})", artifact.getArtifactID()), ex); //NON-NLS
        }

        /*
         * If the artifact is linked to a file via a TSK_PATH_ID attribute add
         * an action to view the file in the timeline.
         */
        try {
            AbstractFile linkedFile = findLinked(artifact);
            if (linkedFile != null) {
                actionsList.add(ViewFileInTimelineAction.createViewFileAction(linkedFile));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting linked file of artifact (onbjID={0})", artifact.getArtifactID()), ex); //NON-NLS
        }

        /*
         * If the source content of the artifact is a file, add an action to
         * view the file in the data source tree.
         */
        AbstractFile file = getLookup().lookup(AbstractFile.class);
        if (null != file) {
            actionsList.add(ViewFileInTimelineAction.createViewSourceFileAction(file));
        }

        return actionsList.toArray(new Action[actionsList.size()]);
    }

    /**
     * Set the filter node display name. The value will either be the file name
     * or something along the lines of e.g. "Messages Artifact" for keyword hits
     * on artifacts.
     */
    @NbBundle.Messages({"# {0} - artifactDisplayName", "BlackboardArtifactNode.displayName.artifact={0} Artifact"})
    private void setDisplayName() {
        String displayName = ""; //NON-NLS

        // If this is a node for a keyword hit on an artifact, we set the
        // display name to be the artifact type name followed by " Artifact"
        // e.g. "Messages Artifact".
        if (artifact != null
                && (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()
                || artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID())) {
            try {
                for (BlackboardAttribute attribute : artifact.getAttributes()) {
                    if (attribute.getAttributeType().getTypeID() == ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()) {
                        BlackboardArtifact associatedArtifact = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                        if (associatedArtifact != null) {
                            if (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()) {
                                artifact.getDisplayName();
                            } else {
                                displayName = NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.displayName.artifact", associatedArtifact.getDisplayName());
                            }
                        }
                    }
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                // Do nothing since the display name will be set to the file name.
            }
        }

        if (displayName.isEmpty() && artifact != null) {
            try {
                Content content = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(this.artifact.getObjectID());
                displayName = (content == null) ? artifact.getName() : content.getName();
            } catch (TskCoreException | NoCurrentCaseException ex) {
                displayName = artifact.getName();
            }
        }

        this.setDisplayName(displayName);

    }

    /**
     * Gets the name of this artifact's source content.
     *
     * @return The source content name.
     */
    public String getSourceName() {
        return sourceContent.getName();
    }

    @NbBundle.Messages({
        "BlackboardArtifactNode.createSheet.artifactType.displayName=Result Type",
        "BlackboardArtifactNode.createSheet.artifactType.name=Result Type",
        "BlackboardArtifactNode.createSheet.artifactDetails.displayName=Result Details",
        "BlackboardArtifactNode.createSheet.artifactDetails.name=Result Details",
        "BlackboardArtifactNode.createSheet.artifactMD5.displayName=MD5 Hash",
        "BlackboardArtifactNode.createSheet.artifactMD5.name=MD5 Hash",
        "BlackboardArtifactNode.createSheet.fileSize.name=Size",
        "BlackboardArtifactNode.createSheet.fileSize.displayName=Size",
        "BlackboardArtifactNode.createSheet.path.displayName=Path",
        "BlackboardArtifactNode.createSheet.path.name=Path"
    })
    @Override
    protected Sheet createSheet() {
        /*
         * Create an empty sheet.
         */
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        /*
         * Add the name of the artifact's source content to the sheet.
         */
        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.srcFile.name"),
                NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.srcFile.displayName"),
                NO_DESCR,
                this.getSourceName()));

        if (!UserPreferences.getHideSCOColumns()) {
            /*
             * Add the S, C, and O columns to the sheet and start a background
             * task to compute the S, C, O values of the artifact.
             */
            sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_score_name(), Bundle.BlackboardArtifactNode_createSheet_score_displayName(), VALUE_LOADING, ""));
            sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_comment_name(), Bundle.BlackboardArtifactNode_createSheet_comment_displayName(), VALUE_LOADING, ""));
            if (CentralRepository.isEnabled()) {
                sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_count_name(), Bundle.BlackboardArtifactNode_createSheet_count_displayName(), VALUE_LOADING, ""));
            }
            backgroundTasksPool.submit(new GetSCOTask(new WeakReference<>(this), weakAppEventListener));
        }

        /*
         * If the artifact is an interesting artifact hit, add the type and
         * description of the interesting artifact to the sheet.
         */
        if (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()) {
            try {
                BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
                if (attribute != null) {
                    BlackboardArtifact associatedArtifact = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                    sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactType.name"),
                            NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactType.displayName"),
                            NO_DESCR,
                            associatedArtifact.getDisplayName()));
                    sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactDetails.name"),
                            NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactDetails.displayName"),
                            NO_DESCR,
                            associatedArtifact.getShortDescription()));
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, MessageFormat.format("Error getting associated artifact of TSK_INTERESTING_ARTIFACT_HIT artifact (objID={0}))", artifact.getId()), ex); //NON-NLS
            }
        }

        /*
         * Add the artifact's attributes to the sheet.
         */
        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, artifact);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sheetSet.put(new NodeProperty<>(entry.getKey(),
                    entry.getKey(),
                    NO_DESCR,
                    entry.getValue()));
        }

        /*
         * Add any "custom properties" for the artifact to the sheet.
         */
        if (customProperties != null) {
            for (NodeProperty<? extends Object> np : customProperties) {
                sheetSet.put(np);
            }
        }


        /*
         * If the artifact is a file extension mismatch artifact, add the
         * extension and type of the file to the sheet.
         */
        final int artifactTypeId = artifact.getArtifactTypeID();
        if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID()) {
            String ext = ""; //NON-NLS
            String actualMimeType = ""; //NON-NLS
            if (sourceContent instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) sourceContent;
                ext = file.getNameExtension();
                actualMimeType = file.getMIMEType();
                if (actualMimeType == null) {
                    actualMimeType = ""; //NON-NLS
                }
            }
            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.ext.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.ext.displayName"),
                    NO_DESCR,
                    ext));
            sheetSet.put(new NodeProperty<>(
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.mimeType.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.mimeType.displayName"),
                    NO_DESCR,
                    actualMimeType));
        }

        /*
         * If the artifact's type dictates the addition of the source content's
         * unique path, add it to the sheet.
         */
        if (Arrays.asList(SHOW_UNIQUE_PATH).contains(artifactTypeId)) {
            String sourcePath = ""; //NON-NLS
            try {
                sourcePath = sourceContent.getUniquePath();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to get unique path from: {0}", sourceContent.getName()); //NON-NLS
            }

            if (sourcePath.isEmpty() == false) {
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.filePath.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.filePath.displayName"),
                        NO_DESCR,
                        sourcePath));
            }

            /*
             * If the artifact's type dictates the addition of the source
             * content's file metadata, add it to the sheet. Otherwise, add the
             * data source to the sheet.
             */
            if (Arrays.asList(SHOW_FILE_METADATA).contains(artifactTypeId)) {
                AbstractFile file = sourceContent instanceof AbstractFile ? (AbstractFile) sourceContent : null;
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileModifiedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileModifiedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getMtime(), file)));
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileChangedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileChangedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getCtime(), file)));
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileAccessedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileAccessedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getAtime(), file)));
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileCreatedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileCreatedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getCrtime(), file)));
                sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileSize.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileSize.displayName"),
                        "",
                        sourceContent.getSize()));
                sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_artifactMD5_name(),
                        Bundle.BlackboardArtifactNode_createSheet_artifactMD5_displayName(),
                        "",
                        file == null ? "" : StringUtils.defaultString(file.getMd5Hash())));
            }
        } else {
            String dataSourceStr = "";
            try {
                Content dataSource = sourceContent.getDataSource();
                if (dataSource != null) {
                    dataSourceStr = dataSource.getName();
                } else {
                    dataSourceStr = getRootParentName();
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to get image name from {0}", sourceContent.getName()); //NON-NLS
            }

            if (dataSourceStr.isEmpty() == false) {
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.dataSrc.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.dataSrc.displayName"),
                        NO_DESCR,
                        dataSourceStr));
            }
        }

        /*
         * If the artifact is an EXIF artifact, ad the source file size and path
         * to the sheet.
         */
        if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()) {
            long size = 0;
            String path = ""; //NON-NLS
            if (sourceContent instanceof AbstractFile) {
                AbstractFile af = (AbstractFile) sourceContent;
                size = af.getSize();
                try {
                    path = af.getUniquePath();
                } catch (TskCoreException ex) {
                    path = af.getParentPath();
                }
            }
            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.fileSize.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.fileSize.displayName"),
                    NO_DESCR,
                    size));
            sheetSet.put(new NodeProperty<>(
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.path.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.path.displayName"),
                    NO_DESCR,
                    path));
        }

        return sheet;
    }

    /**
     * Gets all of the tags applied to the artifact or its source content.
     *
     * @return The list of tags.
     */
    @Override
    protected final List<Tag> getAllTagsFromDatabase() {
        List<Tag> tags = new ArrayList<>();
        try {
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact));
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByContent(sourceContent));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for artifact " + artifact.getDisplayName(), ex);
        }
        return tags;
    }

    /**
     * Gets the correlation attribute for the MD5 hash of the source file of the
     * artifact, if the central repository is enabled and the source content is
     * a file.
     *
     * @return The correlation attribute instance, may be null.
     */
    @Override
    protected final CorrelationAttributeInstance getCorrelationAttributeInstance() {
        CorrelationAttributeInstance correlationAttribute = null;
        if (CentralRepository.isEnabled() && sourceContent instanceof AbstractFile) {
            correlationAttribute = CorrelationAttributeUtil.getCorrAttrForFile((AbstractFile) sourceContent);
        }
        return correlationAttribute;
    }

    /**
     * Computes the value of the comment property ("C" in S, C, O) for this
     * artifact.
     *
     * @param tags      The tags applied to the artifact and its source content.
     * @param attribute The correlation attribute for the MD5 hash of the source
     *                  file of the artifact, may be null.
     *
     * @return The value of the comment property.
     */
    @Override
    protected DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, CorrelationAttributeInstance attribute) {

        HasCommentStatus status = tags.size() > 0 ? HasCommentStatus.TAG_NO_COMMENT : HasCommentStatus.NO_COMMENT;
        for (Tag tag : tags) {
            if (!StringUtils.isBlank(tag.getComment())) {
                //if the tag is null or empty or contains just white space it will indicate there is not a comment
                status = HasCommentStatus.TAG_COMMENT;
                break;
            }
        }
        //currently checks for a comment on the associated file in the central repo not the artifact itself 
        //what we want the column property to reflect should be revisted when we have added a way to comment
        //on the artifact itself
        if (attribute != null && !StringUtils.isBlank(attribute.getComment())) {
            if (status == HasCommentStatus.TAG_COMMENT) {
                status = HasCommentStatus.CR_AND_TAG_COMMENTS;
            } else {
                status = HasCommentStatus.CR_COMMENT;
            }
        }
        return status;
    }

    /**
     * Used by (subclasses of) BlackboardArtifactNode to add the Score property
     * to their sheets.
     *
     * @param sheetSet the modifiable Sheet.Set to add the property to
     * @param tags     the list of tags associated with the file
     *
     * @deprecated Use the GetSCOTask to get this data on a background
     * thread..., and then update the property sheet asynchronously
     */
    @NbBundle.Messages({"BlackboardArtifactNode.createSheet.score.name=S",
        "BlackboardArtifactNode.createSheet.score.displayName=S",
        "BlackboardArtifactNode.createSheet.notableFile.description=Associated file recognized as notable.",
        "BlackboardArtifactNode.createSheet.interestingResult.description=Result has an interesting result associated with it.",
        "BlackboardArtifactNode.createSheet.taggedItem.description=Result or associated file has been tagged.",
        "BlackboardArtifactNode.createSheet.notableTaggedItem.description=Result or associated file tagged with notable tag.",
        "BlackboardArtifactNode.createSheet.noScore.description=No score"})
    @Deprecated
    protected final void addScorePropertyAndDescription(Sheet.Set sheetSet, List<Tag> tags) {
        Pair<DataResultViewerTable.Score, String> scoreAndDescription = getScorePropertyAndDescription(tags);
        sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_score_name(), Bundle.BlackboardArtifactNode_createSheet_score_displayName(), scoreAndDescription.getRight(), scoreAndDescription.getLeft()));
    }

    /**
     * Get the score property for the node.
     *
     * @param tags the list of tags associated with the file
     *
     * @return score property and description
     */
    @Override
    protected Pair<DataResultViewerTable.Score, String> getScorePropertyAndDescription(List<Tag> tags) {
        Score score = Score.NO_SCORE;
        String description = Bundle.BlackboardArtifactNode_createSheet_noScore_description();
        if (sourceContent instanceof AbstractFile) {
            if (((AbstractFile) sourceContent).getKnown() == TskData.FileKnown.BAD) {
                score = Score.NOTABLE_SCORE;
                description = Bundle.BlackboardArtifactNode_createSheet_notableFile_description();
            }
        }
        //if the artifact being viewed is a hashhit check if the hashset is notable 
        if ((score == Score.NO_SCORE || score == Score.INTERESTING_SCORE) && content.getArtifactTypeID() == ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
            try {
                BlackboardAttribute attr = content.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SET_NAME));
                List<HashDbManager.HashDb> notableHashsets = HashDbManager.getInstance().getKnownBadFileHashSets();
                for (HashDbManager.HashDb hashDb : notableHashsets) {
                    if (hashDb.getHashSetName().equals(attr.getValueString())) {
                        score = Score.NOTABLE_SCORE;
                        description = Bundle.BlackboardArtifactNode_createSheet_notableFile_description();
                        break;
                    }
                }
            } catch (TskCoreException ex) {
                //unable to get the attribute so we can not update the status based on the attribute
                logger.log(Level.WARNING, "Unable to get TSK_SET_NAME attribute for artifact of type TSK_HASHSET_HIT with artifact ID " + content.getArtifactID(), ex);
            }
        }
        try {
            if (score == Score.NO_SCORE && !content.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT).isEmpty()) {
                score = Score.INTERESTING_SCORE;
                description = Bundle.BlackboardArtifactNode_createSheet_interestingResult_description();
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting artifacts for artifact: " + content.getName(), ex);
        }
        if (tags.size() > 0 && (score == Score.NO_SCORE || score == Score.INTERESTING_SCORE)) {
            score = Score.INTERESTING_SCORE;
            description = Bundle.BlackboardArtifactNode_createSheet_taggedItem_description();
            for (Tag tag : tags) {
                if (tag.getName().getKnownStatus() == TskData.FileKnown.BAD) {
                    score = Score.NOTABLE_SCORE;
                    description = Bundle.BlackboardArtifactNode_createSheet_notableTaggedItem_description();
                    break;
                }
            }
        }

        return Pair.of(score, description);
    }

    /**
     * Gets the Occurrences property for the node.
     *
     * @param attributeType      the type of the attribute to count
     * @param attributeValue     the value of the attribute to count
     * @param defaultDescription a description to use when none is determined by
     *                           the getCountPropertyAndDescription method
     *
     * @return count and description
     *
     */
    @Override
    protected Pair<Long, String> getCountPropertyAndDescription(Type attributeType, String attributeValue, String defaultDescription) {
        Long count = -1L;
        String description = defaultDescription;
        try {
            //don't perform the query if there is no correlation value
            if (attributeType != null && StringUtils.isNotBlank(attributeValue)) {
                count = CentralRepository.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(attributeType, attributeValue);
                description = Bundle.BlackboardArtifactNode_createSheet_count_description(count, attributeType.getDisplayName());
            } else if (attributeType != null) {
                description = Bundle.BlackboardArtifactNode_createSheet_count_noCorrelationValues_description();
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.WARNING, "Error getting count of datasources with correlation attribute", ex);
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, "Unable to normalize data to get count of datasources with correlation attribute", ex);
        }
        return Pair.of(count, description);
    }

    private void updateSheet() {
        this.setSheet(createSheet());
    }

    private String getRootParentName() {
        String parentName = sourceContent.getName();
        Content parent = sourceContent;
        try {
            while ((parent = parent.getParent()) != null) {
                parentName = parent.getName();
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get parent name from {0}", sourceContent.getName()); //NON-NLS
            return "";
        }
        return parentName;
    }

    /**
     * Add an additional custom node property to that node before it is
     * displayed
     *
     * @param np NodeProperty to add
     */
    public void addNodeProperty(NodeProperty<?> np) {
        if (null == customProperties) {
            //lazy create the list
            customProperties = new ArrayList<>();
        }
        customProperties.add(np);
    }

    /**
     * Fill map with Artifact properties
     *
     * @param map      map with preserved ordering, where property names/values
     *                 are put
     * @param artifact to extract properties from
     */
    @SuppressWarnings("deprecation")
    private void fillPropertyMap(Map<String, Object> map, BlackboardArtifact artifact) {
        try {
            for (BlackboardAttribute attribute : artifact.getAttributes()) {
                final int attributeTypeID = attribute.getAttributeType().getTypeID();
                //skip some internal attributes that user shouldn't see
                if (attributeTypeID == ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE.getTypeID()
                        || attribute.getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.JSON) {
                    continue;
                } else if (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) {
                    addEmailMsgProperty(map, attribute);
                } else if (attribute.getAttributeType().getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
                    map.put(attribute.getAttributeType().getDisplayName(), ContentUtils.getStringTime(attribute.getValueLong(), sourceContent));
                } else if (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_TOOL_OUTPUT.getTypeID()
                        && attributeTypeID == ATTRIBUTE_TYPE.TSK_TEXT.getTypeID()) {
                    /*
                     * This was added because the RegRipper output would often
                     * cause the UI to get a black line accross it and hang if
                     * you hovered over large output or selected it. This
                     * reduces the amount of data in the table. Could consider
                     * doing this for all fields in the UI.
                     */
                    String value = attribute.getDisplayString();
                    if (value.length() > 512) {
                        value = value.substring(0, 512);
                    }
                    map.put(attribute.getAttributeType().getDisplayName(), value);
                } else {
                    map.put(attribute.getAttributeType().getDisplayName(), attribute.getDisplayString());
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Getting attributes failed", ex); //NON-NLS
        }
    }

    /**
     * Fill map with EmailMsg properties, not all attributes are filled
     *
     * @param map       map with preserved ordering, where property names/values
     *                  are put
     * @param attribute attribute to check/fill as property
     */
    private void addEmailMsgProperty(Map<String, Object> map, BlackboardAttribute attribute) {

        final int attributeTypeID = attribute.getAttributeType().getTypeID();

        // Skip certain Email msg attributes
        if (attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_HTML.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_RTF.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_BCC.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CC.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_HEADERS.getTypeID()) {

            // do nothing
        } else if (attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN.getTypeID()) {

            String value = attribute.getDisplayString();
            if (value.length() > 160) {
                value = value.substring(0, 160) + "...";
            }
            map.put(attribute.getAttributeType().getDisplayName(), value);
        } else if (attribute.getAttributeType().getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
            map.put(attribute.getAttributeType().getDisplayName(), ContentUtils.getStringTime(attribute.getValueLong(), sourceContent));
        } else {
            map.put(attribute.getAttributeType().getDisplayName(), attribute.getDisplayString());
        }

    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * Create a Lookup based on what is in the passed in artifact.
     *
     * @param artifact The artifact to make a look up for.
     *
     * @return A lookup with the artifact and possibly any associated content in
     *         it.
     */
    private static Lookup createLookup(BlackboardArtifact artifact) {
        // Add the content the artifact is associated with
        final long objectID = artifact.getObjectID();
        try {
            Content content = contentCache.get(objectID, () -> artifact.getSleuthkitCase().getContentById(objectID));
            if (content == null) {
                return Lookups.fixed(artifact);
            } else {
                return Lookups.fixed(artifact, content);
            }
        } catch (ExecutionException ex) {
            logger.log(Level.WARNING, "Getting associated content for artifact failed", ex); //NON-NLS
            return Lookups.fixed(artifact);
        }
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * Used by (subclasses of) BlackboardArtifactNode to add the tags property
     * to their sheets.
     *
     * @param sheetSet the modifiable Sheet.Set returned by
     *                 Sheet.get(Sheet.PROPERTIES)
     */
    @NbBundle.Messages({
        "BlackboardArtifactNode.createSheet.tags.displayName=Tags"})
    @Deprecated
    protected void addTagProperty(Sheet.Set sheetSet) throws MissingResourceException {
        // add properties for tags
        List<Tag> tags = new ArrayList<>();
        try {
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact));
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByContent(sourceContent));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for artifact " + artifact.getDisplayName(), ex);
        }
        sheetSet.put(new NodeProperty<>("Tags", Bundle.BlackboardArtifactNode_createSheet_tags_displayName(),
                NO_DESCR, tags.stream().map(t -> t.getName().getDisplayName()).collect(Collectors.joining(", "))));
    }

    /**
     * Used by (subclasses of) BlackboardArtifactNode to add the tags property
     * to their sheets.
     *
     * @param sheetSet the modifiable Sheet.Set returned by
     *                 Sheet.get(Sheet.PROPERTIES)
     * @param tags     the list of tags which should appear as the value for the
     *                 property
     */
    @Deprecated
    protected final void addTagProperty(Sheet.Set sheetSet, List<Tag> tags) {
        sheetSet.put(new NodeProperty<>("Tags", Bundle.BlackboardArtifactNode_createSheet_tags_displayName(),
                NO_DESCR, tags.stream().map(t -> t.getName().getDisplayName()).collect(Collectors.joining(", "))));
    }

    /**
     * Used by (subclasses of) BlackboardArtifactNode to add the Occurrences
     * property to their sheets.
     *
     * @param sheetSet  the modifiable Sheet.Set to add the property to
     * @param attribute correlation attribute instance
     *
     * @deprecated Use the GetSCOTask to get this data on a background
     * thread..., and then update the property sheet asynchronously
     */
    @NbBundle.Messages({"BlackboardArtifactNode.createSheet.count.name=O",
        "BlackboardArtifactNode.createSheet.count.displayName=O",
        "BlackboardArtifactNode.createSheet.count.noCorrelationAttributes.description=No correlation properties found",
        "BlackboardArtifactNode.createSheet.count.noCorrelationValues.description=Unable to find other occurrences because no value exists for the available correlation property",
        "# {0} - occurrenceCount",
        "# {1} - attributeType",
        "BlackboardArtifactNode.createSheet.count.description=There were {0} datasource(s) found with occurrences of the correlation value of type {1}"})
    @Deprecated
    protected final void addCountProperty(Sheet.Set sheetSet, CorrelationAttributeInstance attribute) {
        Pair<Long, String> countAndDescription = getCountPropertyAndDescription(attribute.getCorrelationType(), attribute.getCorrelationValue(), Bundle.BlackboardArtifactNode_createSheet_count_noCorrelationAttributes_description());
        sheetSet.put(
                new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_count_name(), Bundle.BlackboardArtifactNode_createSheet_count_displayName(), countAndDescription.getRight(), countAndDescription.getLeft()));
    }

    /**
     * Used by (subclasses of) BlackboardArtifactNode to add the comment
     * property to their sheets.
     *
     * @param sheetSet  the modifiable Sheet.Set to add the property to
     * @param tags      the list of tags associated with the file
     * @param attribute the correlation attribute associated with this
     *                  artifact's associated file, null if central repo is not
     *                  enabled
     *
     * @deprecated Use the GetSCOTask to get this data on a background
     * thread..., and then update the property sheet asynchronously
     */
    @NbBundle.Messages({"BlackboardArtifactNode.createSheet.comment.name=C",
        "BlackboardArtifactNode.createSheet.comment.displayName=C"})
    @Deprecated
    protected final void addCommentProperty(Sheet.Set sheetSet, List<Tag> tags, CorrelationAttributeInstance attribute) {
        HasCommentStatus status = getCommentProperty(tags, attribute);
        sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_comment_name(), Bundle.BlackboardArtifactNode_createSheet_comment_displayName(), NO_DESCR,
                status));
    }

}
