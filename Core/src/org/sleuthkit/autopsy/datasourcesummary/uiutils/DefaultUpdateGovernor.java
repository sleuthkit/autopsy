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
import java.util.Collections;
import java.util.Set;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * The default UpdateGovernor where no updates will be triggered unless
 * overridden.
 */
public interface DefaultUpdateGovernor extends UpdateGovernor {

    @Override
    default boolean isRefreshRequiredForCaseEvent(PropertyChangeEvent evt) {
        return false;
    }

    @Override
    default boolean isRefreshRequired(ModuleContentEvent evt) {
        return false;
    }

    @Override
    default boolean isRefreshRequired(ModuleDataEvent evt) {
        return false;
    }

    @Override
    default boolean isRefreshRequired(IngestManager.IngestJobEvent evt) {
        return false;
    }

    @Override
    default Set<Case.Events> getCaseEventUpdates() {
        return Collections.emptySet();
    }

    @Override
    default Set<IngestManager.IngestJobEvent> getIngestJobEventUpdates() {
        return Collections.emptySet();
    }

    @Override
    default boolean isRefreshRequired(AbstractFile evt) {
        return false;
    }
}
