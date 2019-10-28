/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.events.AutopsyEvent;

/**
 * An application event that is published when a data source has been deleted.
 */
public class DataSourceDeletedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private final long dataSourceID;

    /**
     * Constructs an application event that is published when a data source has
     * been deleted.
     *
     * @param dataSourceId The object ID of the data source that was deleted.
     */
    public DataSourceDeletedEvent(Long dataSourceId) {
        super(Case.Events.DATA_SOURCE_DELETED.toString(), dataSourceId, null);
        this.dataSourceID = dataSourceId;
    }

    /**
     * Gets the object ID of the data source that was deleted.
     *
     * @return The data source id.
     */
    public long getDataSourceId() {
        return dataSourceID;
    }

}
