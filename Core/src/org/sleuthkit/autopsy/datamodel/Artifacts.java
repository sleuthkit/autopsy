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
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;

/**
 * Classes for creating nodes for BlackboardArtifacts.
 */
public class Artifacts {
    /**
     * Base class for a parent node of artifacts.
     */
    static class BaseArtifactNode extends DisplayableItemNode {

        /**
         * Main constructor.
         *
         * @param children    The children of the node.
         * @param icon        The icon for the node.
         * @param name        The name identifier of the node.
         * @param displayName The display name for the node.
         */
        BaseArtifactNode(Children children, String icon, String name, String displayName) {
            super(children, Lookups.singleton(name));
            super.setName(name);
            super.setDisplayName(displayName);
            this.setIconBaseWithExtension(icon); //NON-NLS        
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
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ExtractedContentNode.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "ExtractedContentNode.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "ExtractedContentNode.createSheet.name.desc"),
                    super.getDisplayName()));
            return sheet;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }
}
