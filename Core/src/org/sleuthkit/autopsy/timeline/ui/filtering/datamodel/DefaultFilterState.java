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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.sleuthkit.datamodel.timeline.TimelineFilter;

/**
 *
 *
 * @param <FilterType>
 */
public class DefaultFilterState<FilterType extends TimelineFilter> implements FilterState<FilterType> {

    private final FilterType delegate;

    public DefaultFilterState(FilterType delegate) {
        this.delegate = delegate;
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
        return delegate.getDisplayName();
    }
 
    @Override
    public DefaultFilterState<FilterType> copyOf() {
        @SuppressWarnings("unchecked")
        DefaultFilterState<FilterType> copy = new DefaultFilterState<>((FilterType) delegate.copyOf());
        copy.setSelected(isSelected( ));
        copy.setDisabled(isDisabled());
        return copy;
    }

    @Override
    public FilterType getFilter() {
        return delegate;
    }

    @Override
    public FilterType getActiveFilter() {
        return isActive() ? getFilter() : null;
    }
}
