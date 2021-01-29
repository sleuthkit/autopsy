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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * The data for a person and hosts grouped in this person.
 */
public class PersonGrouping implements AutopsyVisitableItem, Comparator<PersonGrouping> {

    // stub class until this goes into TSK datamodel.
    static class Person {

        private final String name;

        public Person(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
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
    public int compare(PersonGrouping a, PersonGrouping b) {
        String personA = a == null || a.getPerson() == null ? null : a.getPerson().getName();
        String personB = b == null || b.getPerson() == null ? null : b.getPerson().getName();

        // push unknown host to bottom
        if (personA == null && personB == null) {
            return 0;
        } else if (personA == null) {
            return 1;
        } else if (personB == null) {
            return -1;
        }

        return personA.compareToIgnoreCase(personB);
    }
}
