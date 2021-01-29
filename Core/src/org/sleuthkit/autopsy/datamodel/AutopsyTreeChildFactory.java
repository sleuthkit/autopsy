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
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.PersonGrouping.Person;
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
    }

    @Override
    protected void removeNotify() {
        super.removeNotify();
        Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
    }

    /**
     * Creates keys for the top level children.
     *
     * @param list list of keys created
     * @return true, indicating that the key list is complete
     */
    @Override
    protected boolean createKeys(List<Object> list) {
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();

            if (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
                list.addAll(getNodes(getTree(tskCase)));
                list.add(new Reports());
            } else {
                List<AutopsyVisitableItem> keys = new ArrayList<>(Arrays.asList(
                        new DataSources(),
                        new Views(tskCase),
                        new Results(tskCase),
                        new Tags(),
                        new Reports()));

                list.addAll(keys);
            }
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "Error getting datas sources list from the database.", tskCoreException);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
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

    /**
     * Creates a set of DataSourceGrouping objects from a set of data sources.
     *
     * @param dataSources The data sources to convert to a list.
     * @return The data source groupings determined from the data sources.
     */
    private Set<DataSourceGrouping> getDataSources(final Set<DataSource> dataSources) {
        if (dataSources == null) {
            return Collections.emptySet();
        }

        return dataSources.stream()
                .map(d -> new DataSourceGrouping(d))
                .collect(Collectors.toSet());
    }

    /**
     * Creates a set of HostGrouping objects from a set of hosts.
     *
     * @param hosts The hosts.
     * @param hostDataSources The mapping of hosts to data sources that belong
     * to those hosts.
     * @return The generated host groupings.
     */
    private Set<HostGrouping> getHostGroupings(final Set<Host> hosts, final Map<Host, Set<DataSource>> hostDataSources) {
        if (hosts == null) {
            return Collections.emptySet();
        }

        final Map<Host, Set<DataSource>> dataSources = hostDataSources == null ? Collections.emptyMap() : hostDataSources;

        return hosts.stream()
                .map(h -> new HostGrouping(h, getDataSources(dataSources.get(h))))
                .collect(Collectors.toSet());
    }

    /**
     * Creates the nodes to display in the tree.
     *
     * @param tree The tree of model items (TSK Persons, Hosts and DataSources).
     * @return The nodes to be displayed in the tree.
     */
    private List<? extends AutopsyVisitableItem> getNodes(OwnerHostTree tree) {
        if (tree == null) {
            return Collections.emptyList();
        }

        final Map<Person, Set<Host>> persons = tree.getPersons() == null ? Collections.emptyMap() : tree.getPersons();
        final Map<Host, Set<DataSource>> hosts = tree.getHosts() == null ? Collections.emptyMap() : tree.getHosts();

        // if no person nodes except unknown, then show host levels
        if (persons.isEmpty() || (persons.size() == 1 && persons.containsKey(null))) {
            return getHostGroupings(hosts.keySet(), hosts).stream()
                    .sorted()
                    .collect(Collectors.toList());
        } else {
            return persons.entrySet().stream()
                    .map((personKeyVal) -> new PersonGrouping(personKeyVal.getKey(), getHostGroupings(personKeyVal.getValue(), hosts)))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * A mapping of persons (or null key for unknown) to a set of hosts along
     * with a mapping of hosts (or null host if unknown) mapped to a set of
     * datasources. A null key will signify unknown.
     */
    private static class OwnerHostTree {

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
        OwnerHostTree(Map<Person, Set<Host>> persons, Map<Host, Set<DataSource>> hosts) {
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
    private OwnerHostTree getTree(SleuthkitCase tskCase) throws TskCoreException {
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
        return new OwnerHostTree(personsMap, hostsMap);
    }
}
