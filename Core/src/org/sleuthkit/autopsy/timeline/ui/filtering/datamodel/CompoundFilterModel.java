/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.sleuthkit.datamodel.timeline.filters.CompoundFilter;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;

 public class CompoundFilterModel extends DefaultFilterModel< CompoundFilter<TimelineFilter>> implements CompoundFilterModelI {

    private final ObservableList<FilterModel<?>> subFilterModels = FXCollections.observableArrayList();

    public CompoundFilterModel(CompoundFilter< TimelineFilter> delegate) {
        super(delegate);

        delegate.getSubFilters().forEach(this::addSubFilterModel);
        delegate.getSubFilters().addListener((ListChangeListener.Change<? extends TimelineFilter> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(CompoundFilterModel.this::addSubFilterModel);
            }
        });

    }

    @SuppressWarnings("unchecked")
    private void addSubFilterModel(TimelineFilter subFilter) {
        FilterModel<? extends TimelineFilter> newFilterModel;
        if (subFilter instanceof CompoundFilter) {
            newFilterModel = new CompoundFilterModel((CompoundFilter<TimelineFilter>) subFilter);
        } else {
            newFilterModel = new DefaultFilterModel<>(subFilter);
        }

        newFilterModel.selectedProperty().addListener(selectedProperty -> {
            //set this compound filter model  selected af any of the subfilters are selected.
            setSelected(getSubFilterModels().parallelStream().anyMatch(FilterModel::isSelected));
        });
    }

    @Override
    public    ObservableList<FilterModel<?>> getSubFilterModels() {
        return subFilterModels;
    }
}
