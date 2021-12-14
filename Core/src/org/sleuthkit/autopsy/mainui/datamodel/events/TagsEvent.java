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
import org.sleuthkit.autopsy.mainui.datamodel.TagsSearchParams.TagType;
import org.sleuthkit.datamodel.TagName;

/**
 * An event to signal that tags have been added or removed on the 
 * given data source with the given types. 
 */
public class TagsEvent implements DAOEvent {

    private final TagType type;
    private final TagName tagName;
    private final Long dataSourceId;

    public TagsEvent(TagType type, TagName tagName, Long dataSourceId) {
        this.type = type;
        this.tagName = tagName;
        this.dataSourceId = dataSourceId;
    }

    
    public TagType getTagType() {
        return type;
    }

    public TagName getTagName() {
        return tagName;
    }

    /**
     * @return The data source object id for the tag. Is null if cannot be
     *         determined.
     */
    public Long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.type);
        hash = 97 * hash + Objects.hashCode(this.tagName);
        hash = 97 * hash + Objects.hashCode(this.dataSourceId);
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
        final TagsEvent other = (TagsEvent) obj;
        if (this.type != other.type) {
            return false;
        }
        if (!Objects.equals(this.tagName, other.tagName)) {
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
