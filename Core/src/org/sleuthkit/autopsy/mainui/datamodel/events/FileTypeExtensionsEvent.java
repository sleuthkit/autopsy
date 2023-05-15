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
import org.sleuthkit.autopsy.mainui.datamodel.FileExtSearchFilter;

/**
 * An event to signal that files have been added or removed with the given
 * extension on the given data source.
 */
public class FileTypeExtensionsEvent implements DAOEvent {

    private final FileExtSearchFilter extensionFilter;
    private final Long dataSourceId;

    /**
     * Main constructor.
     *
     * @param extensionFilter The extension filter. If null, indicates full
     *                        refresh necessary.
     * @param dataSourceId    The data source id.
     */
    public FileTypeExtensionsEvent(FileExtSearchFilter extensionFilter, Long dataSourceId) {
        this.extensionFilter = extensionFilter;
        this.dataSourceId = dataSourceId;
    }

    public FileExtSearchFilter getExtensionFilter() {
        return extensionFilter;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.extensionFilter);
        hash = 89 * hash + Objects.hashCode(this.dataSourceId);
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
        final FileTypeExtensionsEvent other = (FileTypeExtensionsEvent) obj;
        if (!Objects.equals(this.extensionFilter, other.extensionFilter)) {
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
