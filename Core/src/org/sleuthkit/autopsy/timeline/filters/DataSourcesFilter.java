/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.filters;

import java.util.function.Predicate;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableBooleanValue;
import org.openide.util.NbBundle;

/**
 * union of {@link DataSourceFilter}s
 */
public class DataSourcesFilter extends UnionFilter<DataSourceFilter> {

    //keep references to the overridden properties so they don't get GC'd
    private final BooleanBinding activePropertyOverride;
    private final BooleanBinding disabledPropertyOverride;

    public DataSourcesFilter() {
        disabledPropertyOverride = Bindings.or(super.disabledProperty(), Bindings.size(getSubFilters()).lessThanOrEqualTo(1));
        activePropertyOverride = super.activeProperty().and(Bindings.not(disabledPropertyOverride));
    }

    @Override
    public DataSourcesFilter copyOf() {
        final DataSourcesFilter filterCopy = new DataSourcesFilter();
        //add a copy of each subfilter
        getSubFilters().forEach(dataSourceFilter -> filterCopy.addSubFilter(dataSourceFilter.copyOf()));
        //these need to happen after the listeners fired by adding the subfilters 
        filterCopy.setSelected(isSelected());
        filterCopy.setDisabled(isDisabled());

        return filterCopy;
    }

    @Override
    @NbBundle.Messages("DataSourcesFilter.displayName.text=Data Source")
    public String getDisplayName() {
        return Bundle.DataSourcesFilter_displayName_text();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DataSourcesFilter other = (DataSourcesFilter) obj;

        if (isActive() != other.isActive()) {
            return false;
        }

        return areSubFiltersEqual(this, other);

    }

    @Override
    public int hashCode() {
        return 9;
    }

    @Override
    public ObservableBooleanValue disabledProperty() {
        return disabledPropertyOverride;
    }

    @Override
    public BooleanBinding activeProperty() {
        return activePropertyOverride;
    }

    @Override
    Predicate<DataSourceFilter> getDuplicatePredicate(DataSourceFilter subfilter) {
        return dataSourcefilter -> dataSourcefilter.getDataSourceID() == subfilter.getDataSourceID();
    }
}
