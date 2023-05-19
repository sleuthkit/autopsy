/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.filtering;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.CompoundFilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;
import org.sleuthkit.datamodel.TimelineFilter;

/**
 * A TreeItem for a FilterState.
 */
class FilterTreeItem extends TreeItem<FilterState<?>> {

    /**
     * Recursively construct a tree of TreeItems to parallel the filter tree of
     * the given FilterState.
     *
     *
     * @param filterState  The FilterState for this item. If it has sub-filters,
     *                     tree items will be made for them added added to the
     *                     children of this FilterTreeItem
     * @param expansionMap Map from filter to whether it is expanded or not.
     */
    FilterTreeItem(FilterState<?> filterState, ObservableMap<Object, Boolean> expansionMap) {
        super(filterState);

        //keep expanion map upto date if user expands/collapses filter
        expandedProperty().addListener(expandedProperty -> expansionMap.put(filterState.getFilter(), true));
        setExpanded(true);

        //if the filter is a compound filter, add its subfilters to the tree
        if (filterState instanceof CompoundFilterState<?, ?>) {
            CompoundFilterState<?, ?> compoundFilter = (CompoundFilterState<?, ?>) filterState;

            //add all sub filters
            compoundFilter.getSubFilterStates().forEach(subFilterState -> {
                /*
                 * We removed the known_status column from the tsk_events table
                 * but have not yet added back the logic to implement that
                 * filter. For now, just hide it in the UI.
                 */
                if (subFilterState.getFilter() instanceof TimelineFilter.HideKnownFilter == false) {
                    getChildren().add(new FilterTreeItem(subFilterState, expansionMap));
                }
            });
            //listen to changes in sub filters and keep tree in sync
            compoundFilter.getSubFilterStates().addListener((ListChangeListener.Change<? extends FilterState<?>> change) -> {
                while (change.next()) {
                    for (FilterState<?> subFilterState : change.getAddedSubList()) {
                        setExpanded(true); //emphasize new filters by expanding parent to make sure they are visible
                        getChildren().add(new FilterTreeItem(subFilterState, expansionMap));
                    }
                }
            });

            compoundFilter.selectedProperty().addListener(observable -> {
                if (compoundFilter.isSelected()) {
                    setExpanded(true);
                }
            });
        }
    }
}
