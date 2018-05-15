/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering;

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
public class FilterModel implements TimelineFilter {

    private final TimelineFilter delegate;

    public FilterModel(TimelineFilter delegate) {
        this.delegate = delegate;
    }

    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty disabled = new SimpleBooleanProperty(false);
    private final BooleanBinding activeProp = Bindings.and(selected, disabled.not());

    public SimpleBooleanProperty selectedProperty() {
        return selected;
    }

    public ObservableBooleanValue disabledProperty() {
        return disabled;
    }

    public void setSelected(Boolean act) {
        selected.set(act);
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setDisabled(Boolean act) {
        disabled.set(act);
    }

    public boolean isDisabled() {
        return disabledProperty().get();
    }

    public boolean isActive() {
        return activeProperty().get();
    }

    public BooleanBinding activeProperty() {
        return activeProp;
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public String getSQLWhere(TimelineManager tm) {
        return delegate.getSQLWhere(tm);
    }

    @Override
    public FilterModel copyOf() {
        @SuppressWarnings("unchecked")
        FilterModel copy = new FilterModel(delegate.copyOf());
        copy.setDisabled(isSelected());
        copy.setDisabled(isDisabled());
        return copy;
    }

    public TimelineFilter getFilter() {
        return delegate;
    }
}
