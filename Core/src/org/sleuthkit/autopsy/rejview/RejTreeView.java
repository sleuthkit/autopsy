/*
 * Autopsy Forensic Browser
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

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class RejTreeView extends JScrollPane implements TreeExpansionListener, TreeSelectionListener {

    private final DefaultTreeModel _tree_model;
    private JTree _tree;
    private final RegistryHive _hive;
    private final CopyOnWriteArrayList<RejTreeNodeSelectionListener> _nodeSelectionListeners;

    public RejTreeView(RegistryHive hive) {
        this._hive = hive;
        DefaultMutableTreeNode rootNode;
        this._nodeSelectionListeners = new CopyOnWriteArrayList<RejTreeNodeSelectionListener>();

        try {
            rootNode = getTreeNode(new RejTreeKeyNode(this._hive.getRoot()));
        } catch (RegistryParseException e) {
            System.err.println("Failed to parse root key");
            rootNode = new DefaultMutableTreeNode("PARSE FAILED");
        }

        this._tree_model = new DefaultTreeModel(rootNode);
        this._tree_model.setAsksAllowsChildren(true);

        this._tree = new JTree(this._tree_model);
        this._tree.addTreeExpansionListener(this);
        this._tree.addTreeSelectionListener(this);

        // here's a bit of a hack to force the children to be loaded and shown
        this._tree.collapsePath(new TreePath(rootNode.getPath()));
        this._tree.expandPath(new TreePath(rootNode.getPath()));

        setViewportView(this._tree);
        setPreferredSize(new Dimension(250, 400));
    }

    /**
     * getTreeNode creates a TreeNode from a RejTreeNode, settings the appropriate fields.
     */
    private DefaultMutableTreeNode getTreeNode(RejTreeNode node) {
        DefaultMutableTreeNode ret;
        ret = new DefaultMutableTreeNode(node);
        ret.setAllowsChildren(node.hasChildren());
        return ret;
    }

    @Override
    public void treeExpanded(TreeExpansionEvent event) {
        TreePath path = event.getPath();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

        if (node.getChildCount() == 0) {
            RejTreeNode n = (RejTreeNode)node.getUserObject();
            for (RejTreeNode rejTreeNode : n.getChildren()) {
                node.add(getTreeNode(rejTreeNode));
            }
            this._tree_model.nodeStructureChanged(node);
        }
    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) {
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        System.out.println("Selected: " + path);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        this.triggerRejTreeNodeSelection((RejTreeNode)node.getUserObject());
    }

    public void addRejTreeNodeSelectionListener(RejTreeNodeSelectionListener l) {
        this._nodeSelectionListeners.add(l);
    }

    public void removeRejTreeNodeSelectionListener(RejTreeNodeSelectionListener l ) {
        this._nodeSelectionListeners.remove(l);
    }

    private void triggerRejTreeNodeSelection(RejTreeNode n) {
        RejTreeNodeSelectionEvent e = new RejTreeNodeSelectionEvent(n);
        for (RejTreeNodeSelectionListener listener : this._nodeSelectionListeners) {
            listener.nodeSelected(e);
        }
    }
}
