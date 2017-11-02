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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.TskCoreException;

class AccountDetailsNode extends AbstractNode {

    private final static Logger logger = Logger.getLogger(AccountDetailsNode.class.getName());
    private final CommunicationsFilter filter; //TODO: Use this

    AccountDetailsNode(Set<Account> accounts,CommunicationsFilter filter, CommunicationsManager commsManager) {
        super(new AccountRelationshipChildren(accounts, commsManager, filter));
        this.filter = filter;
    }

    /**
     * Children object for the relationships that the account is a member of.
     */
    private static class AccountRelationshipChildren extends Children.Keys<BlackboardArtifact> {

        private final Set<Account> accounts;
        private final CommunicationsManager commsManager;
        private final CommunicationsFilter filter;//TODO: Use this

        private AccountRelationshipChildren(Set<Account> accounts, CommunicationsManager commsManager, CommunicationsFilter filter) {
            this.accounts = accounts;
            this.commsManager = commsManager;
            this.filter = filter;
        }

        @Override
        protected Node[] createNodes(BlackboardArtifact key) {
            return new Node[]{new RelationShipFilterNode(key)};
        }

        @Override
        protected void addNotify() {
            Set<BlackboardArtifact> keys = new HashSet<>();
            for (Account account : accounts) {
                List<Account> accountsWithRelationship = new ArrayList<>();
                try {
                    accountsWithRelationship.addAll(commsManager.getAccountsWithRelationship(account)); //TODO: Use filter here
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Error loading with relationships to " + account, ex);
                }

                accountsWithRelationship.forEach(otherAcount -> {
                    try {
                        keys.addAll(commsManager.getRelationships(account, otherAcount)); //TODO:Use filter here
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, "Error loading relationships between " + account + " and " + otherAcount, ex);
                    }
                });
            }
            setKeys(keys);
        }
    }
}
