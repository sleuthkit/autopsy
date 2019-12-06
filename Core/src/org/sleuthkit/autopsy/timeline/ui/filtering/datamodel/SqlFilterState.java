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
 * Default FilterState implementation for individual TimelineFilters.
 *
 * @param <FilterType>
 *
 * RJCTODO: We need a better name for this class. Or at least an explanation of
 * the SQL in the name. The SQL part is not actually enforced or even implied.
 * IT can only be known by knowledge of the filter hierarchy underlying the
 * filter states hierarchy.
 */
public class SqlFilterState<FilterType extends TimelineFilter> extends AbstractFilterState<FilterType> {

    /**
     * Selected = false, Disabled = false
     *
     * @param filter
     */
    public SqlFilterState(FilterType filter) {
        this(filter, false);
        /*
         * RJCTODO: This casting is not very nice. We should insert filter state
         * classes for these two types of filters as subclasses of
         * SqlFilterState.
         */
        selectedProperty().addListener(selectedProperty -> {
            if (filter instanceof TimelineFilter.TagsFilter) {
                ((TimelineFilter.TagsFilter) filter).setEventSourcesAreTagged(isSelected());
            } else if (filter instanceof TimelineFilter.HashHitsFilter) {
                ((TimelineFilter.HashHitsFilter) filter).setEventSourcesHaveHashSetHits(isSelected());
            }
        });
    }

    /**
     * Disabled = false
     *
     * @param filter
     * @param selected True to select this filter initially.
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
                + ", activeProp=" + isActive() + '}'; //NON-NLS
    }
}
