/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.nodes.actions;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.swing.Action;
import org.openide.actions.PropertiesAction;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.actions.ReplaceBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.ReplaceContentTagAction;
import org.sleuthkit.autopsy.actions.ViewArtifactAction;
import org.sleuthkit.autopsy.actions.ViewOsAccountAction;
import org.sleuthkit.autopsy.casemodule.DeleteDataSourceAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactItem;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.datasourcesummary.ui.ViewSummaryInformationAction;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.FileSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.RunIngestModulesAction;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.ExtractArchiveWithPasswordAction;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An action factory for node classes that will have a popup menu.
 *
 * Nodes do not need to implement the full ActionContext interface. They should
 * subclass AbstractAutopsyNode and implement only the ActionContext methods for
 * their supported actions.
 */
public final class ActionsFactory {
    
    private static final Logger logger = Logger.getLogger(ActionsFactory.class.getName());

    // private constructor for utility class.
    private ActionsFactory() {}

    /**
     * Create the list of actions for given ActionContext.

     * @param actionContext The context for the actions.
     * 
     * @return The list of Actions to display.
     */
    public static Action[] getActions(ActionContext actionContext) {
        List<ActionGroup> actionGroups = new ArrayList<>();

        Optional<ActionGroup> nodeSpecificGroup = actionContext.getNodeSpecificActions();
        if (nodeSpecificGroup.isPresent()) {
            actionGroups.add(nodeSpecificGroup.get());
        }

        ActionGroup group = new ActionGroup();
        Optional<Action> opAction = getBrowseModeAction(actionContext);
        if(opAction.isPresent()) {
            group.add(opAction.get());
        }
        
        if (actionContext.supportsViewInTimeline()) {
            group.add(getViewInTimelineAction(actionContext));
        } 
        
        actionGroups.add(group);

        group = new ActionGroup();
        if (actionContext.supportsAssociatedFileActions()) {
            Optional<ActionGroup> subGroup = getAssociatedFileActions(actionContext);
            if(subGroup.isPresent()) {
                group.addAll(subGroup.get());
            }
        }

        if (actionContext.getSourceContent().isPresent()) {

            Optional<ActionGroup> optionalGroup = getSourceContentActions(actionContext);
            if (optionalGroup.isPresent()) {
                group.addAll(optionalGroup.get());
            }
        }
        actionGroups.add(group);

        Optional<Content> optionalSourceContext = actionContext.getSourceContent();
        if (optionalSourceContext.isPresent() && optionalSourceContext.get() instanceof Report) {
            actionGroups.add(new ActionGroup(DataModelActionsFactory.getActions(optionalSourceContext.get(), false)));
        }

        if (actionContext.supportsSourceContentViewerActions()) {
            Optional<ActionGroup> optionalGroup = getSourceContentViewerActions(actionContext);
            if (optionalGroup.isPresent()) {
                actionGroups.add(optionalGroup.get());
            }
        }

        if (actionContext.supportsTableExtractActions()) {
            actionGroups.add(getTableExtractActions());
        } else if (actionContext.supportsTreeExtractActions()) {
            actionGroups.add(getTreeExtractActions());
        }
        
        group = new ActionGroup();
        Optional<ActionGroup> ingestGroup = getRunIngestAction(actionContext);
        if(ingestGroup.isPresent()) {
            group.addAll(ingestGroup.get());
        }
        group.addAll(ContextMenuExtensionPoint.getActions());
        actionGroups.add(group);
        
        actionGroups.add(getTagActions(actionContext));
        

        Optional<AbstractFile> optionalFile = actionContext.getExtractArchiveWithPasswordActionFile();
        if (optionalFile.isPresent()) {
            actionGroups.add(new ActionGroup(new ExtractArchiveWithPasswordAction(optionalFile.get())));
        }

        List<Action> actionList = new ArrayList<>();
        for (ActionGroup aGroup : actionGroups) {
            if (aGroup != null) {
                actionList.addAll(aGroup);
                actionList.add(null);
            }
        }

        // Add the properties menu item to the bottom.
        actionList.add(SystemAction.get(PropertiesAction.class));

        Action[] actions = new Action[actionList.size()];
        actionList.toArray(actions);
        return actions;
    }

    /**
     * Returns the Extract actions for a table node. These actions are not specific to the
     * ActionContext.
     *
     * @return The Extract ActionGroup.
     */
    static ActionGroup getTableExtractActions() {
        ActionGroup actionsGroup = new ActionGroup();
        
        Lookup lookup = Utilities.actionsGlobalContext();
        Collection<? extends AbstractFile> selectedFiles =lookup.lookupAll(AbstractFile.class);
        if(selectedFiles.size() > 0) {
            actionsGroup.add(ExtractAction.getInstance());
        }
        actionsGroup.add(ExportCSVAction.getInstance());

        return actionsGroup;
    }
    
    /**
     * Returns the Extract actions for a tree node. These actions are not specific to the
     * ActionContext.
     *
     * @return The Extract ActionGroup.
     */
    static ActionGroup getTreeExtractActions() {
        ActionGroup actionsGroup = new ActionGroup();
        actionsGroup.add(ExtractAction.getInstance());

        return actionsGroup;
    }

    /**
     * Returns the ActionGroup for the source content viewer actions .
     *
     * @param actionContext
     *
     * @return The action group with the actions, or null if these actions are
     *         not supported by the ActionContext.
     */
    @Messages({
        "ActionsFactory_getSrcContentViewerActions_viewInNewWin=View Item in New Window",
        "ActionsFactory_getSrcContentViewerActions_openInExtViewer=Open in External Viewer  Ctrl+E"
    })
    private static Optional<ActionGroup> getSourceContentViewerActions(ActionContext actionContext) {
        ActionGroup actionGroup = new ActionGroup();
        Optional<Node> nodeOptional = actionContext.getNewWindowActionNode();

        if (nodeOptional.isPresent()) {
            actionGroup.add(new NewWindowViewAction(Bundle.ActionsFactory_getSrcContentViewerActions_viewInNewWin(), nodeOptional.get()));
        }

        nodeOptional = actionContext.getExternalViewerActionNode();
        if (nodeOptional.isPresent()) {
            int selectedFileCount = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).size();
            if (selectedFileCount == 1) {
                actionGroup.add(new ExternalViewerAction(Bundle.ActionsFactory_getSrcContentViewerActions_openInExtViewer(), nodeOptional.get()));
            } else {
                actionGroup.add(ExternalViewerShortcutAction.getInstance());
            }
        }
        return actionGroup.isEmpty() ? Optional.empty() : Optional.of(actionGroup);
    }

    /**
     * Creates the ActionGroup for the source content actions.
     *
     * @param actionContext The context for these actions.
     *
     * @return An ActionGroup if one of the actions is supported.
     */
    @Messages({
        "# {0} - contentType",
        "ActionsFactory_getTimelineSrcContentAction_actionDisplayName=View Source {0} in Timeline... "
    })
    private static Optional<ActionGroup> getSourceContentActions(ActionContext actionContext) {
        ActionGroup group = new ActionGroup();

        Optional<Action> optionalAction = getViewSrcContentAction(actionContext);
        if (optionalAction.isPresent()) {
            group.add(optionalAction.get());
        }

        Optional<AbstractFile> srcContentOptional = actionContext.getSourceFileForTimelineAction();
        if (srcContentOptional.isPresent()) {
            group.add(new ViewFileInTimelineAction(srcContentOptional.get(),
                    Bundle.ActionsFactory_getTimelineSrcContentAction_actionDisplayName(
                            getContentTypeStr(srcContentOptional.get()))));
        }

        return group.isEmpty() ? Optional.empty() : Optional.of(group);
    }

    /**
     *
     * @param context
     *
     * @return
     */
    @Messages({
        "# {0} - type",
        "ActionsFactory_getAssociatedFileActions_viewAssociatedFileAction=View {0} in Directory",
        "# {0} - type",
        "ActionsFactory_getAssociatedFileActions_viewAssociatedFileInTimelineAction=View {0} in Timeline..."
    })
    private static Optional<ActionGroup> getAssociatedFileActions(ActionContext context) {
        Optional<AbstractFile> associatedFileOptional = context.getLinkedFile();
        Optional<BlackboardArtifact> artifactOptional = context.getArtifact();

        if (!associatedFileOptional.isPresent() || !artifactOptional.isPresent()) {
            return Optional.empty();
        }

        BlackboardArtifact.Type artifactType;
        try {
            artifactType = artifactOptional.get().getType();
        } catch (TskCoreException ex) {
            
            return Optional.empty();
        }

        ActionGroup group = new ActionGroup(Arrays.asList(
                new ViewContextAction(
                        Bundle.ActionsFactory_getAssociatedFileActions_viewAssociatedFileAction(
                                getAssociatedTypeStr(artifactType)),
                        associatedFileOptional.get()),
                new ViewFileInTimelineAction(associatedFileOptional.get(),
                        Bundle.ActionsFactory_getAssociatedFileActions_viewAssociatedFileInTimelineAction(
                                getAssociatedTypeStr(artifactType)))
        ));

        return Optional.of(group);
    }

    /**
     * Returns the tag actions for the given context.
     *
     * @param context The action context.
     *
     * @return Tag ActionGroup.
     */
    private static ActionGroup getTagActions(ActionContext context) {
        int selectedFileCount = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).size();
        int selectedArtifactCount = Utilities.actionsGlobalContext().lookupAll(BlackboardArtifactItem.class).size();

        ActionGroup actionGroup = new ActionGroup();

        if (context.supportsContentTagAction()) {
            actionGroup.add(AddContentTagAction.getInstance());
        }

        if (context.supportsArtifactTagAction()) {
            actionGroup.add(AddBlackboardArtifactTagAction.getInstance());
        }

        if (context.supportsContentTagAction() && (selectedFileCount == 1)) {
            actionGroup.add(DeleteFileContentTagAction.getInstance());
        }

        if (context.supportsArtifactTagAction() && selectedArtifactCount == 1) {
            actionGroup.add(DeleteFileBlackboardArtifactTagAction.getInstance());
        }
        
        if((context.supportsArtifactTagAction() || context.supportsContentTagAction()) && context.supportsReplaceTagAction()) {
            actionGroup.add(DeleteContentTagAction.getInstance());
            actionGroup.add(ReplaceContentTagAction.getInstance());
        }
        
        return actionGroup;
    }

    @Messages({
        "# {0} - contentType",
        "ArtifactFactory_getViewSrcContentAction_displayName=View Source {0} in Directory",
        "# {0} - contentType",
        "ArtifactFactory_getViewSrcContentAction_displayName2=View Source {0}"
    })
    /**
     * Create an action to navigate to source content in tree hierarchy.
     *
     * @param context
     *
     * @return The action for the given context.
     */
    private static Optional<Action> getViewSrcContentAction(ActionContext context) {
        Optional<Content> sourceContent = context.getSourceContent();
        Optional<BlackboardArtifact> artifact = context.getArtifact();

        if (sourceContent.isPresent()) {
            if (sourceContent.get() instanceof DataArtifact) {
                return Optional.of(new ViewArtifactAction(
                        (BlackboardArtifact) sourceContent.get(),
                        Bundle.ArtifactFactory_getViewSrcContentAction_displayName2(
                                getContentTypeStr(sourceContent.get()))));
            } else if (sourceContent.get() instanceof OsAccount) {
                return Optional.of(new ViewOsAccountAction(
                        (OsAccount) sourceContent.get(),
                        Bundle.ArtifactFactory_getViewSrcContentAction_displayName2(
                                getContentTypeStr(sourceContent.get()))));
            } else if (sourceContent.get() instanceof AbstractFile || (artifact.isPresent() && artifact.get() instanceof DataArtifact)) {
                return Optional.of(new ViewContextAction(
                        Bundle.ArtifactFactory_getViewSrcContentAction_displayName(
                                getContentTypeStr(sourceContent.get())),
                        sourceContent.get()));
            }
        }

        return Optional.empty();
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
        "ActionFactory_getViewSrcContentAction_type_File=File",
        "ActionFactory_getViewSrcContentAction_type_DataArtifact=Data Artifact",
        "ActionFactory_getViewSrcContentAction_type_OSAccount=OS Account",
        "ActionFactory_getViewSrcContentAction_type_unknown=Item"
    })
    private static String getContentTypeStr(Content content) {
        if (content instanceof AbstractFile) {
            return Bundle.ActionFactory_getViewSrcContentAction_type_File();
        } else if (content instanceof DataArtifact) {
            return Bundle.ActionFactory_getViewSrcContentAction_type_DataArtifact();
        } else if (content instanceof OsAccount) {
            return Bundle.ActionFactory_getViewSrcContentAction_type_OSAccount();
        } else {
            return Bundle.ActionFactory_getViewSrcContentAction_type_unknown();
        }
    }

    /**
     * If the artifact represented by this node has a timestamp, an action to
     * view it in the timeline.
     *
     * @param context The action context.
     *
     * @return The action or null if no action should exist.
     */
    @Messages({
        "ActionsFactory_getTimelineArtifactAction_displayName=View Selected Item in Timeline... "
    })
    private static Action getViewInTimelineAction(ActionContext context) {
        Optional<BlackboardArtifact> optionalArtifact = context.getArtifact();
        Optional<AbstractFile> optionalFile = context.getFileForViewInTimelineAction();
        if (optionalArtifact.isPresent()) {
            return new ViewArtifactInTimelineAction(optionalArtifact.get(), Bundle.ActionsFactory_getTimelineArtifactAction_displayName());
        } else if (optionalFile.isPresent()) {
            return ViewFileInTimelineAction.createViewFileAction(optionalFile.get());
        }
        return null;
    }
    
    /**
     * 
     * @param context
     * @return 
     */
    @Messages({
        "ActionFactory_openFileSearchByAttr_text=Open File Search by Attributes"
    })
    private static Optional<ActionGroup> getRunIngestAction(ActionContext context) {
        ActionGroup group = new ActionGroup();        
        Optional<Content> optional = context.getDataSourceForActions();
        if(optional.isPresent()) {
            group.add(new FileSearchAction(Bundle.ActionFactory_openFileSearchByAttr_text(), optional.get().getId()));
            group.add(new ViewSummaryInformationAction(optional.get().getId()));
            group.add(new RunIngestModulesAction(Collections.<Content>singletonList(optional.get())));
            group.add(new DeleteDataSourceAction(optional.get().getId()));
        }
        else {
            optional = context.getContentForRunIngestionModuleAction();

            if(optional.isPresent()) {
                if (optional.get() instanceof AbstractFile) {
                    group.add(new RunIngestModulesAction((AbstractFile)optional.get()));
                } else {
                    logger.log(Level.WARNING, "Can not create RunIngestModulesAction on non-AbstractFile content with ID " + optional.get().getId());
                }
            }
        }
        
        return group.isEmpty() ? Optional.empty() : Optional.of(group);
    }
    
    @Messages({
        "ActionsFactory_viewFileInDir_text=View File in Directory"
    })
    private static Optional<Action> getBrowseModeAction(ActionContext actionContext) {
        Optional<AbstractFile> optional = actionContext.getFileForDirectoryBrowseMode();
        if(optional.isPresent()) {
            return Optional.of(new ViewContextAction(Bundle.ActionsFactory_viewFileInDir_text(), optional.get()));
        }
        
        return Optional.empty();
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
        "ActionsFactory_getAssociatedTypeStr_webCache=Cached File",
        "ActionsFactory_getAssociatedTypeStr_webDownload=Downloaded File",
        "ActionsFactory_getAssociatedTypeStr_associated=Associated File",})
    private static String getAssociatedTypeStr(BlackboardArtifact.Type artifactType) {
        if (BlackboardArtifact.Type.TSK_WEB_CACHE.equals(artifactType)) {
            return Bundle.ActionsFactory_getAssociatedTypeStr_webCache();
        } else if (BlackboardArtifact.Type.TSK_WEB_DOWNLOAD.equals(artifactType)) {
            return Bundle.ActionsFactory_getAssociatedTypeStr_webDownload();
        } else {
            return Bundle.ActionsFactory_getAssociatedTypeStr_associated();
        }
    }

    /**
     * Represents a group of related actions.
     */
    public static class ActionGroup extends AbstractCollection<Action> {

        private final List<Action> actionList;

        /**
         * Construct a new ActionGroup instance with an empty list.
         */
        public ActionGroup() {
            this.actionList = new ArrayList<>();
        }

        /**
         * Construct a new ActionGroup instance with the given list of actions.
         *
         * @param actionList List of actions to add to the group.
         */
        public ActionGroup(List<Action> actionList) {
            this();
            this.actionList.addAll(actionList);
        }

        public ActionGroup(Action action) {
            this();
            actionList.add(action);
        }

        @Override
        public boolean isEmpty() {
            return actionList.isEmpty();
        }

        @Override
        public boolean add(Action action) {
            return actionList.add(action);
        }

        @Override
        public Iterator<Action> iterator() {
            return actionList.iterator();
        }

        @Override
        public void forEach(Consumer<? super Action> action) {
            actionList.forEach(action);
        }

        @Override
        public int size() {
            return actionList.size();
        }

        @Override
        public Stream<Action> stream() {
            return actionList.stream();
        }
    }
}
