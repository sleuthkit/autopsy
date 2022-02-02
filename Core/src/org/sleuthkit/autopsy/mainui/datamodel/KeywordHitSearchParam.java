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
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.datamodel.TskData;

/**
 * Key for keyword hits in order to retrieve data from DAO.
 */
public class KeywordHitSearchParam extends KeywordSearchTermParams {

    private static final String TYPE_ID = "KEYWORD_HIT";

    /**
     * @return The type id for this search parameter.
     */
    public static String getTypeId() {
        return TYPE_ID;
    }

    private final String keyword;
    private final String regex;
    private final TskData.KeywordSearchQueryType searchType;
    
    public KeywordHitSearchParam(Long dataSourceId, String setName, String keyword, String regex, TskData.KeywordSearchQueryType searchType, String configuration) {
        super(setName, regex, searchType, configuration, StringUtils.isNotBlank(keyword) && !Objects.equals(regex, keyword), dataSourceId);
        this.keyword = keyword;
        this.regex = regex;
        this.searchType = searchType;
    }

    public String getRegex() {
        return regex;
    }

    public String getKeyword() {
        return keyword;
    }
    
    public TskData.KeywordSearchQueryType getSearchType() {
        return searchType;
    } 

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + Objects.hashCode(this.keyword);
        hash = 29 * hash + Objects.hashCode(this.regex);
        hash = 29 * hash + Objects.hashCode(this.searchType);
        hash = 29 * hash + super.hashCode();
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
        final KeywordHitSearchParam other = (KeywordHitSearchParam) obj;
        if (!Objects.equals(this.keyword, other.keyword)) {
            return false;
        }
        if (!Objects.equals(this.regex, other.regex)) {
            return false;
        }
        if (this.searchType != other.searchType) {
            return false;
        }
        return super.equals(obj);
    }

}
