/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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

import java.util.Map;

/**
 * Stores data about a search before it is done.
 */
class QueryRequest {

    private KeywordSearchQuery query;
    private String queryString;
    private Map<String, Object> map;

    KeywordSearchQuery getQuery() {
        return query;
    }

    /**
     * NOTE: The below descriptions are based on how it is used in teh code.
     *
     * @param map   Map that stores settings to use during the search
     * @param id    ID that callers simply increment from 0
     * @param query Query that will be performed.
     */
    public QueryRequest(Map<String, Object> map, int id, KeywordSearchQuery query) {
        this.queryString = query.getEscapedQueryString();
        this.map = map;
        this.query = query;
    }

    public String getQueryString() {
        return queryString;
    }

    public Map<String, Object> getProperties() {
        return map;
    }

}
