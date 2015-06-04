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
package org.sleuthkit.autopsy.ingest.events;

import java.io.Serializable;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.Content;

/**
 * RJCTODO
 */
public abstract class DataSourceAnalysisEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private final long ingestJobId;
    private final long dataSourceIngestJobId;
    private transient Content dataSource; // RJCTODO: Make able to be published remotely

    /**
     * RJCTOD
     *
     * @param eventType
     * @param ingestJobId
     * @param dataSourceIngestJobId
     * @param dataSource
     */
    public DataSourceAnalysisEvent(IngestManager.IngestJobEvent eventType, long ingestJobId, long dataSourceIngestJobId, Content dataSource) {
        super(eventType.toString(), null, null);
        this.ingestJobId = ingestJobId;
        this.dataSourceIngestJobId = dataSourceIngestJobId;
        this.dataSource = dataSource;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public long getIngestJobId() {
        return ingestJobId;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public long getDataSourceIngestJobId() {
        return dataSourceIngestJobId;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public Content getDataSource() {
        return dataSource;
    }
}
