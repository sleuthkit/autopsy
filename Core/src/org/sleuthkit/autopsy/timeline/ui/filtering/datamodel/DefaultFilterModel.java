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
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;

/**
 *
 *
 * @param <FilterType>
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
        return getActiveFilter().getSQLWhere(tm);
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

    @Override
    public FilterType getActiveFilter() {
        return isActive() ? getFilter() : null;
    }
}
