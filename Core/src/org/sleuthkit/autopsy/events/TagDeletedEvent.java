/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.events;

import java.io.Serializable;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;

/**
 * Base Class for events that are fired when a Tag is deleted
 */
@Immutable
abstract class TagDeletedEvent<T extends Tag> extends AutopsyEvent {

    private static final long serialVersionUID = 1L;

    TagDeletedEvent(String propertyName, DeletedTagInfo<T> deletedTagInfo) {
        super(propertyName, deletedTagInfo, null);
    }

    /**
     * get info about the Tag that was deleted
     *
     * @return the Tag
     */
    @SuppressWarnings("unchecked")
    abstract public DeletedTagInfo<T> getDeletedTagInfo();

    @Immutable
    abstract static class DeletedTagInfo<T extends Tag> implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String comment;
        private final long tagID;
        private final TagName name;

        DeletedTagInfo(T deletedTag) {
            comment = deletedTag.getComment();
            tagID = deletedTag.getId();
            name = deletedTag.getName();
        }

        public String getComment() {
            return comment;
        }

        public long getTagID() {
            return tagID;
        }

        public TagName getName() {
            return name;
        }
    }
}
