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
import java.util.stream.Collectors;
import javax.swing.Action;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.PersonsUpdatedEvent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.persons.DeletePersonAction;
import org.sleuthkit.autopsy.datamodel.persons.EditPersonAction;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A main tree view node that represents a person in a case. Its child nodes, if
 * any, represent hosts in the case. There must be at least one person in a case
 * for the person nodes layer to appear. If the persons layer is present, any
 * hosts that are not associated with a person are grouped under an "Unknown
 * Persons" person node.
 */
@NbBundle.Messages(value = {"PersonNode_unknownPersonNode_title=Unknown Persons"})
public class PersonNode extends DisplayableItemNode {

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/person.png";

    /**
     * Returns the id of an unknown persons node. This can be used with a node
     * lookup.
     *
     * @return The id of an unknown persons node.
     */
    public static String getUnknownPersonId() {
        return Bundle.PersonNode_unknownPersonNode_title();
    }

    /**
     * Responsible for creating the host children of this person.
     */
    private static class PersonChildren extends ChildFactory.Detachable<HostGrouping> {

        private static final Logger logger = Logger.getLogger(PersonChildren.class.getName());

        private static final Set<Case.Events> HOST_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.HOSTS_ADDED,
                Case.Events.HOSTS_ADDED,
                Case.Events.HOSTS_DELETED,
                Case.Events.HOSTS_ADDED_TO_PERSON,
                Case.Events.HOSTS_REMOVED_FROM_PERSON);

        private static final Set<String> HOST_EVENTS_OF_INTEREST_NAMES = HOST_EVENTS_OF_INTEREST.stream()
                .map(ev -> ev.name())
                .collect(Collectors.toSet());

        private final Person person;

        /**
         * Main constructor.
         *
         * @param person The person record.
         */
        PersonChildren(Person person) {
            this.person = person;
            
        }

        /**
         * Listener for application events that are published when hosts are
         * added to or deleted from a case, and for events published when the
         * associations between persons and hosts change. If the user has
         * selected the group by person/host option for the main tree view,
         * these events mean that person nodes in the tree need to be refreshed
         * to reflect the structural changes.
         */
        private final PropertyChangeListener hostAddedDeletedPcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType != null && HOST_EVENTS_OF_INTEREST_NAMES.contains(eventType)) {
                    refresh(true);
                }
            }
        };
        
        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(hostAddedDeletedPcl, null);

        @Override
        protected void addNotify() {
            Case.addEventTypeSubscriber(HOST_EVENTS_OF_INTEREST, weakPcl);
        }
        
        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            Case.removeEventTypeSubscriber(HOST_EVENTS_OF_INTEREST, weakPcl);
        }
        
        @Override
        protected HostNode createNodeForKey(HostGrouping key) {
            return key == null ? null : new HostNode(key);
        }

        @Override
        protected boolean createKeys(List<HostGrouping> toPopulate) {
            List<Host> hosts = Collections.emptyList();
            try {
                if (person != null) {
                    hosts = Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().getHostsForPerson(person);
                } else {
                    // This is the "Unknown Persons" node, get the hosts that are not associated with a person.
                    hosts = Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().getHostsWithoutPersons();
                }
            } catch (NoCurrentCaseException | TskCoreException ex) {
                String personName = person == null || person.getName() == null ? "<unknown>" : person.getName();
                logger.log(Level.WARNING, String.format("Unable to get data sources for host: %s", personName), ex);
            }

            toPopulate.addAll(hosts.stream()
                    .map(HostGrouping::new)
                    .sorted()
                    .collect(Collectors.toList()));

            return true;
        }
    }

    private final Person person;
    private final Long personId;

    /**
     * Listener for application events that are published when the properties of
     * persons in the case change.
     */
    private final PropertyChangeListener personChangePcl = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (personId != null && eventType.equals(Case.Events.PERSONS_UPDATED.toString()) && evt instanceof PersonsUpdatedEvent) {
                ((PersonsUpdatedEvent) evt).getNewValue().stream()
                        .filter(p -> p != null && p.getPersonId() == personId)
                        .findFirst()
                        .ifPresent((newPerson) -> {
                            setName(newPerson.getName());
                            setDisplayName(newPerson.getName());
                        });
            }
        }
    };
    
    private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(personChangePcl, null);

    /**
     * Gets the display name for this person or "Unknown Persons".
     *
     * @param person The person.
     *
     * @return The non-empty string for the display name.
     */
    private static String getDisplayName(Person person) {
        return (person == null || person.getName() == null)
                ? getUnknownPersonId()
                : person.getName();
    }

    /**
     * Main constructor.
     *
     * @param person The person record to be represented.
     */
    PersonNode(Person person) {
        this(person, getDisplayName(person));
    }

    /**
     * Constructor.
     *
     * @param person      The person.
     * @param displayName The display name for the person.
     */
    private PersonNode(Person person, String displayName) {
        super(Children.create(new PersonChildren(person), true),
                person == null ? Lookups.fixed(displayName) : Lookups.fixed(person, displayName));
        super.setName(displayName);
        super.setDisplayName(displayName);
        this.setIconBaseWithExtension(ICON_PATH);
        this.person = person;
        this.personId = person == null ? null : person.getPersonId();
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.PERSONS_UPDATED), weakPcl);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @NbBundle.Messages({
        "PersonGroupingNode_createSheet_nameProperty=Name",})
    @Override
    protected Sheet createSheet() {
        Sheet sheet = Sheet.createDefault();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>("Name", Bundle.PersonGroupingNode_createSheet_nameProperty(), "", getDisplayName())); //NON-NLS

        return sheet;
    }

    @Override
    @Messages({"PersonGroupingNode_actions_rename=Rename Person...",
        "PersonGroupingNode_actions_delete=Delete Person"})
    public Action[] getActions(boolean context) {
        if (this.person == null) {
            return new Action[0];
        } else {
            return new Action[]{
                new EditPersonAction(this.person),
                new DeletePersonAction(this.person),
                null
            };
        }
    }
}
