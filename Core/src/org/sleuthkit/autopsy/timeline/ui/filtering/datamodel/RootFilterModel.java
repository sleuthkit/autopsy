/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import javafx.collections.ObservableList;
import org.sleuthkit.datamodel.timeline.filters.DataSourcesFilter;
import org.sleuthkit.datamodel.timeline.filters.HashHitsFilter;
import org.sleuthkit.datamodel.timeline.filters.HideKnownFilter;
import org.sleuthkit.datamodel.timeline.filters.RootFilter;
import org.sleuthkit.datamodel.timeline.filters.TagsFilter;
import org.sleuthkit.datamodel.timeline.filters.TextFilter;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;
import org.sleuthkit.datamodel.timeline.filters.TypeFilter;

/**
 *
 */
public class RootFilterModel extends DefaultFilterModel<RootFilter> {

    public RootFilterModel(RootFilter delegate) {
        super(delegate);
    }

    public DataSourcesFilter getDataSourcesFilter() {
        return getFilter().getDataSourcesFilter();
    }

    public TagsFilter getTagsFilter() {
        return getFilter().getTagsFilter();
    }

    public HashHitsFilter getHashHitsFilter() {
        return getFilter().getHashHitsFilter();
    }

    public TypeFilter getTypeFilter() {
        return getFilter().getTypeFilter();
    }

    public HideKnownFilter getKnownFilter() {
        return getFilter().getKnownFilter();
    }

    public TextFilter getTextFilter() {
        return getFilter().getTextFilter();
    }

    @Override
    public RootFilterModel copyOf() {
        RootFilterModel copy = new RootFilterModel(getFilter().copyOf());
        return copy;
    }

    public ObservableList<TimelineFilter> getSubFilters() {
        return getFilter().getSubFilters();
    }

    public RootFilter getActiveSubFiltersRecursive() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
