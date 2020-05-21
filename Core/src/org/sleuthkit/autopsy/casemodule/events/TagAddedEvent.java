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
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.TagDeletedEvent.DeletedTagInfo;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Base Class for events that are fired when a Tag is added
 */
abstract class TagAddedEvent<T extends Tag, V extends DeletedTagInfo<T>> extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The tag that was added. This will be lost during serialization and
     * re-loaded from the database in getNewValue()
     */
    private transient T tag;
    
    private List<V> deletedTagInfoList;

    /**
     * The id of the tag that was added. This will be used to re-load the
     * transient tag from the database.
     */
    private final Long tagID;

    TagAddedEvent(String propertyName, T addedTag) {
        this(propertyName, addedTag, null);
    }

    /**
     * Construct a TagAddedEvent.
     *
     * @param propertyName       Name of property changing
     * @param addedTag           Instance of added tag.
     * @param deletedTagInfoList List of tags deleted as a result of the
     *                           addition of addedTag.
     */
    TagAddedEvent(String propertyName, T addedTag, List<V> deletedTagInfoList) {
        super(propertyName, deletedTagInfoList, null);
        tag = addedTag;
        tagID = addedTag.getId();
        this.deletedTagInfoList = deletedTagInfoList;
    }

    /**
     * get the id of the Tag that was added
     *
     * @return the id of the Tag that was added
     */
    Long getTagID() {
        return tagID;
    }

    /**
     * get the Tag that was added
     *
     * @return the tTag
     */
    public T getAddedTag() {
        return getNewValue();
    }
    
    @Override
    public T getNewValue() {
        /**
         * The tag field is set in the constructor, but it is transient so it
         * will become null when the event is serialized for publication over a
         * network. Doing a lazy load of the Tag object bypasses the issues
         * related to the serialization and de-serialization of Tag objects and
         * may also save database round trips from other nodes since subscribers
         * to this event are often not interested in the event data.
         */
        if (null != tag) {
            return tag;
        }
        try {
            tag = getTagByID();
            return tag;
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Logger.getLogger(TagAddedEvent.class.getName()).log(Level.SEVERE, "Error doing lazy load for remote event", ex); //NON-NLS
            return null;
        }
    }
    
    /**
     * Returns the list of tags that were removed as a result of the addition 
     * of the T.
     * 
     * @return A list of removed tags or null if no tags were removed.
     */
    public List<V> getDeletedTags() {
        return deletedTagInfoList != null ? Collections.unmodifiableList(deletedTagInfoList) : null;
    }
    
    @Override
    public Object getOldValue() {
        return getDeletedTags();
    }

    /**
     * implementors should override this to lookup the appropriate kind of tag
     * (Content/BlackBoardArtifact) during the lazy load of the transient tag
     * field
     *
     *
     * @return the Tag based on the saved tag id
     *
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     */
    abstract T getTagByID() throws NoCurrentCaseException, TskCoreException;
}
