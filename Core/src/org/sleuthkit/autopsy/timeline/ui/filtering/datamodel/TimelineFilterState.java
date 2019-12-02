/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import org.sleuthkit.datamodel.TimelineFilter;

/**
 * A generic implementation of the FilterState interface that decorates a
 * TimelineFilter object for display via the timeline filter panel by providing
 * selected, disabled, and active properties for the object via the
 * AbstractFilterState base class.
 *
 * @param <FilterType> A concrete TimelineFilter type.
 */
public class TimelineFilterState<FilterType extends TimelineFilter> extends AbstractFilterState<FilterType> {

    /**
     * Constucts a generic implementation of the FilterState interface that
     * decorates a TimelineFilter object for display via the timeline filter
     * panel by providing selected, disabled, and active properties for the
     * object via the AbstractFilterState base class.
     *
     * @param filter The TimelineFilter object to be decorated. 
     */
    public TimelineFilterState(FilterType filter) {
        this(filter, false);
    }

    /**
     * Disabled = false
     *
     * @param filter
     * @param selected True to select this filter initially.
     */
    public TimelineFilterState(FilterType filter, boolean selected) {
        super(filter, selected);
    }

    @Override
    public String getDisplayName() {
        return getFilter().getDisplayName();
    }

    @Override
    public TimelineFilterState<FilterType> copyOf() {
        @SuppressWarnings("unchecked")
        TimelineFilterState<FilterType> copy = new TimelineFilterState<>((FilterType) getFilter().copyOf());
        copy.setSelected(isSelected());
        copy.setDisabled(isDisabled());
        return copy;
    }

    @Override
    public String toString() {
        return "TimelineFilterState{"
                + " filter=" + getFilter().toString()
                + ", selected=" + isSelected()
                + ", disabled=" + isDisabled()
                + ", activeProp=" + isActive() + '}'; //NON-NLS
    }
}
