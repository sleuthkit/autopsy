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
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.core.ServicesMonitor;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An implementation of the monitored service interface that reports status for
 * the database server for multi-user cases.
 */
@ServiceProvider(service = ServicesMonitor.MonitoredService.class)
public final class PostgreSqlServer implements ServicesMonitor.MonitoredService {

    private static final Logger logger = Logger.getLogger(PostgreSqlServer.class.getName());

    @Override
    public ServicesMonitor.ServiceStatusReport getStatus() {
        try {
            CaseDbConnectionInfo info = UserPreferences.getDatabaseConnectionInfo();
            SleuthkitCase.tryConnect(info);
            return new ServicesMonitor.ServiceStatusReport(ServicesMonitor.Service.DATABASE_SERVER, ServicesMonitor.ServiceStatus.UP, "");
        } catch (UserPreferencesException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Error connecting to PostgreSQL server", ex); //NON-NLS
            return new ServicesMonitor.ServiceStatusReport(ServicesMonitor.Service.DATABASE_SERVER, ServicesMonitor.ServiceStatus.DOWN, ex.getMessage());
        } 
    }

}
