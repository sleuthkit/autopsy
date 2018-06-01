/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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
import org.sleuthkit.datamodel.timeline.filters.CompoundFilter;
import org.sleuthkit.datamodel.timeline.filters.Filter;

/**
 * A TreeItem for a filter.
 */
final public class FilterTreeItem extends TreeItem<Filter> {

    /**
     * recursively construct a tree of TreeItems to parallel the filter tree of
     * the given filter
     *
     *
     * @param filter       the filter for this item. if f has sub-filters, tree
     *                     items will be made for them added added to the
     *                     children of this FilterTreeItem
     * @param expansionMap
     */
    public FilterTreeItem(Filter filter, ObservableMap<Filter, Boolean> expansionMap) {
        super(filter);

        //listen to changes in the expansion map, and update expansion state of filter object
        expansionMap.addListener((MapChangeListener.Change<? extends Filter, ? extends Boolean> change) -> {
            if (change.getKey().equals(filter)) {
                setExpanded(expansionMap.get(change.getKey()));
            }
        });

        if (expansionMap.containsKey(filter)) {
            setExpanded(expansionMap.get(filter));
        }

        //keep expanion map upto date if user expands/collapses filter
        expandedProperty().addListener(expandedProperty -> expansionMap.put(filter, isExpanded()));

        //if the filter is a compound filter, add its subfilters to the tree
        if (filter instanceof CompoundFilter<?>) {
            final CompoundFilter<?> compoundFilter = (CompoundFilter<?>) filter;

            //add all sub filters
            compoundFilter.getSubFilters().forEach(subFilter -> getChildren().add(new FilterTreeItem(subFilter, expansionMap)));

            //listen to changes in sub filters and keep tree in sync
            compoundFilter.getSubFilters().addListener((ListChangeListener.Change<? extends Filter> c) -> {
                while (c.next()) {
                    for (Filter subfFilter : c.getAddedSubList()) {
                        setExpanded(true); //emphasize new filters by expanding parent to make sure they are visible
                        getChildren().add(new FilterTreeItem(subfFilter, expansionMap));
                    }
                }
            });

            /*
             * enforce the following relationship between a compound filter and
             * its subfilters: if a compound filter's active property changes,
             * disable the subfilters if the compound filter is not active.
             */
            compoundFilter.activeProperty().addListener(activeProperty -> {
                disableSubFiltersIfNotActive(compoundFilter);
            });

            disableSubFiltersIfNotActive(compoundFilter);
        }
    }

    /**
     * disable the sub-filters of the given compound filter if it is not active
     *
     * @param compoundFilter the compound filter
     */
    static private void disableSubFiltersIfNotActive(CompoundFilter<?> compoundFilter) {
        boolean inactive = compoundFilter.isActive() == false;
        compoundFilter.getSubFilters().forEach(subFilter -> subFilter.setDisabled(inactive));
    }
}
