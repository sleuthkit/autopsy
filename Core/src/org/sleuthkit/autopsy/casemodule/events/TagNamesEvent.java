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
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TaggingManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A base class for TagName added and update events.
 */
public class TagNamesEvent extends TskDataModelChangedEvent<TagName, TagName> {

    private static final long serialVersionUID = 1L;

    /**
     * Construct the base event for TagNames that have been added or updated.
     *
     * @param eventName The name of the event.
     * @param tagNames  The TagNames that have been modified.
     */
    private TagNamesEvent(String eventName, List<TagName> tagNames) {
        super(eventName, null, null, tagNames, TagName::getId);
    }

    /**
     * Returns a list of the added or modified TagNames.
     * 
     * @return The event list of TagNames.
     */
    public List<TagName> getTagNames() {
        return getNewValue();
    }

    @Override
    protected List<TagName> getNewValueObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        List<TagName> tagNames = new ArrayList<>();
        TaggingManager taggingMrg = caseDb.getTaggingManager();
        for (Long id : ids) {
            tagNames.add(taggingMrg.getTagName(id));
        }

        return tagNames;
    }

    /**
     * Application events published when TagNames have been Added from the
     * Sleuth Kit data model for a case.
     */
    public static class TagNamesAddedEvent extends TagNamesEvent {

        private static final long serialVersionUID = 1L;

        /**
         * Construct an application event published when TagNames have been
         * added to the Sleuth Kit data model.
         *
         * @param tagNames The TagNames that have been added.
         */
        public TagNamesAddedEvent(List<TagName> tagNames) {
            super(Case.Events.TAG_NAMES_ADDED.name(), tagNames);
        }
    }

    /**
     * Application events published when TagNames have been updated from the
     * Sleuth Kit data model for a case.
     */
    public static class TagNamesUpdatedEvent extends TagNamesEvent {

        private static final long serialVersionUID = 1L;

        /**
         * Construct an application event published when TagNames have been
         * updated in the Sleuth Kit data model.
         *
         * @param tagNames The TagNames that have been updated.
         */
        public TagNamesUpdatedEvent(List<TagName> tagNames) {
            super(Case.Events.TAG_NAMES_UPDATED.name(), tagNames);
        }
    }

    /**
     * Application events published when TagNames have been deleted from the
     * Sleuth Kit data model for a case.
     */
    public static class TagNamesDeletedEvent extends TskDataModelObjectsDeletedEvent {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an application event published when the TagNames have been
         * deleted from the Sleuth Kit data model for a case.
         *
         * @param tagNameIds The IDs of the TagNames that have been deleted.
         */
        public TagNamesDeletedEvent(List<Long> tagNameIds) {
            super(Case.Events.TAG_NAMES_DELETED.name(), tagNameIds);
        }
        
        public List<Long> getTagNameIds() {
            return getOldValue();
        }
    }
}
