/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

import java.util.stream.Collectors;

/**
 *
 */
public class DataSourcesFilter extends UnionFilter {

    public DataSourcesFilter() {
    }

    @Override
    public DataSourcesFilter copyOf() {
        //make a nonrecursive copy of this filter
        final DataSourcesFilter filterCopy = new DataSourcesFilter();
        filterCopy.setActive(isActive());
        filterCopy.setDisabled(isDisabled());
        //add a copy of each subfilter
        this.getSubFilters().forEach((Filter t) -> {
            filterCopy.addDataSourceFilter((DataSourceFilter) t.copyOf());
        });

        return filterCopy;
    }

    @Override
    public String getDisplayName() {
        return "Data Source";
    }

    @Override
    public String getHTMLReportString() {
        String string = getDisplayName();
        if (getSubFilters().isEmpty() == false) {
            string = string + " : " + getSubFilters().stream().filter(Filter::isActive).map(Filter::getHTMLReportString).collect(Collectors.joining("</li><li>", "<ul><li>", "</li></ul>")); // NON-NLS
        }
        return string;
    }

    public void addDataSourceFilter(DataSourceFilter dataSourceFilter) {
        if (getSubFilters().stream().map(DataSourceFilter.class::cast)
                .map(DataSourceFilter::getDataSourceID)
                .filter(t -> t == dataSourceFilter.getDataSourceID())
                .findAny().isPresent() == false) {
            getSubFilters().add(dataSourceFilter);
        }
    }
}
