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
import org.sleuthkit.autopsy.timeline.filters.CompoundFilter;
import org.sleuthkit.autopsy.timeline.filters.Filter;

/**
 * A TreeItem for a filter.
 */
final public class FilterTreeItem extends TreeItem<Filter> {

    /**
     * recursively construct a tree of treeitems to parallel the filter tree of
     * the given filter
     *
     *
     * @param f the filter for this item. if f has sub-filters, tree items will
     *          be made for them added added to the children of this
     *          FilterTreeItem
     */
    public FilterTreeItem(Filter f, ObservableMap<Filter, Boolean> expansionMap) {
        super(f);

        expansionMap.addListener((MapChangeListener.Change<? extends Filter, ? extends Boolean> change) -> {
            if (change.getKey().equals(f)) {
                setExpanded(expansionMap.get(change.getKey()));
            }
        });

        if (expansionMap.containsKey(f)) {
            setExpanded(expansionMap.get(f));
        }

        expandedProperty().addListener(expandedProperty -> expansionMap.put(f, isExpanded()));

        if (f instanceof CompoundFilter<?>) {
            final CompoundFilter<?> compoundFilter = (CompoundFilter<?>) f;

            for (Filter subFilter : compoundFilter.getSubFilters()) {
                getChildren().add(new FilterTreeItem(subFilter, expansionMap));
            }

            compoundFilter.getSubFilters().addListener((ListChangeListener.Change<? extends Filter> c) -> {
                while (c.next()) {
                    for (Filter subfFilter : c.getAddedSubList()) {
                        setExpanded(true);
                        getChildren().add(new FilterTreeItem(subfFilter, expansionMap));
                    }
                }
            });

            compoundFilter.activeProperty().addListener(activeProperty -> {
                compoundFilter.getSubFilters().forEach(subFilter -> subFilter.setDisabled(compoundFilter.isActive() == false));
            });

        }

    }
}
