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
package org.sleuthkit.autopsy.casemodule.multiusercases.services;

import java.util.logging.Level;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.io.IOException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooKeeper;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.openide.util.lookup.ServiceProvider;

/**
 * An implementation of the monitored service interface that reports status for
 * the ZooKeeper database for multi-user cases.
 */
@ServiceProvider(service = ServicesMonitor.MonitoredService.class)
public final class ZooKeeperDatabase implements ServicesMonitor.MonitoredService {

    private static final Logger logger = Logger.getLogger(ZooKeeper.class.getName());
    private static final int ZOOKEEPER_SESSION_TIMEOUT_MILLIS = 3000;
    private static final int ZOOKEEPER_CONNECTION_TIMEOUT_MILLIS = 15000;
    private static final int PORT_OFFSET = 1000; // When run in Solr, ZooKeeper defaults to Solr port + 1000

    @Override
    public ServicesMonitor.ServiceStatusReport getStatus() {
        ServicesMonitor.ServiceStatusReport statusReport;
        try {
            Object workerThreadWaitNotifyLock = new Object();
            int zooKeeperServerPort = Integer.valueOf(UserPreferences.getIndexingServerPort()) + PORT_OFFSET;
            String connectString = UserPreferences.getIndexingServerHost() + ":" + zooKeeperServerPort;
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
                statusReport = new ServicesMonitor.ServiceStatusReport(ServicesMonitor.Service.COORDINATION_SERVICE, ServicesMonitor.ServiceStatus.UP, "");
            } else {
                statusReport = new ServicesMonitor.ServiceStatusReport(ServicesMonitor.Service.COORDINATION_SERVICE, ServicesMonitor.ServiceStatus.DOWN, "");
            }
            zooKeeper.close();
            return statusReport;
        } catch (IOException | InterruptedException ex) {
            logger.log(Level.SEVERE, "Error connecting to ZooKeeper", ex); //NON-NLS
            return new ServicesMonitor.ServiceStatusReport(ServicesMonitor.Service.COORDINATION_SERVICE, ServicesMonitor.ServiceStatus.DOWN, ex.getMessage());
        }
    }
}
