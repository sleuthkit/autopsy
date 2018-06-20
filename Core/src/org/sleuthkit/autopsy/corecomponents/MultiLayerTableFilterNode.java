/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
 * A filter node that creates multiple layers of child nodes for the node it
 * wraps. It is designed to be used in the results view to ensure the individual
 * viewers display only the first layer of child nodes.
 */
public class MultiLayerTableFilterNode extends FilterNode implements TableFilterNode {

    private final boolean createChildren;
    private final boolean forceUseWrappedDisplayName;
    private static final String COLUMN_ORDER_KEY = "NONE";

    /**
     * Constructs a filter node that generates children using
     * <code>TableFilterChildrenWithDescendant</code>s. This enables row to have descendants.
     *
     * Enables use of <code>getDisplayName()</code> for children of this node.
     *
     * @param node The node to wrap
     * @param childLayerDepth number of tree layers before we hit a leaf
     */
    public MultiLayerTableFilterNode(Node node, int childLayerDepth) {
        super(node, TableFilterChildrenWithDescendants.createInstance(node, childLayerDepth), Lookups.proxy(node));
        this.createChildren = true;
        this.forceUseWrappedDisplayName = true;
    }

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

    @Override
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

    @Override
    public String getColumnOrderKey() {
        return COLUMN_ORDER_KEY;
    }

    @Override
    public void setChildNodeSelectionInfo(NodeSelectionInfo selectedChildNodeInfo) {
        /*
         * TODO maybe we dont actually want to do anything here...?
         * Currently, child selection is only supported for nodes selected in
         * the tree view and decorated with a DataResultFilterNode.
         */
        if (getOriginal() instanceof DataResultFilterNode) {
            ((DataResultFilterNode) getOriginal()).setChildNodeSelectionInfo(selectedChildNodeInfo);
        }
    }
}
