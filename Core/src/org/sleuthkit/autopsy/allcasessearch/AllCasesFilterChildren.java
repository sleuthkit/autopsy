/*
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
package org.sleuthkit.autopsy.allcasessearch;

import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;

/**
 * A <code>Children</code> implementation for a
 * <code>CorrelationPropertyFilterNode</code>.
 */
final class AllCasesFilterChildren extends FilterNode.Children {
    
    /**
     * Create a new Children instance.
     * 
     * @param wrappedNode    The node to be wrapped.
     * @param createChildren If false, return LEAF. Otherwise, return a new
     *                       CorrelationPropertyFilterChildren instance.
     * 
     * @return A Children instance.
     */
    static Children createInstance(Node wrappedNode, boolean createChildren) {

        if (createChildren) {
            return new AllCasesFilterChildren(wrappedNode);
        } else {
            return Children.LEAF;
        }
    }
    
    /**
     * Constructs a children (child factory) implementation for a
     * <code>CorrelationPropertyFilterNode</code>.
     * 
     * @param wrappedNode The node wrapped by CorrelationPropertyFilterNode.
     */
    AllCasesFilterChildren(Node wrappedNode) {
        super(wrappedNode);
    }
    
    /**
     * Copies a CorrelationPropertyFilterNode, with the children (child factory)
     * flag set to false.
     * 
     * @param nodeToCopy The CorrelationPropertyFilterNode to copy.
     * 
     * @return A copy of a CorrelationPropertyFilterNode.
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
     * @return A single-element node array.
     */
    @Override
    protected Node[] createNodes(Node key) {
        return new Node[]{this.copyNode(key)};
    }
}
