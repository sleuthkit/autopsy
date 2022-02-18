/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.beans.PropertyChangeEvent;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Dao for hosts.
 */
public class HostPersonDAO extends AbstractDAO {

    private static HostPersonDAO instance = null;

    public static HostPersonDAO getInstance() {
        if (instance == null) {
            instance = new HostPersonDAO();
        }
        return instance;
    }

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    public TreeResultsDTO<HostSearchParams> getAllHosts() throws ExecutionException {
        try {
            return new TreeResultsDTO<>(getCase().getHostManager().getAllHosts().stream()
                    .map(h -> createHostTreeItem(h))
                    .collect(Collectors.toList()));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            throw new ExecutionException("Error while fetching all hosts.", ex);
        }
    }

    public TreeResultsDTO<HostSearchParams> getHosts(Person person) throws ExecutionException {
        try {
            return new TreeResultsDTO<>(getCase().getPersonManager().getHostsForPerson(person).stream()
                    .map(h -> createHostTreeItem(h))
                    .collect(Collectors.toList()));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            throw new ExecutionException("Error while fetching host for person: " + (person == null ? "<null>" : person.getName() + " id: " + person.getPersonId()), ex);
        }
    }

    public TreeResultsDTO<PersonSearchParams> getAllPersons() throws ExecutionException {
        try {
            List<Person> persons = getCase().getPersonManager().getPersons();
            Stream<Person> personStream
                    = getCase().getPersonManager().getHostsWithoutPersons().isEmpty()
                    ? persons.stream()
                    : Stream.concat(persons.stream(), Stream.of(null));

            return new TreeResultsDTO<>(personStream.map(p -> createPersonTreeItem(p)).collect(Collectors.toList()));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            throw new ExecutionException("Error while fetching all hosts.", ex);
        }
    }

    private TreeItemDTO<HostSearchParams> createHostTreeItem(Host host) {
        return new TreeItemDTO<>(
                HostSearchParams.getTypeId(),
                new HostSearchParams(host),
                host.getHostId(),
                host.getName(),
                TreeDisplayCount.NOT_SHOWN);
    }

    private TreeItemDTO<PersonSearchParams> createPersonTreeItem(Person person) {
        return new TreeItemDTO<>(
                HostSearchParams.getTypeId(),
                new PersonSearchParams(person),
                person.getPersonId(),
                person.getName(),
                TreeDisplayCount.NOT_SHOWN);
    }
    
    @Override
    Set<? extends DAOEvent> processEvent(PropertyChangeEvent evt) {
        String eventName = evt.getPropertyName();
        if (Case.Events.PERSONS_ADDED.toString().equals(eventName)) {
            
        } else if (Case.Events.PERSONS_DELETED.toString().equals(eventName)) {
            
        } else if (Case.Events.PERSONS_UPDATED.toString().equals(eventName)) {
            
        } else if (Case.Events.HOSTS_ADDED.toString().equals(eventName)) {
            
        } else if (Case.Events.HOSTS_ADDED_TO_PERSON.toString().equals(eventName)) {
            
        } else if (Case.Events.HOSTS_DELETED.toString().equals(eventName)) {
            
        } else if (Case.Events.HOSTS_REMOVED_FROM_PERSON.toString().equals(eventName)) {
            
        } else if (Case.Events.HOSTS_UPDATED.toString().equals(eventName)) {
            
        }
//                if (CaseEvent.DATA_ADDED.toString().equals(eventName)
//                && (evt.getOldValue() instanceof ModuleDataEvent)) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    void clearCaches() {
    }

    @Override
    Set<? extends DAOEvent> handleIngestComplete() {
        return Collections.emptySet();
    }

    @Override
    Set<? extends TreeEvent> shouldRefreshTree() {
        return Collections.emptySet();
    }
}
