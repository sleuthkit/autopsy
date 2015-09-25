/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

import java.util.Comparator;
import javafx.beans.binding.Bindings;
import org.openide.util.NbBundle;
import static org.sleuthkit.autopsy.timeline.filters.CompoundFilter.areSubFiltersEqual;

/**
 *
 */
public class DescriptionsExclusionFilter extends IntersectionFilter<DescriptionFilter> {

    @Override
    @NbBundle.Messages("descriptionsExclusionFilter.displayName.text=Exclude Descriptions")
    public String getDisplayName() {
        return Bundle.descriptionsExclusionFilter_displayName_text();
    }

    public DescriptionsExclusionFilter() {
        getDisabledProperty().bind(Bindings.size(getSubFilters()).lessThan(1));
        setSelected(false);
    }

    @Override
    public DescriptionsExclusionFilter copyOf() {
        DescriptionsExclusionFilter filterCopy = new DescriptionsExclusionFilter();
        filterCopy.setSelected(isSelected());
        //add a copy of each subfilter
        this.getSubFilters().forEach((DescriptionFilter t) -> {
            filterCopy.addSubFilter(t.copyOf());
        });
        return filterCopy;
    }

    @Override
    public String getHTMLReportString() {
        //move this logic into SaveSnapshot
        String string = getDisplayName() + getStringCheckBox();
        if (getSubFilters().isEmpty() == false) {
            string = string + " : " + super.getHTMLReportString();
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
        final DescriptionsExclusionFilter other = (DescriptionsExclusionFilter) obj;

        if (isSelected() != other.isSelected()) {
            return false;
        }

        return areSubFiltersEqual(this, other);
    }

    public void addSubFilter(DescriptionFilter hashSetFilter) {
        if (getSubFilters().contains(hashSetFilter) == false) {
            getSubFilters().add(hashSetFilter);
            getSubFilters().sort(Comparator.comparing(DescriptionFilter::getDisplayName));
        }
    }
}
