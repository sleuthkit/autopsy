/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.mainui.nodes.TagNameFactory;

/**
 * Instances of this class act as keys for use by instances of the
 * RootContentChildren class. RootContentChildren is a NetBeans child node
 * factory built on top of the NetBeans Children.Keys class.
 */
public class Tags {
    /**
     * Instances of this class are the root nodes of tree that is a sub-tree of
     * the Autopsy presentation of the SleuthKit data model. The sub-tree
     * consists of content and blackboard artifact tags, grouped first by tag
     * type, then by tag name.
     */
    public static class RootNode extends DisplayableItemNode {
        private final static String DISPLAY_NAME = NbBundle.getMessage(RootNode.class, "TagsNode.displayName.text");
        private final static String ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png"; //NON-NLS
        private final Long dataSourceObjId;

        public RootNode(Long dsId) {
            super(Children.create(new TagNameFactory(dsId > 0 ? dsId : null), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(DISPLAY_NAME);
            super.setDisplayName(DISPLAY_NAME);
            this.setIconBaseWithExtension(ICON_PATH);
            this.dataSourceObjId = dsId > 0 ? dsId : null;
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        protected Sheet createSheet() {
            Sheet propertySheet = super.createSheet();
            Sheet.Set properties = propertySheet.get(Sheet.PROPERTIES);
            if (properties == null) {
                properties = Sheet.createPropertiesSet();
                propertySheet.put(properties);
            }
            properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "TagsNode.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "TagsNode.createSheet.name.displayName"), "", getName()));
            return propertySheet;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
        
        public Node clone() {
            return new RootNode(dataSourceObjId);
        }
    }
}
