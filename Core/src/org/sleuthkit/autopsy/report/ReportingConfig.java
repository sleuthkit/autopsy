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

/**
 * A bundling of all the settings objects that define a report configuration.
 */
final class ReportingConfig implements Serializable {

    private static final long serialVersionUID = 1L;
    private String configName;
    private List<ModuleStatus> moduleStatuses = new ArrayList<>();
    private List<ReportModuleSettings> moduleSettings = new ArrayList<>();
    private TableReportSettings tableReportSettings;
    private FileReportSettings fileReportSettings;

    /**
     * Creates ReportingConfig object.
     *
     * @param configName Name of the reporting configuration
     */
    ReportingConfig(String configName) {
        this.configName = configName;
    }

    void setName(String configName) {
        this.configName = configName;
    }

    String getName() {
        return this.configName;
    }

    void setModulesStatuses(List<ModuleStatus> moduleStatuses) {
        this.moduleStatuses = moduleStatuses;
    }

    List<ModuleStatus> getModuleStatuses() {
        return this.moduleStatuses;
    }

    void setModuleSettings(List<ReportModuleSettings> moduleSettings) {
        this.moduleSettings = moduleSettings;
    }

    List<ReportModuleSettings> getModuleSettings() {
        return this.moduleSettings;
    }

    void setTableReportSettings(TableReportSettings settings) {
        this.tableReportSettings = settings;
    }

    TableReportSettings getTableReportSettings() {
        return this.tableReportSettings;
    }

    void setFileReportSettings(FileReportSettings settings) {
        this.fileReportSettings = settings;
    }

    FileReportSettings getFileReportSettings() {
        return this.fileReportSettings;
    }

}
