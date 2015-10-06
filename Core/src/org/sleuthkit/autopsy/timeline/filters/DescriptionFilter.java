/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;

public class DescriptionFilter extends AbstractFilter {

    private final DescriptionLOD descriptionLoD;

    private final String description;

    public DescriptionFilter(DescriptionLOD descriptionLoD, String description) {
        this.descriptionLoD = descriptionLoD;
        this.description = description;
    }

    @Override
    public DescriptionFilter copyOf() {
        DescriptionFilter filterCopy = new DescriptionFilter(getDescriptionLoD(), getDescription());
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

}
