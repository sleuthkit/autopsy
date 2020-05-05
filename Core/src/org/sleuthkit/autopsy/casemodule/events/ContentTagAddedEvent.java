/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent.DeletedContentTagInfo;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An event that is fired when a ContentTag is added.
 */
@Immutable
public class ContentTagAddedEvent extends TagAddedEvent<ContentTag, DeletedContentTagInfo> implements Serializable {

    private static final long serialVersionUID = 1L;

    public ContentTagAddedEvent(ContentTag newTag) {
        super(Case.Events.CONTENT_TAG_ADDED.toString(), newTag);
    }
    
    public ContentTagAddedEvent(ContentTag newTag, List<ContentTag> deletedTagList) {
        super(Case.Events.CONTENT_TAG_ADDED.toString(), newTag, getDeletedInfo(deletedTagList));
    }

    /**
     * get the ContentTag that was added by its id
     *
     * @return ContentTag that was added
     *
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     */
    @Override
    ContentTag getTagByID() throws NoCurrentCaseException, TskCoreException {
        return Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagByTagID(getTagID());
    }
    
    /**
     * Create a list of DeletedContentTagInfo objects from a list of ContentTags.
     * 
     * @param deletedTagList List of deleted ContentTags.
     * 
     * @return List of DeletedContentTagInfo objects or empty list if deletedTagList was empty or null.
     */
    private static List<DeletedContentTagInfo> getDeletedInfo(List<ContentTag> deletedTagList) {
        List<DeletedContentTagInfo> deletedInfoList = new ArrayList<>();
        if (deletedTagList != null) {
            for (ContentTag tag : deletedTagList) {
                deletedInfoList.add(new DeletedContentTagInfo(tag));
            }
        }

        return deletedInfoList;
    }
}
