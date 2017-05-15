/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import javax.swing.SwingWorker;
import org.openide.nodes.AbstractNode;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.TreeView;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.DataSourcesNode;
import org.sleuthkit.autopsy.datamodel.RootContentChildren;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * An action that displays the context for some content by expanding the data
 * sources branch of the tree view to the level of the parent of the content,
 * selecting the parent in the tree view, then selecting the content in the
 * results view. This is commonly called "view file in directory."
 */
public final class ViewContextAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final long ROOT_DIR_META_ADDR = 5L;
    private static final Logger logger = Logger.getLogger(ViewContextAction.class.getName());
    private Content content;

    /**
     * An action that displays the context for the source content of an artifact
     * by expanding the data sources branch of the tree view to the level of the
     * parent of the content, selecting the parent in the tree view, then
     * selecting the content in the results view. This is commonly called "view
     * file in directory."
     *
     * @param displayName  The display name for the action.
     * @param artifactNode The artifact node for the artifact.
     */
    public ViewContextAction(String displayName, BlackboardArtifactNode artifactNode) {
        super(displayName);
        this.content = artifactNode.getLookup().lookup(Content.class);
    }

    /**
     * An action that displays the context for some file system content by
     * expanding the data sources branch of the tree view to the level of the
     * parent of the content, selecting the parent in the tree view, then
     * selecting the content in the results view. This is commonly called "view
     * file in directory."
     *
     * @param displayName           The display name for the action.
     * @param fileSystemContentNode The file system content node for the
     *                              content.
     */
    public ViewContextAction(String displayName, AbstractFsContentNode<? extends AbstractFile> fileSystemContentNode) {
        super(displayName);
        this.content = fileSystemContentNode.getLookup().lookup(Content.class);
    }

    /**
     * An action that displays the context for some content by expanding the
     * data sources branch of the tree view to the level of the parent of the
     * content, selecting the parent in the tree view, then selecting the
     * content in the results view. This is commonly called "view file in
     * directory."
     *
     * @param displayName The display name for the action.
     * @param content     The content.
     */
    public ViewContextAction(String displayName, Content content) {
        super(displayName);
        this.content = content;
    }

    /**
     * Invoked when the action occurs.
     *
     * @param event
     */
    @Override
    public void actionPerformed(ActionEvent event) {
        /*
         * Ensure that the action manipulates the tree view in the GUI event
         * thread.
         */
        EventQueue.invokeLater(() -> {
            /*
             * Create a flattened copy of the branch of the tree view that leads
             * to the specified content, starting with the data source of the
             * content, which actually could be the content itself. Note that
             * the "dummy" root node used to create the branch needs to be
             * wrapped in a DirectoryTreeFilterNode so that its child nodes will
             * also be wrapped in DirectoryTreeFilterNodes via
             * DirectoryTreeFilterNodeChildren. Otherwise, the display names of
             * the nodes in the branch will not have child node counts, and will
             * not match the display names of the corresponding nodes in the
             * actual tree view.
             */
            LeafToRootContentBranchVisitor branchBuilder = new LeafToRootContentBranchVisitor();
            List<Content> branch = content.accept(branchBuilder);
            Collections.reverse(branch);
            Node dummyRootNode = new DirectoryTreeFilterNode(new AbstractNode(new RootContentChildren(branch)), true);
            Children branchChildren = dummyRootNode.getChildren();

            /*
             * Use the flattened copy of the branch of the tree view that leads
             * to the specified content to do a depth-first search for the
             * parent node of the content in the actual tree view, starting from
             * the tree view's "Data Sources" node.
             */
            DirectoryTreeTopComponent treeViewTopComponent = DirectoryTreeTopComponent.findInstance();
            TreeView treeView = treeViewTopComponent.getTree();
            ExplorerManager treeViewExplorerMgr = treeViewTopComponent.getExplorerManager();
            Node treeViewRootNode = treeViewExplorerMgr.getRootContext();
            Children treeViewRootNodeChildren = treeViewRootNode.getChildren();
            Node dataSourcesNode = treeViewRootNodeChildren.findChild(DataSourcesNode.NAME);
            Children currentTreeViewNodeChildren = dataSourcesNode.getChildren();
            Node contentParentNode = null;
            for (int i = 0; i < branchChildren.getNodesCount(); i++) {
                Node currentBranchNode = branchChildren.getNodeAt(i);
                for (int j = 0; j < currentTreeViewNodeChildren.getNodesCount(); j++) {
                    Node currentTreeViewNode = currentTreeViewNodeChildren.getNodeAt(j);
                    if (currentBranchNode.getDisplayName().equals(currentTreeViewNode.getDisplayName())) {
                        contentParentNode = currentTreeViewNode;
                        treeView.expandNode(contentParentNode);
                        currentTreeViewNodeChildren = currentTreeViewNode.getChildren();
                        break;
                    }
                }
            }
            if (null == contentParentNode) {
                logger.log(Level.SEVERE, "Failed to find the parent node of Content node to be selected in the results view in the tree view"); //NON-NLS
                return;
            }

            if (branchChildren.getNodesCount() != 1) {
                DirectoryTreeFilterNode contentParentFilterNode = (DirectoryTreeFilterNode) contentParentNode;
                DirectoryTreeFilterNode.OriginalNode decoratedNodeHolder = contentParentFilterNode.getLookup().lookup(DirectoryTreeFilterNode.OriginalNode.class);
                if (decoratedNodeHolder == null) {
                    logger.log(Level.SEVERE, "Failed to extract decorated node holder of the DirectoryTreeFilterNode decorator of the parent node of Content node to be selected in the results view"); //NON-NLS
                    return;
                }
                Node originNode = decoratedNodeHolder.getNode();
            }

            /*
             * Select the parent node of content node to be selected in the
             * results view in the tree view.
             */
            try {
                treeView.expandNode(contentParentNode);
                treeViewExplorerMgr.setExploredContextAndSelection(contentParentNode, new Node[]{contentParentNode});
            } catch (PropertyVetoException ex) {
                logger.log(Level.SEVERE, "Failed to select the parent node of Content node to be selected in the results view in the tree view", ex); //NON-NLS
                return;
            }

            if (branchChildren.getNodesCount() != 1) {
                /*
                 * The target content is not a data source, queue another task
                 * for the GUI event thread to get the current root node of the
                 * result view top component, get the display name of the
                 * content node to be selected in the results view, and then
                 * dispatch a ResultViewNodeSelectionTask (a SwingWorker).
                 *
                 * TODO (JIRA-1655): This participates in a race condition.
                 */
//                EventQueue.invokeLater(() -> {
//                    DataResultTopComponent resultViewTopComponent = treeViewTopComponent.getDirectoryListing();
//                    Node currentRootNodeOfResultsView = resultViewTopComponent.getRootNode();
//                    Node contentNode = content.accept(new RootContentChildren.CreateSleuthkitNodeVisitor());
//                    new ResultViewNodeSelectionTask(resultViewTopComponent, contentNode.getName(), currentRootNodeOfResultsView).execute();
//                });
            }
        });
    }

    /**
     * Makes a clone of this action.
     *
     * @return The cloned action.
     *
     * @throws CloneNotSupportedException Exception thrown if there is a problem
     *                                    creating the clone.
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        ViewContextAction clone = (ViewContextAction) super.clone();
        clone.setContent(this.content);
        return clone;
    }

    /**
     * Sets the content object associated with the action.
     *
     * @param content A content object.
     */
    private void setContent(Content content) {
        this.content = content;
    }

    /**
     * Waits for a Node's children to be generated, regardless of whether they
     * are lazily loaded, then sets the correct selection in a specified
     * DataResultTopComponent.
     */
    private class ResultViewNodeSelectionTask extends SwingWorker<Node[], Integer> {

        DataResultTopComponent resultViewTopComponent;
        String nameOfNodeToSelect;
        Node currentRootNodeOfResultsView;

        ResultViewNodeSelectionTask(DataResultTopComponent resultViewTopComponent, String nameToSelect, Node currentRootNodeOfResultsView) {
            this.resultViewTopComponent = resultViewTopComponent;
            this.nameOfNodeToSelect = nameToSelect;
            this.currentRootNodeOfResultsView = currentRootNodeOfResultsView;
        }

        @Override
        protected Node[] doInBackground() throws Exception {
            /*
             * Return all of the child nodes of the current root node of the
             * results view. Note that calls to Children.getNodes(true) block
             * until all child nodes have been created, regardless of whether
             * they are created lazily). There are two ideas here: 1) avoid
             * getting a proxy "wait" node, and 2) do this in the background to
             * avoid monopolizing the EDT for this potentially lengthy
             * operation.
             *
             * RJCTODO: Is this all true? What if the user selects another node
             * in the tree while this is going on?
             */
            return currentRootNodeOfResultsView.getChildren().getNodes(true);
        }

        @Override
        protected void done() {
            Node[] nodesDisplayedInResultsView;
            try {
                nodesDisplayedInResultsView = get();
            } catch (InterruptedException | CancellationException | ExecutionException ex) {
                logger.log(Level.SEVERE, "ResultViewNodeSelectionTask failed to get nodes displayed in results view.", ex); //NON-NLS
                return;
            }

            // It is possible the user selected a different Node to be displayed
            // in the DataResultViewer while the child Nodes were being generated.
            // In that case, we don't want to set the selection because it the
            // nodes returned from get() won't be in the DataResultTopComponent's
            // ExplorerManager. If we did call setSelectedNodes, it would clear
            // the current selection, which is not good.
            if (resultViewTopComponent.getRootNode().equals(currentRootNodeOfResultsView) == false) {
                return;
            }

            // Find the correct node to select from the nodes that are displayed
            // in the data result viewer and set it as the selection of the 
            // DataResultTopComponent.
            for (Node node : nodesDisplayedInResultsView) {
                if (nameOfNodeToSelect.equals(node.getName())) {
                    resultViewTopComponent.requestActive();
                    resultViewTopComponent.setSelectedNodes(new Node[]{node});
                    DirectoryTreeTopComponent.getDefault().fireViewerComplete();
                    break;
                }
            }
        }

    }

    /**
     * A ReverseHierarchyVisitor is a ContentVisitor that returns a list of
     * content objects by starting with a given content and following its chain
     * of ancestors to the root content of the hierarchy.
     */
    private class LeafToRootContentBranchVisitor extends ContentVisitor.Default<List<Content>> {

        List<Content> hierarchy = new ArrayList<>();

        @Override
        public List<Content> visit(VolumeSystem volumeSystem) {
            /*
             * Volume systems are not shown in the tree view.
             */
            return visitParentButDontAddMe(volumeSystem);
        }

        @Override
        public List<Content> visit(FileSystem fileSystem) {
            /*
             * File systems are not shown in the tree view.
             */
            return visitParentButDontAddMe(fileSystem);
        }

        @Override
        public List<Content> visit(Directory directory) {
            /*
             * File system root directories are not shown in the tree view.
             */
            if (ROOT_DIR_META_ADDR == directory.getMetaAddr()) {
                return visitParentButDontAddMe(directory);
            } else {
                return defaultVisit(directory);
            }
        }

        @Override
        protected List<Content> defaultVisit(Content content) {
            hierarchy.add(content);
            Content parent = null;
            try {
                parent = content.getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Could not get parent of Content object: %s", content), ex); //NON-NLS
            }
            return parent == null ? hierarchy : parent.accept(this);
        }

        private List<Content> visitParentButDontAddMe(Content content) {
            Content parent = null;
            try {
                parent = content.getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Could not get parent of Content object: %s", content), ex); //NON-NLS
            }
            return parent == null ? hierarchy : parent.accept(this);
        }

    }
}
