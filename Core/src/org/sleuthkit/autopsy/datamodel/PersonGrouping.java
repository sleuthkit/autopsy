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

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The data for a person and hosts grouped in this person.
 */
public class PersonGrouping implements AutopsyVisitableItem, Comparable<PersonGrouping> {

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

    private final Person person;
    private final Set<HostGrouping> hosts;

    /**
     * Main constructor.
     *
     * @param person The person object.
     * @param hosts The hosts to display under this host.
     */
    PersonGrouping(Person person, Set<HostGrouping> hosts) {
        this.person = person;
        this.hosts = Collections.unmodifiableSet(new HashSet<>(hosts));
    }

    /**
     * @return The associated person object.
     */
    Person getPerson() {
        return person;
    }

    /**
     * @return The hosts to be displayed as children under this person.
     */
    Set<HostGrouping> getHosts() {
        return hosts;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public int compareTo(PersonGrouping o) {
        String thisPerson = this.getPerson() == null ? null : this.getPerson().getName();
        String otherPerson = o == null || o.getPerson() == null ? null : o.getPerson().getName();

        // push unknown host to bottom
        if (thisPerson == null && otherPerson == null) {
            return 0;
        } else if (thisPerson == null) {
            return 1;
        } else if (otherPerson == null) {
            return -1;
        }

        return thisPerson.compareToIgnoreCase(otherPerson);
    }
    
    @Override
    public int hashCode() {
        int hash = 7;
        Long thisId = this.person == null ? null : this.person.getId();
        hash = 97 * hash + Objects.hashCode(thisId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PersonGrouping other = (PersonGrouping) obj;
        Long thisId = this.person == null ? null : this.person.getId();
        Long otherId = other.person == null ? null : other.person.getId();
        if (!Objects.equals(thisId, otherId)) {
            return false;
        }
        return true;
    }
}
