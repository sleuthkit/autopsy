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
package org.sleuthkit.autopsy.core.messenger;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Connection info for a Java Message Service (JMS) provider.
 */
public final class MessageServiceConnectionInfo {

    private static final String MESSAGE_SERVICE_URI = "tcp://%s:%d";
    private final String userName;
    private final String password;
    private final URI uri;

    /**
     * Constructs an object containing the connection info for a Java Message
     * Service (JMS) provider.
     *
     * @param userName The user name to use for a message service connection.
     * @param password the password to use for a message service connection.
     * @param host The host to use for a message service connection. May be a
     * host name or an IP address.
     * @param port The port number to use for a message service connection.
     * @throws URISyntaxException if the host and port are not a valid TCP URI.
     */
    public MessageServiceConnectionInfo(String userName, String password, String host, int port) throws URISyntaxException {
        this.userName = userName;
        this.password = password;
        this.uri = new URI(String.format(MESSAGE_SERVICE_URI, host, port));  
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
        return uri.getHost();
    }

    /**
     * Gets the port number to use for a message service connection.
     *
     * @return The port as a string.
     */
    public int getPort() {
        return uri.getPort();
    }
    
    /**
     * Gets the TCP URI to use for a message service connection.
     *
     * @return The URI.
     */
    URI getURI() {
        return uri;
    }

}
