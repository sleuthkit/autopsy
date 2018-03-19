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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * ChildFactory that creates AccountDeviceInstanceKeys and
 * AccountDeviceInstanceNodes using a provided CommunicationsManager and
 * CommunicationsFilter
 */
final class AccountDeviceInstanceNodeFactory extends ChildFactory<AccountDeviceInstanceKey> {

    private static final Logger logger = Logger.getLogger(AccountDeviceInstanceNodeFactory.class.getName());

    private final CommunicationsManager commsManager;
    private final CommunicationsFilter commsFilter;

    AccountDeviceInstanceNodeFactory(CommunicationsManager commsManager, CommunicationsFilter commsFilter) {
        this.commsManager = commsManager;
        this.commsFilter = commsFilter;
    }

    @Override
    protected boolean createKeys(List<AccountDeviceInstanceKey> list) {
        List<AccountDeviceInstanceKey> accountDeviceInstanceKeys = new ArrayList<>();
        try {
            final List<AccountDeviceInstance> accountDeviceInstancesWithRelationships =
                    commsManager.getAccountDeviceInstancesWithRelationships(commsFilter);
            for (AccountDeviceInstance accountDeviceInstance : accountDeviceInstancesWithRelationships) {
                //Filter out device accounts, in the table.
                if (Account.Type.DEVICE.equals(accountDeviceInstance.getAccount().getAccountType()) ==false) {
                    long communicationsCount = commsManager.getRelationshipSourcesCount(accountDeviceInstance, commsFilter);
                    accountDeviceInstanceKeys.add(new AccountDeviceInstanceKey(accountDeviceInstance, commsFilter, communicationsCount));
                }
            }
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "Error getting filtered account device instances", tskCoreException);
        }
        list.addAll(accountDeviceInstanceKeys);

        return true;
    }

    @Override
    protected Node createNodeForKey(AccountDeviceInstanceKey key) {
        return new AccountDeviceInstanceNode(key, commsManager);
    }
}
