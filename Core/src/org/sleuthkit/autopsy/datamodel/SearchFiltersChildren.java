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

import java.util.Arrays;
import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.SearchFilters.FileSearchFilter;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * @author dfickling
 */
class SearchFiltersChildren extends ChildFactory<SearchFilters.SearchFilterInterface> {
    
    SleuthkitCase skCase;
    boolean root;

    public SearchFiltersChildren(SleuthkitCase skCase, boolean root) {
        this.skCase = skCase;
        this.root = root;
    }

    @Override
    protected boolean createKeys(List<SearchFilters.SearchFilterInterface> list) {
        if(root)
            list.addAll(Arrays.asList(FileSearchFilter.values()));
        else
            list.addAll(Arrays.asList(SearchFilters.DocumentFilter.values()));
        return true;
    }
    
    @Override
    protected Node createNodeForKey(SearchFilters.SearchFilterInterface key){
        if(key.getName().equals(SearchFilters.FileSearchFilter.TSK_DOCUMENT_FILTER.getName())){
            return new SearchFiltersNode(skCase, false);
        }
        return new FileSearchFilterNode(key, skCase);
    }
    
}
