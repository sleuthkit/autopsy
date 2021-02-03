/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2021 Basis Technology Corp.
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.HostManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A node to be displayed in the UI tree for a person and persons grouped in
 * this host.
 */
@NbBundle.Messages(value = {"PersonNode_unknownHostNode_title=Unknown Persons"})
class PersonGroupingNode extends DisplayableItemNode {
    // stub class until this goes into TSK datamodel.
    static class PersonManager {
        Set<Host> getHostsForPerson(Person person) throws TskCoreException {
            return Collections.emptySet();
        }
        
        Set<Person> getPersons() throws TskCoreException {
            return Collections.emptySet();
        }
    }
    
    // stub class until this goes into TSK datamodel.
    static class Person {

        private final String name;
        private final long id;

        public Person(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public long getId() {
            return id;
        }
    }

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/person.png";

    private static class PersonChildren extends ChildFactory.Detachable<Host> {

        private static final Logger logger = Logger.getLogger(PersonChildren.class.getName());

        private final Person person;
        private final PersonManager personManager;
        private final HostManager hostManager;
        
        private boolean hasChildren = false;

        PersonChildren(PersonManager personManager, HostManager hostManager, Person person) {
            this.person = person;
            this.personManager = personManager;
            this.hostManager = hostManager;
        }

        /**
         * Listener for handling DATA_SOURCE_ADDED events.
         */
        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())
                        || eventType.equals(Case.Events.DATA_SOURCE_DELETED.toString())) {
                    refresh(true);
                }
            }
        };

        @Override
        protected void addNotify() {
            super.addNotify();
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_DELETED), pcl);
        }

        @Override
        protected void removeNotify() {
            super.removeNotify();
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_DELETED), pcl);
        }

        protected boolean hasChildren() {
            return hasChildren;
        }

        @Override
        protected boolean createKeys(List<Host> toPopulate) {
            Set<Host> hosts = null;
            try {
                hosts = this.personManager.getHostsForPerson(person);
            } catch (TskCoreException ex) {
                String personName = person == null || person.getName() == null ? "<unknown>" : person.getName();
                logger.log(Level.WARNING, String.format("Unable to get data sources for host: %s", personName), ex);
            }

            if (hosts != null) {
                toPopulate.addAll(hosts);
            }

            return true;
        }

        @Override
        protected HostGroupingNode createNodeForKey(Host key) {
            return key == null ? null : new HostGroupingNode(hostManager, key);
        }
    }

    private final PersonChildren personChildren;

    PersonGroupingNode(PersonManager personManager, HostManager hostManager, Person person) {
        this(person, new PersonChildren(personManager, hostManager, person));
    }

    private PersonGroupingNode(Person person, PersonChildren personChildren) {
        super(Children.create(personChildren, false), person == null ? null : Lookups.singleton(person));

        String safeName = (person == null || person.getName() == null)
                ? Bundle.HostNode_unknownHostNode_title()
                : person.getName();

        super.setName(safeName);
        super.setDisplayName(safeName);
        this.setIconBaseWithExtension(ICON_PATH);
        this.personChildren = personChildren;
    }

    @Override
    public boolean isLeafTypeNode() {
        return !this.personChildren.hasChildren();
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
