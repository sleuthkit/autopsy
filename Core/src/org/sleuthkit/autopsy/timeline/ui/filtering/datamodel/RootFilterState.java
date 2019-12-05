/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.TimelineFilter;
import org.sleuthkit.datamodel.TimelineFilter.DataSourceFilter;
import org.sleuthkit.datamodel.TimelineFilter.DataSourcesFilter;
import org.sleuthkit.datamodel.TimelineFilter.EventTypeFilter;
import org.sleuthkit.datamodel.TimelineFilter.FileTypeFilter;
import org.sleuthkit.datamodel.TimelineFilter.FileTypesFilter;
import org.sleuthkit.datamodel.TimelineFilter.HashHitsFilter;
import org.sleuthkit.datamodel.TimelineFilter.HideKnownFilter;
import org.sleuthkit.datamodel.TimelineFilter.RootFilter;
import org.sleuthkit.datamodel.TimelineFilter.TextFilter;
import org.sleuthkit.datamodel.TimelineFilter.TagsFilter;

/**
 * An implementation of the FilterState interface that wraps a RootFilter object
 * for display via the timeline filter panel by providing selected, disabled,
 * and active properties for the object. The wrapped root filter is a compound
 * filter, so additional behavior is provided for the management of child
 * subfilter state objects.
 */
public class RootFilterState extends CompoundFilterState<TimelineFilter, RootFilter> {

    private final static BooleanProperty ALWAYS_TRUE = new SimpleBooleanProperty(true);
    private final static BooleanProperty ALWAYS_FALSE = new SimpleBooleanProperty(false);
    private final CompoundFilterState<EventTypeFilter, EventTypeFilter> eventTypeFilterState;
    private final SqlFilterState<HideKnownFilter> knownFilterState;
    private final TextFilterState textFilterState;
    private final TagsFilterState tagsFilterState;
    private final HashHitsFilterState hashHitsFilterState;
    private final CompoundFilterState<DataSourceFilter, DataSourcesFilter> dataSourcesFilterState;
    private final CompoundFilterState<TimelineFilter.FileTypeFilter, TimelineFilter.FileTypesFilter> fileTypesFilterState;
    private final Set<FilterState<? extends TimelineFilter>> namedFilterStates = new HashSet<>();

    /**
     * Constructs an implementation of the FilterState interface that wraps a
     * RootFilter object for display via the timeline filter panel by providing
     * selected, disabled, and active properties for the object. The underlying
     * root filter is a compound filter, so additional behavior is provided for
     * the management of child subfilter state objects.
     *
     * @param rootFilter The TimelineFilter.RootFilter object to be wrapped.
     */
    public RootFilterState(RootFilter rootFilter) {
        this(rootFilter,
                new CompoundFilterState<>(rootFilter.getEventTypeFilter()),
                new SqlFilterState<>(rootFilter.getKnownFilter()),
                new TextFilterState(rootFilter.getTextFilter()),
                new TagsFilterState(rootFilter.getTagsFilter()),
                new HashHitsFilterState(rootFilter.getHashHitsFilter()),
                new CompoundFilterState<>(rootFilter.getDataSourcesFilter()),
                new CompoundFilterState<>(rootFilter.getFileTypesFilter())
        );
    }

    /**
     * Constructs an implementation of the FilterState interface that wraps a
     * RootFilter object for display via the timeline filter panel by providing
     * selected, disabled, and active properties for the object. The underlying
     * root filter is a compound filter, so additional behavior is provided for
     * the management of child subfilter state objects.
     *
     * @param rootFilter             The TimelineFilter.RootFilter object to be
     *                               wrapped.
     * @param eventTypeFilterState   The top-level event types subfilter.
     * @param knownFilterState       The known state subfilter.
     * @param textFilterState        The text subfilter.
     * @param tagsFilterState        The tags subfilter.
     * @param hashHitsFilterState    The hash set hits subfilter.
     * @param dataSourcesFilterState The data sources subfilter.
     * @param fileTypesFilterState   The file types subfilter.
     */
    private RootFilterState(RootFilter rootFilter,
            CompoundFilterState<EventTypeFilter, EventTypeFilter> eventTypeFilterState,
            SqlFilterState<HideKnownFilter> knownFilterState,
            TextFilterState textFilterState,
            TagsFilterState tagsFilterState,
            HashHitsFilterState hashHitsFilterState,
            CompoundFilterState<DataSourceFilter, DataSourcesFilter> dataSourcesFilterState,
            CompoundFilterState<FileTypeFilter, FileTypesFilter> fileTypesFilterState) {
        super(rootFilter, Arrays.asList(eventTypeFilterState, knownFilterState, textFilterState, tagsFilterState, hashHitsFilterState, dataSourcesFilterState, fileTypesFilterState));
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
     * Gets a new root filter state that contains the intersection of the root
     * filter of a given root filter state with the root filter of this root
     * filter state.
     *
     * @param other A RootFilterState object.
     *
     * @return A new RootFilterState object.
     */
    public RootFilterState intersect(FilterState< ? extends TimelineFilter> other) {
        RootFilterState copyOf = copyOf();
        copyOf.addSubFilterState(other);
        return copyOf;
    }

    @Override
    public RootFilterState copyOf() {
        RootFilterState copy = new RootFilterState(getFilter().copyOf(),
                getEventTypeFilterState().copyOf(),
                getKnownFilterState().copyOf(),
                new TextFilterState(this.textFilterState),
                new TagsFilterState(this.tagsFilterState),
                new HashHitsFilterState(this.hashHitsFilterState),
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

    public SqlFilterState<HideKnownFilter> getKnownFilterState() {
        return knownFilterState;
    }

    public SqlFilterState<TextFilter> getTextFilterState() {
        return textFilterState;
    }

    public SqlFilterState<TagsFilter> getTagsFilterState() {
        return tagsFilterState;
    }

    public SqlFilterState<HashHitsFilter> getHashHitsFilterState() {
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

        if (false == Objects.equals(this.getFilter(), other.getFilter())) {
            return false;
        }

        return Objects.equals(this.getSubFilterStates(), other.getSubFilterStates());
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

    @NbBundle.Messages("RootFilterState.displayName=Root")
    @Override
    public String getDisplayName() {
        return Bundle.RootFilterState_displayName();
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
        /*
         * A RootFitlerState is always enabled, so disabling it is overridden as
         * a no-op.
         */
    }

    @Override
    public void setSelected(Boolean act) {
        /*
         * A RootFitlerState is always enabled, so enabling it is overridden as
         * a no-op.
         */
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
                + "\ndelegate=" + getFilter() + '}'; //NON-NLS
    }
}
