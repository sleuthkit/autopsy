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

import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * An event for an artifact added or changed of a particular type possibly for a
 * particular data source.
 */
public class KeywordHitEvent extends AnalysisResultSetEvent {

    private final String searchString;
    private final String match;
    private final int searchType;

    /**
     * Main constructor.
     *
     * @param searchString The search string or regex.
     * @param match        The match string.
     * @param searchType   THe search type.
     * @param setName      The set name.
     * @param artifactType The artifact type.
     * @param dataSourceId The data source id.
     */
    public KeywordHitEvent(String searchString, String match, int searchType, String setName, BlackboardArtifact.Type artifactType, long dataSourceId) {
        super(setName, artifactType, dataSourceId);
        this.searchString = searchString;
        this.match = match;
        this.searchType = searchType;
    }

    public String getSearchString() {
        return searchString;
    }

    public String getMatch() {
        return match;
    }

    public int getSearchType() {
        return searchType;
    }
}
