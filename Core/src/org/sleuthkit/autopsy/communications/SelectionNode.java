/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * 'Root' Node for the Account/Messages area. Represents all the relationships
 * that are selected in the AccountsBrowser or the VisualizationPanel. Can be
 * populated with AccountDeviceInstance and/or directly with relationships
 * (Content).
 */
final class SelectionNode extends AbstractNode {

    private SelectionNode(Children children, Lookup lookup) {
        super(children, lookup);
    }

    static SelectionNode createFromAccountsAndRelationships(
            Set<Content> edgeRelationshipArtifacts,
            Set<AccountDeviceInstanceKey> accountDeviceInstanceKeys,
            CommunicationsFilter filter,
            CommunicationsManager commsManager) {

        Set<AccountDeviceInstance> accountDeviceInstances = accountDeviceInstanceKeys.stream()
                .map(AccountDeviceInstanceKey::getAccountDeviceInstance)
                .collect(Collectors.toSet());

        SelectionNode node = new SelectionNode(Children.create(
                new RelationshipChildren(
                        edgeRelationshipArtifacts,
                        accountDeviceInstances,
                        commsManager,
                        filter),
                true), Lookups.fixed(accountDeviceInstanceKeys.toArray()));

        //This is not good for internationalization!!!
        String name = "";
        final int accounts = accountDeviceInstances.size();
        if (accounts > 1) {
            name = accounts + " accounts";
        } else if (accounts == 1) {
            name = Iterables.getOnlyElement(accountDeviceInstances).getAccount().getTypeSpecificID();
        }

        final int edges = edgeRelationshipArtifacts.size();

        if (edges > 0) {
            name = name + (name.isEmpty() ? "" : " and ") + edges + " relationship" + (edges > 1 ? "s" : "");
        }

        node.setDisplayName(name);
        return node;
    }

    static SelectionNode createFromAccounts(
            Set<AccountDeviceInstanceKey> accountDeviceInstances,
            CommunicationsFilter filter,
            CommunicationsManager commsManager) {

        return createFromAccountsAndRelationships(Collections.emptySet(), accountDeviceInstances, filter, commsManager);
    }

    /**
     * Children object for the relationships that the accounts are part of.
     */
    private static class RelationshipChildren extends ChildFactory<Content> {

        static final private Logger logger = Logger.getLogger(RelationshipChildren.class.getName());

        private final Set<Content> edgeRelationshipArtifacts;

        private final Set<AccountDeviceInstance> accountDeviceInstances;

        private final CommunicationsManager commsManager;
        private final CommunicationsFilter filter;

        private RelationshipChildren(Set<Content> selectedEdgeRelationshipSources, Set<AccountDeviceInstance> selecedAccountDeviceInstances, CommunicationsManager commsManager, CommunicationsFilter filter) {
            this.edgeRelationshipArtifacts = selectedEdgeRelationshipSources;
            this.accountDeviceInstances = selecedAccountDeviceInstances;
            this.commsManager = commsManager;
            this.filter = filter;
        }

        @Override
        protected boolean createKeys(List<Content> list) {
            try {
                final Set<Content> relationshipSources = commsManager.getRelationshipSources(accountDeviceInstances, filter);
                list.addAll(Sets.union(relationshipSources, edgeRelationshipArtifacts));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error getting communications", ex);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(Content content) {
            if (content instanceof BlackboardArtifact) {
                return new RelationshipNode((BlackboardArtifact) content);
            } else {
                throw new UnsupportedOperationException("Cannot create a RelationshipNode for non BlackboardArtifact content.");
            }
        }
    }
}
