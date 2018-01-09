/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.net.UnknownHostException;

public class NetworkUtils {

    /**
     * Set the host name variable. Sometimes the network can be finicky, so the
     * answer returned by getHostName() could throw an exception or be null.
     * Have it read the environment variable if getHostName() is unsuccessful.
     */
    public static String getLocalHostName() {
        String hostName = "";
        try {
            hostName = java.net.InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            // getLocalHost().getHostName() can fail in some situations. 
            // Use environment variable if so.
            hostName = System.getenv("COMPUTERNAME"); //NON-NLS
        }
        if (hostName == null || hostName.isEmpty()) {
            hostName = System.getenv("COMPUTERNAME"); //NON-NLS
        }
        return hostName;
    }
}
