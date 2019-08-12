/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-16 Basis Technology Corp.
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

import java.util.Set;

/**
 * A TagsUpdatedEvent for tags that have been removed from events.
 * NOTE: This event is internal to timeline components
 */
public class TagsDeletedEvent extends TagsUpdatedEvent {

    /**
     * Constructor
     *
     * @param updatedEventIDs The event IDs of the events that have had tags
     *                        removed from them.
     */
    public TagsDeletedEvent(Set<Long> updatedEventIDs) {
        super(updatedEventIDs);
    }
}
