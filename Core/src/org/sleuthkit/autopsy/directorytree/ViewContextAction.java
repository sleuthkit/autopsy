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
package org.sleuthkit.autopsy.directorytree;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.TreeView;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.TskContentItem;
import org.sleuthkit.autopsy.mainui.nodes.ChildNodeSelectionInfo.ContentNodeSelectionInfo;
import org.sleuthkit.autopsy.mainui.nodes.RootFactory.AllDataSourcesNode;
import org.sleuthkit.autopsy.mainui.nodes.RootFactory.DataSourceFilesNode;
import org.sleuthkit.autopsy.mainui.nodes.RootFactory.DataSourceGroupedNode;
import org.sleuthkit.autopsy.mainui.nodes.RootFactory.PersonNode;
import org.sleuthkit.autopsy.mainui.nodes.TreeNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.UnsupportedContent;
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
        this.content = artifactNode.getLookup().lookup(Content.class);
        if (this.content != null && this.content instanceof AbstractFile) {
            AbstractFile file = (AbstractFile) content;
            //disable the action if the content is a file and the file is hidden
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
     * An action that displays the context for abstract file by expanding the
     * data sources branch of the tree view to the level of the parent of the
     * content, selecting the parent in the tree view, then selecting the
     * content in the results view.
     *
     * @param displayName              The display name for the action.
     * @param abstractAbstractFileNode The AbstractAbstractFileNode node for the
     *                                 content.
     */
    public ViewContextAction(String displayName, AbstractAbstractFileNode<? extends AbstractFile> abstractAbstractFileNode) {
        super(displayName);
        this.content = abstractAbstractFileNode.getLookup().lookup(Content.class);
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
     * NOTE: This code will likely need updating in the event that the structure
     * of the nodes is changed (i.e. adding parent levels). Places to look when
     * changing node structure include:
     *
     * DirectoryTreeTopComponent.viewArtifact, ViewContextAction
     *
     * @param event The action event.
     */
    @Override
    @Messages({
        "ViewContextAction.errorMessage.cannotFindDirectory=Failed to locate directory.",
        "ViewContextAction.errorMessage.cannotSelectDirectory=Failed to select directory in tree.",
        "ViewContextAction.errorMessage.cannotFindNode=Failed to locate data source node in tree.",
        "ViewContextAction.errorMessage.unsupportedParent=Unable to navigate to content not supported in this release."
    })
    public void actionPerformed(ActionEvent event) {
        EventQueue.invokeLater(() -> {

            Content parentContent = getParentContent(this.content);

            if ((parentContent != null) && (parentContent instanceof UnsupportedContent)) {
                MessageNotifyUtil.Message.error(Bundle.ViewContextAction_errorMessage_unsupportedParent());
                logger.log(Level.WARNING, String.format("Could not navigate to unsupported content with id: %d", parentContent.getId())); //NON-NLS
                return;
            }

            // Get the "Data Sources" node from the tree view.
            DirectoryTreeTopComponent treeViewTopComponent = DirectoryTreeTopComponent.findInstance();
            ExplorerManager treeViewExplorerMgr = treeViewTopComponent.getExplorerManager();

            Node parentTreeViewNode = null;
            if (parentContent != null) {
                if (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
                    parentTreeViewNode = getParentNodeGroupedByPersonHost(treeViewExplorerMgr, parentContent);
                } else {
                    parentTreeViewNode = getParentNodeGroupedByDataSource(treeViewExplorerMgr, parentContent);
                }
            }

            // if no node is found, report error and do nothing
            if (parentTreeViewNode == null) {
                MessageNotifyUtil.Message.error(Bundle.ViewContextAction_errorMessage_cannotFindNode());
                logger.log(Level.SEVERE, "Failed to locate data source node in tree."); //NON-NLS
                return;
            }

            setNodeSelection(this.content, parentTreeViewNode, treeViewTopComponent, treeViewExplorerMgr);
        });
    }

    /**
     * Get the parent content for the content to be selected in the results
     * view. If the parent content is null, then the specified content is a data
     * source, and the parent tree view node is the "Data Sources" node.
     * Otherwise, the tree view needs to be searched to find the parent treeview
     * node.
     *
     * @param content The content whose parent will be returned. If this item is
     *                a datasource, it will be returned.
     *
     * @return The content if content is a data source or the parent of this
     *         content.
     */
    private Content getParentContent(Content content) {

        try {
            return (content instanceof DataSource)
                    ? content
                    : content.getParent();
        } catch (TskCoreException ex) {
            MessageNotifyUtil.Message.error(Bundle.ViewContextAction_errorMessage_cannotFindDirectory());
            logger.log(Level.SEVERE, String.format("Could not get parent of Content object: %s", content), ex); //NON-NLS
            return null;
        }
    }

    /**
     * Returns the node in the tree related to the parentContent or null if
     * can't be found. This method should be used when view is grouped by data
     * source.
     *
     * @param treeViewExplorerMgr The explorer manager.
     * @param parentContent       The content whose equivalent node will be
     *                            returned if found.
     *
     * @return The node if found or null.
     */
    private Node getParentNodeGroupedByDataSource(ExplorerManager treeViewExplorerMgr, Content parentContent) {
        // Classic view
        // Start the search at the DataSourcesNode
        Children rootChildren = treeViewExplorerMgr.getRootContext().getChildren();
        long parentContentDsId;
        
        if (parentContent instanceof AbstractFile) {
            parentContentDsId = ((AbstractFile) parentContent).getDataSourceObjectId();
        } else {
            try {
                parentContentDsId = parentContent.getDataSource().getId();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "An error occurred while fetching data source for content with id: " + parentContent.getId(), ex);
                return null;
            }
        }
        
        Node rootDsNode = rootChildren == null ? null : rootChildren.findChild(AllDataSourcesNode.getNameIdentifier());
        if (rootDsNode != null) {
            for (Node dataSourceLevelNode : getDataSourceLevelNodes(rootDsNode)) {
                DataSource dataSource = dataSourceLevelNode.getLookup().lookup(DataSource.class);
                if (dataSource != null && dataSource.getId() == parentContentDsId) {
                    // the tree view needs to be searched to find the parent treeview node.
                    Node potentialParentTreeViewNode = findContentNodeInDS(dataSourceLevelNode, parentContent);
                    if (potentialParentTreeViewNode != null) {
                        return potentialParentTreeViewNode;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Returns the node in the tree related to the parentContent or null if
     * can't be found. This method should be used when view is grouped by
     * hosts/persons.
     *
     * @param treeViewExplorerMgr The explorer manager.
     * @param parentContent       The content whose equivalent node will be
     *                            returned if found.
     *
     * @return The node if found or null.
     */
    private Node getParentNodeGroupedByPersonHost(ExplorerManager treeViewExplorerMgr, Content parentContent) {
        // 'Group by Host/Person' view

        try {
            long contentDSObjid = parentContent.getDataSource().getId();
            
            Children rootChildren = treeViewExplorerMgr.getRootContext().getChildren();

            // the tree view needs to be searched to find the parent treeview node.
            List<Node> dataSourceLevelNodes = Stream.of(rootChildren.getNodes(true))
                    .flatMap(rootNode -> getDataSourceLevelNodes(rootNode).stream())
                    .collect(Collectors.toList());

            for (Node treeNode : dataSourceLevelNodes) {
                // in the root, look for a data source node with the name of interest
                DataSource nodeDs = treeNode.getLookup().lookup(DataSource.class);
                
                // if not data source continue
                if (nodeDs == null || nodeDs.getId() != contentDSObjid) {
                    continue;
                }
                
                // check whether this is the data source we are looking for
                Node parentTreeViewNode = findContentNodeInDS(treeNode, parentContent);
                if (parentTreeViewNode != null) {
                    // found the data source node
                    return parentTreeViewNode;
                }
            }
        } catch (TskCoreException ex) {
            MessageNotifyUtil.Message.error(Bundle.ViewContextAction_errorMessage_cannotFindNode());
            logger.log(Level.SEVERE, "Failed to locate data source node in tree.", ex); //NON-NLS
        }

        return null;
    }

    /**
     * Set the node selection in the tree.
     * @param content The content to select.
     * @param parentTreeViewNode The node that is the parent of the content.
     * @param treeViewTopComponent The DirectoryTreeTopComponent.
     * @param treeViewExplorerMgr The ExplorerManager.
     */
    private void setNodeSelection(Content content, Node parentTreeViewNode, DirectoryTreeTopComponent treeViewTopComponent, ExplorerManager treeViewExplorerMgr) {
        /*
        * Set the child selection info of the parent tree node, then select
        * the parent node in the tree view. The results view will retrieve
        * this selection info and use it to complete this action when the
        * tree view top component responds to the selection of the parent
        * node by pushing it into the results view top component.
         */
        
        Long childIdToSelect = content.getId();

        if (content instanceof BlackboardArtifact) {
            BlackboardArtifact artifact = ((BlackboardArtifact) content);
            long associatedId = artifact.getObjectID();
            try {
                Content associatedFileContent = artifact.getSleuthkitCase().getContentById(associatedId);
                childIdToSelect = associatedFileContent.getId();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not find associated content from artifact with id %d", artifact.getId());
            }
        }
        
        if(parentTreeViewNode instanceof TreeNode) {
            ((TreeNode) parentTreeViewNode).setNodeSelectionInfo(new ContentNodeSelectionInfo(childIdToSelect));
        }

        TreeView treeView = treeViewTopComponent.getTree();
        treeView.expandNode(parentTreeViewNode);
        if (treeViewTopComponent.getSelectedNode().equals(parentTreeViewNode)) {
            //In the case where our tree view already has the destination directory selected
            //due to an optimization in the ExplorerManager.setExploredContextAndSelection method
            //the property change we listen for to call DirectoryTreeTopComponent.respondSelection
            //will not be sent so we call it manually ourselves after making
            //the directory listing the active tab.
            treeViewTopComponent.setDirectoryListingActive();
            treeViewTopComponent.respondSelection(treeViewExplorerMgr.getSelectedNodes(), new Node[]{parentTreeViewNode});
        } else {
            try {
                treeViewExplorerMgr.setExploredContextAndSelection(parentTreeViewNode, new Node[]{parentTreeViewNode});
            } catch (PropertyVetoException ex) {
                MessageNotifyUtil.Message.error(Bundle.ViewContextAction_errorMessage_cannotSelectDirectory());
                logger.log(Level.SEVERE, "Failed to select the parent node in the tree view", ex); //NON-NLS
            }
        }
    }

    /**
     * If the node has lookup of host or person, returns children. If not, just
     * returns itself.
     *
     * @param node The node.
     *
     * @return The child nodes that are at the data source level.
     */
    private List<Node> getDataSourceLevelNodes(Node node) {
        if (node == null) {
            return Collections.emptyList();
        } else if (node.getLookup().lookup(Host.class) != null
                || node.getLookup().lookup(Person.class) != null
                || AllDataSourcesNode.getNameIdentifier().equals(node.getName())
                || PersonNode.getUnknownPersonId().equals(node.getLookup().lookup(String.class))
                || (node.getName() != null && (node.getName().startsWith(DataSourceGroupedNode.getNamePrefix()) || node.getName().startsWith(DataSourceFilesNode.getNamePrefix())))) {
            Children children = node.getChildren();
            Node[] childNodes = children == null ? null : children.getNodes(true);
            if (childNodes == null) {
                return Collections.emptyList();
            }

            return Stream.of(node.getChildren().getNodes(true))
                    .flatMap(parent -> getDataSourceLevelNodes(parent).stream())
                    .collect(Collectors.toList());
        } else {
            return Arrays.asList(node);
        }
    }

    /**
     * Find content node for specified content within a data source level node.  
     * 
     * NOTE: it is probably a good idea to check that the content is in the 
     * data source for the data source node before traversal (for performance 
     * reasons).
     * 
     * @param dataSourceNode The data source level node.
     * @param content The content whose node is to be found.
     * @return The found node or null.
     */
    private Node findContentNodeInDS(Node dataSourceNode, Content content) {
        /*
         * Get an ordered list of the ancestors of the specified
         * content, starting with its data source.
         */
        List<Content> path = content.accept(new AncestorVisitor());
        // last item should be the data source, which can be ignored
        if (path.size() == 0) {
            return null;
        } else {
            path = path.subList(0, path.size() - 1);
        }
        Collections.reverse(path);
                
        /**
         * For each path element in path, find matching content.
         */
        Node curContentNode = dataSourceNode;
        for (Content pathElement : path) {
            // ensure curContentNode and its children exist
            if (curContentNode == null || curContentNode.getChildren() == null) {
                return null;
            }
            
            // ensure node array returned from getNodes
            Node[] curContentChildNodes = curContentNode.getChildren().getNodes(true);
            if (curContentChildNodes == null) {
                return null;
            }
            
            // find child node in path to continue traversal
            boolean found = false;
            for (Node curContentChildNode : curContentChildNodes) {
                TskContentItem<?> contentItem = curContentChildNode.getLookup().lookup(TskContentItem.class);
                if (contentItem != null && contentItem.getTskContent() != null && contentItem.getTskContent().getId() == pathElement.getId()) {
                    curContentNode = curContentChildNode;
                    found = true;
                    break;
                }
            }
            
            // if not found, we won't be able to identify the path.
            if (!found) {
                return null;
            }
        }
        
        return curContentNode;
    }

    /**
     * A ContentVisitor that returns a list of content objects by starting with
     * a given content and following its chain of ancestors to the root content
     * of the lineage.
     * 
     * NOTE: This should be kept in sync with 
     * FileSystemColumnUtils.getDisplayableContentForTableAndTree
     */
    private static class AncestorVisitor extends ContentVisitor.Default<List<Content>> {

        List<Content> lineage = new ArrayList<>();

        @Override
        protected List<Content> defaultVisit(Content content) {
            if (content instanceof AbstractFile && ((AbstractFile) content).isRoot()) {
                return skipToParent(content);
            }
            
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
