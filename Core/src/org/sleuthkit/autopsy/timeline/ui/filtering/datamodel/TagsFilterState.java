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

import com.google.common.collect.Lists;
import java.util.Collection;
import javafx.collections.ListChangeListener;
import org.sleuthkit.datamodel.TimelineFilter.TagNameFilter;
import org.sleuthkit.datamodel.TimelineFilter.TagsFilter;

/**
 * Specialization of CompoundFilterState for TagName/Tags-Filter.
 *
 * Newly added subfilters made to be SELECTED when they are added.
 */
public class TagsFilterState extends CompoundFilterState<TagNameFilter, TagsFilter> {

    public TagsFilterState(TagsFilter delegate) {
        super(delegate);
        installSelectNewFiltersListener();

    }

    public TagsFilterState(TagsFilter delegate, Collection<FilterState<? extends TagNameFilter>> subFilterStates) {
        super(delegate, subFilterStates);
        installSelectNewFiltersListener();
    }

    private void installSelectNewFiltersListener() {
        getSubFilterStates().addListener((ListChangeListener.Change<? extends FilterState<? extends TagNameFilter>> change) -> {
            while (change.next()) {
                change.getAddedSubList().forEach(filterState -> filterState.setSelected(true));
            }
        });
    }

    @Override
    public TagsFilterState copyOf() {
        TagsFilterState copy = new TagsFilterState(getFilter().copyOf(),
                Lists.transform(getSubFilterStates(), FilterState::copyOf));

        copy.setSelected(isSelected());
        copy.setDisabled(isDisabled());
        return copy;
    }

    @Override
    public TagsFilter getActiveFilter() {
        if (isActive() == false) {
            return null;
        }

        TagsFilter copy = new TagsFilter();
        //add active subfilters to copy.
        getSubFilterStates().stream()
                .filter(FilterState::isActive)
                .map(FilterState::getActiveFilter)
                .forEach(copy::addSubFilter);

        return copy;
    }
}
