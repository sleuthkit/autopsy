/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Copyright 2013 Willi Ballenthin
 * Contact: willi.ballenthin <at> gmail <dot> com
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
package org.sleuthkit.autopsy.rejview;

import com.williballenthin.rejistry.RegistryHive;
import com.williballenthin.rejistry.RegistryParseException;
import java.awt.Dimension;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Tree view for registry hive information
 */
final class RejTreeView extends JScrollPane {

    private static final Logger logger = Logger.getLogger(HexView.class.getName());
    private static final long serialVersionUID = 1L;
    private final DefaultTreeModel treeModel;
    private final RejTreeViewListener rejTreeViewListener = new RejTreeViewListener();
    private final CopyOnWriteArrayList<RejTreeNodeSelectionListener> nodeSelectionListeners;

    /**
     * Construct a new RejTreeView given the RegistrHive object
     *
     * @param hive the registryhive to construct the RejTreeView for
     */
    @NbBundle.Messages({"RejTreeView.failureValueName.text=PARSE FAILED"})
    RejTreeView(RegistryHive hive) {
        DefaultMutableTreeNode rootNode;
        this.nodeSelectionListeners = new CopyOnWriteArrayList<>();
        try {
            rootNode = getTreeNode(new RejTreeKeyNode(hive.getRoot()));
        } catch (RegistryParseException ex) {
            logger.log(Level.WARNING, "Failed to parse root key", ex);
            rootNode = new DefaultMutableTreeNode(new RejTreeFailureNode(Bundle.RejTreeView_failureValueName_text()));
        }
        this.treeModel = new DefaultTreeModel(rootNode);
        this.treeModel.setAsksAllowsChildren(true);
        JTree tree = new JTree(this.treeModel);
        tree.addTreeExpansionListener(rejTreeViewListener);
        tree.addTreeSelectionListener(rejTreeViewListener);
        // here's a bit of a hack to force the children to be loaded and shown
        tree.collapsePath(new TreePath(rootNode.getPath()));
        tree.expandPath(new TreePath(rootNode.getPath()));
        setViewportView(tree);
        setPreferredSize(new Dimension(350, 20));
    }

    /**
     * getTreeNode creates a TreeNode from a RejTreeNode, settings the
     * appropriate fields.
     */
    private DefaultMutableTreeNode getTreeNode(RejTreeNode node) {
        DefaultMutableTreeNode ret;
        ret = new DefaultMutableTreeNode(node);
        ret.setAllowsChildren(node.hasChildren());
        return ret;
    }

    /**
     * Add a RejTreeNodeSelectionListener to the list of node selection
     * listeners the RejTreeView has
     *
     * @param selListener the RejTreeNodeSelectionListener to add
     */
    void addRejTreeNodeSelectionListener(RejTreeNodeSelectionListener selListener) {
        this.nodeSelectionListeners.add(selListener);
    }

    /**
     * Remove a RejTreeNodeSelectionListener from the list of node selection
     * listeners the RejTreeView has
     *
     * @param selListener the RejTreeNodeSelectionListener to remove
     */
    void removeRejTreeNodeSelectionListener(RejTreeNodeSelectionListener selListener) {
        this.nodeSelectionListeners.remove(selListener);
    }

    /**
     * Private listener for TreeExpansionEvents and TreeSelectionEvents
     */
    private class RejTreeViewListener implements TreeExpansionListener, TreeSelectionListener {

        @Override
        public void treeExpanded(TreeExpansionEvent event) {
            TreePath path = event.getPath();
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

            if (node.getChildCount() == 0) {
                RejTreeNode n = (RejTreeNode) node.getUserObject();
                for (RejTreeNode rejTreeNode : n.getChildren()) {
                    node.add(getTreeNode(rejTreeNode));
                }
                treeModel.nodeStructureChanged(node);
            }
        }

        @Override
        public void treeCollapsed(TreeExpansionEvent event) {
            //Empty method 
        }

        @Override
        public void valueChanged(TreeSelectionEvent e) {
            TreePath path = e.getPath();
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            this.triggerRejTreeNodeSelection((RejTreeNode) node.getUserObject());
        }

        /**
         * Call each of the node selection listeners for a given node
         */
        void triggerRejTreeNodeSelection(RejTreeNode n) {
            RejTreeNodeSelectionEvent e = new RejTreeNodeSelectionEvent(n);
            for (RejTreeNodeSelectionListener listener : nodeSelectionListeners) {
                listener.nodeSelected(e);
            }
        }
    }
}
