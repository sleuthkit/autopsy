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
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import static org.sleuthkit.autopsy.datamodel.AbstractContentNode.NO_DESCR;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.datamodel.utils.FileNameTransTask;

/**
 * A BlackboardArtifactNode is an AbstractNode implementation that can be used
 * to represent an artifact of any type.
 */
public class BlackboardArtifactNode extends AbstractContentNode<BlackboardArtifact> {

    private static final Logger logger = Logger.getLogger(BlackboardArtifactNode.class.getName());

    /*
     * Cache of Content objects used to avoid repeated trips to the case
     * database to retrieve Content objects that are the source of multiple
     * artifacts.
     */
    private static final Cache<Long, Content> contentCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    /*
     * Case events that indicate an update to the node's property sheet may be
     * required.
     */
    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(
            Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED,
            Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED,
            Case.Events.CONTENT_TAG_ADDED,
            Case.Events.CONTENT_TAG_DELETED,
            Case.Events.CR_COMMENT_CHANGED,
            Case.Events.CURRENT_CASE);

    /*
     * Artifact types for which the unique path of the artifact's source content
     * should be displayed in the node's property sheet.
     */
    private static final Integer[] SHOW_UNIQUE_PATH = new Integer[]{
        BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()
    };

    /*
     * Artifact types for which the file metadata of the artifact's source file
     * should be displayed in the node's property sheet.
     */
    private static final Integer[] SHOW_FILE_METADATA = new Integer[]{
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID()
    };

    private final BlackboardArtifact artifact;
    private Content srcContent;
    private volatile String translatedSourceName;

    /*
     * A method has been provided to allow the injection of properties into this
     * node for display in the node's property sheet, independent of the
     * artifact the node represents.
     */
    private List<NodeProperty<? extends Object>> customProperties;

    private final PropertyChangeListener listener = new PropertyChangeListener() {
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
                if (event.getAddedTag().getContent().equals(srcContent)) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
                ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
                if (event.getDeletedTagInfo().getContentID() == srcContent.getId()) {
                    updateSheet();
                }
            } else if (eventType.equals(Case.Events.CR_COMMENT_CHANGED.toString())) {
                CommentChangedEvent event = (CommentChangedEvent) evt;
                if (event.getContentID() == srcContent.getId()) {
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
                    updateSheet(new NodeProperty<>(
                            Bundle.BlackboardArtifactNode_createSheet_score_name(),
                            Bundle.BlackboardArtifactNode_createSheet_score_displayName(),
                            scoData.getScoreAndDescription().getRight(),
                            scoData.getScoreAndDescription().getLeft()));
                }
                if (scoData.getComment() != null) {
                    updateSheet(new NodeProperty<>(
                            Bundle.BlackboardArtifactNode_createSheet_comment_name(),
                            Bundle.BlackboardArtifactNode_createSheet_comment_displayName(),
                            NO_DESCR, scoData.getComment()));
                }
                if (scoData.getCountAndDescription() != null) {
                    updateSheet(new NodeProperty<>(
                            Bundle.BlackboardArtifactNode_createSheet_count_name(),
                            Bundle.BlackboardArtifactNode_createSheet_count_displayName(),
                            scoData.getCountAndDescription().getRight(),
                            scoData.getCountAndDescription().getLeft()));
                }
            } else if (eventType.equals(FileNameTransTask.getPropertyName())) {
                /*
                 * Replace the value of the Source File property with the
                 * translated name via setDisplayName (see note in createSheet),
                 * and put the untranslated name in the Original Name property
                 * and in the tooltip.
                 */
                String originalName = evt.getOldValue().toString();
                translatedSourceName = evt.getNewValue().toString();
                setDisplayName(translatedSourceName);
                setShortDescription(originalName);
                updateSheet(new NodeProperty<>(
                        Bundle.BlackboardArtifactNode_createSheet_srcFile_origName(),
                        Bundle.BlackboardArtifactNode_createSheet_srcFile_origDisplayName(),
                        NO_DESCR,
                        originalName));
            }
        }
    };

    /*
     * The node's event listener is wrapped in a weak reference that allows the
     * node to be garbage collected when the NetBeans infrastructure discards
     * it. If this is not done, it has been shown that strong references to the
     * listener held by event publishers prevents garbage collection of this
     * node.
     */
    private final PropertyChangeListener weakListener = WeakListeners.propertyChange(listener, null);

    /**
     * Constructs a BlackboardArtifactNode, an AbstractNode implementation that
     * can be used to represent an artifact of any type.
     *
     * @param artifact The artifact to represent.
     * @param iconPath The path to the icon for the artifact type.
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact, String iconPath) {
        super(artifact, createLookup(artifact));
        this.artifact = artifact;
        for (Content lookupContent : this.getLookup().lookupAll(Content.class)) {
            if ((lookupContent != null) && (!(lookupContent instanceof BlackboardArtifact))) {
                srcContent = lookupContent;
                try {
                    /*
                     * Calling this getter causes the unique path of the source
                     * content to be cached in the Content object. This is
                     * advantageous as long as this node is constructed in a
                     * background thread instead of a UI thread.
                     */
                    srcContent.getUniquePath();
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, MessageFormat.format("Error getting the unique path of the source content (artifact objID={0})", artifact.getId()), ex);
                }
                break;
            }
        }
        if (srcContent == null) {
            throw new IllegalArgumentException(MessageFormat.format("Artifact missing source content (artifact objID={0})", artifact));
        }
        setName(Long.toString(artifact.getArtifactID()));
        String displayName = srcContent.getName();
        setDisplayName(displayName);
        setShortDescription(displayName);
        setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakListener);
    }

    /**
     * Constructs a BlackboardArtifactNode, an AbstractNode implementation that
     * can be used to represent an artifact of any type.
     *
     * @param artifact The artifact to represent.
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact) {
        this(artifact, IconsUtil.getIconFilePath(artifact.getArtifactTypeID()));
    }

    /**
     * Creates a Lookup object for this node and populates it with both the
     * artifact this node represents and its source content.
     *
     * @param artifact The artifact this node represents.
     *
     * @return The Lookup.
     */
    private static Lookup createLookup(BlackboardArtifact artifact) {
        final long objectID = artifact.getObjectID();
        Content content = null;
        try {
            if (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID() || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE.getTypeID()) {
                content = getPathIdFile(artifact);
            }
            if (content == null) {
                content = contentCache.get(objectID, () -> artifact.getSleuthkitCase().getContentById(objectID));
            }
        } catch (ExecutionException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting source content (artifact objID={0}", artifact.getId()), ex); //NON-NLS
            content = null;
        }
        if (content == null) {
            return Lookups.fixed(artifact);
        } else {
            return Lookups.fixed(artifact, content);
        }

    }

    /**
     * Private helper method to allow content specified in a path id attribute
     * to be retrieved.
     *
     * @param artifact The artifact for which content may be specified as a tsk
     *                 path attribute.
     *
     * @return The Content specified by the artifact's path id attribute or null
     *         if there was no content available.
     *
     * @throws ExecutionException Error retrieving the file specified by the
     *                            path id from the cache.
     */
    private static Content getPathIdFile(BlackboardArtifact artifact) throws ExecutionException {
        try {
            BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID));
            if (attribute != null) {
                return contentCache.get(attribute.getValueLong(), () -> artifact.getSleuthkitCase().getContentById(attribute.getValueLong()));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, MessageFormat.format("Error getting content for path id attrbiute for artifact: ", artifact.getId()), ex); //NON-NLS
        }
        return null;
    }

    /**
     * Unregisters the application event listener when this node is garbage
     * collected, if this finalizer is actually called.
     *
     * RC: Isn't there some node lifecycle property change event that could be
     * used to unregister the listener instead?
     *
     * @throws Throwable
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        unregisterListener();
    }

    /**
     * Unregisters this node's application event listener.
     */
    private void unregisterListener() {
        Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakListener);
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
         * If the artifact represented by this node has a timestamp, add an
         * action to view it in the timeline.
         */
        try {
            if (ViewArtifactInTimelineAction.hasSupportedTimeStamp(artifact)) {
                actionsList.add(new ViewArtifactInTimelineAction(artifact));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting artifact timestamp (artifact objID={0})", artifact.getId()), ex); //NON-NLS
        }

        /*
         * If the artifact represented by this node is linked to a file via a
         * TSK_PATH_ID attribute, add an action to view the file in the
         * timeline.
         */
        try {
            AbstractFile linkedFile = findLinked(artifact);
            if (linkedFile != null) {
                actionsList.add(ViewFileInTimelineAction.createViewFileAction(linkedFile));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting linked file of artifact (artifact objID={0})", artifact.getId()), ex); //NON-NLS

        }

        /*
         * If the source content of the artifact represented by this node is a
         * file, add an action to view the file in the data source tree.
         */
        AbstractFile file = getLookup().lookup(AbstractFile.class
        );
        if (null != file) {
            actionsList.add(ViewFileInTimelineAction.createViewSourceFileAction(file));
        }

        return actionsList.toArray(new Action[actionsList.size()]);
    }

    /**
     * Gets the name of the source content of the artifact represented by this
     * node.
     *
     * @return The source content name.
     */
    public String getSourceName() {
        return srcContent.getName();
    }

    @NbBundle.Messages({
        "BlackboardArtifactNode.createSheet.srcFile.name=Source File",
        "BlackboardArtifactNode.createSheet.srcFile.displayName=Source File",
        "BlackboardArtifactNode.createSheet.srcFile.origName=Original Name",
        "BlackboardArtifactNode.createSheet.srcFile.origDisplayName=Original Name",
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
         * Create an empty property sheet.
         */
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        /*
         * Add the name of the source content of the artifact represented by
         * this node to the sheet. The value of this property is the same as the
         * display name of the node and this a "special" property that displays
         * the node's icon as well as the display name.
         */
        sheetSet.put(new NodeProperty<>(
                Bundle.BlackboardArtifactNode_createSheet_srcFile_name(),
                Bundle.BlackboardArtifactNode_createSheet_srcFile_displayName(),
                NO_DESCR,
                getDisplayName()));

        if (TextTranslationService.getInstance().hasProvider() && UserPreferences.displayTranslatedFileNames()) {
            /*
             * If machine translation is configured, add the original name of
             * the of the source content of the artifact represented by this
             * node to the sheet.
             */
            sheetSet.put(new NodeProperty<>(
                    Bundle.BlackboardArtifactNode_createSheet_srcFile_origName(),
                    Bundle.BlackboardArtifactNode_createSheet_srcFile_origDisplayName(),
                    NO_DESCR,
                    translatedSourceName != null ? srcContent.getName() : ""));
            if (translatedSourceName == null) {
                /*
                 * NOTE: The task makes its own weak reference to the listener.
                 */
                new FileNameTransTask(srcContent.getName(), this, listener).submit();
            }
        }

        if (!UserPreferences.getHideSCOColumns()) {
            /*
             * Add S(core), C(omments), and O(ther occurences) columns to the
             * sheet and start a background task to compute the value of these
             * properties for the artifact represented by this node. The task
             * will fire a PropertyChangeEvent when the computation is completed
             * and this node's PropertyChangeListener will update the sheet.
             */
            sheetSet.put(new NodeProperty<>(
                    Bundle.BlackboardArtifactNode_createSheet_score_name(),
                    Bundle.BlackboardArtifactNode_createSheet_score_displayName(),
                    VALUE_LOADING,
                    ""));
            sheetSet.put(new NodeProperty<>(
                    Bundle.BlackboardArtifactNode_createSheet_comment_name(),
                    Bundle.BlackboardArtifactNode_createSheet_comment_displayName(),
                    VALUE_LOADING,
                    ""));
            if (CentralRepository.isEnabled()) {
                sheetSet.put(new NodeProperty<>(
                        Bundle.BlackboardArtifactNode_createSheet_count_name(),
                        Bundle.BlackboardArtifactNode_createSheet_count_displayName(),
                        VALUE_LOADING,
                        ""));
            }
            backgroundTasksPool.submit(new GetSCOTask(new WeakReference<>(this), weakListener));
        }

        /*
         * If the artifact represented by this node is an interesting artifact
         * hit, add the type and description of the interesting artifact to the
         * sheet.
         */
        if (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()) {
            try {
                BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
                if (attribute != null) {
                    BlackboardArtifact associatedArtifact = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                    sheetSet.put(new NodeProperty<>(
                            NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactType.name"),
                            NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactType.displayName"),
                            NO_DESCR,
                            associatedArtifact.getDisplayName()));
                    sheetSet.put(new NodeProperty<>(
                            NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactDetails.name"),
                            NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.artifactDetails.displayName"),
                            NO_DESCR,
                            associatedArtifact.getShortDescription()));
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, MessageFormat.format("Error getting associated artifact of TSK_INTERESTING_ARTIFACT_HIT artifact (objID={0}))", artifact.getId()), ex); //NON-NLS
            }
        }

        /*
         * Add the attributes of the artifact represented by this node to the
         * sheet.
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
         * Add any "custom properties" for the node to the sheet.
         */
        if (customProperties != null) {
            for (NodeProperty<? extends Object> np : customProperties) {
                sheetSet.put(np);
            }
        }

        /*
         * If the artifact represented by this node is a file extension mismatch
         * artifact, add the extension and type of the artifact's source file to
         * the sheet.
         */
        final int artifactTypeId = artifact.getArtifactTypeID();
        if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID()) {
            String ext = ""; //NON-NLS
            String actualMimeType = ""; //NON-NLS
            if (srcContent instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) srcContent;
                ext = file.getNameExtension();
                actualMimeType = file.getMIMEType();
                if (actualMimeType == null) {
                    actualMimeType = ""; //NON-NLS

                }
            }
            sheetSet.put(new NodeProperty<>(
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.ext.name"),
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
         * If the type of the artifact represented by this node dictates the
         * addition of the source content's unique path, add it to the sheet.
         */
        if (Arrays.asList(SHOW_UNIQUE_PATH).contains(artifactTypeId)) {
            String sourcePath = ""; //NON-NLS
            try {
                sourcePath = srcContent.getUniquePath();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, MessageFormat.format("Error getting unique path of source content (artifact objID={0})", artifact.getId()), ex); //NON-NLS

            }

            if (sourcePath.isEmpty() == false) {
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.filePath.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.filePath.displayName"),
                        NO_DESCR,
                        sourcePath));
            }

            /*
             * If the type of the artifact represented by this node dictates the
             * addition of the source content's file metadata, add it to the
             * sheet. Otherwise, add the data source to the sheet.
             */
            if (Arrays.asList(SHOW_FILE_METADATA).contains(artifactTypeId)) {
                AbstractFile file = srcContent instanceof AbstractFile ? (AbstractFile) srcContent : null;
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileModifiedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileModifiedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getMtime(), file)));
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileChangedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileChangedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getCtime(), file)));
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileAccessedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileAccessedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getAtime(), file)));
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileCreatedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileCreatedTime.displayName"),
                        "",
                        file == null ? "" : ContentUtils.getStringTime(file.getCrtime(), file)));
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileSize.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class, "ContentTagNode.createSheet.fileSize.displayName"),
                        "",
                        file == null ? "" : file.getSize()));
                sheetSet.put(new NodeProperty<>(
                        Bundle.BlackboardArtifactNode_createSheet_artifactMD5_name(),
                        Bundle.BlackboardArtifactNode_createSheet_artifactMD5_displayName(),
                        "",
                        file == null ? "" : StringUtils.defaultString(file.getMd5Hash())));
            }
        } else {
            String dataSourceStr = "";
            try {
                Content dataSource = srcContent.getDataSource();
                if (dataSource != null) {
                    dataSourceStr = dataSource.getName();
                } else {
                    dataSourceStr = getRootAncestorName();
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, MessageFormat.format("Error getting source data source name (artifact objID={0})", artifact.getId()), ex); //NON-NLS

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
         * If the artifact represented by this node is an EXIF artifact, add the
         * source file size and path to the sheet.
         */
        if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()) {
            long size = 0;
            String path = ""; //NON-NLS
            if (srcContent instanceof AbstractFile) {
                AbstractFile af = (AbstractFile) srcContent;
                size = af.getSize();
                try {
                    path = af.getUniquePath();
                } catch (TskCoreException ex) {
                    path = af.getParentPath();

                }
            }
            sheetSet.put(new NodeProperty<>(
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.fileSize.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.fileSize.displayName"),
                    NO_DESCR,
                    size));
            sheetSet
                    .put(new NodeProperty<>(
                            NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.path.name"),
                            NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.createSheet.path.displayName"),
                            NO_DESCR,
                            path));
        }

        return sheet;
    }

    /**
     * Gets all of the tags applied to the artifact represented by this node and
     * its source content.
     *
     * @return The tags.
     */
    @Override
    protected final List<Tag> getAllTagsFromDatabase() {
        List<Tag> tags = new ArrayList<>();
        try {
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact));
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByContent(srcContent));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting tags for artifact and its source content (artifact objID={0})", artifact.getId()), ex);
        }
        return tags;
    }

    /**
     * Gets the correlation attribute for the MD5 hash of the source file of the
     * artifact represented by this node. The correlation attribute instance can
     * only be returned if the central repository is enabled and the source
     * content is a file.
     *
     * @return The correlation attribute instance, may be null.
     */
    @Override
    protected final CorrelationAttributeInstance getCorrelationAttributeInstance() {
        CorrelationAttributeInstance correlationAttribute = null;
        if (CentralRepository.isEnabled() && srcContent instanceof AbstractFile) {
            correlationAttribute = CorrelationAttributeUtil.getCorrAttrForFile((AbstractFile) srcContent);
        }
        return correlationAttribute;
    }

    /**
     * Computes the value of the comment property ("C" in S, C, O) for the
     * artifact represented by this node.
     *
     * An icon is displayed in the property sheet if a commented tag has been
     * applied to the artifact or its source content, or if there is a
     * corresponding commented correlation attribute instance in the central
     * repository.
     *
     * @param tags      The tags applied to the artifact and its source content.
     * @param attribute A correlation attribute instance Ffor the central
     *                  repository lookup.
     *
     * @return The value of the comment property.
     */
    @Override
    protected DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, CorrelationAttributeInstance attribute) {

        /*
         * Has a tag with a comment been applied to the artifact or its source
         * content?
         */
        HasCommentStatus status = tags.size() > 0 ? HasCommentStatus.TAG_NO_COMMENT : HasCommentStatus.NO_COMMENT;
        for (Tag tag : tags) {
            if (!StringUtils.isBlank(tag.getComment())) {
                status = HasCommentStatus.TAG_COMMENT;
                break;
            }
        }

        /*
         * Does the given correlation attribute instance have a comment in the
         * central repository?
         */
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
     * Computes the value of the score property ("S" in S, C, O) for the
     * artifact represented by this node. The score property indicates whether
     * the artifact or its source content is notable or interesting.
     *
     * IMPORTANT: Notability takes precedence when computing the score.
     *
     * A red icon will be displayed in the property sheet if the hash of the
     * source file has been found in a notable hash set or if either the
     * artifact or its source content has been tagged with a notable tag. A
     * yellow icon will be displayed if the source file belongs to an
     * interesting file set or either the artifact or its source content has
     * been tagged with a non-notable tag.
     *
     * @param tags The tags that have been applied to the artifact and its
     *             source content.
     *
     * @return The value of the score property as an enum element and a
     *         description string for dislpay in a tool tip.
     */
    @Override
    protected Pair<DataResultViewerTable.Score, String> getScorePropertyAndDescription(List<Tag> tags) {
        /*
         * Is the artifact's source content marked as notable?
         */
        Score score = Score.NO_SCORE;
        String description = Bundle.BlackboardArtifactNode_createSheet_noScore_description();
        if (srcContent instanceof AbstractFile) {
            if (((AbstractFile) srcContent).getKnown() == TskData.FileKnown.BAD) {
                score = Score.NOTABLE_SCORE;
                description = Bundle.BlackboardArtifactNode_createSheet_notableFile_description();
            }
        }

        /*
         * If the artifact is a hash set hit, is the hash set a notable hashes
         * hash set?
         */
        if (score == Score.NO_SCORE && artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
            try {
                BlackboardAttribute attr = artifact.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SET_NAME));
                List<HashDbManager.HashDb> notableHashsets = HashDbManager.getInstance().getKnownBadFileHashSets();
                for (HashDbManager.HashDb hashDb : notableHashsets) {
                    if (hashDb.getHashSetName().equals(attr.getValueString())) {
                        score = Score.NOTABLE_SCORE;
                        description = Bundle.BlackboardArtifactNode_createSheet_notableFile_description();
                        break;
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, MessageFormat.format("Error getting TSK_SET_NAME attribute for TSK_HASHSET_HIT artifact (artifact objID={0})", artifact.getId()), ex);
            }
        }

        /*
         * Is the artifact's source content notable?
         */
        if (score == Score.NO_SCORE) {
            try {
                if (!srcContent.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT).isEmpty()) {
                    score = Score.INTERESTING_SCORE;
                    description = Bundle.BlackboardArtifactNode_createSheet_interestingResult_description();
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, MessageFormat.format("Error getting TSK_INTERESTING_ARTIFACT_HIT artifacts for source content (artifact objID={0})", artifact.getId()), ex);
            }
        }

        /*
         * Analyze any tags applied to the artifact or its source content. If
         * there are tags, tha artifact is at least interesting. If one of the
         * tags is a notable tag, the artifact is notable.
         */
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
     * Computes the value of the other occurrences property ("O" in S, C, O) for
     * the artifact represented by this node. The value of the other occurrences
     * property is the number of other data sources this artifact appears in
     * according to a correlation attribute instance lookup in the central
     * repository, plus one for the data source for this instance of the
     * artifact.
     *
     * @param corrAttrType       The correlation attribute instance type to use
     *                           for the central repsoitory lookup.
     * @param attributeValue     The correlation attribute instane value to use
     *                           for the central repsoitory lookup.
     * @param defaultDescription A default description.
     *
     * @return The value of the occurrences property as a data sources count and
     *         a description string.
     *
     */
    @Override
    protected Pair<Long, String> getCountPropertyAndDescription(Type corrAttrType, String attributeValue, String defaultDescription) {
        Long count = -1L;
        String description = defaultDescription;
        try {
            if (corrAttrType != null && StringUtils.isNotBlank(attributeValue)) {
                count = CentralRepository.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(corrAttrType, attributeValue);
                description = Bundle.BlackboardArtifactNode_createSheet_count_description(count, corrAttrType.getDisplayName());
            } else if (corrAttrType != null) {
                description = Bundle.BlackboardArtifactNode_createSheet_count_noCorrelationValues_description();
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error querying central repository for other occurences count (artifact objID={0}, corrAttrType={1}, corrAttrValue={2})", artifact.getId(), corrAttrType, attributeValue), ex);
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error normalizing correlation attribute for central repository query (artifact objID={0}, corrAttrType={2}, corrAttrValue={3})", artifact.getId(), corrAttrType, attributeValue), ex);
        }
        return Pair.of(count, description);
    }

    /**
     * Refreshes this node's property sheet.
     */
    private void updateSheet() {
        this.setSheet(createSheet());
    }

    /**
     * Gets the name of the root ancestor of the source content for the artifact
     * represented by this node.
     *
     * @return The root ancestor name or the empty string if an error occurs.
     */
    private String getRootAncestorName() {
        String parentName = srcContent.getName();
        Content parent = srcContent;
        try {
            while ((parent = parent.getParent()) != null) {
                parentName = parent.getName();
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting root ancestor name for source content (artifact objID={0})", artifact.getId()), ex); //NON-NLS
            return "";
        }
        return parentName;
    }

    /**
     * Adds a "custom" property to the property sheet of this node, indepoendent
     * of the artifact this node represents or its source content.
     *
     * @param property The custom property.
     */
    public void addNodeProperty(NodeProperty<?> property) {
        if (customProperties == null) {
            customProperties = new ArrayList<>();
        }
        customProperties.add(property);
    }

    /**
     * Converts the attributes of the artifact this node represents to a map of
     * name-value pairs, where the names are attribute type display names.
     *
     * @param map      The map to be populated with the artifact attribute
     *                 name-value pairs.
     * @param artifact The artifact.
     */
    @SuppressWarnings("deprecation")
    private void fillPropertyMap(Map<String, Object> map, BlackboardArtifact artifact) {
        try {
            for (BlackboardAttribute attribute : artifact.getAttributes()) {
                final int attributeTypeID = attribute.getAttributeType().getTypeID();
                if (attributeTypeID == ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE.getTypeID()
                        || attribute.getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.JSON) {
                    /*
                     * Do nothing.
                     */
                } else if (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) {
                    addEmailMsgProperty(map, attribute);
                } else if (attribute.getAttributeType().getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
                    map.put(attribute.getAttributeType().getDisplayName(), ContentUtils.getStringTime(attribute.getValueLong(), srcContent));
                } else if (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_TOOL_OUTPUT.getTypeID()
                        && attributeTypeID == ATTRIBUTE_TYPE.TSK_TEXT.getTypeID()) {
                    /*
                     * The truncation of text attributes appears to have been
                     * motivated by the statement that "RegRipper output would
                     * often cause the UI to get a black line accross it and
                     * hang if you hovered over large output or selected it.
                     * This reduces the amount of data in the table. Could
                     * consider doing this for all fields in the UI."
                     */
                    String value = attribute.getDisplayString();
                    if (value.length() > 512) {
                        value = value.substring(0, 512);
                    }
                    map.put(attribute.getAttributeType().getDisplayName(), value);
                } else {
                    switch(attribute.getAttributeType().getValueType()) {
                        case INTEGER:
                            map.put(attribute.getAttributeType().getDisplayName(), attribute.getValueInt());
                            break;
                        case DOUBLE:
                            map.put(attribute.getAttributeType().getDisplayName(), attribute.getValueDouble());
                            break;
                        case LONG:
                            map.put(attribute.getAttributeType().getDisplayName(), attribute.getValueLong());    
                            break;
                        default:
                            map.put(attribute.getAttributeType().getDisplayName(), attribute.getDisplayString()); 
                                
                    }
                    
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting artifact attributes (artifact objID={0})", artifact.getId()), ex); //NON-NLS
        }
    }

    /**
     * Adds an email message attribute of the artifact this node represents to a
     * map of name-value pairs, where the names are attribute type display
     * names.
     *
     * @param map       The map to be populated with the artifact attribute
     *                  name-value pair.
     * @param attribute The attribute to use to make the map entry.
     */
    private void addEmailMsgProperty(Map<String, Object> map, BlackboardAttribute attribute) {
        final int attributeTypeID = attribute.getAttributeType().getTypeID();
        if (attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_HTML.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_RTF.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_BCC.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CC.getTypeID()
                || attributeTypeID == ATTRIBUTE_TYPE.TSK_HEADERS.getTypeID()) {
            /*
             * Do nothing.
             */
        } else if (attributeTypeID == ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN.getTypeID()) {
            String value = attribute.getDisplayString();
            if (value.length() > 160) {
                value = value.substring(0, 160) + "...";
            }
            map.put(attribute.getAttributeType().getDisplayName(), value);
        } else if (attribute.getAttributeType().getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
            map.put(attribute.getAttributeType().getDisplayName(), ContentUtils.getStringTime(attribute.getValueLong(), srcContent));
        } else {
            map.put(attribute.getAttributeType().getDisplayName(), attribute.getDisplayString());
        }
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
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
     * Adds the score property for the artifact represented by this node to the
     * node property sheet.
     *
     * @param sheetSet The property sheet.
     * @param tags     The tags that have been applied to the artifact and its
     *                 source content.
     *
     * @deprecated Do not use. The score property is now computed in a
     * background thread and added to the property sheet via property change
     * event.
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
     * Adds the tags property for the artifact represented by this node to the
     * node property sheet.
     *
     * @param sheetSet The property sheet.
     *
     * @deprecated Do not use. The tags property is now computed in a background
     * thread and added to the property sheet via property change event.
     */
    @NbBundle.Messages({
        "BlackboardArtifactNode.createSheet.tags.displayName=Tags"}
    )
    @Deprecated
    protected void addTagProperty(Sheet.Set sheetSet) throws MissingResourceException {
        List<Tag> tags = new ArrayList<>();
        try {
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact));
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByContent(srcContent));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting tags for artifact and source content (artifact objID={0})", artifact.getId()), ex);
        }
        sheetSet.put(new NodeProperty<>("Tags", Bundle.BlackboardArtifactNode_createSheet_tags_displayName(), NO_DESCR, tags.stream().map(t -> t.getName().getDisplayName()).collect(Collectors.joining(", "))));
    }

    /**
     * Adds the tags property for the artifact represented by this node to the
     * node property sheet.
     *
     * @param sheetSet The property sheet.
     * @param tags     The tags that have been applied to the artifact and its
     *                 source content.
     *
     * @deprecated Do not use. The tags property is now computed in a background
     * thread and added to the property sheet via property change event.
     */
    @Deprecated
    protected final void addTagProperty(Sheet.Set sheetSet, List<Tag> tags) {
        sheetSet.put(new NodeProperty<>("Tags", Bundle.BlackboardArtifactNode_createSheet_tags_displayName(), NO_DESCR, tags.stream().map(t -> t.getName().getDisplayName()).collect(Collectors.joining(", "))));
    }

    /**
     * Adds the count property for the artifact represented by this node to the
     * node property sheet.
     *
     * @param sheetSet  The property sheet.
     * @param attribute The correlation attribute instance to use for the
     *                  central repository lookup.
     *
     * @deprecated Do not use. The count property is now computed in a
     * background thread and added to the property sheet via property change
     * event.
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
        sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_count_name(), Bundle.BlackboardArtifactNode_createSheet_count_displayName(), countAndDescription.getRight(), countAndDescription.getLeft()));
    }

    /**
     * Adds the other occurrences property for the artifact represented by this
     * node to the node property sheet.
     *
     * @param sheetSet  The property sheet.
     * @param tags      The tags that have been applied to the artifact and its
     *                  source content.
     * @param attribute The correlation attribute instance to use for the
     *                  central repository lookup.
     *
     * @deprecated Do not use. The other occurrences property is now computed in
     * a background thread and added to the property sheet via property change
     * event.
     */
    @NbBundle.Messages({"BlackboardArtifactNode.createSheet.comment.name=C",
        "BlackboardArtifactNode.createSheet.comment.displayName=C"})
    @Deprecated
    protected final void addCommentProperty(Sheet.Set sheetSet, List<Tag> tags, CorrelationAttributeInstance attribute) {
        HasCommentStatus status = getCommentProperty(tags, attribute);
        sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_comment_name(), Bundle.BlackboardArtifactNode_createSheet_comment_displayName(), NO_DESCR, status));
    }

}
