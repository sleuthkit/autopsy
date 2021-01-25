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
 * An abstract base class for implementations of the FilterState interface.
 * Filter state classes are wrappers that adapt a timeline data model filtering
 * object for display by the timeline GUI by providing selected, disabled, and
 * active properties for the wrapped filter.
 *
 * @param <FilterType> The type of the wrapped filter.
 */
abstract class AbstractFilterState<FilterType> implements FilterState<FilterType> {

    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty disabled = new SimpleBooleanProperty(false);
    private final BooleanBinding active = Bindings.and(selected, disabled.not());
    private final FilterType filter;

    @Override
    public FilterType getFilter() {
        return filter;
    }

    /**
     * Constructs the base class part for implementations of the FilterState
     * interface. Filter state classes are wrappers that adapt a timeline data
     * model filtering object for display by the timeline GUI by providing
     * selected, disabled, and active properties for the wrapped filter.
     *
     * @param filter   The filter to be wrapped.
     * @param selected Whether or not the filter is selected. The filter is
     *                 disabled by default and is therefore not active by
     *                 default.
     */
    AbstractFilterState(FilterType filter, Boolean selected) {
        this.filter = filter;
        this.selected.set(selected);
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
        return active;
    }

    @Override
    public FilterType getActiveFilter() {
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
