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

/**
 * An event for an artifact added or changed of a particular type possibly for a
 * particular data source.
 */
public class AnalysisResultEvent extends BlackboardArtifactEvent {
    private final String setName;
    private final String regex;
    private final String matchString;

    AnalysisResultEvent(long artifactTypeId, long dataSourceId, String setName, String regex, String matchString) {
        super(artifactTypeId, dataSourceId);
        this.setName = setName;
        this.regex = regex;
        this.matchString = matchString;
    }

    public String getSetName() {
        return setName;
    }

    public String getRegex() {
        return regex;
    }

    public String getMatchString() {
        return matchString;
    }
}
