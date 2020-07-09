/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.infrastructure;

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
     * An enumeration of table report options.
     */
    enum TableReportOption {
        ALL_RESULTS, //NON-NLS
        ALL_TAGGED_RESULTS, //NON-NLS
        SPECIFIC_TAGGED_RESULTS;   //NON-NLS
    }

    private static final long serialVersionUID = 1L;
    private final List<BlackboardArtifact.Type> artifactTypes = new ArrayList<>();
    private final List<String> tagNames = new ArrayList<>();
    private final boolean useStoredTagsAndArtifactsLists;
    private final TableReportOption reportOption;
    private List<Long> selectedDataSources;

    /**
     * Creates TableReportSettings object. This constructor is used when user
     * selects specific tags and artifacts in UI (which requires a case to be
     * open).
     *
     * @param artifactTypeSelections The enabled/disabled state of the artifact
     * types to be included in the report. Only enabled entries will be kept.
     * @param tagNameSelections The enabled/disabled state of the tag names to
     * be included in the report. Only enabled entries will be kept.
     * @param useTagsAndArtifactsLists Flag whether to use the input lists of tags 
     * and artifacts (which may be empty) or read tag and artifact lists at report generation time
     * @param reportOption Selected table report option.
     */
    TableReportSettings(Map<BlackboardArtifact.Type, Boolean> artifactTypeSelections, Map<String, Boolean> tagNameSelections, boolean useTagsAndArtifactsLists, TableReportOption reportOption) {
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

        this.reportOption = reportOption;
        this.useStoredTagsAndArtifactsLists = useTagsAndArtifactsLists;
    }

    List<BlackboardArtifact.Type> getArtifactSelections() {
        return artifactTypes;
    }

    List<String> getTagSelections() {
        return tagNames;
    }

    boolean useStoredTagsAndArtifactsLists() {
        return useStoredTagsAndArtifactsLists;
    }

    /**
     * Get selected report option.
     * 
     * @return the reportOption
     */
    TableReportOption getSelectedReportOption() {
        return reportOption;
    }
    
    /**
     * Returns the selected data sources
     */
    List<Long> getSelectedDataSources() {
        return selectedDataSources;
    }
    
    /**
     * Sets the selected data sources
     */
    void setSelectedDataSources(List<Long> selectedDataSources) {
        this.selectedDataSources = new ArrayList<>(selectedDataSources);
    }
}
