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
 * Filter to restrict query only specific files, chunks, images
 * Single filter supports multiple ids per file/chunk/image, that act as OR filter
 */
public class KeywordQueryFilter {

    public static enum FilterType {

        FILE, CHUNK, IMAGE
    };
    private long[] idFilters;
    private FilterType filterType;

    public KeywordQueryFilter(FilterType filterType, long id) {
        this.filterType = filterType;
        this.idFilters = new long[1];
        this.idFilters[0] = id;
    }

    public KeywordQueryFilter(FilterType filterType, long[] ids) {
        this.filterType = filterType;
        this.idFilters = ids;
    }

    public long[] getIdFilters() {
        return idFilters;
    }

    public FilterType getFilterType() {
        return filterType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String id = null;
        for (int i = 0; i < idFilters.length; ++i) {
            if (i > 0) {
                sb.append(" "); //OR
            }
            long idVal = idFilters[i];
            if (filterType == FilterType.IMAGE) {
                id = Server.Schema.IMAGE_ID.toString();
            } else {
                id = Server.Schema.ID.toString();
            }
            sb.append(id);
            sb.append(":");
            sb.append(Long.toString(idVal));
            if (filterType == FilterType.CHUNK) {
                sb.append("_*");
            }
        }

        return sb.toString();
    }
}
