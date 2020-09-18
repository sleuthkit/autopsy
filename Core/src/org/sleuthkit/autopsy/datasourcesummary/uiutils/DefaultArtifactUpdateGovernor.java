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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestJobEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;

/**
 * An UpdateGovernor that provides a means of providing a set of artifact type
 * id's that should trigger an update.
 */
public interface DefaultArtifactUpdateGovernor extends DefaultUpdateGovernor {

    Set<IngestJobEvent> INGEST_JOB_EVENTS = new HashSet<>(
            Arrays.asList(IngestJobEvent.COMPLETED, IngestJobEvent.CANCELLED));

    @Override
    default boolean isRefreshRequired(ModuleDataEvent evt) {
        if (evt == null || evt.getBlackboardArtifactType() == null) {
            return false;
        }

        return getArtifactTypeIdsForRefresh().contains(evt.getBlackboardArtifactType().getTypeID());
    }

    @Override
    default boolean isRefreshRequired(IngestManager.IngestJobEvent evt) {
        return (evt != null && INGEST_JOB_EVENTS.contains(evt));
    }

    @Override
    default Set<IngestJobEvent> getIngestJobEventUpdates() {
        return INGEST_JOB_EVENTS;
    }

    /**
     * @return The set of artifact type id's that should trigger an update.
     */
    Set<Integer> getArtifactTypeIdsForRefresh();
}
