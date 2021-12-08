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
 * Parameters for a keyword search term.
 */
public class KeywordSearchTermParams {

    private static final String TYPE_ID = "KEYWORD_SEARCH_TERMS";

    /**
     * @return The type id for this search parameter.
     */
    public static String getTypeId() {
        return TYPE_ID;
    }

    private final String setName;
    private final String searchTerm;
    private final boolean hasChildren;
    private final Long dataSourceId;
    private final TskData.KeywordSearchQueryType searchType;

    /**
     * Main constructor.
     *
     * @param setName      The set name.
     * @param searchTerm   The search term (determined from regex or keyword).
     * @param searchType   The keyword search type attribute.
     * @param hasChildren  Whether or not this search term has children tree
     *                     nodes (i.e. url regex search that further divides
     *                     into different urls).
     * @param dataSourceId The data source id or null.
     */
    public KeywordSearchTermParams(String setName, String searchTerm, TskData.KeywordSearchQueryType searchType, boolean hasChildren, Long dataSourceId) {
        this.setName = setName;
        this.searchTerm = searchTerm;
        this.searchType = searchType;
        this.hasChildren = hasChildren;
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
     * @return Whether or not this search term has children tree nodes (i.e. url
     *         regex search that further divides into different urls).
     */
    public boolean hasChildren() {
        return hasChildren;
    }

    /**
     * @return The data source id or null.
     */
    public Long getDataSourceId() {
        return dataSourceId;
    }

    /**
     * @return The keyword search type value.
     */
    public TskData.KeywordSearchQueryType getSearchType() {
        return searchType;
    }
}
