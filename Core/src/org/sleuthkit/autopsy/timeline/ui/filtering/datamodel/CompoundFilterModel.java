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
import org.sleuthkit.datamodel.timeline.filters.CompoundFilter;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;

public class CompoundFilterModel<SubFilterType extends TimelineFilter, C extends CompoundFilter<SubFilterType>>
        extends DefaultFilterModel<C>
        implements CompoundFilterModelI<SubFilterType> {
    
    private final ObservableList<FilterModel<SubFilterType>> subFilterModels = FXCollections.observableArrayList();
    
    public CompoundFilterModel(C delegate) {
        super(delegate);
        
        delegate.getSubFilters().forEach(this::addNewSubFilterModel);
        delegate.getSubFilters().addListener((ListChangeListener.Change<? extends SubFilterType> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(CompoundFilterModel.this::addNewSubFilterModel);
            }
        });
        
    }
    
    public CompoundFilterModel(C delegate, Collection<FilterModel<SubFilterType>> subFilterModels) {
        super(delegate);
        
        subFilterModels.forEach(this::addSubFilterModel);
        delegate.getSubFilters().addListener((ListChangeListener.Change<? extends SubFilterType> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(CompoundFilterModel.this::addNewSubFilterModel);
            }
        });
        
    }
    
    @SuppressWarnings("unchecked")
    private <X extends TimelineFilter, S extends CompoundFilter<X>> void addNewSubFilterModel(SubFilterType subFilter) {
        
        if (subFilter instanceof CompoundFilter<?>) {
            addSubFilterModel((FilterModel<SubFilterType>) new CompoundFilterModel<>((S) subFilter));
        } else {
            addSubFilterModel(new DefaultFilterModel<>(subFilter));
        }
        
    }
    
    private void addSubFilterModel(FilterModel<SubFilterType> newFilterModel) {
        subFilterModels.add(newFilterModel);
        newFilterModel.selectedProperty().addListener(selectedProperty -> {
            //set this compound filter model  selected af any of the subfilters are selected.
            setSelected(getSubFilterModels().parallelStream().anyMatch(FilterModel::isSelected));
        });
    }
    
    @Override
    public ObservableList<FilterModel<SubFilterType>> getSubFilterModels() {
        return subFilterModels;
    }
    
    @Override
    public CompoundFilterModel<SubFilterType, C> copyOf() {
        
        @SuppressWarnings("unchecked")
        CompoundFilterModel<SubFilterType, C> copy = new CompoundFilterModel<>((C) getFilter().copyOf(),
                getSubFilterModels().stream().map(FilterModel::copyOf).collect(Collectors.toList())
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
        
        List<SubFilterType> activeSubFilters = getSubFilterModels().stream()
                .filter(FilterModel::isActive)
                .map(FilterModel::getActiveFilter)
                .collect(Collectors.toList());
        C copy = (C) getFilter().copyOf();
        copy.getSubFilters().clear();
        copy.getSubFilters().addAll(activeSubFilters);
        
        return copy;
    }
}
