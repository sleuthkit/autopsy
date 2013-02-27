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
package org.sleuthkit.autopsy.directorytree;

import org.openide.explorer.ExplorerManager;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;

/**
 * This class is used for the creation of all the children for the
 * DataResultFilterNode that created in the DataResultFilterNode.java.
 *
 */
public class DataResultFilterChildren extends FilterNode.Children {
    
    ExplorerManager sourceEm;

    /** the constructor */
    public DataResultFilterChildren(Node arg, ExplorerManager sourceEm) {
        super(arg);
        this.sourceEm = sourceEm;
    }

    @Override
    protected Node copyNode(Node arg0) {
        return new DataResultFilterNode(arg0, sourceEm);
    }
}