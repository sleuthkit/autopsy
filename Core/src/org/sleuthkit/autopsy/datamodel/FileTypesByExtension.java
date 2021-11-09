/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
import org.sleuthkit.autopsy.mainui.nodes.ViewsTypeFactory;

/**
 * Filters database results by file extension.
 */
public final class FileTypesByExtension implements AutopsyVisitableItem {

    private final FileTypes typesRoot;

    public FileTypesByExtension(FileTypes typesRoot) {
        this.typesRoot = typesRoot;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    long filteringDataSourceObjId() {
        return this.typesRoot.filteringDataSourceObjId();
    }

    public static class FileTypesByExtNode extends DisplayableItemNode {

        private static final String FNAME = NbBundle.getMessage(FileTypesByExtNode.class, "FileTypesByExtNode.fname.text");

        private final long dataSourceId;

        FileTypesByExtNode(long dataSourceId) {
            super(Children.create(new ViewsTypeFactory.FileExtFactory(dataSourceId > 0 ? dataSourceId : null), true), Lookups.singleton(FNAME));
            this.dataSourceId = dataSourceId;
            super.setName(FNAME);
            super.setDisplayName(FNAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png"); //NON-NLS
        } //NON-NLS

        public Node clone() {
            return new FileTypesByExtNode(dataSourceId);
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
            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.name"), NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.displayName"), NbBundle.getMessage(this.getClass(), "FileTypesByExtNode.createSheet.name.desc"), getDisplayName()));
            return sheet;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }
}
