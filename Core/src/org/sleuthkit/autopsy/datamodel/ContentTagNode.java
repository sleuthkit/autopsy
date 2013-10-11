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
import org.sleuthkit.datamodel.ContentTag;

/**
 * Instances of this class wrap ContentTag objects. In the Autopsy
 * presentation of the SleuthKit data model, they are leaf nodes of a tree 
 * consisting of content and blackboard artifact tags, grouped first by tag 
 * type, then by tag name.
 */
public class ContentTagNode extends DisplayableItemNode {
    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png";

    public ContentTagNode(ContentTag tag) {
        super(Children.LEAF, Lookups.singleton(tag));
        super.setName(tag.getContent().getName());
        super.setDisplayName(tag.getContent().getName());
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
    public boolean isLeafTypeNode() {
        return true;
    }    
}
