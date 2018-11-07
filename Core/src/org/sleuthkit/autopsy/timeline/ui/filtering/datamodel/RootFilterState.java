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

import com.google.common.collect.Lists;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import static org.apache.commons.lang3.ObjectUtils.notEqual;
import org.sleuthkit.datamodel.timeline.TimelineFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.DataSourceFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.DataSourcesFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.EventTypeFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.FileTypeFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.FileTypesFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.HashHitsFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.HashSetFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.HideKnownFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.RootFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TextFilter;

/**
 */
public class RootFilterState implements CompoundFilterState< TimelineFilter, RootFilter> {

    private final CompoundFilterState<EventTypeFilter, EventTypeFilter> eventTypeFilterState;
    private final DefaultFilterState<HideKnownFilter> knownFilterState;
    private final DefaultFilterState<TextFilter> textFilterState;
    private final TagsFilterState tagsFilterState;
    private final CompoundFilterState<HashSetFilter, HashHitsFilter> hashHitsFilterState;
    private final CompoundFilterState<DataSourceFilter, DataSourcesFilter> dataSourcesFilterState;
    private final CompoundFilterState<TimelineFilter.FileTypeFilter, TimelineFilter.FileTypesFilter> fileTypesFilterState;

    private static final ReadOnlyBooleanProperty ALWAYS_TRUE = new ReadOnlyBooleanWrapper(true).getReadOnlyProperty();
    private final static ReadOnlyBooleanProperty ALWAYS_FALSE = new ReadOnlyBooleanWrapper(false).getReadOnlyProperty();

    private final ObservableList<   FilterState< ?>> subFilterStates = FXCollections.observableArrayList();
    private final RootFilter delegate;

    public RootFilterState(RootFilter delegate) {
        this(delegate,
                new CompoundFilterStateImpl<>(delegate.getEventTypeFilter()),
                new DefaultFilterState<>(delegate.getKnownFilter()),
                new DefaultFilterState<>(delegate.getTextFilter()),
                new TagsFilterState(delegate.getTagsFilter()),
                new CompoundFilterStateImpl<>(delegate.getHashHitsFilter()),
                new CompoundFilterStateImpl<>(delegate.getDataSourcesFilter()),
                new CompoundFilterStateImpl<>(delegate.getFileTypesFilter())
        );
    }

    private RootFilterState(RootFilter delegate,
                            CompoundFilterState<EventTypeFilter, EventTypeFilter> eventTypeFilterState,
                            DefaultFilterState<HideKnownFilter> knownFilterState,
                            DefaultFilterState<TextFilter> textFilterState,
                            TagsFilterState tagsFilterState,
                            CompoundFilterState<HashSetFilter, HashHitsFilter> hashHitsFilterState,
                            CompoundFilterState<DataSourceFilter, DataSourcesFilter> dataSourcesFilterState,
                            CompoundFilterState<FileTypeFilter, FileTypesFilter> fileTypesFilterState) {
        this.delegate = delegate;
        this.eventTypeFilterState = eventTypeFilterState;
        this.knownFilterState = knownFilterState;
        this.textFilterState = textFilterState;
        this.tagsFilterState = tagsFilterState;
        this.hashHitsFilterState = hashHitsFilterState;
        this.dataSourcesFilterState = dataSourcesFilterState;
        this.fileTypesFilterState = fileTypesFilterState;
        subFilterStates.addAll(
                knownFilterState,
                textFilterState,
                tagsFilterState,
                hashHitsFilterState,
                dataSourcesFilterState,
                fileTypesFilterState,
                 eventTypeFilterState);
    }

    /**
     * Get a new root filter that intersects the given filter with this one.
     *
     * @param otherFilter
     *
     * @return A new RootFilter model that intersects the given filter with this
     *         one.
     */
    public RootFilterState intersect(TimelineFilter otherFilter) {
        RootFilterState copyOf = copyOf();
        copyOf.addSubFilterState(otherFilter);
        return copyOf;
    }

    private void addSubFilterState(TimelineFilter subFilter) {

        if (subFilter instanceof TimelineFilter.CompoundFilter<?>) {
            CompoundFilterStateImpl<? extends TimelineFilter, ? extends TimelineFilter.CompoundFilter<? extends TimelineFilter>> compoundFilterStateImpl = new CompoundFilterStateImpl<>((TimelineFilter.CompoundFilter<?>) subFilter);
            getSubFilterStates().add(compoundFilterStateImpl);
            compoundFilterStateImpl.setSelected(Boolean.TRUE);
        } else {
            DefaultFilterState<TimelineFilter> defaultFilterState = new DefaultFilterState<>(subFilter);
            getSubFilterStates().add(defaultFilterState);
            defaultFilterState.setSelected(Boolean.TRUE);
        }
    }

    @Override
    public RootFilterState copyOf() {
        return new RootFilterState(getFilter().copyOf(),
                getEventTypeFilterState().copyOf(),
                getKnownFilterState().copyOf(),
                getTextFilterState().copyOf(),
                getTagsFilterState().copyOf(),
                getHashHitsFilterState().copyOf(),
                getDataSourcesFilterState().copyOf(),
                getFileTypesFilterState().copyOf()
        );
    }

    public CompoundFilterState<EventTypeFilter, EventTypeFilter> getEventTypeFilterState() {
        return eventTypeFilterState;
    }

    public DefaultFilterState<HideKnownFilter> getKnownFilterState() {
        return knownFilterState;
    }

    public DefaultFilterState<TextFilter> getTextFilterState() {
        return textFilterState;
    }

    public TagsFilterState getTagsFilterState() {
        return tagsFilterState;
    }

    public CompoundFilterState<HashSetFilter, HashHitsFilter> getHashHitsFilterState() {
        return hashHitsFilterState;
    }

    public CompoundFilterState<DataSourceFilter, DataSourcesFilter> getDataSourcesFilterState() {
        return dataSourcesFilterState;
    }

    public CompoundFilterState<FileTypeFilter, FileTypesFilter> getFileTypesFilterState() {
        return fileTypesFilterState;
    }

    @Override
    public RootFilter getActiveFilter() {
        return new RootFilter(knownFilterState.getActiveFilter(),
                tagsFilterState.getActiveFilter(),
                hashHitsFilterState.getActiveFilter(),
                textFilterState.getActiveFilter(),
                eventTypeFilterState.getActiveFilter(),
                dataSourcesFilterState.getActiveFilter(),
                fileTypesFilterState.getActiveFilter(),
                Lists.transform(subFilterStates, FilterState::getActiveFilter));
    }

    @SuppressWarnings("rawtypes")
    public boolean hasActiveHashFilters() {
        return hashHitsFilterState.isActive()
               && hashHitsFilterState.getSubFilterStates().stream().anyMatch(FilterState::isActive);
    }

    @SuppressWarnings("rawtypes")
    public boolean hasActiveTagsFilters() {
        return tagsFilterState.isActive()
               && tagsFilterState.getSubFilterStates().stream().anyMatch(FilterState::isActive);
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

        RootFilterState otherFilterState = (RootFilterState) obj;

        RootFilter activeFilter = getActiveFilter();
        RootFilter activeFilter1 = otherFilterState.getActiveFilter();

        if (notEqual(activeFilter, activeFilter1)) {
            return false;
        }

        RootFilter filter = getFilter();
        RootFilter filter1 = otherFilterState.getFilter();

        return filter.equals(filter1);
    }

    @Override
    public int hashCode() {
        return 7;
    }

    @Override
    public ObservableList<  FilterState< ? extends TimelineFilter>> getSubFilterStates() {
        return subFilterStates;
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
