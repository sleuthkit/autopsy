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
package org.sleuthkit.autopsy.mainui.datamodel.events;

import java.util.Collection;
import java.util.Collections;

/**
 * A single event containing an aggregate of all affected data.
 */
public class DAOAggregateEvent {

    private final Collection<? extends DAOEvent> objects;

    /**
     * Main constructor.
     *
     * @param objects The list of events in this aggregate event.
     */
    public DAOAggregateEvent(Collection<? extends DAOEvent> objects) {
        this.objects = Collections.unmodifiableCollection(objects);
    }

    /**
     * @return The events in this aggregate event.
     */
    public Collection<? extends DAOEvent> getEvents() {
        return objects;
    }
}
