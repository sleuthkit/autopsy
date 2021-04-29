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
import java.util.List;
import java.util.Optional;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.PersonManager;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Base event class for when something pertaining to persons changes.
 */
public class PersonsEvent extends TskDataModelChangedEvent<Person> {

    private static final long serialVersionUID = 1L;
        
    /**
     * Main constructor.
     *
     * @param eventName The name of the Case.Events enum value for the event
     * type.
     * @param dataModelObjects The list of persons for the event.
     */
    PersonsEvent(String eventName, List<Person> dataModelObjects) {
        super(eventName, dataModelObjects, Person::getPersonId);
    }
    
    @Override
    protected List<Person> getDataModelObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        PersonManager personManager = caseDb.getPersonManager();
        List<Person> toRet = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id == null) {
                    continue;
                }

                Optional<Person> thisPersonOpt = personManager.getPerson(id);
                thisPersonOpt.ifPresent((h) -> toRet.add(h));
            }
        }

        return toRet;
    }

}
