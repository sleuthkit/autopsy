/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.events;

import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 *
 */
public class EventAddedEvent extends AutopsyEvent {

    private final Long eventID;

    
    public EventAddedEvent(org.sleuthkit.datamodel.TimelineManager.EventAddedEvent event) {
        super(Case.Events.EVENT_ADDED.name(), null, event.getEvent());
        eventID = event.getEvent().getEventID();
    }

    public Long getEventID() {
        return eventID;
    }
}
