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

import javafx.collections.FXCollections;

/**
 * an implementation of (@link IntersectionFilter} designed to be used as the
 * root of a filter tree. provides named access to specific subfilters.
 */
public class RootFilter extends IntersectionFilter<Filter> {

    private final HideKnownFilter knwonFilter;
    private final HashHitsFilter hashFilter;
    private final TextFilter textFilter;
    private final TypeFilter typeFilter;
    private final DataSourcesFilter dataSourcesFilter;

    public DataSourcesFilter getDataSourcesFilter() {
        return dataSourcesFilter;
    }

    public HashHitsFilter getHashHitsFilter() {
        return hashFilter;
    }

    public RootFilter(HideKnownFilter knownFilter, HashHitsFilter hashFilter, TextFilter textFilter, TypeFilter typeFilter, DataSourcesFilter dataSourceFilter) {
        super(FXCollections.observableArrayList(knownFilter, hashFilter, textFilter, dataSourceFilter, typeFilter));
        this.knwonFilter = knownFilter;
        this.hashFilter = hashFilter;
        this.textFilter = textFilter;
        this.typeFilter = typeFilter;
        this.dataSourcesFilter = dataSourceFilter;
    }

    @Override
    public RootFilter copyOf() {
        RootFilter filter = new RootFilter(knwonFilter.copyOf(), hashFilter.copyOf(), textFilter.copyOf(), typeFilter.copyOf(), dataSourcesFilter.copyOf());
        filter.setSelected(isSelected());
        filter.setDisabled(isDisabled());
        return filter;
    }

    @Override
    public int hashCode() {
        return 3;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return hashEqualSubFilters(this, (CompoundFilter<Filter>) obj);
    }
}
