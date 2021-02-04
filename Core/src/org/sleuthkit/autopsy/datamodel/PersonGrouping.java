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
import org.sleuthkit.autopsy.datamodel.PersonGroupingNode.Person;
import org.sleuthkit.autopsy.datamodel.PersonGroupingNode.PersonManager;
import org.sleuthkit.datamodel.HostManager;

/**
 * A top level UI grouping of hosts under a person.
 */
public class PersonGrouping implements AutopsyVisitableItem {

    private final PersonManager personManager;
    private final HostManager hostManager;
    private final Person person;

    /**
     * Main constructor.
     * @param personManager The person manager for the case.
     * @param hostManager The host manager for the case.
     * @param person The person to be represented.
     */
    PersonGrouping(PersonManager personManager, HostManager hostManager, Person person) {
        this.personManager = personManager;
        this.hostManager = hostManager;
        this.person = person;
    }
    
    /**
     * @return The person manager for the case.
     */
    PersonManager getPersonManager() {
        return personManager;
    }
    
    /**
     * @return The host manager for the case.
     */
    HostManager getHostManager() {
        return hostManager;
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
        return Objects.hashCode(this.person == null ? 0 : this.person.getId());
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
        long thisId = (this.getPerson() == null) ? 0 : this.getPerson().getId();
        long otherId = (other.getPerson() == null) ? 0 : other.getPerson().getId();
        return thisId == otherId;
    }

}
