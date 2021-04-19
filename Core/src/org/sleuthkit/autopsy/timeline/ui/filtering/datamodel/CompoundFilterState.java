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

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.sleuthkit.datamodel.TimelineFilter;
import org.sleuthkit.datamodel.TimelineFilter.CompoundFilter;

/**
 *
 * Defualt implementation of CompoundFilterState
 *
 * @param <SubFilterType> The type of the subfilters in the underlying
 *                        CompoundFilter
 * @param <FilterType>    The type of the underlying CompoundFilter
 */
public class CompoundFilterState<SubFilterType extends TimelineFilter, FilterType extends CompoundFilter<SubFilterType>> extends SqlFilterState<FilterType> {

    private final ObservableList< FilterState<? extends SubFilterType>> subFilterStates = FXCollections.observableArrayList();

    /**
     * A constructor that automatically makes sub FilterStates for all the
     * subfilters of the given compound filter.
     *
     * @param filter The CompoundFilter this will represent the state of.
     */
    CompoundFilterState(FilterType filter) {
        super(filter);
        filter.getSubFilters().forEach(newSubFilter -> {
            //add the appropriate filter type: default or compound
            if (newSubFilter instanceof CompoundFilter<?>) {
                @SuppressWarnings(value = "unchecked")
                FilterState<SubFilterType> compoundFilterState = (FilterState<SubFilterType>) new CompoundFilterState<>((CompoundFilter<?>) newSubFilter);
                addSubFilterStateInternal(compoundFilterState);
            } else {
                addSubFilterStateInternal(new SqlFilterState<>(newSubFilter));
            }
        });

        configureListeners();
    }

    /**
     * A constructor that doesn't make subfilter states automatically, but
     * instead uses the given collection of sub filter states. Designed
     * primarily for use when making a copy of an existing filterstate tree.
     *
     * @param filter          The CompoundFilter this will represent the state
     *                        of.
     * @param subFilterStates The filter states to use as the sub filter states.
     */
    CompoundFilterState(FilterType filter, Collection< FilterState<? extends SubFilterType>> subFilterStates) {
        super(filter);
        subFilterStates.forEach(this::addSubFilterStateInternal);

        configureListeners();
    }

    private void configureListeners() {
        activeProperty().addListener(activeProperty -> disableSubFiltersIfNotActive());
        disableSubFiltersIfNotActive();

        /**
         * If this filter is selected, and none of its subfilters are selected,
         * then select them all.
         */
        selectedProperty().addListener(selectedProperty -> {
            if (isSelected() && getSubFilterStates().stream().noneMatch(FilterState::isSelected)) {
                subFilterStates.forEach(subFilterState -> subFilterState.setSelected(true));
            }
        });
    }

    /**
     * Disable the sub-filters of the given compound filter if it is not active
     */
    private void disableSubFiltersIfNotActive() {
        boolean inactive = isActive() == false;

        subFilterStates.forEach(subFilterState -> subFilterState.setDisabled(inactive));
    }

    /**
     * Add a sub filter state, if one does not already exist for the filter of
     * the state being added. Also added the filter to the wrapped filter of
     * this state.
     *
     * @param newSubFilterState The new filter state to be added as a subfilter
     *                          state.
     */
    public void addSubFilterState(FilterState< ? extends SubFilterType> newSubFilterState) {
        SubFilterType filter = newSubFilterState.getFilter();
        if (getSubFilterStates().stream().map(FilterState::getFilter).noneMatch(filter::equals)) {

            //add the state first, and then the actual filter which will check for an existing state before adding another one.
            addSubFilterStateInternal(newSubFilterState);
            getFilter().getSubFilters().add(filter);
        }
    }

    private void addSubFilterStateInternal(FilterState< ? extends SubFilterType> newSubFilterState) {
        if (subFilterStates.contains(newSubFilterState) == false) {
            subFilterStates.add(newSubFilterState);
            newSubFilterState.selectedProperty().addListener(selectedProperty -> {
                //set this compound filter state selected if any of the subfilters are selected.
                setSelected(subFilterStates.stream().anyMatch(FilterState::isSelected));
            });
            newSubFilterState.setDisabled(isActive() == false);
        }
    }

    public ObservableList<  FilterState< ? extends SubFilterType>> getSubFilterStates() {
        return subFilterStates;
    }

    @Override
    public CompoundFilterState<SubFilterType, FilterType> copyOf() {
        @SuppressWarnings("unchecked")
        CompoundFilterState<SubFilterType, FilterType> copy
                = new CompoundFilterState<>((FilterType) getFilter().copyOf(),
                        Lists.transform(subFilterStates, FilterState::copyOf));

        copy.setSelected(isSelected());
        copy.setDisabled(isDisabled());
        return copy;
    }

    @Override
    @SuppressWarnings("unchecked")
    public FilterType getActiveFilter() {
        if (isActive() == false) {
            return null;
        }

        List<SubFilterType> activeSubFilters = subFilterStates.stream()
                .filter(filterState -> filterState.isActive())
                .map(filterState -> filterState.getActiveFilter())
                .collect(Collectors.toList());
        FilterType copy = (FilterType) getFilter().copyOf();
        copy.getSubFilters().clear();
        copy.getSubFilters().addAll(activeSubFilters);

        return copy;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 41 * hash + Objects.hashCode(this.subFilterStates);
        return hash;
    }

   

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final CompoundFilterState<?, ?> other = (CompoundFilterState<?, ?>) obj;
        if (!Objects.equals(this.getFilter(), other.getFilter())) {
            return false;
        }
        if (!Objects.equals(this.isSelected(), other.isSelected())) {
            return false;
        }
        if (!Objects.equals(this.isDisabled(), other.isDisabled())) {
            return false;
        }
        return Objects.equals(this.subFilterStates, other.subFilterStates);
    }

    @Override
    public String toString() {
        return "CompoundFilterState{ selected=" + isSelected() + ", disabled=" + isDisabled() + ", activeProp=" + isActive() + ",subFilterStates=" + subFilterStates + '}';
    }
    
}
