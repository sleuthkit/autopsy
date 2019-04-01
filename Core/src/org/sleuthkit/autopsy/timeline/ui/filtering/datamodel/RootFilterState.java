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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
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
public class RootFilterState extends CompoundFilterState<TimelineFilter, RootFilter> {

    private final CompoundFilterState<EventTypeFilter, EventTypeFilter> eventTypeFilterState;
    private final DefaultFilterState<HideKnownFilter> knownFilterState;
    private final DefaultFilterState<TextFilter> textFilterState;
    private final TagsFilterState tagsFilterState;
    private final CompoundFilterState<HashSetFilter, HashHitsFilter> hashHitsFilterState;
    private final CompoundFilterState<DataSourceFilter, DataSourcesFilter> dataSourcesFilterState;
    private final CompoundFilterState<TimelineFilter.FileTypeFilter, TimelineFilter.FileTypesFilter> fileTypesFilterState;

    private static final BooleanProperty ALWAYS_TRUE = new SimpleBooleanProperty(true);
    private final static BooleanProperty ALWAYS_FALSE = new SimpleBooleanProperty(false);

    private final Set<   FilterState< ? extends TimelineFilter>> namedFilterStates = new HashSet<>();

    public RootFilterState(RootFilter delegate) {
        this(delegate,
                new CompoundFilterState<>(delegate.getEventTypeFilter()),
                new DefaultFilterState<>(delegate.getKnownFilter()),
                new DefaultFilterState<>(delegate.getTextFilter()),
                new TagsFilterState(delegate.getTagsFilter()),
                new CompoundFilterState<>(delegate.getHashHitsFilter()),
                new CompoundFilterState<>(delegate.getDataSourcesFilter()),
                new CompoundFilterState<>(delegate.getFileTypesFilter())
        );
    }

    private RootFilterState(RootFilter filter,
                            CompoundFilterState<EventTypeFilter, EventTypeFilter> eventTypeFilterState,
                            DefaultFilterState<HideKnownFilter> knownFilterState,
                            DefaultFilterState<TextFilter> textFilterState,
                            TagsFilterState tagsFilterState,
                            CompoundFilterState<HashSetFilter, HashHitsFilter> hashHitsFilterState,
                            CompoundFilterState<DataSourceFilter, DataSourcesFilter> dataSourcesFilterState,
                            CompoundFilterState<FileTypeFilter, FileTypesFilter> fileTypesFilterState) {
        super(filter, Arrays.asList(eventTypeFilterState, knownFilterState, textFilterState, tagsFilterState, hashHitsFilterState, dataSourcesFilterState, fileTypesFilterState));
        this.eventTypeFilterState = eventTypeFilterState;
        this.knownFilterState = knownFilterState;
        this.textFilterState = textFilterState;
        this.tagsFilterState = tagsFilterState;
        this.hashHitsFilterState = hashHitsFilterState;
        this.dataSourcesFilterState = dataSourcesFilterState;
        this.fileTypesFilterState = fileTypesFilterState;

        namedFilterStates.addAll(Arrays.asList(eventTypeFilterState, knownFilterState, textFilterState, tagsFilterState, hashHitsFilterState, dataSourcesFilterState, fileTypesFilterState));
    }

    /**
     * Get a new root filter that intersects the given filter with this one.
     *
     * @param otherFilter
     *
     * @return A new RootFilter model that intersects the given filter with this
     *         one.
     */
    public RootFilterState intersect(FilterState< ? extends TimelineFilter> otherFilter) {
        RootFilterState copyOf = copyOf();
        copyOf.addSubFilterState(otherFilter);
        return copyOf;
    }

    @Override
    public RootFilterState copyOf() {
        RootFilterState copy = new RootFilterState(getFilter().copyOf(),
                getEventTypeFilterState().copyOf(),
                getKnownFilterState().copyOf(),
                getTextFilterState().copyOf(),
                getTagsFilterState().copyOf(),
                getHashHitsFilterState().copyOf(),
                getDataSourcesFilterState().copyOf(),
                getFileTypesFilterState().copyOf()
        );
        this.getSubFilterStates().stream()
                .filter(filterState -> namedFilterStates.contains(filterState) == false)
                .forEach(copy::addSubFilterState);
        return copy;
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
                Lists.transform(getSubFilterStates(), FilterState::getActiveFilter));
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
    public ObservableList<FilterState<? extends TimelineFilter>> getSubFilterStates() {

        ImmutableMap<FilterState<? extends TimelineFilter>, Integer> filterOrder
                = ImmutableMap.<FilterState<? extends TimelineFilter>, Integer>builder()
                        .put(knownFilterState, 0)
                        .put(textFilterState, 1)
                        .put(tagsFilterState, 2)
                        .put(hashHitsFilterState, 3)
                        .put(dataSourcesFilterState, 4)
                        .put(fileTypesFilterState, 5)
                        .put(eventTypeFilterState, 6)
                        .build();

        return super.getSubFilterStates().sorted((state1, state2)
                -> Integer.compare(filterOrder.getOrDefault(state1, Integer.MAX_VALUE), filterOrder.getOrDefault(state2, Integer.MAX_VALUE)));

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

        final RootFilterState other = (RootFilterState) obj;
        if (!Objects.equals(this.getFilter(), other.getFilter())) {
            return false;
        }

        if (!Objects.equals(this.getSubFilterStates(), other.getSubFilterStates())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return 7;
    }

    @Override
    public BooleanProperty activeProperty() {
        return ALWAYS_TRUE;
    }

    @Override
    public BooleanProperty disabledProperty() {
        return ALWAYS_FALSE;
    }

    @Override
    public String getDisplayName() {
        return "Root";
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
    public BooleanProperty selectedProperty() {
        return ALWAYS_TRUE;
    }

    @Override
    public void setDisabled(Boolean act) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setSelected(Boolean act) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String toString() {
        return "RootFilterState{"
               + "\neventTypeFilterState=" + eventTypeFilterState + ","
               + "\nknownFilterState=" + knownFilterState + ","
               + "\ntextFilterState=" + textFilterState + ","
               + "\ntagsFilterState=" + tagsFilterState + ","
               + "\nhashHitsFilterState=" + hashHitsFilterState + ","
               + "\ndataSourcesFilterState=" + dataSourcesFilterState + ","
               + "\nfileTypesFilterState=" + fileTypesFilterState + ","
               + "\nsubFilterStates=" + getSubFilterStates() + ","
               + "\nnamedFilterStates=" + namedFilterStates + ","
               + "\ndelegate=" + getFilter() + '}';
    }
}
