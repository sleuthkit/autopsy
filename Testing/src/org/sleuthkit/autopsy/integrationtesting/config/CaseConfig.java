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
package org.sleuthkit.autopsy.integrationtesting.config;

import java.util.List;

/**
 * Configuration in IntegrationTests per case.
 */
public class CaseConfig {
    private final String caseName;
    private final List<String> dataSourceResources;
    private final String ingestProfilePath;
    private final String ingestModuleSettingsPath;
    private final IntegrationCaseType caseTypes;
    private final TestingConfig testConfig;

    public CaseConfig(String caseName, List<String> dataSourceResources, String ingestProfilePath, String ingestModuleSettingsPath, IntegrationCaseType caseTypes, TestingConfig testConfig) {
        this.caseName = caseName;
        this.dataSourceResources = dataSourceResources;
        this.ingestProfilePath = ingestProfilePath;
        this.ingestModuleSettingsPath = ingestModuleSettingsPath;
        this.caseTypes = caseTypes;
        this.testConfig = testConfig;
    }

    public String getCaseName() {
        return caseName;
    }

    public List<String> getDataSourceResources() {
        return dataSourceResources;
    }

    public String getIngestProfilePath() {
        return ingestProfilePath;
    }

    public String getIngestModuleSettingsPath() {
        return ingestModuleSettingsPath;
    }

    public IntegrationCaseType getCaseTypes() {
        return caseTypes;
    }

    public TestingConfig getTestConfig() {
        return testConfig;
    }
}
