/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import java.util.Comparator;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import org.openide.util.NbBundle;

/**
 * union of {@link DataSourceFilter}s
 */
public class DataSourcesFilter extends UnionFilter<DataSourceFilter> {

    public DataSourcesFilter() {
        setSelected(false);
    }

    @Override
    public DataSourcesFilter copyOf() {
        final DataSourcesFilter filterCopy = new DataSourcesFilter();
        filterCopy.setSelected(isSelected());
        //add a copy of each subfilter
        this.getSubFilters().forEach((DataSourceFilter t) -> {
            filterCopy.addSubFilter(t.copyOf());
        });

        return filterCopy;
    }

    @Override
    @NbBundle.Messages("DataSourcesFilter.displayName.text=Data Source")
    public String getDisplayName() {
        return Bundle.DataSourcesFilter_displayName_text();
    }

    @Override
    public String getHTMLReportString() {
        //move this logic into SaveSnapshot
        String string = getDisplayName() + getStringCheckBox();
        if (getSubFilters().isEmpty() == false) {
            string = string + " : " + getSubFilters().stream()
                    .filter(Filter::isSelected)
                    .map(Filter::getHTMLReportString)
                    .collect(Collectors.joining("</li><li>", "<ul><li>", "</li></ul>")); // NON-NLS
        }
        return string;
    }

    public void addSubFilter(DataSourceFilter dataSourceFilter) {
        if (getSubFilters().stream().map(DataSourceFilter.class::cast)
                .map(DataSourceFilter::getDataSourceID)
                .filter(t -> t == dataSourceFilter.getDataSourceID())
                .findAny().isPresent() == false) {
            getSubFilters().add(dataSourceFilter);
            getSubFilters().sort(Comparator.comparing(DataSourceFilter::getDisplayName));
        }
        if (getSubFilters().size() > 1) {
            setSelected(Boolean.TRUE);
        }
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

        if (isSelected() != other.isSelected()) {
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
        return Bindings.or(super.disabledProperty(), Bindings.size(getSubFilters()).lessThanOrEqualTo(1));
    }

}
