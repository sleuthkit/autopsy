/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import java.util.List;
import java.util.logging.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;

class AccountsRootChildren extends ChildFactory<AccountDeviceInstanceKey> {

    private final List<AccountDeviceInstanceKey> accountDeviceInstanceKeys;
    private final CommunicationsManager commsManager;

    AccountsRootChildren(List<AccountDeviceInstanceKey> accountDeviceInstanceKeys, CommunicationsManager commsManager) {
        super();
        this.accountDeviceInstanceKeys = accountDeviceInstanceKeys;
        this.commsManager = commsManager;
    }
//these are the methods for Children.Keys
//    @Override
//    protected void removeNotify() {
//        super.removeNotify();
//        setKeys(Collections.emptySet());
//    }
//
//    @Override
//    protected void addNotify() {
//        super.addNotify();
//        setKeys(accountDeviceInstanceKeys);
//    }
    //    @Override
//    protected Node[] createNodes(AccountDeviceInstanceKey key) {
//        return new Node[]{new AccountDeviceInstanceNode(key, commsManager)};
//    }
    
    
    //These are the methods for ChildFactory. I am going to keep them around but commented until we make a final descision.
    @Override
    protected boolean createKeys(List<AccountDeviceInstanceKey> list) {
        list.addAll(accountDeviceInstanceKeys);
        return true;
    }

    @Override
    protected Node createNodeForKey(AccountDeviceInstanceKey key) {
        return new AccountDeviceInstanceNode(key, commsManager);
    }

    /**
     * Node to represent an Account in the AccountsBrowser
     */
    static class AccountDeviceInstanceNode extends AbstractNode {

        private static final Logger LOGGER = Logger.getLogger(AccountDeviceInstanceNode.class.getName());
        private final AccountDeviceInstanceKey accountDeviceInstanceKey;
        private final CommunicationsManager commsManager;
        private final Account account;

        private AccountDeviceInstanceNode(AccountDeviceInstanceKey accountDeviceInstanceKey, CommunicationsManager commsManager) {
            super(Children.LEAF, Lookups.fixed(accountDeviceInstanceKey, commsManager));
            this.accountDeviceInstanceKey = accountDeviceInstanceKey;
            this.commsManager = commsManager;
            this.account = accountDeviceInstanceKey.getAccountDeviceInstance().getAccount();
            setName(account.getAccountUniqueID());
            setIconBaseWithExtension("org/sleuthkit/autopsy/communications/images/" + Utils.getIconFileName(account.getAccountType()));
        }

        public AccountDeviceInstance getAccountDeviceInstance() {
            return accountDeviceInstanceKey.getAccountDeviceInstance();
        }

        public CommunicationsManager getCommsManager() {
            return commsManager;
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
                    accountDeviceInstanceKey.getAccountDeviceInstance().getDeviceId())); // NON-NLS
            return s;
        }
    }
}
