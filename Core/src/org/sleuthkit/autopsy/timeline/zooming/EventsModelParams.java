/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.zooming;

import java.util.Objects;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;

/**
 * A container that bundles the user-specified parameters for the events model
 * so that they can be passed around and saved as mementos to support a
 * navigable (forwards-backwards) history feature for the events model.
 */
final public class EventsModelParams {

    private final Interval timeRange;
    private final TimelineEventType.HierarchyLevel eventTypesHierarchyLevel;
    private final RootFilterState eventFilterState;
    private final TimelineLevelOfDetail timelineLOD;

    public EventsModelParams(Interval timeRange, TimelineEventType.HierarchyLevel eventTypesHierarchyLevel, RootFilterState eventFilterState, TimelineLevelOfDetail timelineLOD) {
        this.timeRange = timeRange;
        this.eventTypesHierarchyLevel = eventTypesHierarchyLevel;
        this.eventFilterState = eventFilterState;
        this.timelineLOD = timelineLOD;
    }

    public Interval getTimeRange() {
        return timeRange;
    }

    public TimelineEventType.HierarchyLevel getEventTypesHierarchyLevel() {
        return eventTypesHierarchyLevel;
    }

    public RootFilterState getEventFilterState() {
        return eventFilterState;
    }

    public TimelineLevelOfDetail getTimelineLOD() {
        return timelineLOD;
    }

    public EventsModelParams withTimeAndType(Interval timeRange, TimelineEventType.HierarchyLevel zoomLevel) {
        return new EventsModelParams(timeRange, zoomLevel, eventFilterState, timelineLOD);
    }

    public EventsModelParams withTypeZoomLevel(TimelineEventType.HierarchyLevel zoomLevel) {
        return new EventsModelParams(timeRange, zoomLevel, eventFilterState, timelineLOD);
    }

    public EventsModelParams withTimeRange(Interval timeRange) {
        return new EventsModelParams(timeRange, eventTypesHierarchyLevel, eventFilterState, timelineLOD);
    }

    public EventsModelParams withDescrLOD(TimelineLevelOfDetail descrLOD) {
        return new EventsModelParams(timeRange, eventTypesHierarchyLevel, eventFilterState, descrLOD);
    }

    public EventsModelParams withFilterState(RootFilterState filter) {
        return new EventsModelParams(timeRange, eventTypesHierarchyLevel, filter, timelineLOD);
    }

    public boolean hasFilterState(RootFilterState filterSet) {
        return this.eventFilterState.equals(filterSet);
    }

    public boolean hasTypeZoomLevel(TimelineEventType.HierarchyLevel typeZoom) {
        return this.eventTypesHierarchyLevel.equals(typeZoom);
    }

    public boolean hasTimeRange(Interval timeRange) {
        return this.timeRange != null && this.timeRange.equals(timeRange);
    }

    public boolean hasDescrLOD(TimelineLevelOfDetail newLOD) {
        return this.timelineLOD.equals(newLOD);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.timeRange.getStartMillis());
        hash = 97 * hash + Objects.hashCode(this.timeRange.getEndMillis());
        hash = 97 * hash + Objects.hashCode(this.eventTypesHierarchyLevel);
        hash = 97 * hash + Objects.hashCode(this.eventFilterState);
        hash = 97 * hash + Objects.hashCode(this.timelineLOD);

        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final EventsModelParams other = (EventsModelParams) obj;
        if (!Objects.equals(this.timeRange, other.getTimeRange())) {
            return false;
        }
        if (this.eventTypesHierarchyLevel != other.getEventTypesHierarchyLevel()) {
            return false;
        }
        if (this.eventFilterState.equals(other.getEventFilterState()) == false) {
            return false;
        }
        return this.timelineLOD == other.getTimelineLOD();
    }

    @Override
    public String toString() {
        return "ZoomState{" + "timeRange=" + timeRange + ", typeZoomLevel=" + eventTypesHierarchyLevel + ", filter=" + eventFilterState.getActiveFilter().toString() + ", descrLOD=" + timelineLOD + '}'; //NON-NLS
    }
}
