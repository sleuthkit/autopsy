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
 * A filter node that creates at most one layer of children for the node it
 * wraps. It is designed to be used for nodes displayed in Autopsy table views.
 */
public class TableFilterNode extends FilterNode {

    private final boolean isLeaf;

    /**
     * Constructs a filter node that creates at most one layer of children for
     * the node it wraps. It is designed to be used for nodes displayed in
     * Autopsy table views.
     *
     * @param wrappedNode The node to wrap in the filter node.
     * @param isLeaf      True if the wrapped node is a leaf node.
     */
    public TableFilterNode(Node wrappedNode, boolean isLeaf) {
        super(wrappedNode, TableFilterChildren.createInstance(wrappedNode, isLeaf));
        this.isLeaf = isLeaf;
    }

    /**
     * Returns a display name for the wrapped node, for use in the first column
     * of an Autopsy table view.
     *
     * @return The display name.
     */
    @Override
    public String getDisplayName() {
        if (isLeaf) {
            return NbBundle.getMessage(this.getClass(), "TableFilterNode.displayName.text");
        } else {
            return super.getDisplayName();
        }
    }

}
