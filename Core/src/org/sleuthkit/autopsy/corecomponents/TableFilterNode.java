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
package org.sleuthkit.autopsy.corecomponents;

import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.NodeSelectionInfo;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;

/**
 * A filter node that creates at most one layer of child nodes for the node it
 * wraps. It is designed to be used in the results view to ensure the individual
 * viewers display only the first layer of child nodes.
 */
public class TableFilterNode extends FilterNode {

    private final boolean createChildren;
    private final boolean forceUseWrappedDisplayName;
    private String columnOrderKey = "NONE";

    /**
     * Constructs a filter node that creates at most one layer of child nodes
     * for the node it wraps. It is designed to be used in the results view to
     * ensure the individual viewers display only the first layer of child
     * nodes.
     *
     * @param node The node to wrap in the filter node.
     * @param createChildren True if a Children object should be created for the
     * wrapped node.
     */
    public TableFilterNode(Node node, boolean createChildren) {
        super(node, TableFilterChildren.createInstance(node, createChildren), Lookups.proxy(node));
        this.forceUseWrappedDisplayName = false;
        this.createChildren = createChildren;
    }

    /**
     * Constructs a filter node that creates at most one layer of child nodes
     * for the node it wraps. It is designed to be used in the results view to
     * ensure the individual viewers display only the first layer of child
     * nodes.
     *
     * @param node The node to wrap in the filter node.
     * @param createChildren True if a Children object should be created for the
     * wrapped node.
     * @param columnOrderKey A key that represents the type of the original
     * wrapped node and what is being displayed under that node.
     */
    public TableFilterNode(Node node, boolean createChildren, String columnOrderKey) {
        super(node, TableFilterChildren.createInstance(node, createChildren));
        this.forceUseWrappedDisplayName = false;
        this.createChildren = createChildren;
        this.columnOrderKey = columnOrderKey;
    }
    
    public TableFilterNode(Node node, int childLayerDepth){
        super(node, TableFilterChildrenWithDescendants.createInstance(node, childLayerDepth), Lookups.proxy(node));
        this.createChildren = true;
        this.forceUseWrappedDisplayName = true;
    }

    /**
     * Gets the display name for the wrapped node, for use in the first column
     * of an Autopsy table view.
     *
     * @return The display name.
     */
    @Override
    public String getDisplayName() {
        if (this.forceUseWrappedDisplayName) {
            return super.getDisplayName();
        } else if (createChildren) {
            return NbBundle.getMessage(this.getClass(), "TableFilterNode.displayName.text");
        } else {
            return super.getDisplayName();
        }
    }

    /**
     * Adds information about which child node of this node, if any, should be
     * selected. Can be null.
     *
     * @param selectedChildNodeInfo The child node selection information.
     */
    public void setChildNodeSelectionInfo(NodeSelectionInfo selectedChildNodeInfo) {
        /*
         * Currently, child selection is only supported for nodes selected in
         * the tree view and decorated with a DataResultFilterNode.
         */
        if (getOriginal() instanceof DataResultFilterNode) {
            ((DataResultFilterNode) getOriginal()).setChildNodeSelectionInfo(selectedChildNodeInfo);
        }
    }

    /**
     * Gets information about which child node of this node, if any, should be
     * selected.
     *
     * @return The child node selection information, or null if no child should
     * be selected.
     */
    public NodeSelectionInfo getChildNodeSelectionInfo() {
        /*
         * Currently, child selection is only supported for nodes selected in
         * the tree view and decorated with a DataResultFilterNode.
         */
        if (getOriginal() instanceof DataResultFilterNode) {
            return ((DataResultFilterNode) getOriginal()).getChildNodeSelectionInfo();
        } else {
            return null;
        }
    }

    /**
     * @return the column order key, which allows custom column ordering to be
     * written into a properties file and be reloaded for future use in a table
     * with the same root node or for different cases. This is done by
     * DataResultViewerTable. The key should represent what kinds of items the
     * table is showing.
     */
    public String getColumnOrderKey() {
        return columnOrderKey;
    }
}
