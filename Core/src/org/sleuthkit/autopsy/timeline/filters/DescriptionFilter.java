/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;

public class DescriptionFilter extends AbstractFilter {

    private final DescriptionLOD descriptionLoD;

    private final String description;
    private final FilterMode filterMode;

    public FilterMode getFilterMode() {
        return filterMode;
    }

    public DescriptionFilter(DescriptionLOD descriptionLoD, String description, FilterMode filterMode) {
        this.descriptionLoD = descriptionLoD;
        this.description = description;
        this.filterMode = filterMode;
    }

    @Override
    public DescriptionFilter copyOf() {
        DescriptionFilter filterCopy = new DescriptionFilter(getDescriptionLoD(), getDescription(), getFilterMode());
        filterCopy.setSelected(isSelected());
        filterCopy.setDisabled(isDisabled());
        return filterCopy;
    }

    @Override
    public String getDisplayName() {
        return "description";
    }

    @Override
    public String getHTMLReportString() {
        return getDescriptionLoD().getDisplayName() + " " + getDisplayName() + " = " + getDescription();
    }

    /**
     * @return the descriptionLoD
     */
    public DescriptionLOD getDescriptionLoD() {
        return descriptionLoD;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    public enum FilterMode {

        EXCLUDE,
        INCLUDE;
    }

    @Override
    public boolean test(EventBundle t) {
        if (filterMode == FilterMode.INCLUDE) {
            return getDescription().equals(t.getDescription())
                    && getDescriptionLoD() == t.getDescriptionLOD();
        } else {
            return (getDescription().equals(t.getDescription()) == false)
                    || (getDescriptionLoD() != t.getDescriptionLOD() == false);
        }

    }

}
