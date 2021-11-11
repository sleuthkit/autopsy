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

/**
 * An event where file type extensions could be affected.
 */
public class FileTypeExtensionsEvent implements DAOEvent {

    private final String extension;
    private final long dataSourceId;

    public FileTypeExtensionsEvent(String extension, long dataSourceId) {
        this.extension = extension;
        this.dataSourceId = dataSourceId;
    }

    public String getExtension() {
        return extension;
    }

    public long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.extension);
        hash = 59 * hash + (int) (this.dataSourceId ^ (this.dataSourceId >>> 32));
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
        if (this.dataSourceId != other.dataSourceId) {
            return false;
        }
        if (!Objects.equals(this.extension, other.extension)) {
            return false;
        }
        return true;
    }
    
    
}
