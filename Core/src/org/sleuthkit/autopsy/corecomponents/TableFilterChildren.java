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
package org.sleuthkit.autopsy.corecomponents;

import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;

/**
 * Complementary class to TableFilterNode.
 */
class TableFilterChildren extends FilterNode.Children {

    /**
     * the constructor
     */
    TableFilterChildren(Node arg) {
        super(arg);
    }

    @Override
    protected Node copyNode(Node arg0) {
        return new TableFilterNode(arg0, false);
    }

    @Override
    protected Node[] createNodes(Node arg0) {
        // filter out the children
        return new Node[]{this.copyNode(arg0)};
    }

    /**
     * Converts the given FsContent into "Children".
     *
     * @param fs
     *
     * @return children
     */
    public static Children createInstance(Node arg, boolean createChild) {
        if (createChild) {
            return new TableFilterChildren(arg);
        } else {
            return Children.LEAF;
        }
    }
}
