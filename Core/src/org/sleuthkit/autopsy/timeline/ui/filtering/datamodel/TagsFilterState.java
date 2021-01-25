/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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

import org.sleuthkit.datamodel.TimelineFilter;

/**
 * A wrapper for a TimelineFilter.TagsFilter object that allows it to be
 * displayed by the timeline GUI via the filter panel by providing selected,
 * disabled, and active properties for the TagsFilter.
 */
public class TagsFilterState extends SqlFilterState<TimelineFilter.TagsFilter> {

    /**
     * Constructs a wrapper for a TimelineFilter.TagsFilter object that allows
     * it to be displayed by the timeline GUI via the filter panel by providing
     * selected, disabled, and active properties for the TagsFilter.
     *
     * @param tagsFilter A TimelineFilter.TagsFilter object.
     */
    public TagsFilterState(TimelineFilter.TagsFilter tagsFilter) {
        super(tagsFilter);
        addSelectionListener();
    }

    /**
     * "Copy constructs" a wrapper for a TimelineFilter.TagsFilter object
     * that allows it to be displayed by the timeline GUI via the filter panel
     * by providing selected, disabled, and active properties for the
     * TagsFilter.
     *
     * @param other A TagsFilterState object.
     */
    public TagsFilterState(TagsFilterState other) {
        super(other.getFilter().copyOf());
        setSelected(other.isSelected());
        setDisabled(other.isDisabled());
        addSelectionListener();
    }

    /*
     * Adds a listener to the selected property that updates the flag that turns
     * the wrapped tags filter on/off.
     */
    private void addSelectionListener() {
        selectedProperty().addListener(selectedProperty -> {
            getFilter().setEventSourcesAreTagged(isSelected());
        });
    }

}
