/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest.events;

import java.io.Serializable;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Event published when new content is added to a case or there is a change a
 * recorded attribute of existing content. For example, a content changed event
 * should be published when an analysis (ingest) module adds an extracted or
 * carved file to a case. The "old" value is a legacy ModuleContentEvent object.
 * The "new" value is null.
 */
public final class ContentChangedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ContentChangedEvent.class.getName());
    private transient ModuleContentEvent eventData;

    /**
     * Constructs a event to be published when new content is added to a case or
     * there is a change a recorded attribute of existing content.
     *
     * @param eventData A ModuleContentEvent object containing the data
     *                  associated with the content addition or change.
     */
    public ContentChangedEvent(ModuleContentEvent eventData) {
        /**
         * Putting a serializable data holding object into newValue to allow for
         * lazy loading of the ModuleContent object. This bypasses the issues
         * related to the serialization and de-serialization of Content objects
         * when the event is published over a network.
         */
        super(
                IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString(),
                new SerializableEventData(eventData.getModuleName(), ((Content) eventData.getSource()).getId()),
                null
        );
    }

    /**
     * Gets the legacy ModuleContentEvent object associated with this event.
     * Note that the content object that was added or changed can be accessed
     * via the getSource() method of the ModuleContentEvent.
     *
     * @return The ModuleContentEvent.
     */
    @Override
    public Object getOldValue() {
        /**
         * The eventData field is set in the constructor, but it is transient so
         * it will become null when the event is serialized for publication over
         * a network. Doing a lazy load of the ModuleContentEvent object
         * bypasses the issues related to the serialization and de-serialization
         * of Content objects and may also save database round trips from other
         * nodes since subscribers to this event are often not interested in the
         * event data.
         */
        if (null != eventData) {
            return eventData;
        }
        try {
            SerializableEventData data = (SerializableEventData) super.getOldValue();
            Content content = Case.getOpenCase().getSleuthkitCase().getContentById(data.contentId);
            eventData = new ModuleContentEvent(data.moduleName, content);
            return eventData;
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Error doing lazy load for remote event", ex); //NON-NLS
            return null;
        }
    }

    /**
     * Data holder class.
     */
    private static final class SerializableEventData implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String moduleName;
        private final long contentId;

        private SerializableEventData(String moduleName, long contentId) {
            this.moduleName = moduleName;
            this.contentId = contentId;
        }

    }

}
