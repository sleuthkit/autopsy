/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coordinationservice.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * A utility class for coordination service and ZooKeeper. This class is in a 
 * separate package to avoid exposing it as public API.
 */
public final class CoordinationServiceUtils {

    private static final int ZOOKEEPER_SESSION_TIMEOUT_MILLIS = 3000;
    
    /**
     * Determines if ZooKeeper is accessible with the current settings. Closes
     * the connection prior to returning.
     *
     * @return true if a connection was achieved, false otherwise
     *
     * @throws InterruptedException
     * @throws IOException
     */
    public static boolean isZooKeeperAccessible(String hostName, String port) throws InterruptedException, IOException {
        boolean result = false;
        
        try {
            
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(hostName, Integer.valueOf(port)), ZOOKEEPER_SESSION_TIMEOUT_MILLIS);
            socket.setSoTimeout(ZOOKEEPER_SESSION_TIMEOUT_MILLIS);

            OutputStream out = socket.getOutputStream();
            out.write("ruok".getBytes());
            out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String response = reader.readLine();

            socket.close();

            if (response.toLowerCase().contains("imok")) {
                result = true;
            }
        } catch (SocketTimeoutException e) {
            result = false;
        }
        return result;
    }
}
