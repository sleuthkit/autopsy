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
package org.sleuthkit.autopsy.casemodule.events;

import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagSet;
import org.sleuthkit.datamodel.TaggingManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A base class for TagSet added and update events.
 */
public class TagSetsEvent extends TskDataModelChangedEvent<TagSet, TagSet> {

    private static final long serialVersionUID = 1L;

    /**
     * Construct a new TagSetEvent.
     *
     * @param eventName
     * @param tagSets
     */
    private TagSetsEvent(String eventName, List<TagSet> tagSets) {
        super(eventName, null, null, tagSets, TagSet::getId);
    }

    /**
     * Returns a list of the TagSet objects that were added or modified for this
     * event.
     *
     * @return A list of TagSet objects.
     */
    public List<TagSet> getTagSets() {
        return this.getNewValue();
    }

    @Override
    protected List<TagSet> getNewValueObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        List<TagSet> tagSets = new ArrayList<>();
        TaggingManager taggingMrg = caseDb.getTaggingManager();
        for (Long id : ids) {
            tagSets.add(taggingMrg.getTagSet(id));
        }
        return tagSets;
    }

    /**
     * Application events published when TagSets have been Added from the Sleuth
     * Kit data model for a case.
     */
    public static class TagSetsAddedEvent extends TagSetsEvent {

        private static final long serialVersionUID = 1L;

        /**
         * Construct an application event published when TagSetss have been
         * added to the Sleuth Kit data model.
         *
         * @param tagSets The TagSets that have been added.
         */
        public TagSetsAddedEvent(List<TagSet> tagSets) {
            super(Case.Events.TAG_SETS_ADDED.name(), tagSets);
        }
    }

    /**
     * Application events published when TagSets have been deleted from the
     * Sleuth Kit data model for a case.
     */
    public static class TagSetsDeletedEvent extends TskDataModelObjectsDeletedEvent {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an application event published when the TagSets have been
         * deleted from the Sleuth Kit data model for a case.
         *
         * @param tagNameIds The IDs of the TagNames that have been deleted.
         */
        public TagSetsDeletedEvent(List<Long> tagNameIds) {
            super(Case.Events.TAG_SETS_DELETED.name(), tagNameIds);
        }
    }
}
