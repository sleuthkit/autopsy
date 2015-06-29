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

import java.util.logging.Level;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Base Class for events that are fired when a Tag is deleted
 */
@Immutable
abstract class TagDeletedEvent<T extends Tag> extends TagEvent<T> {

    private static final Logger LOGGER = Logger.getLogger(TagDeletedEvent.class.getName());
    private transient T tag;
    private final Long tagID;

    @Override
    Long getTagID() {
        return tagID;
    }

    protected TagDeletedEvent(String propertyName, T oldValue) {
        super(Case.class, propertyName, oldValue, null);

    }

    /**
     * get the Tag that was deleted
     *
     * @return the Tag
     */
    @SuppressWarnings("unchecked")
    @Override
    public T getTag() {
        return (T) getOldValue();
    }

    @Override
    public Object getOldValue() {
        /**
         * The dataSource field is set in the constructor, but it is transient
         * so it will become null when the event is serialized for publication
         * over a network. Doing a lazy load of the Content object bypasses the
         * issues related to the serialization and de-serialization of Content
         * objects and may also save database round trips from other nodes since
         * subscribers to this event are often not interested in the event data.
         */
        if (null != tag) {
            return tag;
        }
        try {
            long id = tagID;
            tag = getTagByID(id);
            return tag;
        } catch (IllegalStateException | TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error doing lazy load for remote event", ex);
            return null;
        }
    }

}
