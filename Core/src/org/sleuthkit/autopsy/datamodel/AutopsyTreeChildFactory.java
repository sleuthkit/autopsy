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
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
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
                Set<Person> persons = null;
                // TODO use below in future
                //Set<Person> persons = tskCase.getPersonManager().getPersons();
                if (!CollectionUtils.isEmpty(persons)) {
                    persons.stream().sorted((a, b) -> compare(Person::getName, a, b)).forEach(list::add);
                    return true;
                } else {
                    Set<Host> hosts = tskCase.getHostManager().getHosts();
                    hosts.stream().sorted((a, b) -> compare(Host::getName, a, b)).forEach(list::add);
                    return true;
                }
            } else {

                List<AutopsyVisitableItem> keys = new ArrayList<>(Arrays.asList(
                        new DataSources(),
                        new Views(tskCase),
                        new Results(tskCase),
                        new Tags(),
                        new Reports()));

                list.addAll(keys);

            }
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
        } else if (key instanceof Host) {
            return getHostGroupingNode((Host) key);
        } else if (key instanceof Person) {
            return null;
            // TODO when person management in place, use code below
            //return getPersonGroupingNode((Person) key);
        } else {
            logger.log(Level.SEVERE, "Unknown key type ", key.getClass().getName());
            return null;
        }
    }

    private HostGroupingNode getHostGroupingNode(Host host) {
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            return new HostGroupingNode(tskCase.getHostManager(), host);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            String hostName = host == null || host.getName() == null ? "<unknown>" : host.getName();
            logger.log(Level.SEVERE, String.format("Exception while getting host data for %s.", hostName), ex); //NON-NLS
            return null;
        }
    }

//    private PersonGroupingNode getPersonGroupingNode(Person person) {
//        try {
//            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
//            return new HostGroupingNode(tskCase.getPersonManager(), tskCase.getHostManager(), person);
//        } catch (NoCurrentCaseException | TskCoreException ex) {
//            String personName = person == null || person.getName() == null ? "<unknown>" : person.getName();
//            logger.log(Level.SEVERE, String.format("Exception while getting person data for %s.", personName), ex); //NON-NLS
//            return null;
//        }
//    }
    /**
     * Refresh the children
     */
    public void refreshChildren() {
        refresh(true);
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
}
