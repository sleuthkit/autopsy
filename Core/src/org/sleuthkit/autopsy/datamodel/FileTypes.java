/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.util.Arrays;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.accounts.FileTypeExtensionFilters;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * File Types node support
 */
public class FileTypes implements AutopsyVisitableItem {

    private SleuthkitCase skCase;

    FileTypes(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    SleuthkitCase getSleuthkitCase() {
        return skCase;
    }

    /**
     * Node which will contain By Mime Type and By Extension nodes.
     */
    public static class FileTypesNode extends DisplayableItemNode {

        @NbBundle.Messages("FileTypesNew.name.text=File Types")
        private static final String NAME = Bundle.FileTypesNew_name_text();

        public FileTypesNode(SleuthkitCase sleuthkitCase) {
            super(new RootContentChildren(Arrays.asList(
                    new FileTypeExtensionFilters(sleuthkitCase),
                    new FileTypesByMimeType(sleuthkitCase)
            )), Lookups.singleton(NAME));
            setName(NAME);
            setDisplayName(NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png");
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        @NbBundle.Messages({
            "FileTypesNew.createSheet.name.name=Name",
            "FileTypesNew.createSheet.name.displayName=Name",
            "FileTypesNew.createSheet.name.desc=no description"})
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(Bundle.FileTypesNew_createSheet_name_name(),
                    Bundle.FileTypesNew_createSheet_name_displayName(),
                    Bundle.FileTypesNew_createSheet_name_desc(),
                    NAME
            ));
            return s;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

    }
}
