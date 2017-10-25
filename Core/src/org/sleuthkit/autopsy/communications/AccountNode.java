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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Node to represent an Account in the AccountsBrowser
 */
class AccountNode extends AbstractNode {
    
    private static final Logger LOGGER = Logger.getLogger(AbstractNode.class.getName());

    private final AccountDeviceInstance accountDeviceInstance;

    AccountNode(AccountDeviceInstance accountDeviceInstance) {
        super(Children.LEAF, Lookups.fixed(accountDeviceInstance));
        this.accountDeviceInstance = accountDeviceInstance;
        setName(accountDeviceInstance.getAccount().getAccountUniqueID());
        setIconBaseWithExtension("org/sleuthkit/autopsy/communications/images/" + getIconFileName(accountDeviceInstance.getAccount().getAccountType()));
    }

    /**
     * The file name of the icon for the given Account Type. Will not include
     * the path but will include the extension.
     *
     * @return The file name of the icon for the given Account Type.
     */
    final String getIconFileName(Account.Type type) {
        if (type.equals(Account.Type.CREDIT_CARD)) {
            return "credit-card.png";
        } else if (type.equals(Account.Type.DEVICE)) {
            return "image.png";
        } else if (type.equals(Account.Type.EMAIL)) {
            return "email.png";
        } else if (type.equals(Account.Type.FACEBOOK)) {
            return "facebook.png";
        } else if (type.equals(Account.Type.INSTAGRAM)) {
            return "instagram.png";
        } else if (type.equals(Account.Type.MESSAGING_APP)) {
            return "messaging.png";
        } else if (type.equals(Account.Type.PHONE)) {
            return "phone.png";
        } else if (type.equals(Account.Type.TWITTER)) {
            return "twitter.png";
        } else if (type.equals(Account.Type.WEBSITE)) {
            return "web-file.png";
        } else if (type.equals(Account.Type.WHATSAPP)) {
            return "WhatsApp.png";
        } else {
            //there could be a default icon instead...
            throw new IllegalArgumentException("Unknown Account.Type: " + type.getTypeName());
        }

    }

    @Override
    @NbBundle.Messages({
        "AccountNode.device=Device",
        "AccountNode.accountName=Account",
        "AccountNode.accountType=Type",
        "AccountNode.messageCount=Message Count"})
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set properties = s.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            s.put(properties);
        }

        // RAMAN TBD: need to figure out how to get the right filters here
        // We talked about either creating a wrapper class around AccountDeviceInstance to push the selected filters
        // Or some kind of static access to pull the selected filters
        long msgCount = 0;
        try {
            CommunicationsFilter filter = null;
            msgCount = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().getRelationshipsCount(filter, accountDeviceInstance);  
        }
        catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Failed to get message count for account", ex); //NON-NLS
        }
        
        properties.put(new NodeProperty<>("type",
                Bundle.AccountNode_accountType(),
                "type",
                accountDeviceInstance.getAccount().getAccountType().getDisplayName())); // NON-NLS
       
        properties.put(new NodeProperty<>("count",
                Bundle.AccountNode_messageCount(),
                "count",
                msgCount)); // NON-NLS
        
        properties.put(new NodeProperty<>("device",
                Bundle.AccountNode_device(),
                "device",
                accountDeviceInstance.getDeviceId())); // NON-NLS
        return s;
    }
}
