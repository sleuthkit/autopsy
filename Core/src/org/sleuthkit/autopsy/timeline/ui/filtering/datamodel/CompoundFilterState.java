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

import javafx.collections.ObservableList;
import org.sleuthkit.datamodel.timeline.TimelineFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.CompoundFilter;

/**
 *
 * * A CompoundFilter uses listeners to enforce the following relationships
 * between it and its sub-filters: if all of a compound filter's sub-filters
 * become un-selected, un-select the compound filter.
 *
 * @param <SubFilterType>
 * @param <C>
 */
public interface CompoundFilterState<SubFilterType extends TimelineFilter, C extends CompoundFilter<SubFilterType>> extends FilterState<C> {

    ObservableList<? extends FilterState<? extends SubFilterType>> getSubFilterStates();

    @Override
    public CompoundFilterState<SubFilterType, C> copyOf();

 }
