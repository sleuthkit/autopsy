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

/**
 *
 * @author gregd
 */
public class EnvConfig {

    private final String rootCaseOutputPath;
    private final String rootTestOutputPath;
    private final ConnectionConfig connectionInfo;

    private String workingDirectory;

    public EnvConfig(String rootCaseOutputPath,
            String rootTestOutputPath, ConnectionConfig connectionInfo) {
        this.rootCaseOutputPath = rootCaseOutputPath;
        this.rootTestOutputPath = rootTestOutputPath;
        this.connectionInfo = connectionInfo;
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

    public ConnectionConfig getConnectionInfo() {
        return connectionInfo;
    }
}
