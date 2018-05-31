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
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.control.TreeItem;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.CompoundFilterStateI;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;

/**
 * A TreeItem for a filter.
 */
final public class FilterTreeItem extends TreeItem<FilterState<?>> {

    /**
     * recursively construct a tree of TreeItems to parallel the filter tree of
     * the given filter
     *
     *
     * @param filterModel  the filter for this item. if f has sub-filters, tree
     *                     items will be made for them added added to the
     *                     children of this FilterTreeItem
     * @param expansionMap
     */
    public FilterTreeItem(FilterState<?> filterModel, ObservableMap<TimelineFilter, Boolean> expansionMap) {
        super(filterModel);

        //listen to changes in the expansion map, and update expansion state of filter object
        expansionMap.addListener((MapChangeListener.Change<? extends TimelineFilter, ? extends Boolean> change) -> {
            if (change.getKey().equals(filterModel.getFilter())) {
                setExpanded(expansionMap.get(change.getKey()));
            }
        });

        setExpanded(expansionMap.getOrDefault(filterModel, Boolean.FALSE));

        //keep expanion map upto date if user expands/collapses filter
        expandedProperty().addListener(expandedProperty -> expansionMap.put(filterModel.getFilter(), isExpanded()));

        //if the filter is a compound filter, add its subfilters to the tree
        if (filterModel instanceof CompoundFilterStateI<?>) {
            final CompoundFilterStateI<?> compoundFilter = (CompoundFilterStateI<?>) filterModel;

            //add all sub filters
            compoundFilter.getSubFilterStates().forEach((subFilter) -> this.addSubfilter(subFilter, expansionMap));
            //listen to changes in sub filters and keep tree in sync
            compoundFilter.getSubFilterStates().addListener((ListChangeListener.Change<? extends FilterState<?>> change) -> {
                while (change.next()) {
                    for (FilterState<?> subFilter : change.getAddedSubList()) {
                        setExpanded(true); //emphasize new filters by expanding parent to make sure they are visible
                        addSubfilter(subFilter, expansionMap);
                    }
                }
            });

            /*
             * enforce the following relationship between a compound filter and
             * its subfilters: if a compound filter's active property changes,
             * disable the subfilters if the compound filter is not active.
             */
            filterModel.activeProperty().addListener(activeProperty -> {
                disableSubFiltersIfNotActive();
            });
            disableSubFiltersIfNotActive();

            //listen to changes in list of subtree items 
            getChildren().addListener((ListChangeListener.Change<? extends TreeItem<FilterState<?>>> change) -> {
                while (change.next()) {
                    //add a listener to the selected property of each added subfilter
                    change.getAddedSubList().forEach(addedSubFilter -> {

                        addedSubFilter.getValue().selectedProperty().addListener(selectedProperty -> {
                            //set this compound filter selected if any of the subfilters are selected.
                            filterModel.setSelected(getChildren().parallelStream()
                                    .map(TreeItem<FilterState<?>>::getValue)
                                    .anyMatch(FilterState::isSelected)
                            );
                        });
                    });
                }
            });
        }
    }

    private void addSubfilter(FilterState<?> subFilter, ObservableMap<TimelineFilter, Boolean> expansionMap) {
        FilterTreeItem filterTreeItem = new FilterTreeItem(subFilter, expansionMap);

        //if a subfilter's selected property changes...
        subFilter.selectedProperty().addListener(selectedProperty -> {
            //set this compound filter selected if any of the subfilters are selected.
            this.getValue().setSelected(getChildren().parallelStream()
                    .map(TreeItem<FilterState<?>>::getValue)
                    .anyMatch(FilterState::isSelected));
        });
        getChildren().add(filterTreeItem);
    }

    public boolean areAllSubFiltersActiveRecursive() {
        return getChildren().stream()
                .allMatch(subFilter -> {
                    return subFilter.getValue().isActive()
                           && subFilter.isLeaf()
                           || ((FilterTreeItem) subFilter).areAllSubFiltersActiveRecursive();
                });
    }

    /**
     * disable the sub-filters of the given compound filter if it is not active
     *
     * @param compoundFilter the compound filter
     */
    private void disableSubFiltersIfNotActive() {
        boolean inactive = getValue().isActive() == false;
        getChildren().stream()
                .map(TreeItem<FilterState<?>>::getValue)
                .forEach(subFilter -> subFilter.setDisabled(inactive));
    }
}
