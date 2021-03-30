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

import java.util.Objects;
import org.sleuthkit.datamodel.Person;

/**
 * A top level UI grouping of hosts under a person.
 */
public class PersonGrouping implements AutopsyVisitableItem, Comparable<PersonGrouping> {

    private final Person person;

    /**
     * Main constructor.
     *
     * @param person The person to be represented.
     */
    PersonGrouping(Person person) {

        this.person = person;
    }

    /**
     * @return The person to be represented.
     */
    Person getPerson() {
        return person;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.person == null ? 0 : this.person.getPersonId());
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
        long thisId = (this.getPerson() == null) ? 0 : this.getPerson().getPersonId();
        long otherId = (other.getPerson() == null) ? 0 : other.getPerson().getPersonId();
        return thisId == otherId;
    }

    /* 
     * Compares two person groupings to be displayed in a list of children under
     * the root of the tree.
     */
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

}
