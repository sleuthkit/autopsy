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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import org.sleuthkit.datamodel.TimelineFilter;

/**
 * Default FilterState implementation for individual TimelineFilters.
 *
 * @param <FilterType>
 */
public class SqlFilterState<FilterType extends TimelineFilter> extends AbstractFilterState<FilterType> {

    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty disabled = new SimpleBooleanProperty(false);
    private final BooleanBinding activeProp = Bindings.and(selected, disabled.not());

    /**
     * Selected = false, Disabled = false
     *
     * @param filter
     */
    public SqlFilterState(FilterType filter) {
        // Setting the intial state to all filters to "selected" except
        // the "Hide Known Filters".
        this(filter, !(filter instanceof TimelineFilter.HideKnownFilter));
    }

    /**
     * Disabled = false
     *
     * @param filter
     * @param selected True to select this filter initialy.
     */
    public SqlFilterState(FilterType filter, boolean selected) {
        super(filter, selected);
    }

//    protected SqlFilterState(FilterType filter, boolean selected, boolean disabled) {
//        super(filter, selected);
//        setDisabled(disabled);
//    }

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
        activeProp.get();
        return "TimelineFilterState{"
               + " filter=" + getFilter().toString()
               + ", selected=" + isSelected()
               + ", disabled=" + isDisabled()
               + ", activeProp=" + isActive() + '}'; //NON-NLS
    }
}
