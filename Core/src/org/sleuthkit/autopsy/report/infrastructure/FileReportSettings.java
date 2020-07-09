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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for persisting the file properties used by FileReportGenerator to drive
 * report generation by FileReportModules.
 */
final class FileReportSettings implements Serializable {

    private static final long serialVersionUID = 1L;
    private Map<FileReportDataTypes, Boolean> filePropertiesInfo = new HashMap<>();
    private List<Long> selectedDataSources;

    /**
     * Creates FileReportSettings object.
     *
     * @param fileReportInfo The information that should be included about each
     * file in the report.
     */
    FileReportSettings(Map<FileReportDataTypes, Boolean> fileReportInfo) {
        this.filePropertiesInfo = fileReportInfo;
    }

    /**
     * Gets file report settings.
     *
     * @return File report settings
     */
    Map<FileReportDataTypes, Boolean> getFileProperties() {
        return filePropertiesInfo;
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
