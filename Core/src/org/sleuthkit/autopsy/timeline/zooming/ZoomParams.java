/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-18 Basis Technology Corp.
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
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.DefaultFilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;
import org.sleuthkit.datamodel.DescriptionLoD;
import org.sleuthkit.datamodel.timeline.EventTypeZoomLevel;

/**
 * This class encapsulates all the zoom(and filter) parameters into one object
 * for passing around and as a memento of the zoom/filter state.
 */
final public class ZoomParams {

    private final Interval timeRange;

    private final EventTypeZoomLevel typeZoomLevel;

    private final RootFilterState filter;

    private final DescriptionLoD descrLOD;

    public Interval getTimeRange() {
        return timeRange;
    }

    public EventTypeZoomLevel getTypeZoomLevel() {
        return typeZoomLevel;
    }

    public RootFilterState getFilterState() {
        return filter;
    }

    public DescriptionLoD getDescriptionLOD() {
        return descrLOD;
    }

    public ZoomParams(Interval timeRange, EventTypeZoomLevel zoomLevel, RootFilterState filter, DescriptionLoD descrLOD) {
        this.timeRange = timeRange;
        this.typeZoomLevel = zoomLevel;
        this.filter = filter;
        this.descrLOD = descrLOD;
    }

    public ZoomParams withTimeAndType(Interval timeRange, EventTypeZoomLevel zoomLevel) {
        return new ZoomParams(timeRange, zoomLevel, filter, descrLOD);
    }

    public ZoomParams withTypeZoomLevel(EventTypeZoomLevel zoomLevel) {
        return new ZoomParams(timeRange, zoomLevel, filter, descrLOD);
    }

    public ZoomParams withTimeRange(Interval timeRange) {
        return new ZoomParams(timeRange, typeZoomLevel, filter, descrLOD);
    }

    public ZoomParams withDescrLOD(DescriptionLoD descrLOD) {
        return new ZoomParams(timeRange, typeZoomLevel, filter, descrLOD);
    }

    public ZoomParams withFilterState(RootFilterState filter) {
        return new ZoomParams(timeRange, typeZoomLevel, filter, descrLOD);
    }

    public boolean hasFilterState(RootFilterState filterSet) {
        return this.filter.equals(filterSet);
    }

    public boolean hasTypeZoomLevel(EventTypeZoomLevel typeZoom) {
        return this.typeZoomLevel.equals(typeZoom);
    }

    public boolean hasTimeRange(Interval timeRange) {
        return this.timeRange != null && this.timeRange.equals(timeRange);
    }

    public boolean hasDescrLOD(DescriptionLoD newLOD) {
        return this.descrLOD.equals(newLOD);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.timeRange.getStartMillis());
        hash = 97 * hash + Objects.hashCode(this.timeRange.getEndMillis());
        hash = 97 * hash + Objects.hashCode(this.typeZoomLevel);
        hash = 97 * hash + Objects.hashCode(this.filter);
        hash = 97 * hash + Objects.hashCode(this.descrLOD);

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
        final ZoomParams other = (ZoomParams) obj;
        if (!Objects.equals(this.timeRange, other.timeRange)) {
            return false;
        }
        if (this.typeZoomLevel != other.typeZoomLevel) {
            return false;
        }
        if (this.filter.equals(other.filter) == false) {
            return false;
        }
        return this.descrLOD == other.descrLOD;
    }

    @Override
    public String toString() {
        return "ZoomParams{" + "timeRange=" + timeRange + ", typeZoomLevel=" + typeZoomLevel + ", filter=" + filter + ", descrLOD=" + descrLOD + '}'; //NON-NLS
    }
}
