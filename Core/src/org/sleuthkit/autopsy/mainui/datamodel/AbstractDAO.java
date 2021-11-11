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

import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.List;

/**
 * Internal methods that DAOs implement.
 */
abstract class AbstractDAO {

    /**
     * Clear any cached data (Due to change in view).
     */
    abstract void clearCaches();

    /**
     * Handles an autopsy event (i.e. ingest, case, etc.).
     *
     * @param evt The autopsy events.
     *
     * @return The list of dao events emitted due to this autopsy event.
     */
    abstract List<DAOEvent> handleAutopsyEvent(Collection<PropertyChangeEvent> evt);

}
