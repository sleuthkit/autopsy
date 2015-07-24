/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.filters.Filter;

/**
 * This class encapsulates all the zoom(and filter) parameters into one object
 * for passing around and as a memento of the zoom/filter state.
 */
public class ZoomParams {

    private final Interval timeRange;

    private final EventTypeZoomLevel typeZoomLevel;

    private final Filter filter;

    private final DescriptionLOD descrLOD;

    private final Set<Field> changedFields;

    public Set<Field> getChangedFields() {
        return Collections.unmodifiableSet(changedFields);
    }

    public enum Field {

        TIME, EVENT_TYPE_ZOOM, FILTER, DESCRIPTION_LOD;
    }

    public Interval getTimeRange() {
        return timeRange;
    }

    public EventTypeZoomLevel getTypeZoomLevel() {
        return typeZoomLevel;
    }

    public Filter getFilter() {
        return filter;
    }

    public DescriptionLOD getDescrLOD() {
        return descrLOD;
    }

    public ZoomParams(Interval timeRange, EventTypeZoomLevel zoomLevel, Filter filter, DescriptionLOD descrLOD) {
        this.timeRange = timeRange;
        this.typeZoomLevel = zoomLevel;
        this.filter = filter;
        this.descrLOD = descrLOD;
        changedFields = EnumSet.allOf(Field.class);
    }

    public ZoomParams(Interval timeRange, EventTypeZoomLevel zoomLevel, Filter filter, DescriptionLOD descrLOD, EnumSet<Field> changed) {
        this.timeRange = timeRange;
        this.typeZoomLevel = zoomLevel;
        this.filter = filter;
        this.descrLOD = descrLOD;
        changedFields = changed;
    }

    public ZoomParams withTimeAndType(Interval timeRange, EventTypeZoomLevel zoomLevel) {
        return new ZoomParams(timeRange, zoomLevel, filter, descrLOD, EnumSet.of(Field.TIME, Field.EVENT_TYPE_ZOOM));
    }

    public ZoomParams withTypeZoomLevel(EventTypeZoomLevel zoomLevel) {
        return new ZoomParams(timeRange, zoomLevel, filter, descrLOD, EnumSet.of(Field.EVENT_TYPE_ZOOM));
    }

    public ZoomParams withTimeRange(Interval timeRange) {
        return new ZoomParams(timeRange, typeZoomLevel, filter, descrLOD, EnumSet.of(Field.TIME));
    }

    public ZoomParams withDescrLOD(DescriptionLOD descrLOD) {
        return new ZoomParams(timeRange, typeZoomLevel, filter, descrLOD, EnumSet.of(Field.DESCRIPTION_LOD));
    }

    public ZoomParams withFilter(Filter filter) {
        return new ZoomParams(timeRange, typeZoomLevel, filter, descrLOD, EnumSet.of(Field.FILTER));
    }

    public boolean hasFilter(Filter filterSet) {
        return this.filter.equals(filterSet);
    }

    public boolean hasTypeZoomLevel(EventTypeZoomLevel typeZoom) {
        return this.typeZoomLevel.equals(typeZoom);
    }

    public boolean hasTimeRange(Interval timeRange) {
        return this.timeRange == null ? false : this.timeRange.equals(timeRange);
    }

    public boolean hasDescrLOD(DescriptionLOD newLOD) {
        return this.descrLOD.equals(newLOD);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.timeRange.getStartMillis());
        hash = 97 * hash + Objects.hashCode(this.timeRange.getEndMillis());
        hash = 97 * hash + Objects.hashCode(this.typeZoomLevel);
        hash = 97 * hash + Objects.hashCode(this.filter.isSelected());
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
        if (this.descrLOD != other.descrLOD) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return NbBundle.getMessage(this.getClass(), "ZoomParams.toString", getTimeRange().toString());
    }

}
