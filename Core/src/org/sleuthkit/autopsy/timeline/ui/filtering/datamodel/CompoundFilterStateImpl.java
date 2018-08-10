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

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.sleuthkit.datamodel.timeline.TimelineFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.CompoundFilter;

class CompoundFilterStateImpl<SubFilterType extends TimelineFilter, C extends CompoundFilter<SubFilterType>>
        extends DefaultFilterState<C>
        implements CompoundFilterState<SubFilterType, C> {

    private final ObservableList<FilterState<SubFilterType>> subFilterStates = FXCollections.observableArrayList();

    CompoundFilterStateImpl(C delegate) {
        super(delegate);

        delegate.getSubFilters().forEach(this::addSubFilterState);
        delegate.getSubFilters().addListener((ListChangeListener.Change<? extends SubFilterType> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(CompoundFilterStateImpl.this::addSubFilterState);
            }
        });
        /*
         * enforce the following relationship between a compound filter and its
         * subfilters: if a compound filter's active property changes, disable
         * the subfilters if the compound filter is not active.
         */
        activeProperty().addListener(activeProperty -> disableSubFiltersIfNotActive());
        disableSubFiltersIfNotActive();
    }

    CompoundFilterStateImpl(C delegate, Collection<FilterState<SubFilterType>> subFilterStates) {
        super(delegate);

        subFilterStates.forEach(this::addSubFilterState);
        /*
         * enforce the following relationship between a compound filter and its
         * subfilters: if a compound filter's active property changes, disable
         * the subfilters if the compound filter is not active.
         */
        activeProperty().addListener(activeProperty -> disableSubFiltersIfNotActive());
        disableSubFiltersIfNotActive();
    }

    /**
     * disable the sub-filters of the given compound filter if it is not active
     *
     * @param compoundFilter the compound filter
     */
    private void disableSubFiltersIfNotActive() {
        boolean inactive = isActive() == false;

        for (FilterState<SubFilterType> subFilter : getSubFilterStates()) {
            subFilter.setDisabled(inactive);
        }
    }

    @SuppressWarnings("unchecked")
    private <X extends TimelineFilter, S extends CompoundFilter<X>> void addSubFilterState(SubFilterType subFilter) {

        if (subFilter instanceof CompoundFilter<?>) {
            addSubFilterState((FilterState<SubFilterType>) new CompoundFilterStateImpl<>((S) subFilter));
        } else {
            addSubFilterState(new DefaultFilterState<>(subFilter));
        }

    }

    private void addSubFilterState(FilterState<SubFilterType> newFilterModel) {
        subFilterStates.add(newFilterModel);
        newFilterModel.selectedProperty().addListener(selectedProperty -> {
            //set this compound filter model  selected af any of the subfilters are selected.
            setSelected(getSubFilterStates().stream().anyMatch(FilterState::isSelected));
        });
    }

    @Override
    public ObservableList<FilterState<SubFilterType>> getSubFilterStates() {
        return subFilterStates;
    }

    @Override
    public CompoundFilterStateImpl<SubFilterType, C> copyOf() {

        @SuppressWarnings("unchecked")
        CompoundFilterStateImpl<SubFilterType, C> copy = new CompoundFilterStateImpl<>((C) getFilter().copyOf(),
                getSubFilterStates().stream().map(FilterState::copyOf).collect(Collectors.toList())
        );

        copy.setSelected(isSelected());
        copy.setDisabled(isDisabled());
        return copy;
    }

    @Override
    @SuppressWarnings("unchecked")
    public C getActiveFilter() {
        if (isActive() == false) {
            return null;
        }

        List<SubFilterType> activeSubFilters = getSubFilterStates().stream()
                .filter(FilterState::isActive)
                .map(FilterState::getActiveFilter)
                .collect(Collectors.toList());
        C copy = (C) getFilter().copyOf();
        copy.getSubFilters().clear();
        copy.getSubFilters().addAll(activeSubFilters);

        return copy;
    }
}
