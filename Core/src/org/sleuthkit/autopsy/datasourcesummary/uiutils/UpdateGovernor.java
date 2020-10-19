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
import java.util.Set;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestJobEvent;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Interface for determiining when data should update based on autopsy (i.e.
 * case/ingest) events.
 */
public interface UpdateGovernor {

    /**
     * @return The set of Case Events for which data should be updated.
     */
    Set<Case.Events> getCaseEventUpdates();

    /**
     * @return The set of Ingest Job Events for which data should be updated.
     */
    Set<IngestJobEvent> getIngestJobEventUpdates();

    /**
     * Given a module data event, whether or not an update should occur.
     *
     * @param evt The ModuleDataEvent that is occurring.
     *
     * @return Whether or not this event should trigger an update.
     */
    boolean isRefreshRequired(ModuleDataEvent evt);

    /**
     * Given a module content event, whether or not an update should occur.
     *
     * @param evt The ModuleContentEvent.
     *
     * @return Whether or not this event should trigger an update.
     */
    boolean isRefreshRequired(ModuleContentEvent evt);

    /**
     * Given an ingest job event, determines whether or not an update should
     * occur.
     *
     * @param evt The event.
     *
     * @return Whether or not this event should trigger an update.
     */
    boolean isRefreshRequired(IngestJobEvent evt);

    /**
     * Given a case event, whether or not an update should occur.
     *
     * @param evt The event.
     *
     * @return Whether or not this event should trigger an update.
     */
    boolean isRefreshRequiredForCaseEvent(PropertyChangeEvent evt);

    /**
     * Whether or not a newly added AbstractFile should trigger an update.
     *
     * @param evt The AbstractFile.
     *
     * @return True if an update should occur.
     */
    boolean isRefreshRequired(AbstractFile evt);
}
