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
 * Search params for accessing data about deleted content.
 */
public class DeletedContentSearchParams {

    private static final String TYPE_ID = "DELETED_CONTENT";

    /**
     * @return The type id for this search parameter.
     */
    public static String getTypeId() {
        return TYPE_ID;
    }

    private final DeletedContentFilter filter;
    private final Long dataSourceId;

    /**
     * Main constructor.
     *
     * @param filter       The filter (if null, indicates full refresh
     *                     required).
     * @param dataSourceId The data source id or null.
     */
    public DeletedContentSearchParams(DeletedContentFilter filter, Long dataSourceId) {
        this.filter = filter;
        this.dataSourceId = dataSourceId;
    }

    public DeletedContentFilter getFilter() {
        return filter;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(this.filter);
        hash = 71 * hash + Objects.hashCode(this.dataSourceId);
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
        final DeletedContentSearchParams other = (DeletedContentSearchParams) obj;
        if (this.filter != other.filter) {
            return false;
        }
        if (!Objects.equals(this.dataSourceId, other.dataSourceId)) {
            return false;
        }
        return true;
    }

}
