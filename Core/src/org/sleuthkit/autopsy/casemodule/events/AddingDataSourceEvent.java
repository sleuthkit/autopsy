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
 * Event published when a data source is being added to a case.
 */
@Immutable
public final class AddingDataSourceEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private final UUID dataSourceId;

    /**
     * Constructs an event published when a data source is being added to a
     * case.
     *
     * @param dataSourceId A unique identifier associated with the data source.
     *                     Used to pair this AddingDataSourceEvent with a
     *                     DataSourceAddedEvent or a
     *                     AddingDataSourceFailedEvent.
     */
    public AddingDataSourceEvent(UUID dataSourceId) {
        super(Case.Events.ADDING_DATA_SOURCE.toString(), null, null);
        this.dataSourceId = dataSourceId;
    }

    /**
     * Gets the unique event id used to pair this
     * AddindDataSourceEvent with a corresponding DataSourceAddedEvent or
     * AddingDataSourceFailedEvent.
     *
     * @return The unique event id.
     */
    public UUID getEventId() {
        return dataSourceId;
    }

    /**
     * Gets the unique event id used to pair this
     * AddindDataSourceEvent with a corresponding DataSourceAddedEvent or
     * AddingDataSourceFailedEvent.
     *
     * @return The unique event id.
     * @deprecated Use getEventId instead.
     */
    @Deprecated
    public UUID getDataSourceId() {
        return dataSourceId;
    }

}
