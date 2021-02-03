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
package org.sleuthkit.autopsy.datamodel;

import com.google.common.eventbus.EventBus;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.PersonGroupingNode.Person;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.HostManager;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Child factory to create the top level children of the autopsy tree
 *
 */
public final class AutopsyTreeChildFactory extends ChildFactory.Detachable<Object> {

    private static final Logger logger = Logger.getLogger(AutopsyTreeChildFactory.class.getName());

    private EventBus personUpdates = null;
    private EventBus hostUpdates = null;

    private PersonHostMapping personHostMapping = null;
    private Map<Long, Set<DataSource>> hostChildren = null;
    private Map<Long, Set<Host>> personChildren = null;

    /**
     * Listener for handling DATA_SOURCE_ADDED events.
     */
    private final PropertyChangeListener pcl = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())
                    && Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
                refreshChildren();
            }
        }
    };

    @Override
    protected void addNotify() {
        super.addNotify();
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
        personUpdates = new EventBus();
        hostUpdates = new EventBus();
        personHostMapping = null;
        hostChildren = null;
        personChildren = null;
    }

    @Override
    protected void removeNotify() {
        super.removeNotify();
        Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
        personUpdates = null;
        hostUpdates = null;
        hostChildren = null;
        personChildren = null;
        personHostMapping = null;
    }

    /**
     * Creates keys for the top level children.
     *
     * @param list list of keys created
     * @return true, indicating that the key list is complete
     */
    @Override
    protected boolean createKeys(List<Object> list) {
        if (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
            // this will update previously existing host nodes and group nodes; 
            // newly generated items will be updated manually.
            updatePersonHosts();
            getNodes(personHostMapping).forEach(list::add);
            list.add(new Reports());
        } else {
            try {
                SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                List<AutopsyVisitableItem> keys = new ArrayList<>(Arrays.asList(
                        new DataSources(),
                        new Views(tskCase),
                        new Results(tskCase),
                        new Tags(),
                        new Reports()));

                list.addAll(keys);
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            }
        }

        return true;
    }

    /**
     * Creates nodes for the top level Key
     *
     * @param key
     *
     * @return Node for the key, null if key is unknown.
     */
    @Override
    protected Node createNodeForKey(Object key) {
        if (key instanceof SleuthkitVisitableItem) {
            return ((SleuthkitVisitableItem) key).accept(new CreateSleuthkitNodeVisitor());
        } else if (key instanceof AutopsyVisitableItem) {
            return ((AutopsyVisitableItem) key).accept(new RootContentChildren.CreateAutopsyNodeVisitor());
        } else if (key instanceof Host) {
            HostGroupingNode host = new HostGroupingNode((Host) key);
            host.update(hostChildren);
            if (hostUpdates != null) {
                hostUpdates.register(host);
            }

            return host;
        } else if (key instanceof Person) {
            PersonGroupingNode person = new PersonGroupingNode((Person) key);
            person.update(personChildren);
            if (personUpdates != null) {
                personUpdates.register(person);
            }

            return person;
        } else {
            logger.log(Level.SEVERE, "Unknown key type ", key.getClass().getName());
            return null;
        }
    }

    /**
     * Refresh the children
     */
    public void refreshChildren() {
        refresh(true);
    }

    private void updatePersonHosts() {
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            personHostMapping = getMapping(tskCase);
            hostChildren = getIdMapping(personHostMapping.getHosts(), Host::getId); 
            personChildren = getIdMapping(personHostMapping.getPersons(), Person::getId);
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "Error getting datas sources list from the database.", tskCoreException);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
        }

        if (hostChildren != null && hostUpdates != null) {
            hostUpdates.post(hostChildren);
        }

        if (personUpdates != null && personChildren != null) {
            personUpdates.post(personChildren);
        }
    }

    private <K, V> Map<Long, Set<V>> getIdMapping(Map<K, Set<V>> itemMapping, Function<K, Long> idFunction) {
        return itemMapping.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey() == null ? null : idFunction.apply(entry.getKey()),
                        entry -> entry.getValue(),
                        (kv1, kv2) -> {
                            // we shouldn't have a situation where there are two of a distinct key with the same 
                            // id, but if it happens, combine the values.
                            kv1.addAll(kv2);
                            return kv1;
                        }));
    }

    /**
     * Creates the nodes to display in the tree.
     *
     * @param tree The tree of model items (TSK Persons, Hosts and DataSources).
     * @return The nodes to be displayed in the tree.
     */
    private Stream<? extends Object> getNodes(PersonHostMapping tree) {
        if (tree == null) {
            return Stream.empty();
        }

        final Map<Person, Set<Host>> persons = tree.getPersons() == null ? Collections.emptyMap() : tree.getPersons();
        final Map<Host, Set<DataSource>> hosts = tree.getHosts() == null ? Collections.emptyMap() : tree.getHosts();

        // if no person nodes except unknown, then show host levels
        if (persons.isEmpty() || (persons.size() == 1 && persons.containsKey(null))) {
            return hosts.keySet().stream()
                    .sorted((a, b) -> compare(Host::getName, a, b));
        } else {
            return persons.keySet().stream()
                    .sorted((a, b) -> compare(Person::getName, a, b));
        }
    }

    private <T> int compare(Function<T, String> keyFunction, T objA, T objB) {
        String thisKey = objA == null ? null : keyFunction.apply(objA);
        String otherKey = objB == null ? null : keyFunction.apply(objB);

        // push unknown host to bottom
        if (thisKey == null && otherKey == null) {
            return 0;
        } else if (thisKey == null) {
            return 1;
        } else if (otherKey == null) {
            return -1;
        }

        return thisKey.compareToIgnoreCase(otherKey);
    }

    /**
     * A mapping of persons (or null key for unknown) to a set of hosts along
     * with a mapping of hosts (or null host if unknown) mapped to a set of
     * datasources. A null key will signify unknown.
     */
    private static class PersonHostMapping {

        final Map<Person, Set<Host>> persons;
        final Map<Host, Set<DataSource>> hosts;

        /**
         * Main constructor.
         *
         * @param persons The mapping of persons to their hosts (or null key for
         * unknown person).
         * @param hosts The mapping of hosts to their data sources (or null key
         * for unknown hosts).
         */
        PersonHostMapping(Map<Person, Set<Host>> persons, Map<Host, Set<DataSource>> hosts) {
            this.persons = Collections.unmodifiableMap(new HashMap<>(persons));
            this.hosts = Collections.unmodifiableMap(new HashMap<>(hosts));
        }

        /**
         * @return The mapping of persons to their hosts (or null key for
         * unknown person).
         */
        Map<Person, Set<Host>> getPersons() {
            return persons;
        }

        /**
         * @return The mapping of hosts to their data sources (or null key for
         * unknown hosts).
         */
        Map<Host, Set<DataSource>> getHosts() {
            return hosts;
        }
    }

    /**
     * Creates a tree including a mapping of persons to hosts (if present) and a
     * mapping of hosts to data sources.
     *
     * @param tskCase The relevant SleuthkitCase object.
     * @return The generated tree.
     * @throws TskCoreException
     */
    private PersonHostMapping getMapping(SleuthkitCase tskCase) throws TskCoreException {
        Map<Person, Set<Host>> personsMap = new HashMap<>();
        Map<Host, Set<DataSource>> hostsMap = new HashMap<>();

        HostManager hostManager = tskCase.getHostManager();
        for (Host host : hostManager.getHosts()) {
            hostsMap.put(host, new HashSet<>());
        }

        List<DataSource> dataSources = tskCase.getDataSources();
        for (DataSource dataSource : dataSources) {
            Set<DataSource> hostList = hostsMap.computeIfAbsent(dataSource.getHost(), (host) -> new HashSet<>());
            hostList.add(dataSource);
        }

        // TODO calculate persons for hosts
        return new PersonHostMapping(personsMap, hostsMap);
    }
}
