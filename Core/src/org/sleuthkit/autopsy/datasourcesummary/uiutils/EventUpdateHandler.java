/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.guiutils.RefreshThrottler;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;

/**
 * Handles ingest and case events, and determines whether they should trigger an
 * update.
 */
public class EventUpdateHandler {

    /**
     * The refresh throttler that handles ingest events.
     */
    private final RefreshThrottler refreshThrottler = new RefreshThrottler(new RefreshThrottler.Refresher() {
        @Override
        public void refresh() {
            // delegate to EventUpdateHandler method.
            EventUpdateHandler.this.onRefresh();
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (Case.isCaseOpen()) {
                if (IngestManager.IngestModuleEvent.DATA_ADDED.toString().equals(eventType) && evt.getOldValue() instanceof ModuleDataEvent) {
                    ModuleDataEvent dataEvent = (ModuleDataEvent) evt.getOldValue();
                    return EventUpdateHandler.this.isRefreshRequired(dataEvent);
                } else if (IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString().equals(eventType) && evt.getOldValue() instanceof ModuleContentEvent) {
                    ModuleContentEvent contentEvent = (ModuleContentEvent) evt.getOldValue();
                    return EventUpdateHandler.this.isRefreshRequired(contentEvent);
                }
            }
            return false;
        }
    });

    /**
     * Handler for case event updates.
     */
    private final PropertyChangeListener caseEventsListener = (evt) -> {
        if (isRefreshRequiredForCaseEvent(evt)) {
            onRefresh();
        }
    };

    private final List<UpdateGovernor> governors;
    private final Set<Case.Events> caseEvents;
    private final Runnable onUpdate;

    /**
     * Constructor.
     *
     * @param onUpdate  The function to call if an update should be required.
     * @param governors The items used to determine if an update is required. If
     *                  any governor requires an update, then onUpdate is
     *                  triggered.
     */
    public EventUpdateHandler(Runnable onUpdate, UpdateGovernor... governors) {
        if (onUpdate == null) {
            throw new IllegalArgumentException("onUpdate parameter must be non-null.");
        }

        this.onUpdate = onUpdate;

        this.governors = governors == null ? Collections.emptyList() : Arrays.asList(governors);

        this.caseEvents = Stream.of(governors)
                .filter(governor -> governor.getCaseEventUpdates() != null)
                .flatMap(governor -> governor.getCaseEventUpdates().stream())
                .collect(Collectors.toSet());

    }

    /**
     * Handles whether or not a ModuleDataEvent should trigger an update.
     *
     * @param evt The ModuleDataEvent.
     *
     * @return True if an update should occur.
     */
    protected boolean isRefreshRequired(ModuleDataEvent evt) {
        for (UpdateGovernor governor : governors) {
            if (governor.isRefreshRequired(evt)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Handles whether or not a ModuleContentEvent should trigger an update.
     *
     * @param evt The ModuleContentEvent.
     *
     * @return True if an update should occur.
     */
    protected boolean isRefreshRequired(ModuleContentEvent evt) {
        for (UpdateGovernor governor : governors) {
            if (governor.isRefreshRequired(evt)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Handles whether or not a case event should trigger an update.
     *
     * @param evt The case event.
     *
     * @return True if an update should occur.
     */
    protected boolean isRefreshRequiredForCaseEvent(PropertyChangeEvent evt) {
        for (UpdateGovernor governor : governors) {
            if (governor.isRefreshRequiredForCaseEvent(evt)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Method called that triggers refresh.
     */
    protected void onRefresh() {
        onUpdate.run();
    }

    /**
     * Registers ingest and case event listeners.
     */
    public void register() {
        if (!caseEvents.isEmpty()) {
            Case.addEventTypeSubscriber(caseEvents, caseEventsListener);
        }

        refreshThrottler.registerForIngestModuleEvents();
    }

    /**
     * Unregisters ingest and case event listeners.
     */
    public void unregister() {
        if (!caseEvents.isEmpty()) {
            Case.removeEventTypeSubscriber(caseEvents, caseEventsListener);
        }
        
        refreshThrottler.unregisterEventListener();
    }
}
