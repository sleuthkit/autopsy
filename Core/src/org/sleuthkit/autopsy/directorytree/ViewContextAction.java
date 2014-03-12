/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * View the directory content associated with the given Artifact in the DataResultViewer.
 * 
 * 1.   Expands the Directory Tree to the location of the parent Node of the 
 *      associated Content.
 * 2.   Selects the parent Node of the associated Content in the Directory Tree,
 *      which causes the parent Node's Children to be visible in the DataResultViewer.
 * 3.   Waits for all the Children to be contentNode in the DataResultViewer and
 *      selects the Node that represents the Content.
 */
public class ViewContextAction extends AbstractAction {

    private Content content;
    private static final Logger logger = Logger.getLogger(ViewContextAction.class.getName());

    public ViewContextAction(String title, BlackboardArtifactNode node) {
        super(title);
        this.content = node.getLookup().lookup(Content.class);

    }

    public ViewContextAction(String title, AbstractFsContentNode<? extends AbstractFile> node) {
        super(title);
        this.content = node.getLookup().lookup(Content.class);
    }

    public ViewContextAction(String title, Content content) {
        super(title);
        this.content = content;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                // create a list of Content objects starting with content's
                // Image and ends with content
                ReverseHierarchyVisitor vtor = new ReverseHierarchyVisitor();
                List<Content> hierarchy = content.accept(vtor);
                Collections.reverse(hierarchy);

                Node generated = new DirectoryTreeFilterNode(new AbstractNode(new RootContentChildren(hierarchy)), true);
                Children genChilds = generated.getChildren();

                final DirectoryTreeTopComponent dirTree = DirectoryTreeTopComponent.findInstance();
                TreeView dirTreeView = dirTree.getTree();
                ExplorerManager dirTreeExplorerManager = dirTree.getExplorerManager();
                Node dirTreeRootNode = dirTreeExplorerManager.getRootContext();
                Children dirChilds = dirTreeRootNode.getChildren();
                Children currentChildren = dirChilds.findChild(DataSourcesNode.NAME).getChildren();

                Node dirExplored = null;

                // Find the parent node of the content in the directory tree
                for (int i = 0; i < genChilds.getNodesCount() - 1; i++) {
                    Node currentGeneratedNode = genChilds.getNodeAt(i);
                    for (int j = 0; j < currentChildren.getNodesCount(); j++) {
                        Node currentDirectoryTreeNode = currentChildren.getNodeAt(j);
                        if (currentGeneratedNode.getDisplayName().equals(currentDirectoryTreeNode.getDisplayName())) {
                            dirExplored = currentDirectoryTreeNode;
                            dirTreeView.expandNode(dirExplored);
                            currentChildren = currentDirectoryTreeNode.getChildren();
                            break;
                        }
                    }
                }

                // Set the parent node of the content as the selection in the
                // directory tree
                try {
                    if (dirExplored != null) {
                        dirTreeView.expandNode(dirExplored);
                        dirTreeExplorerManager.setExploredContextAndSelection(dirExplored, new Node[]{dirExplored});
                    }
                } catch (PropertyVetoException ex) {
                    logger.log(Level.WARNING, "Couldn't set selected node", ex);
                }

                
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        DataResultTopComponent dataResultTC = dirTree.getDirectoryListing();
                        Node currentRootNodeOfDataResultTC = dataResultTC.getRootNode();
                        Node contentNode = content.accept(new RootContentChildren.CreateSleuthkitNodeVisitor());
                        new SelectionWorker(dataResultTC, contentNode.getName(), currentRootNodeOfDataResultTC).execute();
                    }
                });
            }
        });
    }
    
    /**
     * Waits for a Node's children to be generated, regardless of whether they
     * are lazily loaded, then sets the correct selection in a specified
     * DataResultTopComponent.
     */
    private class SelectionWorker extends SwingWorker<Node[], Integer> {

        DataResultTopComponent dataResultTC;
        String nameOfNodeToSelect;
        Node originalRootNodeOfDataResultTC;
        
        SelectionWorker(DataResultTopComponent dataResult, String nameToSelect, Node originalRoot) {
            this.dataResultTC = dataResult;
            this.nameOfNodeToSelect = nameToSelect;
            this.originalRootNodeOfDataResultTC = originalRoot;
        }
        
        @Override
        protected Node[] doInBackground() throws Exception {
            // Calls to Children::getNodes(true) block until all child Nodes have
            // been created, regardless of whether they are created lazily. 
            // This means that this call will return the actual child Nodes
            // and will *NEVER* return a proxy wait Node. This is done on the 
            // background thread to ensure we are not hanging the ui as it could
            // be a lengthy operation. 
            return originalRootNodeOfDataResultTC.getChildren().getNodes(true);
        }
        
        @Override
        protected void done() {
            Node[] nodesDisplayedInDataResultViewer;
            try {
                nodesDisplayedInDataResultViewer = get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.WARNING, "Failed to get nodes in selection worker.", ex);
                return;
            } 
            
            // It is possible the user selected a different Node to be displayed
            // in the DataResultViewer while the child Nodes were being generated.
            // In that case, we don't want to set the selection because it the
            // nodes returned from get() won't be in the DataResultTopComponent's
            // ExplorerManager. If we did call setSelectedNodes, it would clear
            // the current selection, which is not good.
            if (dataResultTC.getRootNode().equals(originalRootNodeOfDataResultTC) == false) {
                return;
            }
            
            // Find the correct node to select from the nodes that are displayed
            // in the data result viewer and set it as the selection of the 
            // DataResultTopComponent.
            for (Node node : nodesDisplayedInDataResultViewer) {
                if (nameOfNodeToSelect.equals(node.getName())) {
                    dataResultTC.requestActive();
                    dataResultTC.setSelectedNodes(new Node[]{node});
                    DirectoryTreeTopComponent.getDefault().fireViewerComplete();
                    break;
                }
            }
        }
        
    }

    /**
     * The ReverseHierarchyVisitor class is designed to return a list of Content
     * objects starting with the one the user calls 'accept' with and ending at
     * the Image object. Please NOTE that Content objects in this hierarchy of
     * type VolumeSystem and FileSystem are skipped. This seems to be necessary
     * because
     * org.sleuthkit.autopsy.datamodel.AbstractContentChildren.CreateSleuthkitNodeVisitor
     * does not support these types.
     */
    private class ReverseHierarchyVisitor extends ContentVisitor.Default<List<Content>> {

        List<Content> ret = new ArrayList<Content>();

        private List<Content> visitParentButDontAddMe(Content content) {
            Content parent = null;
            try {
                parent = content.getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Couldn't get parent of Content object: " + content);
            }
            return parent == null ? ret : parent.accept(this);
        }

        @Override
        protected List<Content> defaultVisit(Content content) {
            ret.add(content);
            Content parent = null;
            try {
                parent = content.getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Couldn't get parent of Content object: " + content);
            }
            return parent == null ? ret : parent.accept(this);
        }

        @Override
        public List<Content> visit(FileSystem fs) {
            return visitParentButDontAddMe(fs);
        }

        @Override
        public List<Content> visit(VolumeSystem vs) {
            return visitParentButDontAddMe(vs);
        }
    }
}
