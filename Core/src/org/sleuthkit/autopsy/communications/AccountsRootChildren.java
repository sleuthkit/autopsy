/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

class AccountsRootChildren extends ChildFactory<AccountDeviceInstanceKey> {

    private static final Logger logger = Logger.getLogger(AccountsRootChildren.class.getName());

    private final CommunicationsManager commsManager;
    private final CommunicationsFilter commsFilter;

    AccountsRootChildren(CommunicationsManager commsManager, CommunicationsFilter commsFilter) {
        super();
        this.commsManager = commsManager;
        this.commsFilter = commsFilter;
    }

    @Override
    protected boolean createKeys(List<AccountDeviceInstanceKey> list) {
        List<AccountDeviceInstanceKey> accountDeviceInstanceKeys = new ArrayList<>();
        try {
            for (AccountDeviceInstance accountDeviceInstance : commsManager.getAccountDeviceInstancesWithRelationships(commsFilter)) {
                long communicationsCount = commsManager.getRelationshipSourcesCount(accountDeviceInstance, commsFilter);
                String dataSourceName = getDataSourceName(accountDeviceInstance);
                accountDeviceInstanceKeys.add(new AccountDeviceInstanceKey(accountDeviceInstance, commsFilter, communicationsCount, dataSourceName));
            };
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

    private String getDataSourceName(AccountDeviceInstance accountDeviceInstance) {
        try {
            final SleuthkitCase sleuthkitCase = Case.getCurrentCase().getSleuthkitCase();
            for (DataSource dataSource : sleuthkitCase.getDataSources()) {
                if (dataSource.getDeviceId().equals(accountDeviceInstance.getDeviceId())) {
                    return sleuthkitCase.getContentById(dataSource.getId()).getName();
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting datasource name, falling back on device ID.", ex);
        }
        return accountDeviceInstance.getDeviceId();
    }

    /**
     * Node to represent an Account in the AccountsBrowser
     */
    static class AccountDeviceInstanceNode extends AbstractNode {

        private final AccountDeviceInstanceKey accountDeviceInstanceKey;

        private final CommunicationsManager commsManager;
        private final Account account;

        AccountDeviceInstanceNode(AccountDeviceInstanceKey accountDeviceInstanceKey, CommunicationsManager commsManager) {
            super(Children.LEAF, Lookups.fixed(accountDeviceInstanceKey, commsManager));
            this.accountDeviceInstanceKey = accountDeviceInstanceKey;
            this.commsManager = commsManager;
            this.account = accountDeviceInstanceKey.getAccountDeviceInstance().getAccount();
            setName(account.getTypeSpecificID());
            setIconBaseWithExtension("org/sleuthkit/autopsy/communications/images/" + Utils.getIconFileName(account.getAccountType()));
        }

        public AccountDeviceInstance getAccountDeviceInstance() {
            return accountDeviceInstanceKey.getAccountDeviceInstance();
        }

        public AccountDeviceInstanceKey getAccountDeviceInstanceKey() {
            return accountDeviceInstanceKey;
        }

        public CommunicationsManager getCommsManager() {
            return commsManager;
        }

        public long getMessageCount() {
            return accountDeviceInstanceKey.getMessageCount();
        }

        public CommunicationsFilter getFilter() {
            return accountDeviceInstanceKey.getCommunicationsFilter();
        }

        @Override
        @NbBundle.Messages(value = {"AccountNode.device=Device",
            "AccountNode.accountName=Account",
            "AccountNode.accountType=Type",
            "AccountNode.messageCount=Msgs"})
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set properties = s.get(Sheet.PROPERTIES);
            if (properties == null) {
                properties = Sheet.createPropertiesSet();
                s.put(properties);
            }

            properties.put(new NodeProperty<>("type", Bundle.AccountNode_accountType(), "type",
                    account.getAccountType().getDisplayName())); // NON-NLS
            properties.put(new NodeProperty<>("count", Bundle.AccountNode_messageCount(), "count",
                    accountDeviceInstanceKey.getMessageCount())); // NON-NLS
            properties.put(new NodeProperty<>("device", Bundle.AccountNode_device(), "device",
                    accountDeviceInstanceKey.getDataSourceName())); // NON-NLS
            return s;
        }

        @Override
        public Action[] getActions(boolean context) {
            ArrayList<Action> actions = new ArrayList<>(Arrays.asList(super.getActions(context)));
            actions.add(new PinAccountAction());
            return actions.toArray(new Action[actions.size()]);
        }

        /**
         *
         */
        private class PinAccountAction extends AbstractAction {

            public PinAccountAction() {
                super("Visualize Account");
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                CVTEvents.getCVTEventBus().post(new PinAccountEvent(AccountDeviceInstanceNode.this));
            }
        }
    }
}
