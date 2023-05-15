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

import java.util.Objects;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskData;

/**
 * Parameters for a keyword search term.
 */
public class KeywordSearchTermParams extends KeywordListSearchParam {

    private static final String TYPE_ID = "KEYWORD_SEARCH_TERMS";

    /**
     * @return The type id for this search parameter.
     */
    public static String getTypeId() {
        return TYPE_ID;
    }

    private final String searchTerm;
    private final Boolean hasChildren;
    private final TskData.KeywordSearchQueryType searchType;

    /**
     * Main constructor.
     *
     * @param setName       The set name.
     * @param searchTerm    The search term (determined from regex or keyword).
     * @param searchType    The keyword search type attribute.
     * @param configuration The configuration of the analysis results set if
     *                      hasChildren is false.
     * @param hasChildren   Whether or not this search term has children tree
     *                      nodes (i.e. url regex search that further divides
     *                      into different urls).
     * @param dataSourceId  The data source id or null.
     */
    public KeywordSearchTermParams(String setName, String searchTerm, TskData.KeywordSearchQueryType searchType, String configuration, boolean hasChildren, Long dataSourceId) {
        super(dataSourceId, configuration, setName);
        this.searchTerm = searchTerm;
        this.hasChildren = hasChildren;
        this.searchType = searchType;
    }

    /**
     * @return The search term (determined from regex or keyword).
     */
    public String getRegex() {
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
     * @return The keyword search type value.
     */
    public TskData.KeywordSearchQueryType getSearchType() {
        return searchType;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.searchTerm);
        hash = 47 * hash + Objects.hashCode(this.searchType);
        hash = 47 * hash + super.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final KeywordSearchTermParams other = (KeywordSearchTermParams) obj;
        if (!Objects.equals(this.searchTerm, other.searchTerm)) {
            return false;
        }
        if (this.searchType != other.searchType) {
            return false;
        }
        return super.equals(obj);
    }

}
