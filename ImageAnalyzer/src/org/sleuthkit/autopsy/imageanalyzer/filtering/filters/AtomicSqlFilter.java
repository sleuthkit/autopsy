/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.imageanalyzer.filtering.filters;

import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute;

public class AtomicSqlFilter extends AtomicFilter implements SqlFilter {

    public AtomicSqlFilter(DrawableAttribute filterAttribute, FilterComparison filterComparisson, Object filterValue) {
        super(filterAttribute, filterComparisson, filterValue);
    }

    @Override
    public String getFilterQueryString() {
        return getFilterAttribute().attrName.name() + " " + getFilterComparisson().getSqlOperator() + " " + getFilterValue().toString();
    }
}
