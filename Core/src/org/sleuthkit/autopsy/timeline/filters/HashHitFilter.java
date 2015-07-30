/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

import org.openide.util.NbBundle;

/**
 *
 */
public class HashHitFilter extends AbstractFilter {

    @Override
    @NbBundle.Messages("hashHitFilter.displayName.text=Show only Hash Hits")
    public String getDisplayName() {
        return Bundle.hashHitFilter_displayName_text();
    }

    public HashHitFilter() {
        super();
        getActiveProperty().set(false);
    }

    @Override
    public HashHitFilter copyOf() {
        HashHitFilter hashHitFilter = new HashHitFilter();
        hashHitFilter.setSelected(isSelected());
        hashHitFilter.setDisabled(isDisabled());
        return hashHitFilter;
    }

    @Override
    public String getHTMLReportString() {
        return "only hash hits" + getStringCheckBox();// NON-NLS
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
        final HashHitFilter other = (HashHitFilter) obj;

        return isSelected() == other.isSelected();
    }
}
