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

class AccountNode extends AbstractNode {

    private final Account account;

    AccountNode(Account account) {
        super(Children.LEAF, Lookups.fixed(account));
        this.account = account;
        setName(account.getAccountUniqueID());
        setIconBaseWithExtension("org/sleuthkit/autopsy/communications/images/" + getIconFileName(account.getAccountType()));
    }

    final String getIconFileName(Account.Type type) {
        if (type == Account.Type.CREDIT_CARD) {
            return "creditcards.png";
        } else if (type == Account.Type.DEVICE) {
            return "device.png";
        } else if (type == Account.Type.EMAIL) {
            return "email.png";
        } else if (type == Account.Type.FACEBOOK) {
            return "facebook.png";
        } else if (type == Account.Type.INSTAGRAM) {
            return "instagram.png";
        } else if (type == Account.Type.MESSAGING_APP) {
            return "messaging.png";
        } else if (type == Account.Type.PHONE) {
            return "phone.png";
        } else if (type == Account.Type.TWITTER) {
            return "twitter.png";
        } else if (type == Account.Type.WEBSITE) {
            return "world.png";
        } else if (type == Account.Type.WHATSAPP) {
            return "WhatsApp.png";
        } else {
            throw new IllegalArgumentException("Unknown Account.Type: " + type.getTypeName());
        }

    }

    @Override
    @NbBundle.Messages({
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
