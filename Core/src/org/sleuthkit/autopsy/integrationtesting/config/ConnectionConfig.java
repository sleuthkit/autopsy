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
 * Configuration information for a connection.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConnectionConfig {
    private final String hostName;
    private final Integer port;
    private final String userName;
    private final String password;

    /**
     * Main constructor.
     * @param hostName The host name.
     * @param port The port to use.
     * @param userName The user name to use.
     * @param password The password to use.
     */
    @JsonCreator
    public ConnectionConfig(
            @JsonProperty("hostName") String hostName, 
            @JsonProperty("port") Integer port, 
            @JsonProperty("userName") String userName, 
            @JsonProperty("password") String password) {
        
        this.hostName = hostName;
        this.port = port;
        this.userName = userName;
        this.password = password;
    }

    /**
     * @return The host name.
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * @return The port.
     */
    public Integer getPort() {
        return port;
    }

    /**
     * @return The user name.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * @return The password to use.
     */
    public String getPassword() {
        return password;
    }
}
