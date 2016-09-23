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
 */
public class TableFilterNode extends FilterNode {

    private final boolean createChildren;
    private String itemType = "NONE";

    /**
     * Constructs a filter node that creates at most one layer of child nodes
     * for the node it wraps. It is designed to be used for nodes displayed in
     * Autopsy table views.
     *
     * @param wrappedNode    The node to wrap in the filter node.
     * @param createChildren True if a children (child factory) object should be
     *                       created for the wrapped node.
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
     * @param itemType       A name of the node, based on its class name and
     *                       filter or artifact type if it holds those.
     */
    public TableFilterNode(Node wrappedNode, boolean createChildren, String itemType) {
        super(wrappedNode, TableFilterChildren.createInstance(wrappedNode, createChildren));
        this.createChildren = createChildren;
        this.itemType = itemType;
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
     * @return itemType of associated DisplayableItemNode to allow for custom
     *         column orderings in the DataResultViewerTable
     */
    String getItemType() {
        return itemType;
    }
}
