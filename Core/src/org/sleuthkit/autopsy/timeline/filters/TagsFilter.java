/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.TagName;

/**
 *
 */
public class TagsFilter extends UnionFilter<TagNameFilter> {

    @Override
    @NbBundle.Messages("tagsFilter.displayName.text=Only Events Tagged")
    public String getDisplayName() {
        return Bundle.tagsFilter_displayName_text();
    }

    public TagsFilter() {
        getDisabledProperty().bind(Bindings.size(getSubFilters()).lessThan(1));
        setSelected(false);
    }

    @Override
    public TagsFilter copyOf() {
        TagsFilter filterCopy = new TagsFilter();
        filterCopy.setSelected(isSelected());
        //add a copy of each subfilter
        this.getSubFilters().forEach((TagNameFilter t) -> {
            filterCopy.addTagFilter(t.copyOf());
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

        if (isSelected() != other.isSelected()) {
            return false;
        }

        return areSubFiltersEqual(this, other);
    }

    public void addTagFilter(TagNameFilter tagFilter) {
        if (getSubFilters().stream().map(TagNameFilter.class::cast)
                .map(TagNameFilter::getTagName)
                .filter(t -> t == tagFilter.getTagName())
                .findAny().isPresent() == false) {
            getSubFilters().add(tagFilter);
        }
    }

    public void removeFilterForTag(TagName tagName) {
        getSubFilters().removeIf((TagNameFilter t) -> t.getTagName().equals(tagName));
    }
}
