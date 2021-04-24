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

import java.io.IOException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooKeeper;

/**
 * A utility class for coordination service and ZooKeeper. This class is in a 
 * separate package to avoid exposing it as public API.
 */
public final class CoordinationServiceUtils {

    private static final int ZOOKEEPER_SESSION_TIMEOUT_MILLIS = 3000;
    private static final int ZOOKEEPER_CONNECTION_TIMEOUT_MILLIS = 15000;

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
        Object workerThreadWaitNotifyLock = new Object();
        String connectString = hostName + ":" + port;
        ZooKeeper zooKeeper = new ZooKeeper(connectString, ZOOKEEPER_SESSION_TIMEOUT_MILLIS,
                (WatchedEvent event) -> {
                    synchronized (workerThreadWaitNotifyLock) {
                        workerThreadWaitNotifyLock.notify();
                    }
                });
        synchronized (workerThreadWaitNotifyLock) {
            workerThreadWaitNotifyLock.wait(ZOOKEEPER_CONNECTION_TIMEOUT_MILLIS);
        }
        ZooKeeper.States state = zooKeeper.getState();
        if (state == ZooKeeper.States.CONNECTED || state == ZooKeeper.States.CONNECTEDREADONLY) {
            result = true;
        }
        zooKeeper.close();
        return result;
    }
}
