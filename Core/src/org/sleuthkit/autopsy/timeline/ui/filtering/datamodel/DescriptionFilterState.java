/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 *
 *
 */
public class DescriptionFilterState implements FilterState<DescriptionFilter> {
    
    private final DescriptionFilter filter;
    
    public DescriptionFilterState(DescriptionFilter filter) {
        this(filter, false);
    }
    
    public DescriptionFilterState(DescriptionFilter filter, boolean selected) {
        this.filter = filter;
        this.selected.set(selected);
    }
    
    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty disabled = new SimpleBooleanProperty(false);
    private final BooleanBinding activeProp = Bindings.and(selected, disabled.not());
    
    public BooleanProperty selectedProperty() {
        return selected;
    }
    
    public BooleanProperty disabledProperty() {
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
    
    public BooleanExpression activeProperty() {
        return activeProp;
    }
    
    public String getDisplayName() {
        return filter.getDescription();
    }
    
    public DescriptionFilter getFilter() {
        return filter;
    }
    
    public DescriptionFilter getActiveFilter() {
        return isActive() ? getFilter() : null;
    }
    
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.filter);
        hash = 37 * hash + Objects.hashCode(this.selected);
        hash = 37 * hash + Objects.hashCode(this.disabled);
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
        final DescriptionFilterState other = (DescriptionFilterState) obj;
        if (!Objects.equals(this.filter, other.filter)) {
            return false;
        }
        if (!Objects.equals(this.selected, other.selected)) {
            return false;
        }
        if (!Objects.equals(this.disabled, other.disabled)) {
            return false;
        }
        return true;
    }
    
    @Override
    public DescriptionFilterState copyOf() {
        DescriptionFilterState copy = new DescriptionFilterState(filter);
        copy.setSelected(isSelected());
        copy.setDisabled(isDisabled());
        return copy;
    }
}
