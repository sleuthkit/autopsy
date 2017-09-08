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
import javax.jms.Connection;
import javax.jms.JMSException;
import org.apache.activemq.ActiveMQConnectionFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.util.MissingResourceException;
import org.openide.util.NbBundle;

/**
 * Connection info for a Java Message Service (JMS) provider. Thread-safe.
 */
@Immutable
public final class MessageServiceConnectionInfo {

    private static final String MESSAGE_SERVICE_URI = "tcp://%s:%s?wireFormat.maxInactivityDuration=0"; //NON-NLS
    private static final String CONNECTION_TIMED_OUT = "connection timed out"; //NON-NLS
    private static final String CONNECTION_REFUSED = "connection refused"; //NON-NLS
    private static final String PASSWORD_OR_USERNAME_BAD = "user name ["; //NON-NLS
    private static final int IS_REACHABLE_TIMEOUT_MS = 1000;
    private final String userName;
    private final String password;
    private final String host;
    private final int port;

    /**
     * Constructs an object containing connection info for a Java Message
     * Service (JMS) provider.
     *
     * @param host     The host to use for a message service connection. May be
     *                 a host name or an IP address.
     * @param port     The port number to use for a message service connection.
     * @param userName The user name to use for a message service connection.
     *                 May be the empty string.
     * @param password The password to use for a message service connection. May
     *                 be the empty string.
     *
     */
    public MessageServiceConnectionInfo(String host, int port, String userName, String password) {
        this.host = host;
        this.port = port;
        this.userName = userName;
        this.password = password;
    }

    /**
     * Gets the user name to use for a message service connection.
     *
     * @return The user name as a string. May be empty.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Gets the password to use for a message service connection.
     *
     * @return The password as a string. May be empty.
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
     * @return The port as an int.
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the TCP URI to use for a message service connection.
     *
     * @return The URI.
     *
     * @throws URISyntaxException if the connection info is not for a valid TCP
     *                            URI.
     */
    URI getURI() throws URISyntaxException {
        return new URI(String.format(MESSAGE_SERVICE_URI, getHost(), Integer.toString(getPort())));
    }

    /**
     * Verifies connection to messaging service. Throws if we cannot communicate
     * with messaging service.
     *
     * When issues occur, it attempts to diagnose them by looking at the
     * exception messages, returning the appropriate user-facing text for the
     * exception received. This method expects the Exceptions messages to be in
     * English and compares against English text.
     *
     * @throws org.sleuthkit.autopsy.events.MessageServiceException
     */
    public void tryConnect() throws MessageServiceException {
        if (host == null || host.isEmpty()) {
            throw new MessageServiceException(NbBundle.getMessage(MessageServiceConnectionInfo.class, "MessageServiceConnectionInfo.MissingHostname")); //NON-NLS
        } else if (userName == null) {
            throw new MessageServiceException(NbBundle.getMessage(MessageServiceConnectionInfo.class, "MessageServiceConnectionInfo.MissingUsername")); //NON-NLS
        } else if (password == null) {
            throw new MessageServiceException(NbBundle.getMessage(MessageServiceConnectionInfo.class, "MessageServiceConnectionInfo.MissingPassword")); //NON-NLS
        }
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(getUserName(), getPassword(), getURI());
            Connection connection = connectionFactory.createConnection(getUserName(), getPassword());
            connection.start();
            connection.close();
        } catch (URISyntaxException ex) {
            // The hostname or port seems bad
            throw new MessageServiceException(NbBundle.getMessage(MessageServiceConnectionInfo.class, "MessageServiceConnectionInfo.ConnectionCheck.HostnameOrPort")); //NON-NLS
        } catch (JMSException ex) {
            String result;
            Throwable cause = ex.getCause();
            if (null != cause && null != cause.getMessage()) {
                // there is more information from another exception
                String msg = cause.getMessage();
                if (msg.startsWith(CONNECTION_TIMED_OUT)) {
                    // The hostname or IP address seems bad
                    result = NbBundle.getMessage(MessageServiceConnectionInfo.class, "MessageServiceConnectionInfo.ConnectionCheck.Hostname"); //NON-NLS
                } else if (msg.toLowerCase().startsWith(CONNECTION_REFUSED)) {
                    // The port seems bad
                    result = NbBundle.getMessage(MessageServiceConnectionInfo.class, "MessageServiceConnectionInfo.ConnectionCheck.Port"); //NON-NLS
                } else if (msg.toLowerCase().startsWith(PASSWORD_OR_USERNAME_BAD)) {
                    // The username or password seems bad
                    result = NbBundle.getMessage(MessageServiceConnectionInfo.class, "MessageServiceConnectionInfo.ConnectionCheck.UsernameAndPassword"); //NON-NLS
                } else {
                    // Could be either hostname or port number
                    result = NbBundle.getMessage(MessageServiceConnectionInfo.class, "MessageServiceConnectionInfo.ConnectionCheck.HostnameOrPort"); //NON-NLS
                }
            } else {
                // there is no more information from another exception
                try {
                    if (InetAddress.getByName(getHost()).isReachable(IS_REACHABLE_TIMEOUT_MS)) {
                        // if we can reach the host, then it's probably a port problem
                        result = NbBundle.getMessage(MessageServiceConnectionInfo.class, "MessageServiceConnectionInfo.ConnectionCheck.Port"); //NON-NLS
                    } else {
                        result = NbBundle.getMessage(MessageServiceConnectionInfo.class, "MessageServiceConnectionInfo.ConnectionCheck.Hostname"); //NON-NLS
                    }
                } catch (IOException | MissingResourceException any) {
                    // it may be anything
                    result = NbBundle.getMessage(MessageServiceConnectionInfo.class, "MessageServiceConnectionInfo.ConnectionCheck.Everything"); //NON-NLS
                }
            }
            throw new MessageServiceException(result);
        }
    }
}
