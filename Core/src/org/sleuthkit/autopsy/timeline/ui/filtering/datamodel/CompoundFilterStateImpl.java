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
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.sleuthkit.datamodel.timeline.TimelineFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.CompoundFilter;

class CompoundFilterStateImpl<SubFilterType extends TimelineFilter, FilterType extends CompoundFilter<SubFilterType>>
        extends DefaultFilterState<FilterType>
        implements CompoundFilterState<SubFilterType, FilterType> {

    private final ObservableList<FilterState<SubFilterType>> subFilterStates = FXCollections.observableArrayList();

    /**
     * A constructor that automatically makes sub FilterStates for all the
     * subfilters of the given compound filter.
     *
     * @param filter The CompoundFilter this will represent the state of.
     */
    CompoundFilterStateImpl(FilterType filter) {
        super(filter);
        filter.getSubFilters().forEach(this::addStateForSubFilter);

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
    CompoundFilterStateImpl(FilterType filter, Collection<FilterState<SubFilterType>> subFilterStates) {
        super(filter);
        subFilterStates.forEach(this::addSubFilterState);

        configureListeners();
    }

    private void configureListeners() {
        //Add a new subfilterstate whenever the underlying subfilters change.
        getFilter().getSubFilters().addListener((ListChangeListener.Change<? extends SubFilterType> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(this::addStateForSubFilter);
            }
        });

        /*
         * enforce the following relationship between a compound filter and its
         * subfilters: if a compound filter's active property changes, disable
         * the subfilters if the compound filter is not active.
         */
        activeProperty().addListener(activeProperty -> disableSubFiltersIfNotActive());
        disableSubFiltersIfNotActive();
        selectedProperty().addListener(selectedProperty -> {
            if (isSelected() && getSubFilterStates().stream().noneMatch(FilterState::isSelected)) {
                subFilterStates.forEach(subFilterState -> subFilterState.setSelected(true));
            }
        });
    }

    /**
     * disable the sub-filters of the given compound filter if it is not active
     *
     * @param compoundFilter the compound filter
     */
    private void disableSubFiltersIfNotActive() {
        boolean inactive = isActive() == false;

        subFilterStates.forEach(subFilterState -> subFilterState.setDisabled(inactive));
    }

    @SuppressWarnings("unchecked")
    private void addStateForSubFilter(SubFilterType subFilter) {
        if (subFilter instanceof CompoundFilter<?>) {
            addSubFilterState((FilterState<SubFilterType>) new CompoundFilterStateImpl<>((CompoundFilter<?>) subFilter));
        } else {
            addSubFilterState(new DefaultFilterState<>(subFilter));
        }
    }

    private void addSubFilterState(FilterState<SubFilterType> newSubFilterState) {
        subFilterStates.add(newSubFilterState);
        newSubFilterState.selectedProperty().addListener(selectedProperty -> {
            //set this compound filter state selected af any of the subfilters are selected.
            setSelected(subFilterStates.stream().anyMatch(FilterState::isSelected));
        });
    }

    @Override
    public ObservableList<FilterState<SubFilterType>> getSubFilterStates() {
        return subFilterStates;
    }

    @Override
    public CompoundFilterStateImpl<SubFilterType, FilterType> copyOf() {
        @SuppressWarnings("unchecked")
        CompoundFilterStateImpl<SubFilterType, FilterType> copy
                = new CompoundFilterStateImpl<>((FilterType) getFilter().copyOf(),
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
                .filter(FilterState::isActive)
                .map(FilterState::getActiveFilter)
                .collect(Collectors.toList());
        FilterType copy = (FilterType) getFilter().copyOf();
        copy.getSubFilters().clear();
        copy.getSubFilters().addAll(activeSubFilters);

        return copy;
    }
}
