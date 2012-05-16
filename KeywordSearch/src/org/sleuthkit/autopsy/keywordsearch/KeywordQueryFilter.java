/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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

package org.sleuthkit.autopsy.keywordsearch;

/**
 *
 * Filter to select only specific id or chunks for that id
 */
public class KeywordQueryFilter {
    public static enum FilterType {FILE, CHUNK};
    private long idFilter;
    private FilterType filterType;
    
    public KeywordQueryFilter(FilterType filterType, long id) {
        this.filterType = filterType;
        this.idFilter = id;
    }
    
    public long getIdFilter() {
        return idFilter;
    }
    public FilterType getFilterType() {
        return filterType;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Server.Schema.ID.toString());
        sb.append(":");
        sb.append(Long.toString(idFilter));
        if (filterType == FilterType.CHUNK) { 
            sb.append("_*");
        }
        return sb.toString();
    }
    
    
    
    
}
