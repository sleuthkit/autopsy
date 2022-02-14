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
import org.sleuthkit.datamodel.TagName;

/**
 * Key for accessing data about tags from the DAO.
 */
public class TagsSearchParams extends TagNameSearchParams {

    private static final String TYPE_ID = "TAG";

    /**
     * @return The type id for this search parameter.
     */
    public static String getTypeId() {
        return TYPE_ID;
    }

    public enum TagType {
        FILE,
        RESULT;
    }

    private final TagType type;

    public TagsSearchParams(TagName tagName, TagType type, Long dataSourceId) {
        super(tagName, dataSourceId);
        this.type = type;
    }

    public TagType getTagType() {
        return type;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.type);
        hash = 97 * hash + super.hashCode();
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
        final TagsSearchParams other = (TagsSearchParams) obj;
        if (this.type != other.type) {
            return false;
        }
        return super.equals(obj);
    }

    
}
