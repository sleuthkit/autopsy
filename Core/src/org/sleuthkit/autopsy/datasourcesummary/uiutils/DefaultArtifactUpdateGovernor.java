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

import java.util.Set;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;

/**
 * An UpdateGovernor that provides a means of providing a set of artifact type
 * id's that should trigger an update.
 */
public interface DefaultArtifactUpdateGovernor extends DefaultUpdateGovernor {

    @Override
    default boolean isRefreshRequired(ModuleDataEvent evt) {
        return getArtifactTypeIdsForRefresh().contains(evt.getBlackboardArtifactType().getTypeID());
    }

    /**
     * @return The set of artifact type id's that should trigger an update.
     */
    Set<Integer> getArtifactTypeIdsForRefresh();
}
