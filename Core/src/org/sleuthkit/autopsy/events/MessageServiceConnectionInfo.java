/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.events;

import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.concurrent.Immutable;

/**
 * Connection info for a Java Message Service (JMS) provider. Thread-safe.
 */
@Immutable
public final class MessageServiceConnectionInfo {

    private static final String MESSAGE_SERVICE_URI = "tcp://%s:%s?wireFormat.maxInactivityDuration=0";
    private final String userName;
    private final String password;
    private final String host;
    private final String port;

    /**
     * Constructs an object containing connection info for a Java Message
     * Service (JMS) provider.
     *
     * @param userName The user name to use for a message service connection.
     * @param password The password to use for a message service connection.
     * @param host The host to use for a message service connection. May be a
     * host name or an IP address.
     * @param port The port number to use for a message service connection.
     */
    public MessageServiceConnectionInfo(String userName, String password, String host, String port) {
        this.userName = userName;
        this.password = password;
        this.host = host;
        this.port = port;
    }

    /**
     * Gets the user name to use for a message service connection.
     *
     * @return The user name as a string.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Gets the password to use for a message service connection.
     *
     * @return The password as a string.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Gets the host to use for a message service connection. May be a host name
     * or an IP address.
     *
     * @return The host as a string.
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the port number to use for a message service connection.
     *
     * @return The port as a string.
     */
    public String getPort() {
        return port;
    }

    /**
     * Gets the TCP URI to use for a message service connection.
     *
     * @return The URI.
     * @throws URISyntaxException if the connection info is not for a valid TCP
     * URI.
     */
    URI getURI() throws URISyntaxException {
        return new URI(String.format(MESSAGE_SERVICE_URI, host, port));
    }

}
