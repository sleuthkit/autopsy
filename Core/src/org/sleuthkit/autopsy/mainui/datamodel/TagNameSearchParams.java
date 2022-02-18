/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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
import org.sleuthkit.datamodel.TagName;

/**
 *
 * Search param for a tag name.
 */
public class TagNameSearchParams {

    private static final String TYPE_ID = "TAG_TYPE";

    /**
     * @return The type id for this search parameter.
     */
    public static String getTypeId() {
        return TYPE_ID;
    }

    private final TagName tagName;
    private final Long dataSourceId;

    public TagNameSearchParams(TagName tagName, Long dataSourceId) {
        this.dataSourceId = dataSourceId;
        this.tagName = tagName;
    }

    public TagName getTagName() {
        return tagName;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.tagName);
        hash = 67 * hash + Objects.hashCode(this.dataSourceId);
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
        final TagNameSearchParams other = (TagNameSearchParams) obj;
        if (!Objects.equals(this.tagName, other.tagName)) {
            return false;
        }
        if (!Objects.equals(this.dataSourceId, other.dataSourceId)) {
            return false;
        }
        return true;
    }

    

}
