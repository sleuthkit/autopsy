/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import java.util.Collection;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.sleuthkit.datamodel.timeline.filters.CompoundFilter;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;

public class CompoundFilterModel<SubFilterType extends TimelineFilter>
        extends DefaultFilterModel< CompoundFilter<SubFilterType>>
        implements CompoundFilterModelI<SubFilterType> {

    private final ObservableList<FilterModel<SubFilterType>> subFilterModels = FXCollections.observableArrayList();

    public CompoundFilterModel(CompoundFilter< SubFilterType> delegate) {
        super(delegate);

        delegate.getSubFilters().forEach(this::addSubFilterModel);
        delegate.getSubFilters().addListener((ListChangeListener.Change<? extends SubFilterType> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(CompoundFilterModel.this::addSubFilterModel);
            }
        });

    }

    public CompoundFilterModel(CompoundFilter< SubFilterType> delegate, Collection<FilterModel<SubFilterType>> subFilterModels) {
        super(delegate);

        delegate.getSubFilters().forEach(this::addSubFilterModel);
        delegate.getSubFilters().addListener((ListChangeListener.Change<? extends SubFilterType> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(CompoundFilterModel.this::addSubFilterModel);
            }
        });

    }

    @SuppressWarnings("unchecked")
    private void addSubFilterModel(SubFilterType subFilter) {
        FilterModel<  SubFilterType> newFilterModel;
        if (subFilter instanceof CompoundFilter) {
            newFilterModel = (FilterModel<SubFilterType>) newCo((CompoundFilter<?>) subFilter);
        } else {
            newFilterModel = new DefaultFilterModel<>(subFilter);
        }
        subFilterModels.add(newFilterModel);
        newFilterModel.selectedProperty().addListener(selectedProperty -> {
            //set this compound filter model  selected af any of the subfilters are selected.
            setSelected(getSubFilterModels().parallelStream().anyMatch(FilterModel::isSelected));
        });
    }

    private CompoundFilterModel<?> newCo(CompoundFilter<?> subFilter) {
        return new CompoundFilterModel<>(subFilter);
    }

    @Override
    public ObservableList<FilterModel<SubFilterType>> getSubFilterModels() {
        return subFilterModels;
    }

    @Override
    public CompoundFilterModel<SubFilterType> copyOf() {

        CompoundFilterModel<SubFilterType> copy = new CompoundFilterModel<>(getFilter().copyOf(),
                getSubFilterModels().stream().filter(FilterModel::isActive).map(FilterModel::copyOf).collect(Collectors.toList())
        );

        return copy;
    }

}
