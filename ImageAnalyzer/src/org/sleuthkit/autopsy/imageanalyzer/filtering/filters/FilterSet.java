/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.imageanalyzer.filtering.filters;

/**
 *
 * @author jonathan
 */
public class FilterSet extends IntersectionFilter<AttributeFilter> {

    @Override
    public String getDisplayName() {
        return "Filters";
    }
}
