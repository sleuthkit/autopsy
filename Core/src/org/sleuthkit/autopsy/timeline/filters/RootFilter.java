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

import java.util.Set;
import java.util.stream.Collectors;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;

/**
 * An implementation of IntersectionFilter designed to be used as the root of a
 * filter tree. provides named access to specific subfilters.
 */
public class RootFilter extends IntersectionFilter<Filter> {

    private final HideKnownFilter knownFilter;
    private final TagsFilter tagsFilter;
    private final HashHitsFilter hashFilter;
    private final TextFilter textFilter;
    private final TypeFilter typeFilter;
    private final DataSourcesFilter dataSourcesFilter;

    public DataSourcesFilter getDataSourcesFilter() {
        return dataSourcesFilter;
    }

    public TagsFilter getTagsFilter() {
        return tagsFilter;
    }

    public HashHitsFilter getHashHitsFilter() {
        return hashFilter;
    }

    public TypeFilter getTypeFilter() {
        return typeFilter;
    }

    public HideKnownFilter getKnownFilter() {
        return knownFilter;
    }

    public TextFilter getTextFilter() {
        return textFilter;
    }

    public RootFilter(HideKnownFilter knownFilter, TagsFilter tagsFilter, HashHitsFilter hashFilter, TextFilter textFilter, TypeFilter typeFilter, DataSourcesFilter dataSourceFilter, Set<Filter> annonymousSubFilters) {
        super(FXCollections.observableArrayList(
                textFilter,
                knownFilter,
                dataSourceFilter, tagsFilter,
                hashFilter,
                typeFilter
        ));
        this.knownFilter = knownFilter;
        this.tagsFilter = tagsFilter;
        this.hashFilter = hashFilter;
        this.textFilter = textFilter;
        this.typeFilter = typeFilter;
        this.dataSourcesFilter = dataSourceFilter;
        getSubFilters().addAll(annonymousSubFilters);
        setSelected(Boolean.TRUE);
        setDisabled(false);
    }

    @Override
    public RootFilter copyOf() {
        Set<Filter> annonymousSubFilters = getSubFilters().stream()
                .filter(subFilter ->
                        !(subFilter.equals(knownFilter)
                        || subFilter.equals(tagsFilter)
                        || subFilter.equals(hashFilter)
                        || subFilter.equals(typeFilter)
                        || subFilter.equals(textFilter)
                        || subFilter.equals(dataSourcesFilter)))
                .map(Filter::copyOf)
                .collect(Collectors.toSet());

        RootFilter filter = new RootFilter(
                knownFilter.copyOf(),
                tagsFilter.copyOf(),
                hashFilter.copyOf(),
                textFilter.copyOf(),
                typeFilter.copyOf(),
                dataSourcesFilter.copyOf(),
                annonymousSubFilters);
        filter.setSelected(isSelected());
        filter.setDisabled(isDisabled());
        return filter;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
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
        return areSubFiltersEqual(this, (CompoundFilter<Filter>) obj);
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public BooleanBinding activeProperty() {

        return new BooleanBinding() {
            @Override
            protected boolean computeValue() {
                return true;
            }
        };
    }
}
