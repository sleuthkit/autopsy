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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.beans.PropertyChangeListener;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeListener;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.Content;

/**
 * Listener for changes that would affect case data or cached mainui.datamodel
 * data.
 */
abstract class AutopsyDAOEventListener {

    private static final Logger logger = Logger.getLogger(RegisteringDataEventListener.class.getName());

    /**
     * The relevant ingest module events.
     */
    private static final Set<IngestModuleEvent> INGEST_MODULE_EVENTS = EnumSet.of(IngestModuleEvent.CONTENT_CHANGED, IngestModuleEvent.DATA_ADDED);

    /**
     * The ingest module event listener.
     */
    private final PropertyChangeListener ingestModuleEventListener = (evt) -> {
        String eventName = evt.getPropertyName();
        if (IngestModuleEvent.DATA_ADDED.toString().equals(eventName)
                && (evt.getOldValue() instanceof ModuleDataEvent)) {

            this.onModuleData((ModuleDataEvent) evt.getOldValue());

        } else if (IngestModuleEvent.CONTENT_CHANGED.toString().equals(eventName)
                && (evt.getOldValue() instanceof ModuleContentEvent)
                && ((ModuleContentEvent) evt.getOldValue()).getSource() instanceof Content) {

            Content changedContent = (Content) ((ModuleContentEvent) evt.getOldValue()).getSource();
            this.onContentChange(changedContent);

        } else if (IngestModuleEvent.FILE_DONE.toString().equals(eventName)
                && evt.getNewValue() instanceof Content) {

            Content changedContent = (Content) evt.getNewValue();
            this.onContentChange(changedContent);

        } else {
            logger.log(Level.WARNING, MessageFormat.format("Unknown event with eventName: {0} and event: {1}.", eventName, evt));
        }
    };

    /**
     * The relevant case events.
     */
    private static final Set<Case.Events> CASE_EVENTS = EnumSet.of(Case.Events.CURRENT_CASE);

    /**
     * The case event listener.
     */
    private final PropertyChangeListener caseEventListener = (evt) -> {
        if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())
                && (evt.getOldValue() == null || evt.getOldValue() instanceof Case)
                && (evt.getNewValue() == null || evt.getNewValue() instanceof Case)) {
            this.onCaseChange((Case) evt.getOldValue(), (Case) evt.getNewValue());
        } else {
            logger.log(Level.WARNING, MessageFormat.format("Unknown event with eventName: {0} and event: {1}.", evt.getPropertyName(), evt));
        }
    };

    /**
     * The user preference listener.
     */
    private final PreferenceChangeListener userPreferenceListener = (evt) -> {
        if (evt.getKey().equals(UserPreferences.RESULTS_TABLE_PAGE_SIZE)) {
            int pageSize = UserPreferences.getResultsTablePageSize();
            this.onPageSizeChange(pageSize);
        }
    };

    /**
     * Registers listeners with autopsy event publishers.
     */
    protected void register() {
        IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS, ingestModuleEventListener);
        Case.addEventTypeSubscriber(CASE_EVENTS, caseEventListener);
        UserPreferences.addChangeListener(userPreferenceListener);
    }

    /**
     * Unregisters listeners from autopsy event publishers.
     */
    protected void unregister() {
        IngestManager.getInstance().removeIngestModuleEventListener(INGEST_MODULE_EVENTS, ingestModuleEventListener);
        Case.removeEventTypeSubscriber(CASE_EVENTS, caseEventListener);
        UserPreferences.removeChangeListener(userPreferenceListener);
    }
}
