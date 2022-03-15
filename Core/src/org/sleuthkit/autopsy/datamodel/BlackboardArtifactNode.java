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

import org.sleuthkit.autopsy.actions.ViewArtifactAction;
import org.sleuthkit.autopsy.actions.ViewOsAccountAction;
import com.google.common.annotations.Beta;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import java.util.stream.Stream;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.CommentChangedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.datamodel.DisplayableItemNode.findLinked;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable.HasCommentStatus;
import static org.sleuthkit.autopsy.datamodel.AbstractContentNode.backgroundTasksPool;
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
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import static org.sleuthkit.autopsy.datamodel.AbstractContentNode.NO_DESCR;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.datamodel.utils.FileNameTransTask;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.ExtractArchiveWithPasswordAction;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.BlackboardArtifact.Category;
import org.sleuthkit.datamodel.HostAddress;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;
import org.sleuthkit.datamodel.Image;

/**
 * An AbstractNode implementation that can be used to represent an data artifact
 * or analysis result of any type.
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
     * Artifact types for which the file metadata of the artifact's source file
     * should be displayed in the node's property sheet.
     *
     * @SuppressWarnings("deprecation") - we need to support already existing
     * interesting file and artifact hits.
     */
    @SuppressWarnings("deprecation")
    private static final Integer[] SHOW_FILE_METADATA = new Integer[]{
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ITEM.getTypeID()
    };

    private final BlackboardArtifact artifact;
    private final BlackboardArtifact.Type artifactType;
    private Content srcContent;
    private volatile String translatedSourceName;
    private final String sourceObjTypeName;

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
                updateSCOColumns((SCOData) evt.getNewValue());
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
     * Constructs an AbstractNode implementation that can be used to represent a
     * data artifact or analysis result of any type. The Lookup of the Node will
     * contain the data artifact or analysis result and its parent content as
     * its source content.
     *
     * @param artifact The data artifact or analysis result.
     * @param iconPath The path to the icon for the data artifact or analysis
     *                 result type.
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact, String iconPath) {
        super(artifact, createLookup(artifact, false));
        this.artifact = artifact;
        this.artifactType = getType(artifact);

        srcContent = getSourceContentFromLookup(artifact);

        if (srcContent == null) {
            throw new IllegalArgumentException(MessageFormat.format("Artifact missing source content (artifact objID={0})", artifact));
        }

        try {
            /*
             * Calling this getter causes the unique path of the source content
             * to be cached in the Content object. This is advantageous as long
             * as this node is constructed in a background thread instead of a
             * UI thread.
             */
            srcContent.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, MessageFormat.format("Error getting the unique path of the source content (artifact objID={0})", artifact.getId()), ex);
        }
        sourceObjTypeName = getSourceObjType(srcContent);
        setDisplayNameBySourceContent();
        setName(Long.toString(artifact.getArtifactID()));
        setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakListener);
    }

    /**
     * Constructs an AbstractNode implementation that can be used to represent a
     * data artifact or analysis result of any type. The Lookup of the Node will
     * contain the data artifact or analysis result and its source content,
     * either the parent content or the associated file.
     *
     * @param artifact                  The data artifact or analysis result.
     * @param useAssociatedFileInLookup True if the source content in the Lookup
     *                                  should be the associated file instead of
     *                                  the parent content.
     */
    @Beta
    public BlackboardArtifactNode(BlackboardArtifact artifact, boolean useAssociatedFileInLookup) {
        super(artifact, createLookup(artifact, useAssociatedFileInLookup));
        this.artifact = artifact;
        this.artifactType = getType(artifact);

        try {
            srcContent = artifact.getParent();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, MessageFormat.format("Error getting the parent of the artifact for (artifact objID={0})", artifact.getId()), ex);
        }

        if (srcContent != null) {
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
        } else {
            throw new IllegalArgumentException(MessageFormat.format("Artifact missing source content (artifact objID={0})", artifact));
        }
        sourceObjTypeName = getSourceObjType(srcContent);
        setName(Long.toString(artifact.getArtifactID()));
        setDisplayNameBySourceContent();
        String iconPath = IconsUtil.getIconFilePath(artifact.getArtifactTypeID());
        setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakListener);
    }

    /**
     * Constructs an AbstractNode implementation that can be used to represent a
     * data artifact or analysis result of any type. The Lookup of the Node will
     * contain the data artifact or analysis result and its parent content as
     * its source content.
     *
     * @param artifact The data artifact or analysis result.
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact) {
        this(artifact, IconsUtil.getIconFilePath(artifact.getArtifactTypeID()));
    }

    /**
     * Returns the artifact type of the artifact.
     *
     * @param artifact The artifact.
     *
     * @return The artifact type or null if no type could be retrieved.
     */
    private static BlackboardArtifact.Type getType(BlackboardArtifact artifact) {
        try {
            return artifact.getType();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, MessageFormat.format("Error getting the artifact type for artifact (artifact objID={0})", artifact.getId()), ex);
            return null;
        }
    }

    /**
     * Creates a Lookup object for this node and populates it with both the
     * artifact this node represents and its source content.
     *
     * @param artifact          The artifact this node represents.
     * @param useAssociatedFile True if the source content in the Lookup should
     *                          be the associated file instead of the parent
     *                          content.
     *
     * @return The Lookup.
     */
    private static Lookup createLookup(BlackboardArtifact artifact, boolean useAssociatedFile) {
        /*
         * Get the source content.
         */
        Content content = null;
        try {
            if (useAssociatedFile) {
                content = getPathIdFile(artifact);
            } else {
                long srcObjectID = artifact.getObjectID();
                content = contentCache.get(srcObjectID, () -> artifact.getSleuthkitCase().getContentById(srcObjectID));
            }
        } catch (ExecutionException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting source/associated content (artifact object ID={0})", artifact.getId()), ex); //NON-NLS
        }

        /*
         * Make an Autopsy Data Model wrapper for the artifact.
         *
         * NOTE: The creation of an Autopsy Data Model independent of the
         * NetBeans nodes is a work in progress. At the time this comment is
         * being written, this object is only being used to indicate the item
         * represented by this BlackboardArtifactNode.
         */
        BlackboardArtifactItem<?> artifactItem;
        if (artifact instanceof AnalysisResult) {
            artifactItem = new AnalysisResultItem((AnalysisResult) artifact, content);
        } else {
            artifactItem = new DataArtifactItem((DataArtifact) artifact, content);
        }

        /*
         * Create the Lookup.
         *
         * NOTE: For now, we are putting both the Autopsy Data Model item and
         * the Sleuth Kit Data Model item in the Lookup so that code that is not
         * aware of the new Autopsy Data Model will still function.
         */
        if (content == null) {
            return Lookups.fixed(artifact, artifactItem);
        } else {
            return Lookups.fixed(artifact, artifactItem, content);
        }
    }

    /**
     * Finds the source content in the Lookup created by createLookup() method.
     *
     * @param artifact Artifact who's source Content we are trying to find.
     *
     * @return Source Content of the input artifact, if one exists. Null
     *         otherwise.
     */
    private Content getSourceContentFromLookup(BlackboardArtifact artifact) {
        for (Content lookupContent : this.getLookup().lookupAll(Content.class)) {
            /*
             * NOTE: createLookup() saves the artifact and its source content
             * (if one exists). However, createLookup() has to be static because
             * it is being called by super(), therefore it can't store the
             * source content in this.srcContent class variable. That's why we
             * have to have the logic below, which reads the Lookup contents,
             * and decides that the source content is the entry in Lookup that
             * is NOT the input artifact.
             */
            if ((lookupContent != null) && (lookupContent.getId() != artifact.getId())) {
                return lookupContent;
            }
        }
        return null;
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

    /**
     * Returns a list of non null actions from the given possibly null options.
     *
     * @param items The items to purge of null items.
     *
     * @return The list of non-null actions.
     */
    private List<Action> getNonNull(Action... items) {
        return Stream.of(items)
                .filter(i -> i != null)
                .collect(Collectors.toList());
    }

    @Override
    public Action[] getActions(boolean context) {
        // groupings of actions where each group will be separated by a divider
        List<List<Action>> actionsLists = new ArrayList<>();

        // view artifact in timeline
        actionsLists.add(getNonNull(
                getTimelineArtifactAction(this.artifact)
        ));

        // view associated file (TSK_PATH_ID attr) in directory and timeline
        actionsLists.add(getAssociatedFileActions(this.artifact, this.artifactType));

        // view source content in directory and timeline
        actionsLists.add(getNonNull(
                getViewSrcContentAction(this.artifact, this.srcContent),
                getTimelineSrcContentAction(this.srcContent)
        ));

        // extract with password from encrypted file
        actionsLists.add(getNonNull(
                getExtractWithPasswordAction(this.srcContent)
        ));

        // menu options for artifact with report parent
        if (this.srcContent instanceof Report) {
            actionsLists.add(DataModelActionsFactory.getActions(this.srcContent, false));
        }

        Node parentFileNode = getParentFileNode(srcContent);
        int selectedFileCount = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).size();
        int selectedArtifactCount = Utilities.actionsGlobalContext().lookupAll(BlackboardArtifactItem.class).size();

        // view source content if source content is some sort of file
        actionsLists.add(getSrcContentViewerActions(parentFileNode, selectedFileCount));

        // extract / export if source content is some sort of file
        if (parentFileNode != null) {
            actionsLists.add(Arrays.asList(ExtractAction.getInstance(), ExportCSVAction.getInstance()));
        }

        // file and result tagging
        actionsLists.add(getTagActions(parentFileNode != null, this.artifact, selectedFileCount, selectedArtifactCount));

        // menu extension items (i.e. add to central repository)
        actionsLists.add(ContextMenuExtensionPoint.getActions());

        // netbeans default items (i.e. properties)
        actionsLists.add(Arrays.asList(super.getActions(context)));

        return actionsLists.stream()
                // remove any empty lists
                .filter((lst) -> lst != null && !lst.isEmpty())
                // add in null between each list group
                .flatMap(lst -> Stream.concat(Stream.of((Action) null), lst.stream()))
                // skip the first null
                .skip(1)
                .toArray(sz -> new Action[sz]);
    }

    /**
     * Returns the name of the artifact based on the artifact type to be used
     * with the associated file string in a right click menu.
     *
     * @param artifactType The artifact type.
     *
     * @return The artifact type name.
     */
    @Messages({
        "BlackboardArtifactNode_getAssociatedTypeStr_webCache=Cached File",
        "BlackboardArtifactNode_getAssociatedTypeStr_webDownload=Downloaded File",
        "BlackboardArtifactNode_getAssociatedTypeStr_associated=Associated File",})
    private String getAssociatedTypeStr(BlackboardArtifact.Type artifactType) {
        if (BlackboardArtifact.Type.TSK_WEB_CACHE.equals(artifactType)) {
            return Bundle.BlackboardArtifactNode_getAssociatedTypeStr_webCache();
        } else if (BlackboardArtifact.Type.TSK_WEB_DOWNLOAD.equals(artifactType)) {
            return Bundle.BlackboardArtifactNode_getAssociatedTypeStr_webDownload();
        } else {
            return Bundle.BlackboardArtifactNode_getAssociatedTypeStr_associated();
        }
    }

    /**
     * Returns the name to represent the type of the content (file, data
     * artifact, os account, item).
     *
     * @param content The content.
     *
     * @return The name of the type of content.
     */
    @Messages({
        "BlackboardArtifactNode_getViewSrcContentAction_type_File=File",
        "BlackboardArtifactNode_getViewSrcContentAction_type_DataArtifact=Data Artifact",
        "BlackboardArtifactNode_getViewSrcContentAction_type_OSAccount=OS Account",
        "BlackboardArtifactNode_getViewSrcContentAction_type_unknown=Item"
    })
    private String getContentTypeStr(Content content) {
        if (content instanceof AbstractFile) {
            return Bundle.BlackboardArtifactNode_getViewSrcContentAction_type_File();
        } else if (content instanceof DataArtifact) {
            return Bundle.BlackboardArtifactNode_getViewSrcContentAction_type_DataArtifact();
        } else if (content instanceof OsAccount) {
            return Bundle.BlackboardArtifactNode_getViewSrcContentAction_type_OSAccount();
        } else {
            return Bundle.BlackboardArtifactNode_getViewSrcContentAction_type_unknown();
        }
    }

    /**
     * Returns actions for navigating to an associated file in the directory or
     * in the timeline.
     *
     * @param artifact     The artifact whose associated file will be
     *                     identified.
     * @param artifactType The type of artifact.
     *
     * @return The actions or an empty list.
     */
    @Messages({
        "# {0} - type",
        "BlackboardArtifactNode_getAssociatedFileActions_viewAssociatedFileAction=View {0} in Directory",
        "# {0} - type",
        "BlackboardArtifactNode_getAssociatedFileActions_viewAssociatedFileInTimelineAction=View {0} in Timeline..."
    })
    private List<Action> getAssociatedFileActions(BlackboardArtifact artifact, BlackboardArtifact.Type artifactType) {
        try {
            AbstractFile associatedFile = findLinked(artifact);
            if (associatedFile != null) {
                return Arrays.asList(
                        new ViewContextAction(
                                Bundle.BlackboardArtifactNode_getAssociatedFileActions_viewAssociatedFileAction(
                                        getAssociatedTypeStr(artifactType)),
                                associatedFile),
                        new ViewFileInTimelineAction(associatedFile,
                                Bundle.BlackboardArtifactNode_getAssociatedFileActions_viewAssociatedFileInTimelineAction(
                                        getAssociatedTypeStr(artifactType)))
                );
            }

        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting linked file of artifact (artifact objID={0})", artifact.getId()), ex); //NON-NLS
        }
        return Collections.emptyList();
    }

    /**
     * Creates an action to navigate to src content in tree hierarchy.
     *
     * @param artifact The artifact.
     * @param content  The content.
     *
     * @return The action or null if no action derived.
     */
    @Messages({
        "# {0} - contentType",
        "BlackboardArtifactNode_getSrcContentAction_actionDisplayName=View Source {0} in Directory"
    })
    private Action getViewSrcContentAction(BlackboardArtifact artifact, Content content) {
        if (content instanceof DataArtifact) {
            return new ViewArtifactAction(
                    (BlackboardArtifact) content,
                    Bundle.BlackboardArtifactNode_getSrcContentAction_actionDisplayName(
                            getContentTypeStr(content)));
        } else if (content instanceof OsAccount) {
            return new ViewOsAccountAction(
                    (OsAccount) content,
                    Bundle.BlackboardArtifactNode_getSrcContentAction_actionDisplayName(
                            getContentTypeStr(content)));
        } else if (content instanceof AbstractFile || artifact instanceof DataArtifact) {
            return new ViewContextAction(
                    Bundle.BlackboardArtifactNode_getSrcContentAction_actionDisplayName(
                            getContentTypeStr(content)),
                    content);
        } else {
            return null;
        }
    }

    /**
     * Returns a Node representing the file content if the content is indeed
     * some sort of file. Otherwise, return null.
     *
     * @param content The content.
     *
     * @return The file node or null if not a file.
     */
    private Node getParentFileNode(Content content) {
        if (content instanceof File) {
            return new FileNode((AbstractFile) content);
        } else if (content instanceof Directory) {
            return new DirectoryNode((Directory) content);
        } else if (content instanceof VirtualDirectory) {
            return new VirtualDirectoryNode((VirtualDirectory) content);
        } else if (content instanceof LocalDirectory) {
            return new LocalDirectoryNode((LocalDirectory) content);
        } else if (content instanceof LayoutFile) {
            return new LayoutFileNode((LayoutFile) content);
        } else if (content instanceof LocalFile || content instanceof DerivedFile) {
            return new LocalFileNode((AbstractFile) content);
        } else if (content instanceof SlackFile) {
            return new SlackFileNode((AbstractFile) content);
        } else {
            return null;
        }
    }

    /**
     * Returns actions for extracting content from file or null if not possible.
     *
     * @param srcContent The source content.
     *
     * @return The action or null if not appropriate source content.
     */
    private Action getExtractWithPasswordAction(Content srcContent) {
        if ((srcContent instanceof AbstractFile)
                && FileTypeExtensions.getArchiveExtensions()
                        .contains("." + ((AbstractFile) srcContent).getNameExtension().toLowerCase())) {
            try {
                if (srcContent.getArtifacts(BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED.getTypeID()).size() > 0) {
                    return new ExtractArchiveWithPasswordAction((AbstractFile) srcContent);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to add unzip with password action to context menus", ex);
            }
        }

        return null;
    }

    /**
     * Returns tag actions.
     *
     * @param hasSrcFile            Whether or not the artifact has a source
     *                              file.
     * @param artifact              This artifact.
     * @param selectedFileCount     The count of selected files.
     * @param selectedArtifactCount The count of selected artifacts.
     *
     * @return The tag actions.
     */
    private List<Action> getTagActions(boolean hasSrcFile, BlackboardArtifact artifact, int selectedFileCount, int selectedArtifactCount) {
        List<Action> actionsList = new ArrayList<>();

        // don't show AddContentTagAction for data artifacts.
        if (hasSrcFile && !(artifact instanceof DataArtifact)) {
            actionsList.add(AddContentTagAction.getInstance());
        }

        actionsList.add(AddBlackboardArtifactTagAction.getInstance());

        // don't show DeleteFileContentTagAction for data artifacts.
        if (hasSrcFile && (!(artifact instanceof DataArtifact)) && (selectedFileCount == 1)) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }

        if (selectedArtifactCount == 1) {
            actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
        }

        return actionsList;
    }

    /**
     * Returns actions to view src content in a different viewer or window.
     *
     * @param srcFileNode       The source file node or null if no source file.
     * @param selectedFileCount The number of selected files.
     *
     * @return The list of actions or an empty list.
     */
    @Messages({
        "BlackboardArtifactNode_getSrcContentViewerActions_viewInNewWin=View Item in New Window",
        "BlackboardArtifactNode_getSrcContentViewerActions_openInExtViewer=Open in External Viewer  Ctrl+E"
    })
    private List<Action> getSrcContentViewerActions(Node srcFileNode, int selectedFileCount) {
        List<Action> actionsList = new ArrayList<>();
        if (srcFileNode != null) {
            actionsList.add(new NewWindowViewAction(Bundle.BlackboardArtifactNode_getSrcContentViewerActions_viewInNewWin(), srcFileNode));
            if (selectedFileCount == 1) {
                actionsList.add(new ExternalViewerAction(Bundle.BlackboardArtifactNode_getSrcContentViewerActions_openInExtViewer(), srcFileNode));
            } else {
                actionsList.add(ExternalViewerShortcutAction.getInstance());
            }
        }
        return actionsList;
    }

    /**
     * If the source content of the artifact represented by this node is a file,
     * returns an action to view the file in the data source tree.
     *
     * @param srcContent The src content to navigate to in the timeline action.
     *
     * @return The src content navigation action or null.
     */
    @NbBundle.Messages({
        "# {0} - contentType",
        "BlackboardArtifactNode_getTimelineSrcContentAction_actionDisplayName=View Source {0} in Timeline... "
    })
    private Action getTimelineSrcContentAction(Content srcContent) {
        if (srcContent instanceof AbstractFile) {
            return new ViewFileInTimelineAction((AbstractFile) srcContent,
                    Bundle.BlackboardArtifactNode_getTimelineSrcContentAction_actionDisplayName(
                            getContentTypeStr(srcContent)));
        } else if (srcContent instanceof DataArtifact) {
            try {
                if (ViewArtifactInTimelineAction.hasSupportedTimeStamp((BlackboardArtifact) srcContent)) {
                    return new ViewArtifactInTimelineAction((BlackboardArtifact) srcContent,
                            Bundle.BlackboardArtifactNode_getTimelineSrcContentAction_actionDisplayName(
                                    getContentTypeStr(srcContent)));
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, MessageFormat.format("Error getting source data artifact timestamp (artifact objID={0})", srcContent.getId()), ex); //NON-NLS
            }
        }

        return null;
    }

    /**
     * If the artifact represented by this node has a timestamp, an action to
     * view it in the timeline.
     *
     * @param art The artifact for timeline navigation action.
     *
     * @return The action or null if no action should exist.
     */
    @Messages({
        "BlackboardArtifactNode_getTimelineArtifactAction_displayName=View Selected Item in Timeline... "
    })
    private Action getTimelineArtifactAction(BlackboardArtifact art) {
        try {
            // don't show ViewArtifactInTimelineAction for AnalysisResults.
            if (!(art instanceof AnalysisResult) && ViewArtifactInTimelineAction.hasSupportedTimeStamp(art)) {
                return new ViewArtifactInTimelineAction(art, Bundle.BlackboardArtifactNode_getTimelineArtifactAction_displayName());
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error getting artifact timestamp (artifact objID={0})", art.getId()), ex); //NON-NLS
        }

        return null;
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
        "BlackboardArtifactNode.createSheet.srcFile.name=Source Name",
        "BlackboardArtifactNode.createSheet.srcFile.displayName=Source Name",
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
    /*
     * @SuppressWarnings("deprecation") - we need to support already existing
     * interesting file and artifact hits.
     */
    @SuppressWarnings("deprecation")
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

        GetSCOTask scoTask;
        if (artifact instanceof AnalysisResult
                && !(artifactType.getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()
                || artifactType.getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID())) {
            scoTask = updateSheetForAnalysisResult((AnalysisResult) artifact, sheetSet);
        } else {
            scoTask = addSCOColumns(sheetSet);
        }

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

        /*
         * If the artifact represented by this node is an interesting artifact
         * hit, add the type and description of the interesting artifact to the
         * sheet.
         */
        if (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID() || artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_INTERESTING_ITEM.getTypeID()) {
            try {
                BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
                if (attribute != null) {
                    BlackboardArtifact associatedArtifact = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                    sheetSet.put(new NodeProperty<>(
                            NbBundle.getMessage(BlackboardArtifactNode.class,
                                    "BlackboardArtifactNode.createSheet.artifactType.name"),
                            NbBundle.getMessage(BlackboardArtifactNode.class,
                                    "BlackboardArtifactNode.createSheet.artifactType.displayName"),
                            NO_DESCR,
                            associatedArtifact.getDisplayName()));
                    sheetSet.put(new NodeProperty<>(
                            NbBundle.getMessage(BlackboardArtifactNode.class,
                                    "BlackboardArtifactNode.createSheet.artifactDetails.name"),
                            NbBundle.getMessage(BlackboardArtifactNode.class,
                                    "BlackboardArtifactNode.createSheet.artifactDetails.displayName"),
                            NO_DESCR,
                            associatedArtifact.getShortDescription()));
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, MessageFormat.format("Error getting associated artifact with type " + artifact.getArtifactTypeName() + " artifact (objID={0}))", artifact.getId()), ex); //NON-NLS
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
                    NbBundle.getMessage(BlackboardArtifactNode.class,
                            "BlackboardArtifactNode.createSheet.ext.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class,
                            "BlackboardArtifactNode.createSheet.ext.displayName"),
                    NO_DESCR,
                    ext));
            sheetSet.put(new NodeProperty<>(
                    NbBundle.getMessage(BlackboardArtifactNode.class,
                            "BlackboardArtifactNode.createSheet.mimeType.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class,
                            "BlackboardArtifactNode.createSheet.mimeType.displayName"),
                    NO_DESCR,
                    actualMimeType));
        }

        /*
         * If the type of the artifact represented by this node dictates the
         * addition of the source content's unique path, add it to the sheet.
         */
        if (artifactType != null && artifactType.getCategory() == Category.ANALYSIS_RESULT) {
            String sourcePath = ""; //NON-NLS
            try {
                sourcePath = srcContent.getUniquePath();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, MessageFormat.format("Error getting unique path of source content (artifact objID={0})", artifact.getId()), ex); //NON-NLS

            }

            if (sourcePath.isEmpty() == false) {
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "BlackboardArtifactNode.createSheet.filePath.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "BlackboardArtifactNode.createSheet.filePath.displayName"),
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
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "ContentTagNode.createSheet.fileModifiedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "ContentTagNode.createSheet.fileModifiedTime.displayName"),
                        "",
                        file == null ? "" : TimeZoneUtils.getFormattedTime(file.getMtime())));
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "ContentTagNode.createSheet.fileChangedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "ContentTagNode.createSheet.fileChangedTime.displayName"),
                        "",
                        file == null ? "" : TimeZoneUtils.getFormattedTime(file.getCtime())));
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "ContentTagNode.createSheet.fileAccessedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "ContentTagNode.createSheet.fileAccessedTime.displayName"),
                        "",
                        file == null ? "" : TimeZoneUtils.getFormattedTime(file.getAtime())));
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "ContentTagNode.createSheet.fileCreatedTime.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "ContentTagNode.createSheet.fileCreatedTime.displayName"),
                        "",
                        file == null ? "" : TimeZoneUtils.getFormattedTime(file.getCrtime())));
                sheetSet.put(new NodeProperty<>(
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "ContentTagNode.createSheet.fileSize.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "ContentTagNode.createSheet.fileSize.displayName"),
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
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "BlackboardArtifactNode.createSheet.dataSrc.name"),
                        NbBundle.getMessage(BlackboardArtifactNode.class,
                                "BlackboardArtifactNode.createSheet.dataSrc.displayName"),
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
                    NbBundle.getMessage(BlackboardArtifactNode.class,
                            "BlackboardArtifactNode.createSheet.fileSize.name"),
                    NbBundle.getMessage(BlackboardArtifactNode.class,
                            "BlackboardArtifactNode.createSheet.fileSize.displayName"),
                    NO_DESCR,
                    size));
            sheetSet
                    .put(new NodeProperty<>(
                            NbBundle.getMessage(BlackboardArtifactNode.class,
                                    "BlackboardArtifactNode.createSheet.path.name"),
                            NbBundle.getMessage(BlackboardArtifactNode.class,
                                    "BlackboardArtifactNode.createSheet.path.displayName"),
                            NO_DESCR,
                            path));
        }

        if (scoTask != null) {
            backgroundTasksPool.submit(scoTask);
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
     * Computes the value of the comment property ("C" in S, C, O) for the
     * artifact represented by this node.
     *
     * An icon is displayed in the property sheet if a commented tag has been
     * applied to the artifact or its source content, or if there is a
     * corresponding commented correlation attribute instance in the central
     * repository.
     *
     * @param tags       The tags applied to the artifact and its source
     *                   content.
     * @param attributes A correlation attribute instance for the central
     *                   repository lookup.
     *
     * @return The value of the comment property.
     */
    @Override
    protected DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, List<CorrelationAttributeInstance> attributes) {
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
            logger.log(Level.SEVERE, "Attempted to Query CR for presence of comments in a Blackboard Artifact node and was unable to perform query, comment column will only reflect caseDB", ex);
        }
        return status;
    }

    @Override
    protected Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attribute, String defaultDescription) {
        Long count = -1L;
        String description = defaultDescription;
        try {
            if (attribute != null && StringUtils.isNotBlank(attribute.getCorrelationValue())) {
                count = CentralRepository.getInstance().getCountCasesWithOtherInstances(attribute);
                description = Bundle.BlackboardArtifactNode_createSheet_count_description(count, attribute.getCorrelationType().getDisplayName());
            } else if (attribute != null) {
                description = Bundle.BlackboardArtifactNode_createSheet_count_noCorrelationValues_description();
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, MessageFormat.format("Error querying central repository for other occurences count (artifact objID={0}, corrAttrType={1}, corrAttrValue={2})", artifact.getId(), attribute.getCorrelationType(), attribute.getCorrelationValue()), ex);
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, MessageFormat.format("Error normalizing correlation attribute for central repository query (artifact objID={0}, corrAttrType={2}, corrAttrValue={3})", artifact.getId(), attribute.getCorrelationType(), attribute.getCorrelationValue()), ex);
        }
        return Pair.of(count, description);
    }

    /**
     * Refreshes this node's property sheet.
     */
    private void updateSheet() {
        SwingUtilities.invokeLater(() -> {
            this.setSheet(createSheet());
        });
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
     * Adds a "custom" property to the property sheet of this node, independent
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
                    map.put(attribute.getAttributeType().getDisplayName(), TimeZoneUtils.getFormattedTime(attribute.getValueLong()));
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
                    switch (attribute.getAttributeType().getValueType()) {
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
            map.put(attribute.getAttributeType().getDisplayName(), TimeZoneUtils.getFormattedTime(attribute.getValueLong()));
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

    @Messages({
        "BlackboardArtifactNode_analysisSheet_sourceType_name=Source Type",
        "BlackboardArtifactNode_analysisSheet_soureName_name=Source Name",
        "BlackboardArtifactNode_analysisSheet_score_name=Score",
        "BlackboardArtifactNode_analysisSheet_conclusion_name=Conclusion",
        "BlackboardArtifactNode_analysisSheet_configuration_name=Configuration",
        "BlackboardArtifactNode_analysisSheet_justifaction_name=Justification"
    })

    /**
     * Add the columns to the Sheet.Set for AnalysisResults.
     *
     * @param result   The AnalysisResult the sheet is being created.
     * @param sheetSet The sheetSet to add the values to.
     */
    private GetSCOTask updateSheetForAnalysisResult(AnalysisResult result, Sheet.Set sheetSet) {
        sheetSet.put(new NodeProperty<>(
                Bundle.BlackboardArtifactNode_analysisSheet_soureName_name(),
                Bundle.BlackboardArtifactNode_analysisSheet_soureName_name(),
                NO_DESCR,
                getDisplayName()));

        GetSCOTask task = addSCOColumns(sheetSet);

        sheetSet.put(new NodeProperty<>(
                Bundle.BlackboardArtifactNode_analysisSheet_sourceType_name(),
                Bundle.BlackboardArtifactNode_analysisSheet_sourceType_name(),
                NO_DESCR,
                sourceObjTypeName));

        sheetSet.put(new NodeProperty<>(
                Bundle.BlackboardArtifactNode_analysisSheet_score_name(),
                Bundle.BlackboardArtifactNode_analysisSheet_score_name(),
                NO_DESCR,
                result.getScore().getSignificance().getDisplayName()));

        sheetSet.put(new NodeProperty<>(
                Bundle.BlackboardArtifactNode_analysisSheet_conclusion_name(),
                Bundle.BlackboardArtifactNode_analysisSheet_conclusion_name(),
                NO_DESCR,
                result.getConclusion()));

        sheetSet.put(new NodeProperty<>(
                Bundle.BlackboardArtifactNode_analysisSheet_configuration_name(),
                Bundle.BlackboardArtifactNode_analysisSheet_configuration_name(),
                NO_DESCR,
                result.getConfiguration()));

        sheetSet.put(new NodeProperty<>(
                Bundle.BlackboardArtifactNode_analysisSheet_justifaction_name(),
                Bundle.BlackboardArtifactNode_analysisSheet_justifaction_name(),
                NO_DESCR,
                result.getJustification()));

        return task;
    }

    private GetSCOTask addSCOColumns(Sheet.Set sheetSet) {
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
            return new GetSCOTask(new WeakReference<>(this), weakListener);
        }
        return null;
    }

    /**
     * Returns a displayable type string for the given content object.
     *
     * If the content object is a artifact of a custom type then this method may
     * cause a DB call BlackboardArtifact.getType
     *
     * @param source The object to determine the type of.
     *
     * @return A string representing the content type.
     */
    private String getSourceObjType(Content source) {
        if (source instanceof BlackboardArtifact) {
            BlackboardArtifact srcArtifact = (BlackboardArtifact) source;
            try {
                return srcArtifact.getType().getDisplayName();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to get custom artifact type id=" + source.getId(), ex);
            }
        } else if (srcContent instanceof Volume) {
            return TskData.ObjectType.VOL.toString();
        } else if (srcContent instanceof AbstractFile) {
            return TskData.ObjectType.ABSTRACTFILE.toString();
        } else if (srcContent instanceof Image) {
            return TskData.ObjectType.IMG.toString();
        } else if (srcContent instanceof VolumeSystem) {
            return TskData.ObjectType.VS.toString();
        } else if (srcContent instanceof OsAccount) {
            return TskData.ObjectType.OS_ACCOUNT.toString();
        } else if (srcContent instanceof HostAddress) {
            return TskData.ObjectType.HOST_ADDRESS.toString();
        } else if (srcContent instanceof Pool) {
            return TskData.ObjectType.POOL.toString();
        }
        return "";
    }

    /**
     * Update the SCO columns with the data retrieved in the background thread.
     *
     * @param scoData The data for the SCO columns.
     */
    private void updateSCOColumns(final SCOData scoData) {
        // Make sure this happens in the EDT
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
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
            }
        });
    }

    /**
     * Sets the displayName of the node based on the source content.
     */
    private void setDisplayNameBySourceContent() {
        if (srcContent instanceof BlackboardArtifact) {
            try {
                setDisplayName(((BlackboardArtifact) srcContent).getShortDescription());
            } catch (TskCoreException ex) {
                // Log the error, but set the display name to
                // Content.getName so there is something visible to the user.
                logger.log(Level.WARNING, "Failed to get short description for artifact id = " + srcContent.getId(), ex);
                setDisplayName(srcContent.getName());
            }
        } else if (srcContent instanceof OsAccount) {
            setDisplayName(((OsAccount) srcContent).getAddr().orElse(srcContent.getName()));
        } else {
            setDisplayName(srcContent.getName());
        }

        setShortDescription(getDisplayName());
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
        Pair<Score, String> scoreAndDescription = getScorePropertyAndDescription();
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
        Pair<Long, String> countAndDescription = getCountPropertyAndDescription(attribute, Bundle.BlackboardArtifactNode_createSheet_count_noCorrelationAttributes_description());
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
        List<CorrelationAttributeInstance> attributes = new ArrayList<>();
        attributes.add(attribute);
        HasCommentStatus status = getCommentProperty(tags, attributes);
        sheetSet.put(new NodeProperty<>(Bundle.BlackboardArtifactNode_createSheet_comment_name(), Bundle.BlackboardArtifactNode_createSheet_comment_displayName(), NO_DESCR, status));
    }
}
