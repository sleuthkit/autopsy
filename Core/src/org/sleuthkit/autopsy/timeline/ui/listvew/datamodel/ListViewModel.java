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
package org.sleuthkit.autopsy.timeline.ui.listvew.datamodel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import static java.util.stream.Collectors.groupingBy;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TimelineEvent;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;

/**
 * Model for the ListView. Uses FilteredEventsModel as underlying datamodel and
 * supplies abstractions / data objects specific to the ListView.
 *
 */
public class ListViewModel {

    private final EventsModel eventsModel;
    private final TimelineManager eventManager;

    public ListViewModel(EventsModel eventsModel) {
        this.eventsModel = eventsModel;
        this.eventManager = eventsModel.getEventManager();
    }

    /**
     * Get a representation of all the events, within the given time range, that
     * pass the given filter, grouped by time and description such that file
     * system events for the same file, with the same timestamp, are combined
     * together.
     *
     * @return A List of combined events, sorted by timestamp.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<CombinedEvent> getCombinedEvents() throws TskCoreException {
        return getCombinedEvents(eventsModel.getTimeRange(), eventsModel.getEventFilterState());
    }

    /**
     * Get a representation of all the events, within the given time range, that
     * pass the given filter, grouped by time and description such that file
     * system events for the same file, with the same timestamp, are combined
     * together.
     *
     * @param timeRange   The Interval that all returned events must be within.
     * @param filterState The Filter that all returned events must pass.
     *
     * @return A List of combined events, sorted by timestamp.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    
    public List<CombinedEvent> getCombinedEvents(Interval timeRange, RootFilterState filterState) throws TskCoreException {
        List<TimelineEvent> events = eventManager.getEvents(timeRange, filterState.getActiveFilter());
        
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        
        ArrayList<CombinedEvent> combinedEvents = new ArrayList<>();
        
        Map<CombinedEventGroup, List<TimelineEvent>> groupedEventList = events.stream().collect(groupingBy(event -> new CombinedEventGroup(event.getTime(), event.getContentObjID(), event.getDescription(TimelineLevelOfDetail.HIGH))));
        
        for(Entry<CombinedEventGroup, List<TimelineEvent>> entry: groupedEventList.entrySet()){
            List<TimelineEvent> groupedEvents = entry.getValue();
            CombinedEventGroup group = entry.getKey();
            
            Map<TimelineEventType, Long> eventMap = new HashMap<>();
            for(TimelineEvent event: groupedEvents) {
                eventMap.put(event.getEventType(), event.getEventID());
            }
            
            // We want to merge together file sub-type events that are at 
            //the same time, but create individual events for other event
            // sub-types
            if (hasFileTypeEvents(eventMap.keySet()) || eventMap.size() == 1) {
                 combinedEvents.add(new CombinedEvent(group.getTime() * 1000,   eventMap));
            } else {
                for(Entry<TimelineEventType, Long> singleEntry: eventMap.entrySet()) {
                     Map<TimelineEventType, Long> singleEventMap = new HashMap<>();
                     singleEventMap.put(singleEntry.getKey(), singleEntry.getValue());
                     combinedEvents.add(new CombinedEvent(group.getTime() * 1000,   singleEventMap));
                }
            }
        }
        
        Collections.sort(combinedEvents, new SortEventByTime());
        
        return combinedEvents;
    }
    
    private boolean hasFileTypeEvents(Collection<TimelineEventType> eventTypes) {
        for (TimelineEventType type: eventTypes) {
            if (type.getCategory() != TimelineEventType.FILE_SYSTEM) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * This class for use with the groupingBy and stores the three parameters
     * that the Events are grouped by to make "CombinedEvents".
     */
    final class CombinedEventGroup {
        private final String description;
        private final long time;
        private final long fileID;
        
        /**
         * Construct a new CombinedEventGroup.
         * 
         * @param time
         * @param fileID
         * @param description 
         */
        CombinedEventGroup(long time, long fileID, String description) {
            this.description = description;
            this.time = time;
            this.fileID = fileID;
        }
        
        /**
         * Get the value of the time parameter.
         * 
         * @return long time value
         */
        long getTime() {
            return time;
        }
        
        @Override
        public boolean equals (Object obj) {
            if ( !(obj instanceof CombinedEventGroup)) {
                return false;
            }
            
            CombinedEventGroup group = (CombinedEventGroup)obj;
            
            return description.equals(group.description) 
                    && time == group.time 
                    && fileID == group.fileID;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 31 * hash + Objects.hashCode(this.description);
            hash = 31 * hash + (int) (this.time ^ (this.time >>> 32));
            hash = 31 * hash + (int) (this.fileID ^ (this.fileID >>> 32));
            return hash;
        }
        
    }
    
    /**
     * A Comparator for sorting CombinedEvents by start time.
     */
    class SortEventByTime implements Comparator<CombinedEvent> {

        @Override
        public int compare(CombinedEvent o1, CombinedEvent o2) {
            return Long.compare(o1.getStartMillis(), o2.getStartMillis());
        }
        
    }
}
