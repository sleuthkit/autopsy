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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.TskCoreException;

class AccountDetailsNode extends FilterNode {

    /**
     * @param wrappedNode the value of selectedNode
     */
    AccountDetailsNode(AccountDeviceInstanceNode wrappedNode) throws TskCoreException {
        super(wrappedNode, new AccountRelationshipChildren(wrappedNode));
    }

    /**
     * Children object for the relationships that the account is a member of.
     */
    private static class AccountRelationshipChildren extends Children.Keys<BlackboardArtifact> {

        private final AccountDeviceInstanceNode wrappedNode;

        private AccountRelationshipChildren(AccountDeviceInstanceNode wrappedNode) {
            this.wrappedNode = wrappedNode;
        }

        @Override
        protected Node[] createNodes(BlackboardArtifact t) {
            
            final RelationShipFilterNode blackboardArtifactNode = new RelationShipFilterNode(new BlackboardArtifactNode(t));
            return new Node[]{blackboardArtifactNode};
        }

        @Override
        protected void addNotify() {
            try {
                AccountDeviceInstance adi = wrappedNode.getLookup().lookup(AccountDeviceInstance.class);
                CommunicationsManager communicationsManager = wrappedNode.getLookup().lookup(CommunicationsManager.class);
                List<Account> accountsWithRelationship = communicationsManager.getAccountsWithRelationship(adi.getAccount());
                Set<BlackboardArtifact> keys = new HashSet<>();

                accountsWithRelationship.forEach(account -> {
                    try {
                        keys.addAll(communicationsManager.getRelationships(adi.getAccount(), account));
                    } catch (TskCoreException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                });

                setKeys(keys);
            } catch (TskCoreException ex) {
                //TODO: proper logging
                Exceptions.printStackTrace(ex);
            }
        }
    }
}
