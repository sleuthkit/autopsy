/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Key for AccountDeviceInstance node.
 *
 * Encapsulates a AccountDeviceInstanc,some meta data about it, and
 * CommunicationsFilter.
 */
final class AccountDeviceInstanceKey {

    private static final Logger logger = Logger.getLogger(AccountDeviceInstanceKey.class.getName());

    private final AccountDeviceInstance accountDeviceInstance;
    private final CommunicationsFilter filter;
    private final long messageCount;
    private final String dataSourceName;

    AccountDeviceInstanceKey(AccountDeviceInstance accountDeviceInstance, CommunicationsFilter filter, long msgCount, String dataSourceName) {
        this.accountDeviceInstance = accountDeviceInstance;
        this.filter = filter;
        this.messageCount = msgCount;
        this.dataSourceName = dataSourceName;
    }

    AccountDeviceInstanceKey(AccountDeviceInstance accountDeviceInstance, CommunicationsFilter filter, long msgCount) {
        this.accountDeviceInstance = accountDeviceInstance;
        this.filter = filter;
        this.messageCount = msgCount;
        this.dataSourceName = getDataSourceName(accountDeviceInstance, Case.getCurrentCase().getSleuthkitCase());
    }

    AccountDeviceInstance getAccountDeviceInstance() {
        return accountDeviceInstance;
    }

    CommunicationsFilter getCommunicationsFilter() {
        return filter;
    }

    long getMessageCount() {
        return messageCount;
    }

    String getDataSourceName() {
        return dataSourceName;
    }

    @Override
    public String toString() {
        return accountDeviceInstance.getAccount().getTypeSpecificID();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.accountDeviceInstance);
        hash = 37 * hash + (int) (this.messageCount ^ (this.messageCount >>> 32));
        hash = 37 * hash + Objects.hashCode(this.dataSourceName);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AccountDeviceInstanceKey other = (AccountDeviceInstanceKey) obj;
        if (this.messageCount != other.messageCount) {
            return false;
        }
        if (!Objects.equals(this.dataSourceName, other.dataSourceName)) {
            return false;
        }
        return Objects.equals(this.accountDeviceInstance, other.accountDeviceInstance);
    }

    private static String getDataSourceName(AccountDeviceInstance accountDeviceInstance, SleuthkitCase db) {
        try {
            for (DataSource dataSource : db.getDataSources()) {
                if (dataSource.getDeviceId().equals(accountDeviceInstance.getDeviceId())) {
                    return db.getContentById(dataSource.getId()).getName();
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting datasource name, falling back on device ID.", ex);
        }
        return accountDeviceInstance.getDeviceId();
    }
}
