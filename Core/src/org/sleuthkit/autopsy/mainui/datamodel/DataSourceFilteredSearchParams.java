/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.Objects;

/**
 * Search parameters filtered by data source id.
 */
public class DataSourceFilteredSearchParams extends BaseSearchParams {

    private final Long dataSourceId;

    /**
     * Main constructor.
     *
     * @param dataSourceId    The data source id to filter on or null.
     * @param startItem       The starting item for paging.
     * @param maxResultsCount The maximum number of results.
     */
    public DataSourceFilteredSearchParams(long startItem, Long maxResultsCount, Long dataSourceId) {
        super(startItem, maxResultsCount);
        this.dataSourceId = dataSourceId;
    }

    /**
     * Main constructor.
     *
     * @param dataSourceId    The data source id to filter on or null.
     */
    public DataSourceFilteredSearchParams(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    /**
     * Returns the data source id to filter on or null.
     *
     * @return The data source id to filter on or null.
     */
    public Long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.dataSourceId);
        hash = 67 * hash + super.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DataSourceFilteredSearchParams other = (DataSourceFilteredSearchParams) obj;
        if (!Objects.equals(this.dataSourceId, other.dataSourceId)) {
            return false;
        }
        return super.equals(obj);
    }

}
