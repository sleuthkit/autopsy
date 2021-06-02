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
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * An abstract base class for application events published when one or more
 * Sleuth Kit Data Model objects for a case have been deleted.
 *
 * This class extends AutopsyEvent. The AutopsyEvent class extends
 * PropertyChangeEvent to integrate with legacy use of JavaBeans
 * PropertyChangeEvents and PropertyChangeListeners as an application event
 * publisher-subcriber mechanism. Subclasses need to decide what constitutes
 * "old" and "new" objects for them.
 *
 * For this class the "old" values are the unique numeric IDs (TSK object IDs,
 * case database row IDs, etc.) of the deleted TSK Data Model objects. There are
 * no "new" values. Subclasses are encouraged to provide less generic getters
 * with descriptive names for the unique IDs than the override of the inherited
 * getOldValue() method below. These getters can be implemented by delegating to
 * getOldValue().
 */
public class TskDataModelObjectsDeletedEvent extends AutopsyEvent {

    private static final long serialVersionUID = 1L;

    private final List<Long> deletedObjectIds;

    protected TskDataModelObjectsDeletedEvent(String eventName, List<Long> deletedObjectIds) {
        super(eventName, null, null);
        this.deletedObjectIds = new ArrayList<>();
        this.deletedObjectIds.addAll(deletedObjectIds);
    }

    @Override
    public List<Long> getOldValue() {
        return Collections.unmodifiableList(deletedObjectIds);
    }

}
