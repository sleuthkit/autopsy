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
package org.sleuthkit.autopsy.datamodel;

import java.awt.datatransfer.Transferable;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.datatransfer.PasteType;


/**
 * Interface for all displayable Nodes
 */
public abstract class DisplayableItemNode extends AbstractNode {

    public DisplayableItemNode(Children children) {
        super(children);
    }

    public DisplayableItemNode(Children children, Lookup lookup) {
        super(children, lookup);
    }
    
    
    /**
     * Possible sub-implementations
     */
    public enum TYPE {
        CONTENT,  ///< content node, such as file, image
        ARTIFACT, ///< artifact data node
        META, ///< top-level category node, such as view, filters, etc.
    };
    
    /**
     * Get possible subtype of the displayable item node
     * @return 
     */
    public abstract TYPE getDisplayableItemNodeType();
    
    public boolean isLeafTypeNode() {
        return false;
    }

    /**
     * Visitor pattern support.
     * 
     * @param v visitor
     * @return visitor's visit return value
     */
    public abstract <T> T accept(DisplayableItemNodeVisitor<T> v);

    
    
}
