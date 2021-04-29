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
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
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
 * Child factory to create the top level children of the autopsy tree
 *
 */
public final class AutopsyTreeChildFactory extends ChildFactory.Detachable<Object> {

    private static final Set<Case.Events> LISTENING_EVENTS = EnumSet.of(
            Case.Events.DATA_SOURCE_ADDED,
            Case.Events.HOSTS_ADDED,
            Case.Events.HOSTS_DELETED,
            Case.Events.PERSONS_ADDED,
            Case.Events.PERSONS_DELETED,
            Case.Events.PERSONS_CHANGED
    );

    private static final Set<String> LISTENING_EVENT_NAMES = LISTENING_EVENTS.stream()
            .map(evt -> evt.name())
            .collect(Collectors.toSet());

    private static final Logger logger = Logger.getLogger(AutopsyTreeChildFactory.class.getName());

    /**
     * Listener for handling DATA_SOURCE_ADDED events.
     */
    private final PropertyChangeListener pcl = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (LISTENING_EVENT_NAMES.contains(eventType)
                    && Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
                refreshChildren();
            }
        }
    };

    @Override
    protected void addNotify() {
        super.addNotify();
        Case.addEventTypeSubscriber(LISTENING_EVENTS, pcl);
    }

    @Override
    protected void removeNotify() {
        super.removeNotify();
        Case.removeEventTypeSubscriber(LISTENING_EVENTS, pcl);
    }

    /**
     * Creates keys for the top level children.
     *
     * @param list list of keys created
     *
     * @return true, indicating that the key list is complete
     */
    @Override
    protected boolean createKeys(List<Object> list) {
        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            if (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
                PersonManager personManager = tskCase.getPersonManager();
                List<Person> persons = personManager.getPersons();
                // show persons level if there are persons to be shown
                if (!CollectionUtils.isEmpty(persons)) {
                    persons.stream()
                            .map(PersonGrouping::new)
                            .sorted()
                            .forEach(list::add);

                    if (CollectionUtils.isNotEmpty(personManager.getHostsForPerson(null))) {
                        list.add(new PersonGrouping(null));
                    }

                    return true;
                } else {
                    // otherwise, just show host level
                    tskCase.getHostManager().getAllHosts().stream()
                            .map(HostGrouping::new)
                            .sorted()
                            .forEach(list::add);
                    return true;
                }
            } else {
                // data source by type view
                List<AutopsyVisitableItem> keys = new ArrayList<>(Arrays.asList(
                        new DataSourcesByType(),
                        new Views(Case.getCurrentCaseThrows().getSleuthkitCase()),
                        new DataArtifacts(),
                        new AnalysisResults(),
                        new OsAccounts(Case.getCurrentCaseThrows().getSleuthkitCase()),
                        new Tags(),
                        new Reports()
                ));

                list.addAll(keys);
            }
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Exception while getting data from case.", ex); //NON-NLS
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
}
