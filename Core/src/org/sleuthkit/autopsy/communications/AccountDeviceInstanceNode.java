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
import java.util.Arrays;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;

/**
 * Node to represent an Account Device Instance in the CVT
 */
final class AccountDeviceInstanceNode extends AbstractNode {

    private final AccountDeviceInstanceKey accountDeviceInstanceKey;
    private final CommunicationsManager commsManager;
    private final Account account;

    AccountDeviceInstanceNode(AccountDeviceInstanceKey accountDeviceInstanceKey, CommunicationsManager commsManager) {
        super(Children.LEAF, Lookups.fixed(accountDeviceInstanceKey, commsManager));
        this.accountDeviceInstanceKey = accountDeviceInstanceKey;
        this.commsManager = commsManager;
        this.account = accountDeviceInstanceKey.getAccountDeviceInstance().getAccount();
        setName(account.getTypeSpecificID());
        setDisplayName(getName());
        setIconBaseWithExtension(Utils.getIconFilePath(account.getAccountType()));
    }

    AccountDeviceInstance getAccountDeviceInstance() {
        return accountDeviceInstanceKey.getAccountDeviceInstance();
    }

    AccountDeviceInstanceKey getAccountDeviceInstanceKey() {
        return accountDeviceInstanceKey;
    }

    CommunicationsManager getCommsManager() {
        return commsManager;
    }

    long getMessageCount() {
        return accountDeviceInstanceKey.getMessageCount();
    }

    CommunicationsFilter getFilter() {
        return accountDeviceInstanceKey.getCommunicationsFilter();
    }

    @Override
    @NbBundle.Messages(value = {"AccountNode.device=Device", "AccountNode.accountName=Account", "AccountNode.accountType=Type", "AccountNode.messageCount=Msgs"})
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set properties = s.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            s.put(properties);
        }
        properties.put(new NodeProperty<>("type",
                Bundle.AccountNode_accountType(),
                "type",
                account.getAccountType().getDisplayName())); // NON-NLS
        properties.put(new NodeProperty<>("count",
                Bundle.AccountNode_messageCount(),
                "count",
                accountDeviceInstanceKey.getMessageCount())); // NON-NLS
        properties.put(new NodeProperty<>("device",
                Bundle.AccountNode_device(),
                "device",
                accountDeviceInstanceKey.getDataSourceName())); // NON-NLS
        return s;
    }

    @Override
    public Action[] getActions(boolean context) {
        ArrayList<Action> actions = new ArrayList<>(Arrays.asList(super.getActions(context)));
        actions.add(PinAccountsAction.getInstance());
        actions.add(ResetAndPinAccountsAction.getInstance());
        return actions.toArray(new Action[actions.size()]);
    }
}
