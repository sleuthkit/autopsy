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
import org.sleuthkit.datamodel.BlackboardArtifactTag;

/**
 * Event that is fired when a black board artifact tag is deleted.
 */
@Immutable
public class BlackBoardArtifactTagDeletedEvent extends TagDeletedEvent<BlackboardArtifactTag> implements Serializable {

    private static final long serialVersionUID = 1L;

    public BlackBoardArtifactTagDeletedEvent(BlackboardArtifactTag deletedTag) {
        super(Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED.toString(), new DeletedBlackboardArtifactTagInfo(deletedTag));
    }

    /**
     * {@inheritDoc }
     *
     * @return the DeletedBlackboardArtifactTagInfo for the deleted tag
     */
    @Override
    public DeletedBlackboardArtifactTagInfo getDeletedTagInfo() {
        return (DeletedBlackboardArtifactTagInfo) getOldValue();
    }

    /**
     * Extension of DeletedTagInfo for BlackBoardArtifactTags that
     * includes artifact related info.
     */
    @Immutable
    public static class DeletedBlackboardArtifactTagInfo extends DeletedTagInfo<BlackboardArtifactTag> implements Serializable {

        private static final long serialVersionUID = 1L;

        private final long contentID;

        private final long artifactID;

        DeletedBlackboardArtifactTagInfo(BlackboardArtifactTag deletedTag) {
            super(deletedTag);
            artifactID = deletedTag.getArtifact().getArtifactID();
            contentID = deletedTag.getContent().getId();
        }

        @Override
        public long getContentID() {
            return contentID;
        }

        public long getArtifactID() {
            return artifactID;
        }
    }
}
