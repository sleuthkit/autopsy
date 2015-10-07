/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.datamodel;

import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

/**
 *
 */
public interface EventBundle<T extends EventBundle<?>> {

    String getDescription();

    DescriptionLoD getDescriptionLoD();

    Set<Long> getEventIDs();

    Set<Long> getEventIDsWithHashHits();

    Set<Long> getEventIDsWithTags();

    EventType getEventType();

    long getEndMillis();

    long getStartMillis();

    Optional<T> getParentBundle();

    default long getCount() {
        return getEventIDs().size();
    }

    SortedSet<EventCluster> getClusters();
}
