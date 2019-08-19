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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Class for persisting the selection of the tag types and artifact types used
 * by the TableReportGenerator class to drive report generation by
 * TableReportModules.
 */
final class TableReportSettings implements Serializable {

    /**
     * An enumeration of table report types.
     */
    enum TableReportType {
        ALL_RESULTS, //NON-NLS
        ALL_TAGGED_RESULTS, //NON-NLS
        SPECIFIC_TAGGED_RESULTS;   //NON-NLS
    }

    private static final long serialVersionUID = 1L;
    private List<BlackboardArtifact.Type> artifactTypes = new ArrayList<>();
    private List<String> tagNames = new ArrayList<>();
    private boolean useCaseSpecificData = false;
    private TableReportType reportType = TableReportType.ALL_RESULTS;

    /**
     * Creates TableReportSettings object. This constructor is used when user
     * selects specific tags and artifacts in UI (which requires a case to be
     * open).
     *
     * @param artifactTypeSelections The enabled/disabled state of the artifact
     * types to be included in the report. Only enabled entries will be kept.
     * @param tagNameSelections The enabled/disabled state of the tag names to
     * be included in the report. Only enabled entries will be kept.
     */
    TableReportSettings(Map<BlackboardArtifact.Type, Boolean> artifactTypeSelections, Map<String, Boolean> tagNameSelections, boolean useCaseSpecificData, TableReportType reportType) {
        // Get the artifact types selected by the user
        for (Map.Entry<BlackboardArtifact.Type, Boolean> entry : artifactTypeSelections.entrySet()) {
            if (entry.getValue()) {
                artifactTypes.add(entry.getKey());
            }
        }

        // Get the tag names selected by the user

        for (Map.Entry<String, Boolean> entry : tagNameSelections.entrySet()) {
            if (entry.getValue() == true) {
                tagNames.add(entry.getKey());
            }
        }

        this.reportType = reportType;
        this.useCaseSpecificData = useCaseSpecificData;
    }

    // ELTODO remove this constructor?
    /*TableReportSettings(List<BlackboardArtifact.Type> artifactTypes, List<String> tagNames, boolean useCaseSpecificData) {
        this.artifactTypes = artifactTypes;
        this.tagNames = tagNames;
        this.useCaseSpecificData = useCaseSpecificData;
    }*/
    /**
     * Creates TableReportSettings object. This constructor is used when user
     * configures command line ingest. Most likely there will be no case open at
     * the time, so tags and artifacts will have to be read from database at the
     * report execution time.
     *
     * @param useCaseSpecificData
     * @param reportType
     */
    /*TableReportSettings(boolean useCaseSpecificData, TableReportType reportType) {
        this.useCaseSpecificData = useCaseSpecificData;
        this.reportType = reportType;
    }*/

    List<BlackboardArtifact.Type> getArtifactSelections() {
        return artifactTypes;
    }

    List<String> getTagSelections() {
        return tagNames;
    }

    boolean isUseCaseSpecificData() {
        return useCaseSpecificData;
    }

    /**
     * Get report type.
     * 
     * @return the reportType
     */
    TableReportType getReportType() {
        return reportType;
    }
}
