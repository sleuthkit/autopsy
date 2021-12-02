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
 * An event pertaining to MIME types view from the DAO.
 */
public class FileTypeMimeEvent implements DAOEvent {

    private final String mimeTypePrefix;
    private final String mimeTypeSuffix;
    private final long dataSourceId;

    public FileTypeMimeEvent(String mimeTypePrefix, String mimeTypeSuffix, long dataSourceId) {
        this.mimeTypePrefix = mimeTypePrefix;
        this.mimeTypeSuffix = mimeTypeSuffix;
        this.dataSourceId = dataSourceId;
    }

    public String getMimeTypePrefix() {
        return mimeTypePrefix;
    }

    public String getMimeTypeSuffix() {
        return mimeTypeSuffix;
    }

    public long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(this.mimeTypePrefix);
        hash = 31 * hash + Objects.hashCode(this.mimeTypeSuffix);
        hash = 31 * hash + (int) (this.dataSourceId ^ (this.dataSourceId >>> 32));
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
        final FileTypeMimeEvent other = (FileTypeMimeEvent) obj;
        if (this.dataSourceId != other.dataSourceId) {
            return false;
        }
        if (!Objects.equals(this.mimeTypePrefix, other.mimeTypePrefix)) {
            return false;
        }
        if (!Objects.equals(this.mimeTypeSuffix, other.mimeTypeSuffix)) {
            return false;
        }
        return true;
    }

    

    @Override
    public Type getType() {
        return Type.RESULT;
    }
}
