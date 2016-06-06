/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-16 Basis Technology Corp.
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

import java.util.function.Predicate;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import org.openide.util.NbBundle;

/**
 *
 */
public class HashHitsFilter extends UnionFilter<HashSetFilter> {

    @Override
    @NbBundle.Messages("hashHitsFilter.displayName.text=Hash Sets")
    public String getDisplayName() {
        return Bundle.hashHitsFilter_displayName_text();
    }

    public HashHitsFilter() {
        setSelected(false);
    }

    @Override
    public HashHitsFilter copyOf() {
        HashHitsFilter filterCopy = new HashHitsFilter();
        //add a copy of each subfilter
        this.getSubFilters().forEach(hashSetFilter -> filterCopy.addSubFilter(hashSetFilter.copyOf()));
        //these need to happen after the listeners fired by adding the subfilters 
        filterCopy.setSelected(isSelected());
        filterCopy.setDisabled(isDisabled());

        return filterCopy;
    }

   
    @Override
    public int hashCode() {
        return 7;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HashHitsFilter other = (HashHitsFilter) obj;

        if (isActive() != other.isActive()) {
            return false;
        }

        return areSubFiltersEqual(this, other);
    }

    @Override
    public ObservableBooleanValue disabledProperty() {
        return Bindings.or(super.disabledProperty(), Bindings.isEmpty(getSubFilters()));
    }

    @Override
    Predicate<HashSetFilter> getDuplicatePredicate(HashSetFilter subfilter) {
        return hashSetFilter -> subfilter.getHashSetID() == hashSetFilter.getHashSetID();
    }
}
