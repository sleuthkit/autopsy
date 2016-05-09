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

import java.util.Comparator;
import java.util.function.Predicate;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.TagName;

/**
 * Filter to show only events tag with the tagNames of the selected subfilters.
 */
public class TagsFilter extends UnionFilter<TagNameFilter> {

    @Override
    @NbBundle.Messages("tagsFilter.displayName.text=Tags")
    public String getDisplayName() {
        return Bundle.tagsFilter_displayName_text();
    }

    public TagsFilter() {
        setSelected(false);
    }

    @Override
    public TagsFilter copyOf() {
        TagsFilter filterCopy = new TagsFilter();
        //add a copy of each subfilter
        getSubFilters().forEach(tagNameFilter -> filterCopy.addSubFilter(tagNameFilter.copyOf()));
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
        final TagsFilter other = (TagsFilter) obj;

        if (isActive() != other.isActive()) {
            return false;
        }

        return areSubFiltersEqual(this, other);
    }

    public void removeFilterForTag(TagName tagName) {
        getSubFilters().removeIf(subfilter -> subfilter.getTagName().equals(tagName));
        getSubFilters().sort(Comparator.comparing(TagNameFilter::getDisplayName));
    }

    @Override
    public ObservableBooleanValue disabledProperty() {
        return Bindings.or(super.disabledProperty(), Bindings.isEmpty(getSubFilters()));
    }

    @Override
    Predicate<TagNameFilter> getDuplicatePredicate(TagNameFilter subfilter) {
        return tagNameFilter -> subfilter.getTagName().equals(tagNameFilter.getTagName());
    }

}
