/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Class for persisting the selection of the tag types and artifact types used
 * by the TableReportGenerator class to drive report generation by
 * TableReportModules.
 */
class TableReportSettings implements Serializable  {

    private static final long serialVersionUID = 1L;
    private Map<BlackboardArtifact.Type, Boolean> artifactTypeSelections = new HashMap<>();
    private Map<String, Boolean> tagNameSelections = new HashMap<>();

    /**
     * Creates TableReportSettings object.
     *
     * @param artifactTypeSelections the enabled/disabled state of the artifact
     * types to be included in the report
     * @param tagNameSelections the enabled/disabled state of the tag names to
     * be included in the report
     */
    TableReportSettings(Map<BlackboardArtifact.Type, Boolean> artifactTypeSelections, Map<String, Boolean> tagNameSelections) {
        this.artifactTypeSelections = artifactTypeSelections;
        this.tagNameSelections = tagNameSelections;
    }

    Map<BlackboardArtifact.Type, Boolean> getArtifactSelections() {
        return artifactTypeSelections;
    }

    Map<String, Boolean> getTagSelections() {
        return tagNameSelections;
    }
}
