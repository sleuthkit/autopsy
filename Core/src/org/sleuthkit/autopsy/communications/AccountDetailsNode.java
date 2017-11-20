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
import java.util.Set;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;

/**
 * 'Root' Node for the Account/Messages area. Has children which are all the
 * relationships of all the accounts in this node.
 *
 */
class AccountDetailsNode extends AbstractNode {

    private final static Logger logger = Logger.getLogger(AccountDetailsNode.class.getName());

    AccountDetailsNode(Set<BlackboardArtifact> accountDeviceInstances, CommunicationsFilter filter, CommunicationsManager commsManager) {
        super(Children.create(new AccountRelationshipChildren(accountDeviceInstances, commsManager, filter), true));
    }

    /**
     * Children object for the relationships that the accounts are part of.
     */
    private static class AccountRelationshipChildren extends ChildFactory<BlackboardArtifact> {

        private final Set<BlackboardArtifact> accountDeviceInstances;
        private final CommunicationsManager commsManager;
        private final CommunicationsFilter filter;

        private AccountRelationshipChildren(Set<BlackboardArtifact> accountDeviceInstances, CommunicationsManager commsManager, CommunicationsFilter filter) {
            this.accountDeviceInstances = accountDeviceInstances;
            this.commsManager = commsManager;
            this.filter = filter;
        }

        @Override
        protected boolean createKeys(List<BlackboardArtifact> list) {
            list.addAll(accountDeviceInstances);
            return true;
        }

        @Override
        protected Node createNodeForKey(BlackboardArtifact t) {
            return new RelationShipNode(t); //To change body of generated methods, choose Tools | Templates.
        }

//        @Override
//        protected Node[] createNodes(BlackboardArtifact key) {
//            return new Node[]{new RelationShipNode(key)};
//        }
//        @Override
//        protected void addNotify() {
//            setKeys(accountDeviceInstances);
//        }
    }
}
