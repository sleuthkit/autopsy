/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

/**
 * A filter node that creates at most one layer of child nodes for the node it
 * wraps. It is designed to be used for nodes displayed in Autopsy table views.
 * This ensures that the table view for the node will not recursively display
 * child nodes and display only the first layer of child nodes.
 */
public class TableFilterNode extends FilterNode {

    private final boolean createChildren;
    private String columnOrderKey = "NONE";

    /**
     * Constructs a filter node that creates at most one layer of child nodes
     * for the node it wraps. It is designed to be used for nodes displayed in
     * Autopsy table views.
     *
     * @param wrappedNode    The node to wrap in the filter node.
     * @param createChildren True if a children (child factory) object should be
     *                       created for the wrapped node.
     * The constructor should include column order key. (See getColumnOrderKey)
     */
    public TableFilterNode(Node wrappedNode, boolean createChildren) {
        super(wrappedNode, TableFilterChildren.createInstance(wrappedNode, createChildren));
        this.createChildren = createChildren;
    }

    /**
     * Constructs a filter node that has information about the node's type.
     * 
     * @param wrappedNode    The node to wrap in the filter node.
     * @param createChildren True if a children (child factory) object should be
     *                       created for the wrapped node.
     * @param columnOrderKey A key that represents the type of the original
     *                       wrapped node and what is being displayed under that
     *                       node.
     */
    public TableFilterNode(Node wrappedNode, boolean createChildren, String columnOrderKey) {
        super(wrappedNode, TableFilterChildren.createInstance(wrappedNode, createChildren));
        this.createChildren = createChildren;
        this.columnOrderKey = columnOrderKey;
    }

    /**
     * Returns a display name for the wrapped node, for use in the first column
     * of an Autopsy table view.
     *
     * @return The display name.
     */
    @Override
    public String getDisplayName() {
        if (createChildren) {
            return NbBundle.getMessage(this.getClass(), "TableFilterNode.displayName.text");
        } else {
            return super.getDisplayName();
        }
    }

    /**
     * @return the column order key, which allows custom column ordering to be
     *         written into a properties file and be reloaded for future use in
     *         a table with the same root node or for different cases. This is
     *         done by DataResultViewerTable. The key should represent what
     *         kinds of items the table is showing.
     */
    String getColumnOrderKey() {
        return columnOrderKey;
    }
}
