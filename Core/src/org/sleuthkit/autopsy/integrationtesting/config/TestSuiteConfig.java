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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * Configuration per test suite.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestSuiteConfig {

    private final String name;
    private final String description;
    private final List<String> dataSources;
    private final List<ParameterizedResourceConfig> configurationModules;
    private final TestingConfig integrationTests;
    private final IntegrationCaseType caseTypes;
    private String relativeOutputPath;

    /**
     * Main constructor.
     *
     * @param name Name of the test suite.
     * @param description The description for the test suite.
     * @param dataSources The data sources to use.
     * @param configurationModules The modules in order to configure the autopsy
     * environment.
     * @param integrationTests The integration tests to run.
     * @param caseTypes The case types (single user, multi user, both).
     */
    @JsonCreator
    public TestSuiteConfig(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("dataSources") List<String> dataSources,
            @JsonProperty("configurationModules") List<ParameterizedResourceConfig> configurationModules,
            @JsonProperty("integrationTests") TestingConfig integrationTests,
            @JsonProperty("caseTypes") IntegrationCaseType caseTypes) {

        this.name = name;
        this.description = description;
        this.dataSources = dataSources;
        this.configurationModules = configurationModules;
        this.integrationTests = integrationTests;
        this.caseTypes = caseTypes;
    }

    /**
     * @return The name of the test suite.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The description of the test suite.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return The data sources to be ingested.
     */
    public List<String> getDataSources() {
        return dataSources == null ? Collections.emptyList() : Collections.unmodifiableList(dataSources);
    }

    /**
     * @return The configuration modules to be run to set up the autopsy
     * environment.
     */
    public List<ParameterizedResourceConfig> getConfigurationModules() {
        return configurationModules == null ? Collections.emptyList() : Collections.unmodifiableList(configurationModules);
    }

    /**
     * @return The configuration for integration tests to run for output.
     */
    public TestingConfig getIntegrationTests() {
        return integrationTests;
    }

    /**
     * @return The case type (single user, multi user, both).
     */
    public IntegrationCaseType getCaseTypes() {
        return caseTypes;
    }

    /**
     * @return The relative output path used if EnvConfig.useRelativeOutputPath
     * is true. If file is found at /testSuitePath/relPathX/fileY.json, and
     * EnvConfig.useRelativeOutputPath is true, then output results will be
     * located in /outputPath/relPathX/fileY/.
     */
    public String getRelativeOutputPath() {
        return relativeOutputPath;
    }

    /**
     * Sets the relative output path.
     *
     * @param relativeOutputPath The relative output path used if
     * EnvConfig.useRelativeOutputPath is true. If file is found at
     * /testSuitePath/relPathX/fileY.json, and EnvConfig.useRelativeOutputPath
     * is true, then output results will be located in
     * /outputPath/relPathX/fileY/.
     */
    public void setRelativeOutputPath(String relativeOutputPath) {
        this.relativeOutputPath = relativeOutputPath;
    }

}
