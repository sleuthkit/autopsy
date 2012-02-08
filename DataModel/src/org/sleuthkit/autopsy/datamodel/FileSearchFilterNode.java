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

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.SearchFilters.FileSearchFilter;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * @author dfickling
 */
public class FileSearchFilterNode extends AbstractNode implements DisplayableItemNode{
    
    FileSearchFilter filter;
    SleuthkitCase skCase;

    FileSearchFilterNode(FileSearchFilter filter, SleuthkitCase skCase) {
        super(Children.create(new FileSearchFilterChildren(filter, skCase), true), Lookups.singleton(filter));
        super.setName(filter.getName());
        super.setDisplayName(filter.getDisplayName());
        this.filter = filter;
        this.skCase = skCase;
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/filter-icon.png");
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
        
        ss.put(new NodeProperty("Filter Type",
                                "Filter Type",
                                "no description",
                                filter.getDisplayName()));
        String extensions = "";
        for(String ext : filter.getFilter()){
            extensions += "'" + ext + "', ";
        }
        extensions = extensions.substring(0, extensions.lastIndexOf(','));
        ss.put(new NodeProperty("File Extensions",
                                "File Extensions",
                                "no description",
                                extensions));

        return s;
    }
    
}
