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
 * An implementation of the FilterState interface for wrapping TimelineFilter
 * objects. TimelineFilter objects provide SQL WHERE clauses for querying the
 * case database and are displayed in the timeline GUI via the filters panel.
 * Filter state objects provide selected, disabled, and active properties for
 * the wrapped filter.
 *
 * @param <FilterType> The type of the wrapped filter, required to be a
 *                     TimelineFilter type.
 */
public class SqlFilterState<FilterType extends TimelineFilter> extends AbstractFilterState<FilterType> {

    /**
     * Constructs an implementation of the FilterState interface for wrapping
     * TimelineFilter objects. TimelineFilter objects provide SQL WHERE clauses
     * for querying the case database and are displayed in the timeline GUI via
     * the filters panel. Filter state objects provide selected, disabled, and
     * active properties for the wrapped filter.
     *
     * @param filter The TimelineFilter object to be wrapped.
     */
    public SqlFilterState(FilterType filter) {
        this(filter, false);
    }

    /**
     * Constructs an implementation of the FilterState interface for wrapping
     * TimelineFilter objects. TimelineFilter objects provide SQL WHERE clauses
     * for querying the case database and are displayed in the timeline GUI via
     * the filters panel.
     *
     * @param filter   The TimelineFilter object to be wrapped.
     * @param selected The initial value for the selected property.
     */
    public SqlFilterState(FilterType filter, boolean selected) {
        super(filter, selected);
    }

    @Override
    public String getDisplayName() {
        return getFilter().getDisplayName();
    }

    @Override
    public SqlFilterState<FilterType> copyOf() {
        @SuppressWarnings("unchecked")
        SqlFilterState<FilterType> copy = new SqlFilterState<>((FilterType) getFilter().copyOf());
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
                + ", activeP=" + isActive() + '}'; //NON-NLS
    }

}
