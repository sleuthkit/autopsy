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
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.Content;

/**
 * RJCTODO
 */
public class DataSourceAnalysisCompletedEvent extends DataSourceAnalysisEvent implements Serializable {

    public enum Reason {
        COMPLETED,
        CANCELLED
    }
    
    private static final long serialVersionUID = 1L;
    private final Reason reason;
    
    public DataSourceAnalysisCompletedEvent(long ingestJobId, long dataSourceIngestJobId, Content dataSource, Reason reason) {
        super(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED, ingestJobId, dataSourceIngestJobId, dataSource);
        this.reason = reason;
    }
    
    public Reason getResult() {
        return reason;
    }
    
}
