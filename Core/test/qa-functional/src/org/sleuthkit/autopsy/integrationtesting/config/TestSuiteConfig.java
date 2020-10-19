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
public class TestSuiteConfig {
    private final String name;
    private final String description;
    private final List<String> dataSources;
    private final List<ParameterizedResourceConfig> configurationModules;
    private final TestingConfig integrationTests;
    private final IntegrationCaseType caseTypes;
    private String relativeOutputPath;

    public TestSuiteConfig(String name, String description, List<String> dataSources, List<ParameterizedResourceConfig> configurationModules, TestingConfig integrationTests, IntegrationCaseType caseTypes) {
        this.name = name;
        this.description = description;
        this.dataSources = dataSources;
        this.configurationModules = configurationModules;
        this.integrationTests = integrationTests;
        this.caseTypes = caseTypes;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getDataSources() {
        return dataSources;
    }

    public List<ParameterizedResourceConfig> getConfigurationModules() {
        return configurationModules;
    }

    public TestingConfig getIntegrationTests() {
        return integrationTests;
    }

    public IntegrationCaseType getCaseTypes() {
        return caseTypes;
    }

    public String getRelativeOutputPath() {
        return relativeOutputPath;
    }

    public void setRelativeOutputPath(String relativeOutputPath) {
        this.relativeOutputPath = relativeOutputPath;
    }
    
    
}
