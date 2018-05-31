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

import java.util.Collections;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.sleuthkit.datamodel.timeline.filters.CompoundFilter;
import org.sleuthkit.datamodel.timeline.filters.DataSourceFilter;
import org.sleuthkit.datamodel.timeline.filters.DataSourcesFilter;
import org.sleuthkit.datamodel.timeline.filters.HashHitsFilter;
import org.sleuthkit.datamodel.timeline.filters.HashSetFilter;
import org.sleuthkit.datamodel.timeline.filters.HideKnownFilter;
import org.sleuthkit.datamodel.timeline.filters.RootFilter;
import org.sleuthkit.datamodel.timeline.filters.TagNameFilter;
import org.sleuthkit.datamodel.timeline.filters.TagsFilter;
import org.sleuthkit.datamodel.timeline.filters.TextFilter;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;
import org.sleuthkit.datamodel.timeline.filters.TypeFilter;

/**
 */
public class RootFilterModel extends DefaultFilterModel<RootFilter> implements CompoundFilterModelI< TimelineFilter> {

    private final CompoundFilterModel<TypeFilter, TypeFilter> typeFilterModel;
    private final DefaultFilterModel<HideKnownFilter> knownFilterModel;
    private final DefaultFilterModel<TextFilter> textFilterModel;
    private final CompoundFilterModel<TagNameFilter, TagsFilter> tagsFilterModel;
    private final CompoundFilterModel<HashSetFilter, HashHitsFilter> hashHitsFilterModel;
    private final CompoundFilterModel<DataSourceFilter, DataSourcesFilter> dataSourcesFilterModel;

    private final ObservableList<   FilterModel< ?>> subFilterModels = FXCollections.observableArrayList();

    public RootFilterModel(RootFilter delegate) {
        this(delegate,
                new CompoundFilterModel<>(delegate.getTypeFilter()),
                new DefaultFilterModel<>(delegate.getKnownFilter()),
                new DefaultFilterModel<>(delegate.getTextFilter()),
                new CompoundFilterModel<>(delegate.getTagsFilter()),
                new CompoundFilterModel<>(delegate.getHashHitsFilter()),
                new CompoundFilterModel<>(delegate.getDataSourcesFilter())
        );
    }

    private RootFilterModel(RootFilter delegate,
                            CompoundFilterModel<TypeFilter, TypeFilter> typeFilterModel,
                            DefaultFilterModel<HideKnownFilter> knownFilterModel,
                            DefaultFilterModel<TextFilter> textFilterModel,
                            CompoundFilterModel<TagNameFilter, TagsFilter> tagsFilterModel,
                            CompoundFilterModel<HashSetFilter, HashHitsFilter> hashHitsFilterModel,
                            CompoundFilterModel<DataSourceFilter, DataSourcesFilter> dataSourcesFilterModel) {
        super(delegate);
        this.typeFilterModel = typeFilterModel;
        this.knownFilterModel = knownFilterModel;
        this.textFilterModel = textFilterModel;
        this.tagsFilterModel = tagsFilterModel;
        this.hashHitsFilterModel = hashHitsFilterModel;
        this.dataSourcesFilterModel = dataSourcesFilterModel;
        subFilterModels.addAll(
                knownFilterModel, textFilterModel,
                tagsFilterModel,
                hashHitsFilterModel,
                dataSourcesFilterModel, typeFilterModel);
    }

    @Override
    public RootFilterModel copyOf() {
        return new RootFilterModel(getFilter().copyOf(),
                getTypeFilterModel().copyOf(),
                getKnownFilterModel().copyOf(),
                getTextFilterModel().copyOf(),
                getTagsFilterModel().copyOf(),
                getHashHitsFilterModel().copyOf(),
                getDataSourcesFilterModel().copyOf());
    }

    public CompoundFilterModel<TypeFilter, TypeFilter> getTypeFilterModel() {
        return typeFilterModel;
    }

    public DefaultFilterModel<HideKnownFilter> getKnownFilterModel() {
        return knownFilterModel;
    }

    public DefaultFilterModel<TextFilter> getTextFilterModel() {
        return textFilterModel;
    }

    public CompoundFilterModel<TagNameFilter, TagsFilter> getTagsFilterModel() {
        return tagsFilterModel;
    }

    public CompoundFilterModel<HashSetFilter, HashHitsFilter> getHashHitsFilterModel() {
        return hashHitsFilterModel;
    }

    public CompoundFilterModel<DataSourceFilter, DataSourcesFilter> getDataSourcesFilterModel() {
        return dataSourcesFilterModel;
    }

    @Override
    public RootFilter getActiveFilter() {
        TypeFilter activeOrNullCompound = typeFilterModel.getActiveFilter();

        return new RootFilter(knownFilterModel.getActiveFilter(),
                tagsFilterModel.getActiveFilter(),
                hashHitsFilterModel.getActiveFilter(),
                textFilterModel.getActiveFilter(), activeOrNullCompound,
                dataSourcesFilterModel.getActiveFilter(),
                Collections.emptySet());

    }



    public boolean hasActiveHashFilters() {
        return hashHitsFilterModel.isActive()
               && hashHitsFilterModel.getSubFilterModels().stream().anyMatch(FilterModel::isActive);
    }

    public boolean hasActiveTagsFilters() {
        return tagsFilterModel.isActive()
               && tagsFilterModel.getSubFilterModels().stream().anyMatch(FilterModel::isActive);
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
        RootFilter activeFilter = getActiveFilter();
        RootFilter activeFilter1 = ((RootFilterModel) obj).getActiveFilter();

        return activeFilter.equals(activeFilter1);
    }

    @Override
    public int hashCode() {
        int hash = 7;

        return hash;
    }

    @Override
    public ObservableList<  FilterModel< ? extends TimelineFilter>> getSubFilterModels() {
        return subFilterModels;
    }

}
