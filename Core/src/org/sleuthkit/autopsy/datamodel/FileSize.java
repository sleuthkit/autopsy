/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.nodes.ViewsTypeFactory.FileSizeTypeFactory;

/**
 * Files by Size View node and related child nodes
 */
public class FileSize implements AutopsyVisitableItem {

    private static final Logger logger = Logger.getLogger(FileTypes.class.getName());

    private final long filteringDSObjId; // 0 if not filtering/grouping by data source

    public FileSize(long dsObjId) {
        this.filteringDSObjId = dsObjId;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    long filteringDataSourceObjId() {
        return this.filteringDSObjId;
    }

    /*
     * Root node. Children are nodes for specific sizes.
     */
    public static class FileSizeRootNode extends DisplayableItemNode {

        private static final String NAME = NbBundle.getMessage(FileSize.class, "FileSize.fileSizeRootNode.name");
        
        private final long dataSourceObjId;

        FileSizeRootNode(long datasourceObjId) {
            super(Children.create(new FileSizeTypeFactory(datasourceObjId > 0 ? datasourceObjId : null), true), Lookups.singleton(NAME));
            super.setName(NAME);
            super.setDisplayName(NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-size-16.png"); //NON-NLS
            this.dataSourceObjId = datasourceObjId;
        }

        public Node clone() {
            return new FileSizeRootNode(this.dataSourceObjId);
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

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileSize.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "FileSize.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "FileSize.createSheet.name.desc"),
                    NAME));
            return sheet;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }
}
