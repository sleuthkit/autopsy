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
package org.sleuthkit.autopsy.mainui.datamodel.events;

import java.beans.PropertyChangeEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 *
 * Utilities for handling events in DAO
 */
public class DAOEventUtils {

    /**
     * Returns the content from the event. If the event does not
     * contain a event or the event does not contain Content, null
     * is returned.
     *
     * @param evt The event
     *
     * @return The inner content or null if no content.
     */
    public static Content getContentFromEvt(PropertyChangeEvent evt) {
        String eventName = evt.getPropertyName();
        Content derivedContent = getDerivedContentFromEvt(evt);
        if (derivedContent != null) {
            return derivedContent;
        } else if (IngestManager.IngestModuleEvent.FILE_DONE.toString().equals(eventName)
                && (evt.getNewValue() instanceof Content)) {
            return (Content) evt.getNewValue();
        } else {
            return null;
        }
    }
    
    /**
     * Returns the content from the ModuleContentEvent. If the event does not
     * contain a event or the event does not contain Content, null
     * is returned.
     * @param evt The event
     * @return The inner content or null if no content.
     */
    public static Content getDerivedContentFromEvt(PropertyChangeEvent evt) {
        String eventName = evt.getPropertyName();
        if (IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString().equals(eventName)
                && (evt.getOldValue() instanceof ModuleContentEvent)
                && ((ModuleContentEvent) evt.getOldValue()).getSource() instanceof Content) {

            return (Content) ((ModuleContentEvent) evt.getOldValue()).getSource();

        } else {
            return null;
        }
    }

    /**
     * Returns a file in the event if a file is found in the event.
     * @param evt The autopsy event.
     * @return The inner file or null if no file found.
     */
    public static AbstractFile getFileFromEvt(PropertyChangeEvent evt) {
        Content content = getContentFromEvt(evt);
        return (content instanceof AbstractFile)
                ? ((AbstractFile) content)
                : null;
    }

    /**
     * Returns the ModuleDataEvent in the event if there is a child
     * ModuleDataEvent. If not, null is returned.
     *
     * @param evt The event.
     *
     * @return The inner ModuleDataEvent or null.
     */
    public static ModuleDataEvent getModuleDataFromEvt(PropertyChangeEvent evt) {
        String eventName = evt.getPropertyName();
        if (IngestManager.IngestModuleEvent.DATA_ADDED.toString().equals(eventName)
                && (evt.getOldValue() instanceof ModuleDataEvent)) {

            return (ModuleDataEvent) evt.getOldValue();
        } else {
            return null;
        }
    }
}
