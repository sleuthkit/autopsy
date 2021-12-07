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
import org.sleuthkit.autopsy.datamodel.DeletedContent.DeletedContentFilter;

/**
 * An event to signal that deleted files have been added to the given case on
 * the given data source.
 */
public class DeletedContentEvent implements DAOEvent {

    private final DeletedContentFilter filter;
    private final Long dataSourceId;

    public DeletedContentEvent(DeletedContentFilter filter, Long dataSourceId) {
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
        int hash = 3;
        hash = 41 * hash + Objects.hashCode(this.filter);
        hash = 41 * hash + Objects.hashCode(this.dataSourceId);
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
        final DeletedContentEvent other = (DeletedContentEvent) obj;
        if (this.filter != other.filter) {
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
