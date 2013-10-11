/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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

import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.BlackboardArtifactTag;

/**
 * Instances of this class wrap BlackboardArtifactTag objects. In the Autopsy
 * presentation of the SleuthKit data model, they are leaf nodes of a sub-tree 
 * organized as follows: there is a tags root node with tag name child nodes; 
 * tag name nodes have tag type child nodes; tag type nodes are the parents of 
 * either content or blackboard artifact tag nodes.
 */
public class BlackboardArtifactTagNode  extends DisplayableItemNode {  
    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png"; // RJCTODO: Want better icons?

    public BlackboardArtifactTagNode(BlackboardArtifactTag tag) {
        super(Children.LEAF, Lookups.singleton(tag));
        super.setName(tag.getArtifact().getDisplayName());
        super.setDisplayName(tag.getArtifact().getDisplayName());
        this.setIconBaseWithExtension(ICON_PATH);
    }

    @Override
    protected Sheet createSheet() {
        // RJCTODO: Make additional properties as needed for DataResultViewers
        Sheet propertySheet = super.createSheet();
        Sheet.Set properties = propertySheet.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            propertySheet.put(properties);
        }

        properties.put(new NodeProperty("Name", "Name", "", getName()));

        return propertySheet;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        // See classes derived from DisplayableItemNodeVisitor<AbstractNode> 
        // for behavior added using the Visitor pattern.
        return v.visit(this);
    }

    @Override
    public DisplayableItemNode.TYPE getDisplayableItemNodeType() {
        return DisplayableItemNode.TYPE.META; // RJCTODO: Is this right? What is this stuff for?
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }    
}

