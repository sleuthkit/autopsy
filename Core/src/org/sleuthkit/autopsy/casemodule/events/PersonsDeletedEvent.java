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

import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An event fired when persons are deleted from the case.
 */
public class PersonsDeletedEvent extends TskDataModelChangedEvent<Person> {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an event fired when persons are deleted from the case.
     *
     * @param dataModelObjectIds The unique numeric IDs (case database row IDs)
     *                           of the persons that have been deleted.
     */
    public PersonsDeletedEvent(List<Long> dataModelObjectIds) {
        super(Case.Events.PERSONS_DELETED.name(), dataModelObjectIds);
    }

    @Override
    protected List<Person> getDataModelObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        return Collections.emptyList();
    }

}
