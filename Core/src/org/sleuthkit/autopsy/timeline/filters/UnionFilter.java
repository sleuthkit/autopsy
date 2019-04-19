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
package org.sleuthkit.autopsy.timeline.filters;

import java.util.Comparator;
import java.util.function.Predicate;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Union(or) filter
 */
abstract public class UnionFilter<SubFilterType extends Filter> extends CompoundFilter<SubFilterType> {

    public UnionFilter(ObservableList<SubFilterType> subFilters) {
        super(subFilters);
    }

    public UnionFilter() {
        super(FXCollections.<SubFilterType>observableArrayList());
    }

    abstract Predicate<SubFilterType> getDuplicatePredicate(SubFilterType subfilter);

    public void addSubFilter(SubFilterType subfilter) {
        addSubFilter(subfilter, Comparator.comparing(SubFilterType::getDisplayName));
    }

    protected void addSubFilter(SubFilterType subfilter, Comparator<SubFilterType> comparator) {
        Predicate<SubFilterType> duplicatePredicate = getDuplicatePredicate(subfilter);
        if (getSubFilters().stream().anyMatch(duplicatePredicate) == false) {
            getSubFilters().add(subfilter);
        }
        getSubFilters().sort(comparator);
    }
}
