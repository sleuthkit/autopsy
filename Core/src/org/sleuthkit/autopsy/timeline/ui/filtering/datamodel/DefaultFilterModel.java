/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;

/**
 *
 *
 */
public class DefaultFilterModel<FilterType extends TimelineFilter> implements FilterModel<FilterType> {

    private final FilterType delegate;

    public DefaultFilterModel(FilterType delegate) {
        this.delegate = delegate;
    }

    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty disabled = new SimpleBooleanProperty(false);
    private final BooleanBinding activeProp = Bindings.and(selected, disabled.not());

    @Override
    public SimpleBooleanProperty selectedProperty() {
        return selected;
    }

    @Override
    public ObservableBooleanValue disabledProperty() {
        return disabled;
    }

    @Override
    public void setSelected(Boolean act) {
        selected.set(act);
    }

    @Override
    public boolean isSelected() {
        return selected.get();
    }

    @Override
    public void setDisabled(Boolean act) {
        disabled.set(act);
    }

    @Override
    public boolean isDisabled() {
        return disabledProperty().get();
    }

    @Override
    public boolean isActive() {
        return activeProperty().get();
    }

    @Override
    public BooleanBinding activeProperty() {
        return activeProp;
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public String getSQLWhere(TimelineManager tm) {
        //TODO: intercept and prune out inactive filters;
        return delegate.getSQLWhere(tm);
    }

    @Override
    public DefaultFilterModel<FilterType> copyOf() {
        @SuppressWarnings("unchecked")
        DefaultFilterModel<FilterType> copy = new DefaultFilterModel<>((FilterType) delegate.copyOf());
        copy.setDisabled(isSelected());
        copy.setDisabled(isDisabled());
        return copy;
    }

    @Override
    public FilterType getFilter() {
        return delegate;
    }
}
