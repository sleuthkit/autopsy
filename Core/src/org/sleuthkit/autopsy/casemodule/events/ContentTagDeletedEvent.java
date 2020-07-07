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
package org.sleuthkit.autopsy.casemodule.events;

import java.io.Serializable;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.ContentTag;

/**
 * An event that is fired when a ContentTag is deleted.
 */
@Immutable
public class ContentTagDeletedEvent extends TagDeletedEvent<ContentTag> implements Serializable {

    private static final long serialVersionUID = 1L;

    public ContentTagDeletedEvent(ContentTag deletedTag) {
        super(Case.Events.CONTENT_TAG_DELETED.toString(), new DeletedContentTagInfo(deletedTag));
    }

    /**
     * {@inheritDoc }
     *
     * @return the DeletedContentTagInfo for the deleted tag
     */
    @Override
    public DeletedContentTagInfo getDeletedTagInfo() {
        return (DeletedContentTagInfo) getOldValue();
    }

    /**
     * Extension of DeletedTagInfo for BlackBoardArtifactTags that
     * includes byte offset related info.
     */
    @Immutable
    public static class DeletedContentTagInfo extends DeletedTagInfo<ContentTag> implements Serializable {

        private static final long serialVersionUID = 1L;

        private final long contentID;
        private final long beginByteOffset;
        private final long endByteOffset;

        DeletedContentTagInfo(ContentTag deletedTag) {
            super(deletedTag);
            beginByteOffset = deletedTag.getBeginByteOffset();
            endByteOffset = deletedTag.getEndByteOffset();
            contentID = deletedTag.getContent().getId();

        }

        @Override
        public long getContentID() {
            return contentID;
        }

        public long getBeginByteOffset() {
            return beginByteOffset;
        }

        public long getEndByteOffset() {
            return endByteOffset;
        }
    }
}
