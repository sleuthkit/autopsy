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

/**
 * Defines integration testing environment settings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvConfig {

    private final String rootCaseOutputPath;
    private final String rootTestOutputPath;
    private final String rootTestSuitesPath;
    private final String rootGoldPath;
    private final String diffOutputPath;

    private final ConnectionConfig dbConnection;
    private final ConnectionConfig mqConnection;
    private final ConnectionConfig solrConnection;
    private final ConnectionConfig zkConnection;
    private final ConnectionConfig crConnection;

    private String workingDirectory;
    private Boolean useRelativeOutput;


    /**
     * Main constructor.
     *
     * @param rootCaseOutputPath The location where cases will be created.
     * @param rootTestSuitesPath The location of test suite configuration
     * file(s).
     * @param rootTestOutputPath The location where output results should be
     * created.
     * @param dbConnection The connection information for the database
     * (postgres).
     * @param mqConnection The active mq connection information.
     * @param solrConnection The solr connection information.
     * @param zkConnection The zookeeper connection information.
     * @param crConnection The central repo connection information.
     * @param workingDirectory The working directory (if not specified, the
     * parent directory of the EnvConfig file is used.
     * @param useRelativeOutput If true, results will be outputted maintaining
     * the same relative path structure as the file (i.e. if file was found at
     * /rootTestSuitesPath/folderX/fileY.json then it will now be outputted in
     * /rootTestOutputPath/folderX/fileY/)
     * @param rootGoldPath The path to the gold data for diff comparison.
     * @param diffOutputPath The file location for diff output.
     */
    @JsonCreator
    public EnvConfig(
            @JsonProperty("rootCaseOutputPath") String rootCaseOutputPath,
            @JsonProperty("rootTestSuitesPath") String rootTestSuitesPath,
            @JsonProperty("rootTestOutputPath") String rootTestOutputPath,
            @JsonProperty("dbConnection") ConnectionConfig dbConnection,
            @JsonProperty("mqConnection") ConnectionConfig mqConnection,
            @JsonProperty("solrConnection") ConnectionConfig solrConnection,
            @JsonProperty("zkConnection") ConnectionConfig zkConnection,
            @JsonProperty("crConnection") ConnectionConfig crConnection,
            @JsonProperty("workingDirectory") String workingDirectory,
            @JsonProperty("useRelativeOutput") Boolean useRelativeOutput,
            @JsonProperty("rootGoldPath") String rootGoldPath,
            @JsonProperty("diffOutputPath") String diffOutputPath) {

        this.rootCaseOutputPath = rootCaseOutputPath;
        this.rootTestOutputPath = rootTestOutputPath;
        this.rootTestSuitesPath = rootTestSuitesPath;
        this.rootGoldPath = rootGoldPath;
        this.diffOutputPath = diffOutputPath;
        this.workingDirectory = workingDirectory;
        this.useRelativeOutput = useRelativeOutput;

        this.dbConnection = dbConnection;
        this.mqConnection = mqConnection;
        this.solrConnection = solrConnection;
        this.zkConnection = zkConnection;
        this.crConnection = crConnection;
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

    /**
     * @return The root test suites path that will be searched or the path to a
     * single file.
     */
    public String getRootTestSuitesPath() {
        return rootTestSuitesPath;
    }

    /**
     * @return If true, results will be outputted maintaining the same relative
     * path structure as the file (i.e. if file was found at
     * /rootTestSuitesPath/folderX/fileY.json then it will now be outputted in
     * /rootTestOutputPath/folderX/fileY/)
     */
    public boolean getUseRelativeOutput() {
        return Boolean.TRUE.equals(useRelativeOutput);
    }

    /**
     * Sets whether or not to use the relative output path.
     *
     * @param useRelativeOutput If true, results will be outputted maintaining
     * the same relative path structure as the file (i.e. if file was found at
     * /rootTestSuitesPath/folderX/fileY.json then it will now be outputted in
     * /rootTestOutputPath/folderX/fileY/)
     */
    public void setUseRelativeOutput(boolean useRelativeOutput) {
        this.useRelativeOutput = useRelativeOutput;
    }

    /**
     * @return The connection information for the database (postgres).
     */
    public ConnectionConfig getDbConnection() {
        return dbConnection;
    }

    /**
     * @return The active mq connection information.
     */
    public ConnectionConfig getMqConnection() {
        return mqConnection;
    }

    /**
     * @return The solr connection information.
     */
    public ConnectionConfig getSolrConnection() {
        return solrConnection;
    }

    /**
     * @return The zookeeper connection.
     */
    public ConnectionConfig getZkConnection() {
        return zkConnection;
    }

    /**
     * @return The central repo connection.
     */
    public ConnectionConfig getCrConnection() {
        return crConnection;
    }

    /**
     * @return The path to the gold data for diff comparison.
     */
    public String getRootGoldPath() {
        return rootGoldPath;
    }

    /**
     * @return The file location for diff output.
     */
    public String getDiffOutputPath() {
        return diffOutputPath;
    }

}
