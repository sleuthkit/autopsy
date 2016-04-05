/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

import java.util.Comparator;
import java.util.stream.Collectors;
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
        filterCopy.setSelected(isSelected());
        filterCopy.setDisabled(isDisabled());
        //add a copy of each subfilter
        this.getSubFilters().forEach((TagNameFilter t) -> {
            filterCopy.addSubFilter(t.copyOf());
        });
        return filterCopy;
    }

    @Override
    public String getHTMLReportString() {
        //move this logic into SaveSnapshot
        String string = getDisplayName() + getStringCheckBox();
        if (getSubFilters().isEmpty() == false) {
            string = string + " : " + getSubFilters().stream()
                    .filter(Filter::isSelected)
                    .map(Filter::getHTMLReportString)
                    .collect(Collectors.joining("</li><li>", "<ul><li>", "</li></ul>")); // NON-NLS
        }
        return string;
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

    public void addSubFilter(TagNameFilter tagFilter) {
        TagName newFilterTagName = tagFilter.getTagName();
        if (getSubFilters().stream()
                .map(TagNameFilter::getTagName)
                .filter(newFilterTagName::equals)
                .findAny().isPresent() == false) {
            getSubFilters().add(tagFilter);
        }
        getSubFilters().sort(Comparator.comparing(TagNameFilter::getDisplayName));
    }

    public void removeFilterForTag(TagName tagName) {
        getSubFilters().removeIf(subfilter -> subfilter.getTagName().equals(tagName));
        getSubFilters().sort(Comparator.comparing(TagNameFilter::getDisplayName));
    }

    @Override
    public ObservableBooleanValue disabledProperty() {
        return Bindings.or(super.disabledProperty(), Bindings.isEmpty(getSubFilters()));
    }
}
