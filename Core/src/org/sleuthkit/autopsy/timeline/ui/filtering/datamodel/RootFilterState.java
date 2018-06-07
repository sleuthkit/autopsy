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
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.sleuthkit.datamodel.TimelineManager;
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
public class RootFilterState implements FilterState<RootFilter>, CompoundFilterState< TimelineFilter, RootFilter> {

    private final CompoundFilterState<TypeFilter, TypeFilter> typeFilterModel;
    private final DefaultFilterState<HideKnownFilter> knownFilterModel;
    private final DefaultFilterState<TextFilter> textFilterModel;
    private final CompoundFilterState<TagNameFilter, TagsFilter> tagsFilterModel;
    private final CompoundFilterState<HashSetFilter, HashHitsFilter> hashHitsFilterModel;
    private final CompoundFilterState<DataSourceFilter, DataSourcesFilter> dataSourcesFilterModel;

    private static final ReadOnlyBooleanProperty ALWAYS_TRUE = new ReadOnlyBooleanWrapper(true).getReadOnlyProperty();
    private final static ReadOnlyBooleanProperty ALWAYS_FALSE = new ReadOnlyBooleanWrapper(false).getReadOnlyProperty();

    private final ObservableList<   FilterState< ?>> subFilterModels = FXCollections.observableArrayList();
    private final RootFilter delegate;

    public RootFilterState(RootFilter delegate) {
        this(delegate,
                new CompoundFilterStateImpl<>(delegate.getTypeFilter()),
                new DefaultFilterState<>(delegate.getKnownFilter()),
                new DefaultFilterState<>(delegate.getTextFilter()),
                new CompoundFilterStateImpl<>(delegate.getTagsFilter()),
                new CompoundFilterStateImpl<>(delegate.getHashHitsFilter()),
                new CompoundFilterStateImpl<>(delegate.getDataSourcesFilter())
        );
    }

    private RootFilterState(RootFilter delegate,
                            CompoundFilterState<TypeFilter, TypeFilter> typeFilterModel,
                            DefaultFilterState<HideKnownFilter> knownFilterModel,
                            DefaultFilterState<TextFilter> textFilterModel,
                            CompoundFilterState<TagNameFilter, TagsFilter> tagsFilterModel,
                            CompoundFilterState<HashSetFilter, HashHitsFilter> hashHitsFilterModel,
                            CompoundFilterState<DataSourceFilter, DataSourcesFilter> dataSourcesFilterModel) {
        this.delegate = delegate;
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
    public RootFilterState copyOf() {
        return new RootFilterState(getFilter().copyOf(),
                getTypeFilterModel().copyOf(),
                getKnownFilterModel().copyOf(),
                getTextFilterModel().copyOf(),
                getTagsFilterModel().copyOf(),
                getHashHitsFilterModel().copyOf(),
                getDataSourcesFilterModel().copyOf());
    }

    public CompoundFilterState<TypeFilter, TypeFilter> getTypeFilterModel() {
        return typeFilterModel;
    }

    public DefaultFilterState<HideKnownFilter> getKnownFilterModel() {
        return knownFilterModel;
    }

    public DefaultFilterState<TextFilter> getTextFilterModel() {
        return textFilterModel;
    }

    public CompoundFilterState<TagNameFilter, TagsFilter> getTagsFilterModel() {
        return tagsFilterModel;
    }

    public CompoundFilterState<HashSetFilter, HashHitsFilter> getHashHitsFilterModel() {
        return hashHitsFilterModel;
    }

    public CompoundFilterState<DataSourceFilter, DataSourcesFilter> getDataSourcesFilterModel() {
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

    @SuppressWarnings("rawtypes")
    public boolean hasActiveHashFilters() {
        return hashHitsFilterModel.isActive()
               && hashHitsFilterModel.getSubFilterStates().stream().anyMatch(FilterState::isActive);
    }

    @SuppressWarnings("rawtypes")
    public boolean hasActiveTagsFilters() {
        return tagsFilterModel.isActive()
               && tagsFilterModel.getSubFilterStates().stream().anyMatch(FilterState::isActive);
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
        RootFilter activeFilter1 = ((RootFilterState) obj).getActiveFilter();

        return activeFilter.equals(activeFilter1);
    }

    @Override
    public int hashCode() {
        int hash = 7;

        return hash;
    }

    @Override
    public ObservableList<  FilterState< ? extends TimelineFilter>> getSubFilterStates() {
        return subFilterModels;
    }

    @Override
    public ReadOnlyBooleanProperty activeProperty() {
        return ALWAYS_TRUE;
    }

    @Override
    public ReadOnlyBooleanProperty disabledProperty() {
        return ALWAYS_FALSE;
    }

    @Override
    public String getDisplayName() {
        return "Root";
    }

    @Override
    public RootFilter getFilter() {
        return delegate;
    }

    @Override
    public String getSQLWhere(TimelineManager timelineManager) {
        return getActiveFilter().getSQLWhere(timelineManager);
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public boolean isSelected() {
        return true;
    }

    @Override
    public ReadOnlyBooleanProperty selectedProperty() {
        return ALWAYS_TRUE;
    }

    @Override
    public void setDisabled(Boolean act) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setSelected(Boolean act) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
