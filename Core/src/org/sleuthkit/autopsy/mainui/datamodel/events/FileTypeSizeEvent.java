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
package org.sleuthkit.autopsy.mainui.datamodel.events;

import java.util.Objects;
import org.sleuthkit.autopsy.mainui.datamodel.FileSizeFilter;

/**
 * An event to signal that files have been added or removed within the given
 * size range on the given data source.
 */
public class FileTypeSizeEvent implements DAOEvent {

    private final FileSizeFilter sizeFilter;
    private final Long dataSourceId;

    /**
     * Main constructor.
     *
     * @param sizeFilter   The size filter. If null, indicates full refresh is
     *                     necessary.
     * @param dataSourceId The data source id or null.
     */
    public FileTypeSizeEvent(FileSizeFilter sizeFilter, Long dataSourceId) {
        this.sizeFilter = sizeFilter;
        this.dataSourceId = dataSourceId;
    }

    public FileSizeFilter getSizeFilter() {
        return sizeFilter;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 73 * hash + Objects.hashCode(this.sizeFilter);
        hash = 73 * hash + Objects.hashCode(this.dataSourceId);
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
        final FileTypeSizeEvent other = (FileTypeSizeEvent) obj;
        if (this.sizeFilter != other.sizeFilter) {
            return false;
        }
        if (!Objects.equals(this.dataSourceId, other.dataSourceId)) {
            return false;
        }
        return true;
    }



    @Override
    public Type getType() {
        return Type.RESULT;
    }
}
