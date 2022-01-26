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
package org.sleuthkit.autopsy.mainui.datamodel.events;

import java.util.Objects;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskData;

/**
 * An event for an artifact added or changed of a particular type possibly for a
 * particular data source.
 */
public class KeywordHitEvent extends AnalysisResultEvent {

    private final String searchString;
    private final String match;
    private final TskData.KeywordSearchQueryType searchType;
    private final String setName;

    /**
     * Main constructor.
     *
     * @param setName      The set name.
     * @param searchString The search string or regex.
     * @param searchType   THe search type.
     * @param match        The match string.
     * @param dataSourceId The data source id.
     */
    public KeywordHitEvent(String setName, String searchString, TskData.KeywordSearchQueryType searchType, String match, long dataSourceId) {
        super(BlackboardArtifact.Type.TSK_KEYWORD_HIT, dataSourceId);
        this.setName = setName;
        this.searchString = searchString;
        this.match = match;
        this.searchType = searchType;
    }

    public String getSetName() {
        return setName;
    }
    
    public String getSearchString() {
        return searchString;
    }

    public String getMatch() {
        return match;
    }

    public TskData.KeywordSearchQueryType getSearchType() {
        return searchType;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.searchString);
        hash = 67 * hash + Objects.hashCode(this.match);
        hash = 67 * hash + Objects.hashCode(this.searchType);
        hash = 67 * hash + super.hashCode();
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
        final KeywordHitEvent other = (KeywordHitEvent) obj;
        if (!Objects.equals(this.searchString, other.searchString)) {
            return false;
        }
        if (!Objects.equals(this.match, other.match)) {
            return false;
        }
        if (this.searchType != other.searchType) {
            return false;
        }
        return super.equals(obj);
    }

}
