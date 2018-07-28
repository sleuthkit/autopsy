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

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.collections.ListChangeListener;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TagNameFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TagsFilter;

/**
 *
 */
public class TagsFilterState extends CompoundFilterStateImpl<TagNameFilter, TagsFilter> {

    public TagsFilterState(TagsFilter delegate) {
        super(delegate);
        installSelectNewFiltersListener();

    }

    public TagsFilterState(TagsFilter delegate, Collection<FilterState<TagNameFilter>> subFilterStates) {
        super(delegate, subFilterStates);
        installSelectNewFiltersListener();
    }

    private void installSelectNewFiltersListener() {
        getSubFilterStates().addListener((ListChangeListener.Change<? extends FilterState<TagNameFilter>> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(filterState -> filterState.setSelected(true));
            }
        });
    }

    @Override
    public TagsFilterState copyOf() {
        @SuppressWarnings("unchecked")
        TagsFilterState copy = new TagsFilterState(getFilter().copyOf(),
                getSubFilterStates().stream().map(FilterState::copyOf).collect(Collectors.toList())
        );

        copy.setSelected(isSelected());
        copy.setDisabled(isDisabled());
        return copy;
    }

    @Override
    public TagsFilter getActiveFilter() {
        if (isActive() == false) {
            return null;
        }

        Set<TagNameFilter> activeSubFilters = getSubFilterStates().stream()
                .filter(FilterState::isActive)
                .map(FilterState::getActiveFilter)
                .collect(Collectors.toSet());
        TagsFilter copy = new TagsFilter();
        activeSubFilters.forEach(copy::addSubFilter);

        return copy;
    }
}
