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
 * Configuration for running Integration Tests including things like ingest
 * parameters, datasource locations, cases to create, tests to run, etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntegrationTestConfig {

    private final List<TestSuiteConfig> testSuites;
    private final EnvConfig envConfig;

    /**
     * Main constructor.
     * @param testSuites The test suites to be run.
     * @param envConfig The environment configuration.
     */
    @JsonCreator
    public IntegrationTestConfig(
            @JsonProperty("testSuites") List<TestSuiteConfig> testSuites,
            @JsonProperty("envConfig") EnvConfig envConfig) {

        this.testSuites = testSuites;
        this.envConfig = envConfig;
    }

    /**
     * @return A list of test suite configurations.
     */
    public List<TestSuiteConfig> getTestSuites() {
        return testSuites == null ? Collections.emptyList() : Collections.unmodifiableList(testSuites);
    }

    /**
     * @return The integration test environment configuration.
     */
    public EnvConfig getEnvConfig() {
        return envConfig;
    }
}
