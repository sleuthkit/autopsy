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
package org.sleuthkit.autopsy.casemodule.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Application events published when one or more hosts have been added to a
 * person.
 */
public final class HostsAddedToPersonEvent extends TskDataModelChangedEvent<Person, Host> {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an application event published when one or more hosts have
     * been added to a person.
     *
     * @param person The person.
     * @param hosts  The hosts.
     */
    public HostsAddedToPersonEvent(Person person, List<Host> hosts) {
        super(Case.Events.HOSTS_ADDED_TO_PERSON.toString(), Collections.singletonList(person), Person::getPersonId, hosts, Host::getHostId);
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
