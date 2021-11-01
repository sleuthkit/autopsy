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
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.Action;
import org.openide.actions.PropertiesAction;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.actions.ViewArtifactAction;
import org.sleuthkit.autopsy.actions.ViewOsAccountAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactItem;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.ExtractArchiveWithPasswordAction;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Report;

/**
 * An action factory for node classes that will have a popup menu.
 *
 * Nodes do not need to implement the full ActionContext interface. They should
 * subclass AbstractAutopsyNode and implement only the ActionContext methods for
 * their supported actions.
 */
public final class ActionsFactory {

    // private constructor for utility class.
    private ActionsFactory() {

    }

    public static Action[] getActions(boolean nodeActionContext, ActionContext actionContext) {
        List<ActionGroup> actionGroups = new ArrayList<>();

        if (actionContext.supportsNodeSpecificActions()) {
            actionGroups.add(actionContext.getNodeSpecificActions());
        }

        if (actionContext.supportsViewArtifactInTimeline() || actionContext.supportsViewFileInTimeline()) {
            actionGroups.add(new ActionGroup(getViewInTimelineAction(actionContext)));
        }

        ActionGroup group = new ActionGroup();
        if (actionContext.supportsAssociatedFileActions()) {
            group.addAll(getAssociatedFileActions(actionContext));
        }

        if (actionContext.getSourceContent() != null) {
            group.addAll(getSourceContentActions(actionContext));
        }
        actionGroups.add(group);

        if (actionContext.getSourceContent() instanceof Report) {
            actionGroups.add(new ActionGroup(DataModelActionsFactory.getActions(actionContext.getSourceContent(), false)));
        }

        actionGroups.add(getSourceContentViewerActions(actionContext));
        actionGroups.add(getExtractActions(actionContext));
        actionGroups.add(getTagActions(actionContext));
        actionGroups.add(new ActionGroup(ContextMenuExtensionPoint.getActions()));

        if (actionContext.supportsExtractArchiveWithPasswordAction()) {
            actionGroups.add(new ActionGroup(new ExtractArchiveWithPasswordAction(actionContext.getExtractArchiveWithPasswordActionFile())));
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

    static ActionGroup getExtractActions(ActionContext actionContext) {
        ActionGroup actionsGroup = new ActionGroup();
        if (actionContext.supportsExtractAction()) {
            actionsGroup.add(ExtractAction.getInstance());
        }

        if (actionContext.supportsExportCSVAction()) {
            actionsGroup.add(ExportCSVAction.getInstance());
        }

        return actionsGroup.isEmpty() ? null : actionsGroup;
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
    private static ActionGroup getSourceContentViewerActions(ActionContext actionContext) {
        ActionGroup actionGroup = new ActionGroup();
        Node node = actionContext.getNewWindowActionNode();

        if (actionContext.supportsNewWindowAction()) {
            actionGroup.add(new NewWindowViewAction(Bundle.ActionsFactory_getSrcContentViewerActions_viewInNewWin(), node));
        }

        if (actionContext.supportsExternalViewerAction()) {
            int selectedFileCount = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).size();
            if (selectedFileCount == 1) {
                actionGroup.add(new ExternalViewerAction(Bundle.ActionsFactory_getSrcContentViewerActions_openInExtViewer(), node));
            } else {
                actionGroup.add(ExternalViewerShortcutAction.getInstance());
            }
        }
        return actionGroup.isEmpty() ? null : actionGroup;
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
    private static ActionGroup getSourceContentActions(ActionContext actionContext) {
        ActionGroup group = new ActionGroup();

        if (actionContext.supportsViewSourceContentActions()) {
            group.add(getViewSrcContentAction(actionContext));
        }

        if (actionContext.supportsViewSourceContentTimelineActions()) {
            AbstractFile srcContent = actionContext.getSourceContextForTimelineAction();
            group.add(new ViewFileInTimelineAction(srcContent,
                    Bundle.ActionsFactory_getTimelineSrcContentAction_actionDisplayName(
                            getContentTypeStr(srcContent))));
        }

        return group.isEmpty() ? null : group;
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
    private static ActionGroup getAssociatedFileActions(ActionContext context) {
        AbstractFile associatedFile = context.getLinkedFile();
        BlackboardArtifact.Type artifactType = context.getArtifactType();

        return new ActionGroup(Arrays.asList(
                new ViewContextAction(
                        Bundle.ActionsFactory_getAssociatedFileActions_viewAssociatedFileAction(
                                getAssociatedTypeStr(artifactType)),
                        associatedFile),
                new ViewFileInTimelineAction(associatedFile,
                        Bundle.ActionsFactory_getAssociatedFileActions_viewAssociatedFileInTimelineAction(
                                getAssociatedTypeStr(artifactType)))
        ));
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

        return actionGroup;
    }

    @Messages({
        "# {0} - contentType",
        "ArtifactFactory_getViewSrcContentAction_displayName=View Source {0} in Directory"
    })
    /**
     * Create an action to navigate to source content in tree hierarchy.
     *
     * @param context
     *
     * @return The action for the given context.
     */
    private static Action getViewSrcContentAction(ActionContext context) {
        Content sourceContent = context.getSourceContent();
        if (sourceContent instanceof DataArtifact) {
            return new ViewArtifactAction(
                    (BlackboardArtifact) sourceContent,
                    Bundle.ArtifactFactory_getViewSrcContentAction_displayName(
                            getContentTypeStr(sourceContent)));
        } else if (sourceContent instanceof OsAccount) {
            return new ViewOsAccountAction(
                    (OsAccount) sourceContent,
                    Bundle.ArtifactFactory_getViewSrcContentAction_displayName(
                            getContentTypeStr(sourceContent)));
        } else if (sourceContent instanceof AbstractFile || (context.getArtifact() instanceof DataArtifact)) {
            return new ViewContextAction(
                    Bundle.ArtifactFactory_getViewSrcContentAction_displayName(
                            getContentTypeStr(sourceContent)),
                    sourceContent);
        }

        return null;
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
        if (context.supportsViewArtifactInTimeline()) {
            return new ViewArtifactInTimelineAction(context.getArtifactForTimeline(), Bundle.ActionsFactory_getTimelineArtifactAction_displayName());
        } else if (context.supportsViewFileInTimeline()) {
            return ViewFileInTimelineAction.createViewFileAction(context.getFileForTimeline());
        }
        return null;
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
        ActionGroup() {
            this.actionList = new ArrayList<>();
        }

        /**
         * Construct a new ActionGroup instance with the given list of actions.
         *
         * @param actionList List of actions to add to the group.
         */
        ActionGroup(List<Action> actionList) {
            this();
            this.actionList.addAll(actionList);
        }

        ActionGroup(Action action) {
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
