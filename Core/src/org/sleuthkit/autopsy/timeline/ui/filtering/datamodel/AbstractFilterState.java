/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
 * Abstract base class for FilterStates. Provides selected, disabled, and active
 * properties.
 */
abstract class AbstractFilterState<F> implements FilterState<F> {

    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty disabled = new SimpleBooleanProperty(false);
    private final BooleanBinding activeProp = Bindings.and(selected, disabled.not());
    private final F filter;

    @Override
    public F getFilter() {
        return filter;
    }

    AbstractFilterState(F filter, Boolean select) {
        this.filter = filter;
        selected.set(select);
    }

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
    public F getActiveFilter() {
        return isActive() ? getFilter() : null;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.getFilter());
        hash = 37 * hash + Objects.hashCode(this.isSelected());
        hash = 37 * hash + Objects.hashCode(this.isDisabled());
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
        final AbstractFilterState<?> other = (AbstractFilterState<?>) obj;
        if (!Objects.equals(this.getFilter(), other.getFilter())) {
            return false;
        }
        if (!Objects.equals(this.isSelected(), other.isSelected())) {
            return false;
        }
        return Objects.equals(this.isDisabled(), other.isDisabled());
    }
}
