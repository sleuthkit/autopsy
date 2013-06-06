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
 */
class SearchFiltersChildren extends ChildFactory<SearchFilters.SearchFilterInterface> {
    
    private SleuthkitCase skCase;
    private SearchFilters.FileSearchFilter filter;

    public SearchFiltersChildren(SleuthkitCase skCase, SearchFilters.FileSearchFilter filter) {
        this.skCase = skCase;
        this.filter = filter;
    }

    @Override
    protected boolean createKeys(List<SearchFilters.SearchFilterInterface> list) {
        if (filter == null) {
            list.addAll(Arrays.asList(FileSearchFilter.values()));
        }
        else if (filter.equals(FileSearchFilter.TSK_DOCUMENT_FILTER) ){
            list.addAll(Arrays.asList(SearchFilters.DocumentFilter.values()));
        }
        else if (filter.equals(FileSearchFilter.TSK_EXECUTABLE_FILTER) ){
            list.addAll(Arrays.asList(SearchFilters.ExecutableFilter.values()));
        }
        return true;
    }
    
    @Override
    protected Node createNodeForKey(SearchFilters.SearchFilterInterface key){
        if(key.getName().equals(SearchFilters.FileSearchFilter.TSK_DOCUMENT_FILTER.getName())){
            return new SearchFiltersNode(skCase, SearchFilters.FileSearchFilter.TSK_DOCUMENT_FILTER);
        }
        else if(key.getName().equals(SearchFilters.FileSearchFilter.TSK_EXECUTABLE_FILTER.getName())){
            return new SearchFiltersNode(skCase, SearchFilters.FileSearchFilter.TSK_EXECUTABLE_FILTER);
        }
        else {
            return new FileSearchFilterNode(key, skCase);
        }
    }
    
}
