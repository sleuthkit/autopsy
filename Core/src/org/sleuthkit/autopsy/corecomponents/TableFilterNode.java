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

import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;

/**
 * This class is used to filter the nodes that we want to show on the "TreeTableView".
 * So basically we just want to show one layer of nodes from it's parent.
 *
 * @author jantonius
 */
public class TableFilterNode extends FilterNode {

    private boolean createChild;

    /** the constructor */
    public TableFilterNode(Node arg, boolean crChild) {
        super(arg, TableFilterChildren.createInstance(arg, crChild));
        this.createChild = crChild;
    }

    /**
     * Override the display name / header for the first (tree) column on the
     * "TreeTableView".
     *
     * @return disName  the display name for the first column
     */
    @Override
    public String getDisplayName() {
        if (createChild) {
            return NbBundle.getMessage(this.getClass(), "TableFilterNode.displayName.text");
        } else {
            return super.getDisplayName();
        }
    }
}
