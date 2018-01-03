/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.filters;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Interface for Filters. Filters are given to the EventDB who interpretes them
 * a appropriately for all db queries. Since the filters are primarily
 * configured in the UI, this interface provides selected, disabled and active
 * (selected and not disabled) properties.
 */
public interface Filter {

    /**
     * get a filter that is the intersection of the given filters
     *
     * @param filters a set of filters to intersect
     *
     * @return a filter that is the intersection of the given filters
     */
    public static IntersectionFilter<Filter> intersect(ObservableList<Filter> filters) {
        return new IntersectionFilter<>(filters);
    }

    /**
     * get a filter that is the intersection of the given filters
     *
     * @param filters a set of filters to intersect
     *
     * @return a filter that is the intersection of the given filters
     */
    public static IntersectionFilter<Filter> intersect(Filter[] filters) {
        return intersect(FXCollections.observableArrayList(filters));
    }

    /**
     * since filters have mutable state (selected/disabled/active) and are
     * observed in various places, we need a mechanism to copy the current state
     * to keep in the history.
     *
     * Concrete sub classes should implement this in a way that preserves the
     * state and any sub-filters.
     *
     * @return a copy of this filter.
     */
    Filter copyOf();

    /**
     * get the display name of this filter
     *
     * @return a name for this filter to show in the UI
     */
    String getDisplayName();

    /**
     * is this filter selected
     *
     * @return true if this filter is selected
     */
    boolean isSelected();

    /**
     * set this filter selected
     *
     * @param selected true to selecte, false to un-select
     */
    void setSelected(Boolean selected);

    /**
     * observable selected property
     *
     * @return the observable selected property for this filter
     */
    SimpleBooleanProperty selectedProperty();

    /**
     * set the filter disabled
     */
    void setDisabled(Boolean act);

    /**
     * observable disabled property
     *
     * @return the observable disabled property for this filter
     */
    ObservableBooleanValue disabledProperty();

    /**
     * is this filter disabled
     *
     * @return true if this filter is disabled
     */
    boolean isDisabled();

    /**
     * is this filter active (selected and not disabled)
     *
     * @return true if this filter is active
     */
    boolean isActive();

    /**
     * observable active property
     *
     * @return the observable active property for this filter
     */
    BooleanBinding activeProperty();
}
