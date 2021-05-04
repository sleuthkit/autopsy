/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An abstract super class for person and host association change events.
 */
public abstract class PersonHostsEvent extends TskDataModelChangedEvent<Person, Host> {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs the abstract super class part of a person and host association
     * change event.
     *
     * @param eventName The name of the Case.Events enum value for the event
     *                  type.
     * @param person    The person that is the subject of the event.
     * @param hosts     The hosts that are the subject of the event.
     */
    PersonHostsEvent(String eventName, Person person, List<Host> hosts) {
        super(eventName, Collections.singletonList(person), Person::getPersonId, hosts, Host::getHostId);
    }

    /**
     * Gets the person.
     *
     * @return The person.
     */
    public Person getPerson() {
        return getOldValue().get(0);
    }

    /**
     * Gets the hosts.
     *
     * @return The hosts.
     */
    public List<Host> getHosts() {
        return getNewValue();
    }

    @Override
    protected List<Person> getOldValueObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        List<Person> persons = new ArrayList<>();
        for (Long id : ids) {
            Optional<Person> person = caseDb.getPersonManager().getPerson(id);
            if (person.isPresent()) {
                persons.add(person.get());
            }
        }
        return persons;
    }

    @Override
    protected List<Host> getNewValueObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        List<Host> hosts = new ArrayList<>();
        for (Long id : ids) {
            Optional<Host> host = caseDb.getHostManager().getHostById(id);
            if (host.isPresent()) {
                hosts.add(host.get());
            }
        }
        return hosts;
    }

}
