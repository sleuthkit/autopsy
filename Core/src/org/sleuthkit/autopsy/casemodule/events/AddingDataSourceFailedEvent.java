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
package org.sleuthkit.autopsy.casemodule.events;

import java.io.Serializable;
import java.util.UUID;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * Event published when an attempt to add a data source to a case fails.
 */
@Immutable
public final class AddingDataSourceFailedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private final UUID dataSourceId;

    /**
     * Constructs an event published when an attempt to add a data source to a
     * case fails.
     *
     * @param dataSourceId A unique identifier associated with the data source.
     *                     Used to pair this AddingDataSourceFailedEvent with a
     *                     AddingDataSourceEvent.
     */
    public AddingDataSourceFailedEvent(UUID dataSourceId) {
        super(Case.Events.ADDING_DATA_SOURCE_FAILED.toString(), null, null);
        this.dataSourceId = dataSourceId;
    }

    /**
     * Gets the unique id used to pair this AddingDataSourceFailedEvent with the
     * corresponding AddingDataSourceEvent.
     *
     * @return The unique event id.
     */
    public UUID getAddingDataSourceEventId() {
        return dataSourceId;
    }

    /**
     * Gets the unique id used to pair this AddingDataSourceFailedEvent with the
     * corresponding AddingDataSourceEvent.
     *
     * @return The unique event id.
     * @deprecated Use getAddingDataSourceEventId instead.
     */
    @Deprecated
    public UUID getDataSourceId() {
        return dataSourceId;
    }

}
