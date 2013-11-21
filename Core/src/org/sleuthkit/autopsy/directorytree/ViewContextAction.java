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
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.DataSourcesNode;
import org.sleuthkit.autopsy.datamodel.RootContentChildren;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * View the directory content associated with the given Artifact
 */
public class ViewContextAction extends AbstractAction {

    private Content content;
    private static final Logger logger = Logger.getLogger(ViewContextAction.class.getName());

    public ViewContextAction(String title, BlackboardArtifactNode node) {
        super(title);
        this.content = node.getLookup().lookup(Content.class);

    }

    public ViewContextAction(String title, AbstractFsContentNode node) {
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

                final DirectoryTreeTopComponent directoryTree = DirectoryTreeTopComponent.findInstance();
                TreeView tree = directoryTree.getTree();
                ExplorerManager man = directoryTree.getExplorerManager();
                Node dirRoot = man.getRootContext();
                Children dirChilds = dirRoot.getChildren();
                Node imagesRoot = dirChilds.findChild(DataSourcesNode.NAME);
                dirChilds = imagesRoot.getChildren();

                Node dirExplored = null;

                for (int i = 0; i < genChilds.getNodesCount() - 1; i++) {
                    Node currentGeneratedNode = genChilds.getNodeAt(i);
                    for (int j = 0; j < dirChilds.getNodesCount(); j++) {
                        Node currentDirectoryTreeNode = dirChilds.getNodeAt(j);
                        if (currentGeneratedNode.getDisplayName().equals(currentDirectoryTreeNode.getDisplayName())) {
                            dirExplored = currentDirectoryTreeNode;
                            tree.expandNode(dirExplored);
                            dirChilds = currentDirectoryTreeNode.getChildren();
                            break;
                        }
                    }
                }

                try {
                    if (dirExplored != null) {
                        tree.expandNode(dirExplored);
                        man.setExploredContextAndSelection(dirExplored, new Node[]{dirExplored});
                    }

                } catch (PropertyVetoException ex) {
                    logger.log(Level.WARNING, "Couldn't set selected node", ex);
                }

                // Another thread is needed because we have to wait for dataResult to populate
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        DataResultTopComponent dataResult = directoryTree.getDirectoryListing();
                        Node resultRoot = dataResult.getRootNode();
                        Node generated = content.accept(new RootContentChildren.CreateSleuthkitNodeVisitor());
                        new SelectionWorker(dataResult, generated.getName(), resultRoot).execute();
                        
                    }
                });
            }
        });
    }
    
    /**
     * Thread that waits for a Node's children to completely be generated (they
     * may be lazily loaded), then sets the correct selection in a 
     * DataResultTopComponent.
     */
    private class SelectionWorker extends SwingWorker<Node[], Integer> {

        DataResultTopComponent dataResult;
        String nameOfNodeToSelect;
        Node originalRootNode;
        
        SelectionWorker(DataResultTopComponent dataResult, String nameToSelect, Node originalRoot) {
            this.dataResult = dataResult;
            this.nameOfNodeToSelect = nameToSelect;
            this.originalRootNode = originalRoot;
        }
        
        @Override
        protected Node[] doInBackground() throws Exception {
            return originalRootNode.getChildren().getNodes(true);
        }
        
        @Override
        protected void done() {
            Node[] nodes;
            try {
                nodes = get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.WARNING, "Failed to get nodes in selection worker.");
                return;
            } 
            
            // If the root node of the data result viewer has changed, don't
            // set the selection.
            if (dataResult.getRootNode().equals(originalRootNode) == false) {
                return;
            }
            
            for (Node current : nodes) {
                if (nameOfNodeToSelect.equals(current.getName())) {
                    dataResult.requestActive();
                    dataResult.setSelectedNodes(new Node[]{current});
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
