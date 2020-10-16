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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Configuration for running Integration Tests including things like ingest
 * parameters, datasource locations, cases to create, tests to run, etc.
 */
public class IntegrationTestConfig {

    private final String rootCaseOutputPath;
    private final String rootTestOutputPath;
    private final List<CaseConfig> cases;
    private String workingDirectory;

    public IntegrationTestConfig(String rootCaseOutputPath,
            String rootTestOutputPath, List<CaseConfig> cases) {
        this.rootCaseOutputPath = rootCaseOutputPath;
        this.rootTestOutputPath = rootTestOutputPath;
        this.cases = cases;
    }

    /**
     * @return The path for where cases should be saved.
     */
    public String getRootCaseOutputPath() {
        return rootCaseOutputPath;
    }

    /**
     * @return The path for where output yaml data should be saved.
     */
    public String getRootTestOutputPath() {
        return rootTestOutputPath;
    }

    /**
     * @return The per-case configuration.
     */
    public List<CaseConfig> getCases() {
        return cases;
    }

    /**
     * @return The working directory. In practice, this is the folder that the
     * configuration is located within. Any relative paths will be relative to
     * this.
     */
    public String getWorkingDirectory() {
        return workingDirectory;
    }

    /**
     * Sets the working directory.
     *
     * @param workingDirectory The working directory. In practice, this is the
     * folder that the configuration is located within. Any relative paths will
     * be relative to this.
     */
    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

}
