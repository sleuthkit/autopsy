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
        filterCopy.setSelected(isSelected());
        //add a copy of each subfilter
        this.getSubFilters().forEach((HashSetFilter t) -> {
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
        final HashHitsFilter other = (HashHitsFilter) obj;

        if (isSelected() != other.isSelected()) {
            return false;
        }

        return areSubFiltersEqual(this, other);
    }

    public void addSubFilter(HashSetFilter hashSetFilter) {
        if (getSubFilters().stream()
                .map(HashSetFilter::getHashSetID)
                .filter(t -> t == hashSetFilter.getHashSetID())
                .findAny().isPresent() == false) {
            getSubFilters().add(hashSetFilter);
            getSubFilters().sort(Comparator.comparing(HashSetFilter::getDisplayName));
        }
    }

    @Override
    public ObservableBooleanValue disabledProperty() {
        return Bindings.or(super.disabledProperty(), Bindings.isEmpty(getSubFilters()));
    }
}
