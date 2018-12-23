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

import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;

/**
 * A <code>Children</code> implementation for a <code>TableFilterNode</code>. A
 * <code>TableFilterNode</code> creates at most one layer of child nodes for the
 * node it wraps. It is designed to be used in the results view to ensure the
 * individual viewers display only the first layer of child nodes.
 */
class TableFilterChildren extends FilterNode.Children {

    /**
     * Creates a Children object for a TableFilterNode. A TableFilterNode
     * creates at most one layer of child nodes for the node it wraps. It is
     * designed to be used in the results view to ensure the individual viewers
     * display only the first layer of child nodes.
     *
     * @param wrappedNode    The node wrapped by the TableFilterNode.
     * @param createChildren True if a children (child factory) object should be
     *                       created for the wrapped node.
     *
     * @return A children (child factory) object for a node wrapped by a
     *         TableFilterNode.
     */
    public static Children createInstance(Node wrappedNode, boolean createChildren) {

        if (createChildren) {
            return new TableFilterChildren(wrappedNode);
        } else {
            return Children.LEAF;
        }
    }

    /**
     * Constructs a children (child factory) implementation for a
     * <code>TableFilterNode</code>. A <code>TableFilterNode</code> creates at
     * most one layer of child nodes for the node it wraps. It is designed to be
     * used for nodes displayed in Autopsy table views.
     *
     * @param wrappedNode The node wrapped by the TableFilterNode.
     */
    TableFilterChildren(Node wrappedNode) {
        super(wrappedNode);
    }

    /**
     * Copies a TableFilterNode, with the create children (child factory) flag
     * set to false.
     *
     * @param nodeToCopy The TableFilterNode to copy.
     *
     * @return A copy of a TableFilterNode.
     */
    @Override
    protected Node copyNode(Node nodeToCopy) {
        return new TableFilterNode(nodeToCopy, false);
    }

    /**
     * Creates the child nodes represented by this children (child factory)
     * object.
     *
     * @param key The key, i.e., the node, for which to create the child nodes.
     *
     * @return
     */
    @Override
    protected Node[] createNodes(Node key) {
        return new Node[]{this.copyNode(key)};
    }
}
