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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaAccount;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Node to represent an Account Device Instance in the CVT
 */
final class AccountDeviceInstanceNode extends AbstractNode {

    private static final Logger logger = Logger.getLogger(AccountDeviceInstanceNode.class.getName());

    private final AccountDeviceInstanceKey accountDeviceInstanceKey;
    private final CommunicationsManager commsManager;
    private final Account account;

    private static final BlackboardAttribute.Type NAME_ATTRIBUTE = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.fromID(TSK_NAME.getTypeID()));

    AccountDeviceInstanceNode(AccountDeviceInstanceKey accountDeviceInstanceKey, CommunicationsManager commsManager) {
        super(Children.LEAF, Lookups.fixed(accountDeviceInstanceKey, commsManager));
        this.accountDeviceInstanceKey = accountDeviceInstanceKey;
        this.commsManager = commsManager;
        this.account = accountDeviceInstanceKey.getAccountDeviceInstance().getAccount();
        setName(account.getTypeSpecificID());
        setDisplayName(getName());
        String iconPath = Utils.getIconFilePath(account.getAccountType());
        this.setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);
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
    @NbBundle.Messages(value = {"AccountNode.device=Device", "AccountNode.accountName=Account", "AccountNode.accountType=Type", "AccountNode.messageCount=Items"})
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set properties = sheet.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            sheet.put(properties);
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
        return sheet;
    }

    @Override
    public Action[] getActions(boolean context) {
        ArrayList<Action> actions = new ArrayList<>(Arrays.asList(super.getActions(context)));
        actions.add(PinAccountsAction.getInstance());
        actions.add(ResetAndPinAccountsAction.getInstance());
        return actions.toArray(new Action[actions.size()]);
    }

    @Messages({
        "# {0} - Contact Name",
        "# {1} - Persona Name",
        "AccountInstanceNode_Tooltip_Template=Contact: {0} - Persona: {1}",
        "# {0} - PersonaAccount count",
        "AccountInstanceNode_Tooltip_suffix=(1 of {0})"
    })
    @Override
    public String getShortDescription() {
        List<PersonaAccount> personaList;
        List<BlackboardArtifact> contactArtifactList;
        try {
            personaList = CVTPersonaCache.getPersonaAccounts(account);
            contactArtifactList = ContactCache.getContacts(account);
        } catch (ExecutionException ex) {
            logger.log(Level.WARNING, "Failed to retrieve Persona details for node.", ex);
            return getDisplayName();
        }

        String personaName;
        if (personaList != null && !personaList.isEmpty()) {
            personaName = personaList.get(0).getPersona().getName();
            if (personaList.size() > 1) {
                personaName += Bundle.AccountInstanceNode_Tooltip_suffix(Integer.toString(personaList.size()));
            }
        } else {
            personaName = "None";
        }

        String contactName = getDisplayName();
        if (contactArtifactList != null && !contactArtifactList.isEmpty()) {
            try {
                BlackboardArtifact contactArtifact = contactArtifactList.get(0);
                BlackboardAttribute attribute = contactArtifact.getAttribute(NAME_ATTRIBUTE);
                if (attribute != null) {
                    contactName = attribute.getValueString();
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to retrive name attribute from contact artifact.", ex);
            }
        }

        return Bundle.AccountInstanceNode_Tooltip_Template(contactName, personaName);
    }
}
