/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.imageanalyzer.filtering.filters;

import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute;

/**
 *
 * @author jonathan
 */
public class AttributeFilter<AVT, FVT> extends UnionFilter<AtomicFilter<AVT, FVT>> {

    DrawableAttribute filterAttribute;

    public AttributeFilter(DrawableAttribute filterAttribute) {
        super();
        this.filterAttribute = filterAttribute;
    }

    @Override
    public String getDisplayName() {
        return filterAttribute.getDisplayName();
    }

    public DrawableAttribute getAttribute() {
        return filterAttribute;
    }

    public boolean containsSubFilterForValue(FVT val) {
        try {
            for (AtomicFilter sf : subFilters) {
                if (val.equals(sf.getFilterValue())) {
                    return true;
                }
            }
        } catch (Exception e) {
        }

        return false;
    }
}
