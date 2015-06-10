/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2014 Basis Technology Corp.
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
import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Node for extension/file type filter view
 */
public class FileTypesNode extends DisplayableItemNode {

    private static final String FNAME = NbBundle.getMessage(FileTypesNode.class, "FileTypesNode.fname.text");

    /**
     *
     * @param skCase
     * @param filter null to display root node of file type tree, pass in
     * something to provide a sub-node.
     */
    FileTypesNode(SleuthkitCase skCase, FileTypeExtensionFilters.RootFilter filter) {
        super(Children.create(new FileTypesChildren(skCase, filter), true), Lookups.singleton(filter == null ? FNAME : filter.getName()));
        // root node of tree
        if (filter == null) {
            super.setName(FNAME);
            super.setDisplayName(FNAME);
        } // sub-node in file tree (i.e. documents, exec, etc.)
        else {
            super.setName(filter.getName());
            super.setDisplayName(filter.getDisplayName());
        }
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png"); //NON-NLS
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
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "FileTypesNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "FileTypesNode.createSheet.name.displayName"),
                NbBundle.getMessage(this.getClass(), "FileTypesNode.createSheet.name.desc"),
                getName()));
        return s;
    }

    /**
     *
     */
    static class FileTypesChildren extends ChildFactory<FileTypeExtensionFilters.SearchFilterInterface> {

        private SleuthkitCase skCase;
        private FileTypeExtensionFilters.RootFilter filter;

        /**
         *
         * @param skCase
         * @param filter Is null for root node
         */
        public FileTypesChildren(SleuthkitCase skCase, FileTypeExtensionFilters.RootFilter filter) {
            super();
            this.skCase = skCase;
            this.filter = filter;
        }

        @Override
        protected boolean createKeys(List<FileTypeExtensionFilters.SearchFilterInterface> list) {
            // root node
            if (filter == null) {
                list.addAll(Arrays.asList(FileTypeExtensionFilters.RootFilter.values()));
            }
            // document and executable has another level of nodes
            else if (filter.equals(FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER)) {
                list.addAll(Arrays.asList(FileTypeExtensionFilters.DocumentFilter.values()));
            } else if (filter.equals(FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER)) {
                list.addAll(Arrays.asList(FileTypeExtensionFilters.ExecutableFilter.values()));
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(FileTypeExtensionFilters.SearchFilterInterface key) {
            // make new nodes for the sub-nodes
            if (key.getName().equals(FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER.getName())) {
                return new FileTypesNode(skCase, FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER);
            } else if (key.getName().equals(FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER.getName())) {
                return new FileTypesNode(skCase, FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER);
            } else {
                return new FileTypeNode(key, skCase);
            }
        }
    }
}
