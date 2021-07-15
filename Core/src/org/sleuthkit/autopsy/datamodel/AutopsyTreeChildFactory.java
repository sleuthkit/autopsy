/*
 * Autopsy Forensic Browser
 * Copyright 2018-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.PersonManager;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A child factory to create the top level nodes in the main tree view. These
 * nodes are the child nodes of the invisible root node of the tree. The child
 * nodes that are created vary with the view option selected by the user: group
 * by data type or group by person/host.
 */
public final class AutopsyTreeChildFactory extends ChildFactory.Detachable<Object> {

    private static final Set<Case.Events> EVENTS_OF_INTEREST = EnumSet.of(Case.Events.DATA_SOURCE_ADDED,
            Case.Events.HOSTS_ADDED,
            Case.Events.HOSTS_DELETED,
            Case.Events.PERSONS_ADDED,
            Case.Events.PERSONS_DELETED,
            Case.Events.HOSTS_ADDED_TO_PERSON,
            Case.Events.HOSTS_REMOVED_FROM_PERSON
    );

    private static final Set<String> EVENTS_OF_INTEREST_NAMES = EVENTS_OF_INTEREST.stream()
            .map(evt -> evt.name())
            .collect(Collectors.toSet());

    private static final Logger logger = Logger.getLogger(AutopsyTreeChildFactory.class.getName());

    /**
     * Listener for application events published when persons and/or hosts are
     * added to or deleted from the data model for the current case. If the user
     * has selected the group by person/host option for the tree, these events
     * mean that the top-level person/host nodes in the tree need to be
     * refreshed to reflect the changes.
     */
    private final PropertyChangeListener pcl = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (EVENTS_OF_INTEREST_NAMES.contains(eventType)
                    && Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
                refreshChildren();
            }
        }
    };

    private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);
    
    @Override
    protected void addNotify() {
        super.addNotify();
        Case.addEventTypeSubscriber(EVENTS_OF_INTEREST, weakPcl);
    }
    
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        Case.removeEventTypeSubscriber(EVENTS_OF_INTEREST, weakPcl);
    }

    /**
     * Creates the keys for the top level nodes in the main tree view. These
     * nodes are the child nodes of the invisible root node of the tree. The
     * child nodes that are created vary with the view option selected by the
     * user: group by data type or group by person/host.
     *
     * IMPORTANT: Every time a key is added to the keys list, the NetBeans
     * framework reacts. To avoid significant performance hits, all of the keys
     * need to be added at once.
     *
     * @param list A list to contain the keys.
     *
     * @return True, indicating that the list of keys is complete.
     */
    @Override
    protected boolean createKeys(List<Object> list) {
        List<Object> nodes = Collections.emptyList();
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            if (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
                /*
                 * The user has selected the group by person/host tree view
                 * option.
                 */
                PersonManager personManager = tskCase.getPersonManager();
                List<Person> persons = personManager.getPersons();
                // show persons level if there are persons to be shown
                if (!CollectionUtils.isEmpty(persons)) {
                    nodes = persons.stream()
                            .map(PersonGrouping::new)
                            .sorted()
                            .collect(Collectors.toList());

                    if (CollectionUtils.isNotEmpty(personManager.getHostsWithoutPersons())) {
                        nodes.add(new PersonGrouping(null));
                    }
                } else {
                    // otherwise, just show host level
                    nodes = tskCase.getHostManager().getAllHosts().stream()
                            .map(HostGrouping::new)
                            .sorted()
                            .collect(Collectors.toList());                    
                }
                
                // either way, add in reports node
                nodes.add(new Reports());
            } else {
                // data source by type view
                nodes = Arrays.asList(
                        new DataSourcesByType(),
                        new Views(Case.getCurrentCaseThrows().getSleuthkitCase()),
                        new DataArtifacts(),
                        new AnalysisResults(),
                        new OsAccounts(Case.getCurrentCaseThrows().getSleuthkitCase()),
                        new Tags(),
                        new Reports()
                );
            }
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to create tree because there is no current case", ex); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to create tree because of an error querying the case database", ex); //NON-NLS
        }
        
        // add all nodes to the netbeans node list
        list.addAll(nodes);
        return true;
    }

    /**
     * Creates a node for a given key for the top level nodes in the main tree
     * view.
     *
     * @param key The key.
     *
     * @return A node for the key.
     */
    @Override
    protected Node createNodeForKey(Object key) {
        Node node = null;
        if (key != null) {
            if (key instanceof SleuthkitVisitableItem) {
                node = ((SleuthkitVisitableItem) key).accept(new CreateSleuthkitNodeVisitor());
            } else if (key instanceof AutopsyVisitableItem) {
                node = ((AutopsyVisitableItem) key).accept(new RootContentChildren.CreateAutopsyNodeVisitor());
            } else {
                logger.log(Level.SEVERE, "Unknown key type: ", key.getClass().getName());
            }
        }
        return node;
    }

    /**
     * Refreshes the top level nodes in the main tree view.
     */
    public void refreshChildren() {
        refresh(true);
    }
    
}
