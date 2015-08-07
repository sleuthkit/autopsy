/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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

import java.util.List;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

/**
 * A Filter with a collection of {@link Filter} sub-filters. If this
 * filter is not active than none of its sub-filters are applied either.
 * Concrete implementations can decide how to combine the sub-filters.
 *
 * a {@link CompoundFilter} uses listeners to enforce the following
 * relationships between it and its sub-filters:
 * <ol>
 * <le>if a filter becomes active, and all its sub-filters were inactive, make
 * them all active</le>
 * <le>if a filter becomes inactive and all its sub-filters were active, make
 * them all inactive</le>
 * <le>if a sub-filter changes active state set the parent filter active if any
 * of its sub-filters are active.</le>
 * </ol>
 */
public abstract class CompoundFilter<SubFilterType extends Filter> extends AbstractFilter {

    /** the list of sub-filters that make up this filter */
    private final ObservableList<SubFilterType> subFilters = FXCollections.observableArrayList();

    public final ObservableList<SubFilterType> getSubFilters() {
        return subFilters;
    }

    /** construct a compound filter from a list of other filters to combine.
     *
     * @param subFilters
     */
    public CompoundFilter(List<SubFilterType> subFilters) {
        super();

        //listen to changes in list of subfilters and add active state listener to newly added filters
        this.subFilters.addListener((ListChangeListener.Change<? extends SubFilterType> c) -> {
            while (c.next()) {
                addSubFilterListeners(c.getAddedSubList());
            }
        });
        this.subFilters.setAll(subFilters);
    }

    private void addSubFilterListeners(List<? extends SubFilterType> newSubfilters) {
        for (SubFilterType sf : newSubfilters) {
            //if a subfilter changes active state
            sf.getSelectedProperty().addListener((Observable observable) -> {
                //set this filter acttive af any of the subfilters are active.
                setSelected(getSubFilters().parallelStream().anyMatch(Filter::isSelected));
            });
        }
    }

    static <SubFilterType extends Filter> boolean hashEqualSubFilters(final CompoundFilter<SubFilterType> oneFilter, final CompoundFilter<SubFilterType> otherFilter) {
        if (oneFilter.getSubFilters().size() != otherFilter.getSubFilters().size()) {
            return false;
        }
        for (int i = 0; i < oneFilter.getSubFilters().size(); i++) {
            if (oneFilter.getSubFilters().get(i).equals(otherFilter.getSubFilters().get(i)) == false) {
                return false;
            }
        }
        return true;
    }
}
