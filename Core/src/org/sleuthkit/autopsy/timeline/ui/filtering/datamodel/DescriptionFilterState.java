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

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * A FilterState implementation for DescriptionFilters
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

    @Override
    public BooleanProperty selectedProperty() {
        return selected;
    }

    @Override
    public BooleanProperty disabledProperty() {
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
    public BooleanExpression activeProperty() {
        return activeProp;
    }

    @Override
    public String getDisplayName() {
        return filter.getDescription();
    }

    @Override
    public DescriptionFilter getFilter() {
        return filter;
    }

    @Override
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
