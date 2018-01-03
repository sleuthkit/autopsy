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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 *
 * Filter to restrict query only specific files, chunks, images Single filter
 * supports multiple ids per file/chunk/image, that act as OR filter
 */
class KeywordQueryFilter {

    public static enum FilterType {

        FILE, CHUNK, DATA_SOURCE
    };
    private Set<Long> idFilters;
    private FilterType filterType;

    public KeywordQueryFilter(FilterType filterType, long id) {
        this.filterType = filterType;
        this.idFilters = new HashSet<Long>();
        this.idFilters.add(id);
    }

    public KeywordQueryFilter(FilterType filterType, Set<Long> ids) {
        this.filterType = filterType;
        this.idFilters = ids;
    }

    public Set<Long> getIdFilters() {
        return idFilters;
    }

    public FilterType getFilterType() {
        return filterType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String id = null;

        Iterator<Long> it = idFilters.iterator();
        for (int i = 0; it.hasNext(); ++i) {
            if (i > 0) {
                sb.append(" "); //OR
            }
            long idVal = it.next();
            if (filterType == FilterType.DATA_SOURCE) {
                id = Server.Schema.IMAGE_ID.toString();
            } else {
                id = Server.Schema.ID.toString();
            }
            sb.append(id);
            sb.append(":");
            sb.append(KeywordSearchUtil.escapeLuceneQuery(Long.toString(idVal)));
            if (filterType == FilterType.CHUNK) {
                sb.append("_*");
            }
        }

        return sb.toString();
    }
}
