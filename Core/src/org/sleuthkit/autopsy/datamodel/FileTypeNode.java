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

import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Node for a specific file type / extension
 */
public class FileTypeNode extends DisplayableItemNode {

    FileTypeExtensionFilters.SearchFilterInterface filter;
    SleuthkitCase skCase;

    FileTypeNode(FileTypeExtensionFilters.SearchFilterInterface filter, SleuthkitCase skCase) {
        super(Children.create(new FileTypeChildren(filter, skCase), true), Lookups.singleton(filter.getDisplayName()));
        
        this.filter = filter;
        this.skCase = skCase;
        
        super.setName(filter.getName());
        
        //get count of children without preloading all children nodes
        final long count = new FileTypeChildren(filter, skCase).calculateItems();
        //final long count = getChildren().getNodesCount(true);
        super.setDisplayName(filter.getDisplayName() + " (" + count + ")");

        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-filter-icon.png");
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

        ss.put(new NodeProperty(NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.filterType.name"),
                                NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.filterType.displayName"),
                                NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.filterType.desc"),
                                filter.getDisplayName()));
        String extensions = "";
        for (String ext : filter.getFilter()) {
            extensions += "'" + ext + "', ";
        }
        extensions = extensions.substring(0, extensions.lastIndexOf(','));
        ss.put(new NodeProperty(NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.fileExt.name"),
                                NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.fileExt.displayName"),
                                NbBundle.getMessage(this.getClass(), "FileTypeNode.createSheet.fileExt.desc"),
                                extensions));

        return s;
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }
}
