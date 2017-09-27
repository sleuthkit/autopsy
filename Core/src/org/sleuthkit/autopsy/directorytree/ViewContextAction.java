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
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import org.openide.nodes.AbstractNode;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.TreeView;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.ContentNodeSelectionInfo;
import org.sleuthkit.autopsy.datamodel.DataSourcesNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.RootContentChildren;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * An action that displays the context for some content by expanding the data
 * sources branch of the tree view to the level of the parent of the content,
 * selecting the parent in the tree view, then selecting the content in the
 * results view.
 */
public class ViewContextAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ViewContextAction.class.getName());
    private final Content content;

    /**
     * An action that displays the context for the source content of an artifact
     * by expanding the data sources branch of the tree view to the level of the
     * parent of the content, selecting the parent in the tree view, then
     * selecting the content in the results view.
     *
     * @param displayName  The display name for the action.
     * @param artifactNode The artifact node for the artifact.
     */
    public ViewContextAction(String displayName, BlackboardArtifactNode artifactNode) {
        super(displayName);
        this.content = artifactNode.getLookup().lookup(AbstractFile.class);
        if(this.content != null) {
            AbstractFile file = (AbstractFile) content;
            if ((TskData.FileKnown.KNOWN == file.getKnown() && UserPreferences.hideKnownFilesInDataSourcesTree())
                    || (TskData.TSK_DB_FILES_TYPE_ENUM.SLACK == file.getType() && UserPreferences.hideSlackFilesInDataSourcesTree())) {
                this.setEnabled(false);
            }
        }
    }

    /**
     * An action that displays the context for some file system content by
     * expanding the data sources branch of the tree view to the level of the
     * parent of the content, selecting the parent in the tree view, then
     * selecting the content in the results view.
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
     * content in the results view.
     *
     * @param displayName The display name for the action.
     * @param content     The content.
     */
    public ViewContextAction(String displayName, Content content) {
        super(displayName);
        this.content = content;
    }

    /**
     * Displays the context for some content by expanding the data sources
     * branch of the tree view to the level of the parent of the content,
     * selecting the parent in the tree view, then selecting the content in the
     * results view.
     *
     * @param event The action event.
     */
    @Override
    @Messages({
        "ViewContextAction.errorMessage.cannotFindDirectory=Failed to locate directory.",
        "ViewContextAction.errorMessage.cannotSelectDirectory=Failed to select directory in tree.",
    })
    public void actionPerformed(ActionEvent event) {
        EventQueue.invokeLater(() -> {
            /*
             * Get the "Data Sources" node from the tree view.
             */
            DirectoryTreeTopComponent treeViewTopComponent = DirectoryTreeTopComponent.findInstance();
            ExplorerManager treeViewExplorerMgr = treeViewTopComponent.getExplorerManager();
            Node parentTreeViewNode = treeViewExplorerMgr.getRootContext().getChildren().findChild(DataSourcesNode.NAME);

            /*
             * Get the parent content for the content to be selected in the
             * results view. If the parent content is null, then the specified
             * content is a data source, and the parent tree view node is the
             * "Data Sources" node. Otherwise, the tree view needs to be
             * searched to find the parent treeview node.
             */
            Content parentContent = null;
            try {
                parentContent = content.getParent();
            } catch (TskCoreException ex) {
                MessageNotifyUtil.Message.error(Bundle.ViewContextAction_errorMessage_cannotFindDirectory());
                logger.log(Level.SEVERE, String.format("Could not get parent of Content object: %s", content), ex); //NON-NLS
                return;
            }
            if (null != parentContent) {
                /*
                 * Get an ordered list of the ancestors of the specified
                 * content, starting with its data source.
                 *
                 */
                AncestorVisitor ancestorVisitor = new AncestorVisitor();
                List<Content> contentBranch = parentContent.accept(ancestorVisitor);
                Collections.reverse(contentBranch);

                /**
                 * Convert the list of ancestors into a list of tree nodes.
                 *
                 * IMPORTANT: The "dummy" root node used to create this single
                 * layer of children needs to be wrapped in a
                 * DirectoryTreeFilterNode so that its child nodes will also be
                 * wrapped in DirectoryTreeFilterNodes, via
                 * DirectoryTreeFilterNodeChildren. Otherwise, the display names
                 * of the nodes in the branch will not have child node counts
                 * and will not match the display names of the corresponding
                 * nodes in the actual tree view.
                 */
                Node dummyRootNode = new DirectoryTreeFilterNode(new AbstractNode(new RootContentChildren(contentBranch)), true);
                Children ancestorChildren = dummyRootNode.getChildren();

                /*
                 * Search the tree for the parent node. Note that this algorithm
                 * simply discards "extra" ancestor nodes not shown in the tree,
                 * such as the root directory of the file system for file system
                 * content.
                 */
                Children treeNodeChildren = parentTreeViewNode.getChildren();
                for (int i = 0; i < ancestorChildren.getNodesCount(); i++) {
                    Node ancestorNode = ancestorChildren.getNodeAt(i);
                    for (int j = 0; j < treeNodeChildren.getNodesCount(); j++) {
                        Node treeNode = treeNodeChildren.getNodeAt(j);
                        if (ancestorNode.getDisplayName().equals(treeNode.getDisplayName())) {
                            parentTreeViewNode = treeNode;
                            treeNodeChildren = treeNode.getChildren();
                            break;
                        }
                    }
                }
            }

            /*
             * Set the child selection info of the parent tree node, then select
             * the parent node in the tree view. The results view will retrieve
             * this selection info and use it to complete this action when the
             * tree view top component responds to the selection of the parent
             * node by pushing it into the results view top component.
             */
            DisplayableItemNode undecoratedParentNode = (DisplayableItemNode) ((DirectoryTreeFilterNode) parentTreeViewNode).getOriginal();
            undecoratedParentNode.setChildNodeSelectionInfo(new ContentNodeSelectionInfo(content));
            TreeView treeView = treeViewTopComponent.getTree();
            treeView.expandNode(parentTreeViewNode);
            try {
                treeViewExplorerMgr.setExploredContextAndSelection(parentTreeViewNode, new Node[]{parentTreeViewNode});
            } catch (PropertyVetoException ex) {
                MessageNotifyUtil.Message.error(Bundle.ViewContextAction_errorMessage_cannotSelectDirectory());
                logger.log(Level.SEVERE, "Failed to select the parent node in the tree view", ex); //NON-NLS
            }
        });
    }

    /**
     * A ContentVisitor that returns a list of content objects by starting with
     * a given content and following its chain of ancestors to the root content
     * of the lineage.
     */
    private static class AncestorVisitor extends ContentVisitor.Default<List<Content>> {

        List<Content> lineage = new ArrayList<>();

        @Override
        protected List<Content> defaultVisit(Content content) {
            lineage.add(content);
            Content parent = null;
            try {
                parent = content.getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Could not get parent of Content object: %s", content), ex); //NON-NLS
            }
            return parent == null ? lineage : parent.accept(this);
        }

        @Override
        public List<Content> visit(VolumeSystem volumeSystem) {
            /*
             * Volume systems are not shown in the tree view. This is not
             * strictly necesssary given the algorithm above, but it is a simple
             * optimization.
             */
            return skipToParent(volumeSystem);
        }

        @Override
        public List<Content> visit(FileSystem fileSystem) {
            /*
             * File systems are not shown in the tree view. This is not strictly
             * necesssary given the algorithm above, but it is a simple
             * optimization.
             */
            return skipToParent(fileSystem);
        }

        private List<Content> skipToParent(Content content) {
            Content parent = null;
            try {
                parent = content.getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Could not get parent of Content object: %s", content), ex); //NON-NLS
            }
            return parent == null ? lineage : parent.accept(this);
        }
    }
    
}
