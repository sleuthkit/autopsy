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
package org.sleuthkit.autopsy.integrationtesting;

import java.util.List;

/**
 * Configuration in IntegrationTests per case.
 */
public class CaseConfig {

    private final String caseName;
    private final List<String> dataSourceResources;

    private final List<String> ingestModules;
    private final String ingestModuleSettingsPath;

    private final IntegrationCaseType caseTypes;
    private final TestingConfig testConfig;

    public CaseConfig(String caseName, List<String> dataSourceResources,
            List<String> ingestModules, String ingestModuleSettingsPath,
            IntegrationCaseType caseTypes, TestingConfig testConfig) {
        this.caseName = caseName;
        this.dataSourceResources = dataSourceResources;
        this.ingestModules = ingestModules;
        this.ingestModuleSettingsPath = ingestModuleSettingsPath;
        this.caseTypes = caseTypes;
        this.testConfig = testConfig;
    }

    /**
     * @return The name for the case (also used in formulating output name).
     */
    public String getCaseName() {
        return caseName;
    }

    /**
     * @return The paths (relative to working directory) of data sources.
     */
    public List<String> getDataSourceResources() {
        return dataSourceResources;
    }

    /**
     * @return The path to ingest module settings.
     */
    public String getIngestModuleSettingsPath() {
        return ingestModuleSettingsPath;
    }

    /**
     * @return The type(s) of cases to create for this (single user, multi user,
     *         or both).
     */
    public IntegrationCaseType getCaseTypes() {
        return caseTypes;
    }

    /**
     * @return The configuration for how testing should be done on this case and
     *         datasources.
     */
    public TestingConfig getTestConfig() {
        return testConfig;
    }

    /**
     * @return The ingestModules to use for this case.
     */
    public List<String> getIngestModules() {
        return ingestModules;
    }
}
