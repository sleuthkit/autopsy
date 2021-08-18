/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.TimeLineModule;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TimelineFilter;
import org.sleuthkit.datamodel.TimelineFilter.DataSourceFilter;
import org.sleuthkit.datamodel.TimelineFilter.RootFilter;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for interacting with Timeline in relation to data sources.
 */
public class TimelineDataSourceUtils {

    private static TimelineDataSourceUtils instance = null;

    /**
     * @return Singleton instance of this class.
     */
    public static TimelineDataSourceUtils getInstance() {
        if (instance == null) {
            instance = new TimelineDataSourceUtils();
        }

        return instance;
    }

    /**
     * Main constructor. Should be instantiated through getInstance().
     */
    private TimelineDataSourceUtils() {
    }

    /**
     * Retrieves a RootFilter based on the default filter state but only the
     * specified dataSource is selected.
     *
     * @param dataSource The data source.
     * @return The root filter representing a default filter with only this data
     * source selected.
     * @throws TskCoreException
     */
    public RootFilter getDataSourceFilter(DataSource dataSource) throws TskCoreException {
        RootFilterState filterState = getDataSourceFilterState(dataSource);
        return filterState == null ? null : filterState.getActiveFilter();
    }

    /**
     * Retrieves a TimeLineController based on the default filter state but only
     * the specified dataSource is selected.
     *
     * @param dataSource The data source.
     * @return The root filter state representing a default filter with only
     * this data source selected.
     * @throws TskCoreException
     */
    public RootFilterState getDataSourceFilterState(DataSource dataSource) throws TskCoreException {
        TimeLineController controller = TimeLineModule.getController();
        RootFilterState dataSourceState = controller.getEventsModel().getDefaultEventFilterState().copyOf();

        for (FilterState<? extends TimelineFilter.DataSourceFilter> filterState : dataSourceState.getDataSourcesFilterState().getSubFilterStates()) {
            DataSourceFilter dsFilter = filterState.getFilter();
            if (dsFilter != null) {
                filterState.setSelected(dsFilter.getDataSourceID() == dataSource.getId());
            }

        }

        return dataSourceState;
    }
}
