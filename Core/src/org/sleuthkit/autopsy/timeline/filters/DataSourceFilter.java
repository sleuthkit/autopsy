/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.filters;

import java.util.Objects;

/**
 * Filter for an individual datasource
 */
public class DataSourceFilter extends AbstractFilter {

    private final String dataSourceName;
    private final long dataSourceID;

    public long getDataSourceID() {
        return dataSourceID;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public DataSourceFilter(String dataSourceName, long dataSourceID) {
        this.dataSourceName = dataSourceName;
        this.dataSourceID = dataSourceID;
    }

    @Override
    synchronized public DataSourceFilter copyOf() {
        DataSourceFilter filterCopy = new DataSourceFilter(getDataSourceName(), getDataSourceID());
        filterCopy.setSelected(isSelected());
        filterCopy.setDisabled(isDisabled());
        return filterCopy;
    }

    @Override
    public String getDisplayName() {
        return getDataSourceName();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.dataSourceName);
        hash = 97 * hash + (int) (this.dataSourceID ^ (this.dataSourceID >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DataSourceFilter other = (DataSourceFilter) obj;
        if (!Objects.equals(this.dataSourceName, other.dataSourceName)) {
            return false;
        }
        if (this.dataSourceID != other.dataSourceID) {
            return false;
        }
        return isSelected() == other.isSelected();
    }
}
