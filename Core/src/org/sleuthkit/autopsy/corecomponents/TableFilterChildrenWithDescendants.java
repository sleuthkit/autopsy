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

import org.openide.nodes.Children;
import org.openide.nodes.Node;

/**
 * Provides TableFilterChildren functionality, and adds support for children 
 * of rows (plus/minus buttons for each row with children).
 */
class TableFilterChildrenWithDescendants extends TableFilterChildren {
    
    private TableFilterChildrenWithDescendants(Node wrappedNode) {
        super(wrappedNode);
    }
    
    public static Children createInstance(Node wrappedNode, boolean createChildren){
        if(createChildren){
            return new TableFilterChildrenWithDescendants(wrappedNode);
        } else {
            return Children.LEAF;
        }
    }
    
    @Override
    protected Node copyNode(Node nodeToCopy){
        return new TableFilterNode(nodeToCopy, true);
    }    
}
