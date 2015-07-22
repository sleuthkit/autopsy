/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

import javafx.collections.FXCollections;

/**
 *
 */
public class RootFilter extends IntersectionFilter {

    private final HideKnownFilter knwonFilter;
    private final TextFilter textFilter;
    private final TypeFilter typeFilter;
    private final DataSourcesFilter dataSourcesFilter;

    public DataSourcesFilter getDataSourcesFilter() {
        return dataSourcesFilter;
    }

    public RootFilter(HideKnownFilter knownFilter, TextFilter textFilter, TypeFilter typeFilter, DataSourcesFilter dataSourceFilter) {
        super(FXCollections.observableArrayList(knownFilter, textFilter, dataSourceFilter, typeFilter));
        this.knwonFilter = knownFilter;
        this.textFilter = textFilter;
        this.typeFilter = typeFilter;
        this.dataSourcesFilter = dataSourceFilter;
    }

    @Override
    public RootFilter copyOf() {
        RootFilter filter = new RootFilter(knwonFilter.copyOf(), textFilter.copyOf(), typeFilter.copyOf(), dataSourcesFilter.copyOf());
        filter.setActive(isActive());
        filter.setDisabled(isDisabled());
        return filter;
    }

}
