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

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.Account;

public class AccountNode extends AbstractNode {

    private final Account account;

    public AccountNode(Account account) {
        super(Children.LEAF, Lookups.fixed(account));
        this.account = account;
    }

    @Override
    @NbBundle.Messages({
        "AccountNode.icon=Icon",
        "AccountNode.device=Device",
        "AccountNode.accountName=Account Name",
        "AccountNode.accountType=Type",
        "AccountNode.messageCount=Message Count"})
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set properties = s.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            s.put(properties);
        }

        properties.put(new NodeProperty<>("icon",
                Bundle.AccountNode_icon(),
                "icon",
                true)); // NON-NLS //gets overridden with icon
        properties.put(new NodeProperty<>("name",
                Bundle.AccountNode_accountName(),
                "name",
                account.getAccountUniqueID())); // NON-NLS
        properties.put(new NodeProperty<>("type",
                Bundle.AccountNode_accountType(),
                "type",
                account.getAccountType().getDisplayName())); // NON-NLS
        properties.put(new NodeProperty<>("count",
                Bundle.AccountNode_messageCount(),
                "count",
                1)); // NON-NLS
//        properties.put(new NodeProperty<>("device",
//                Bundle.AccountNode_device(),
//                "device",
//                account.)); // NON-NLS

        return s;
    }
}
