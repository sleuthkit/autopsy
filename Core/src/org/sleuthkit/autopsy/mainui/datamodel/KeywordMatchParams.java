/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import org.sleuthkit.datamodel.TskData;

/**
 * Parameters for a keyword match found in files.
 */
public class KeywordMatchParams {

    private static final String TYPE_ID = "KEYWORD_MATCH";

    /**
     * @return The type id for this search parameter.
     */
    public static String getTypeId() {
        return TYPE_ID;
    }

    private final String setName;
    private final String searchTerm;
    private final String keywordMatch;
    private final Long dataSourceId;
    private final TskData.KeywordSearchQueryType searchType;

    /**
     * Main constructor.
     *
     * @param setName      The set name.
     * @param searchTerm   The search term (determined from regex or keyword).
     * @param keywordMatch The actual keyword match.
     * @param searchType   The keyword search type.
     * @param dataSourceId The data source id or null.
     */
    public KeywordMatchParams(String setName, String searchTerm, String keywordMatch, TskData.KeywordSearchQueryType searchType, Long dataSourceId) {
        this.setName = setName;
        this.searchTerm = searchTerm;
        this.keywordMatch = keywordMatch;
        this.searchType = searchType;
        this.dataSourceId = dataSourceId;
    }

    /**
     * @return The set name.
     */
    public String getSetName() {
        return setName;
    }

    /**
     * @return The search term (determined from regex or keyword).
     */
    public String getSearchTerm() {
        return searchTerm;
    }

    /**
     * @return The actual keyword match.
     */
    public String getKeywordMatch() {
        return keywordMatch;
    }

    /**
     * @return The data source id or null.
     */
    public Long getDataSourceId() {
        return dataSourceId;
    }

    /**
     * @return The type of keyword search performed.
     */
    public TskData.KeywordSearchQueryType getSearchType() {
        return searchType;
    }
}
