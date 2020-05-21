/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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

/**
 * Configuration parameters for general report modules
 */
public class GeneralReportSettings implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private List<Long> selectedDataSources;
    private String reportDirectoryPath;
    
    /**
     * Returns the selected data sources
     * @return List of data source ids
     */
    public List<Long> getSelectedDataSources() {
        return selectedDataSources;
    }
    
    /**
     * Sets the selected data sources
     * @param selectedDataSources List of data source ids
     */
    public void setSelectedDataSources(List<Long> selectedDataSources) {
        this.selectedDataSources = new ArrayList<>(selectedDataSources);
    }
    
    /**
     * Returns the directory that the report file should be saved in.
     * @return Path to report directory
     */
    public String getReportDirectoryPath() {
        return this.reportDirectoryPath;
    } 
    
     /**
     * Sets the directory that the report file should be saved in.
     * @param reportDirectoryPath Path to report directory
     */
    public void setReportDirectoryPath(String reportDirectoryPath) {
        this.reportDirectoryPath = reportDirectoryPath;
    }
}
