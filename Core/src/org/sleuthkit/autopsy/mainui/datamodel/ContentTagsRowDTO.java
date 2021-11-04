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

import java.util.List;
import org.sleuthkit.datamodel.ContentTag;

/**
 * A result row for a ContentTag.
 */
public class ContentTagsRowDTO extends BaseRowDTO {

    private static final String TYPE_ID = "CONTENT_TAG";

    private final ContentTag tag;

    public ContentTagsRowDTO(ContentTag tag, List<Object> cellValues, long id) {
        super(cellValues, TYPE_ID, id);
        this.tag = tag;
    }

    public static String getTypeIdForClass() {
        return TYPE_ID;
    }

    /**
     * Return the tag for this result row.
     *
     * @return The tag for this row.
     */
    public ContentTag getTag() {
        return tag;
    }
    
    /**
     * Returns the tags display name.
     * 
     * @return The display name for this tag.
     */
    public String getDisplayName() {
       return getCellValues().size() > 0
                ? getCellValues().get(0).toString()
                : "";
    }

}
