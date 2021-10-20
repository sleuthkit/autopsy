/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2021 Basis Technology Corp.
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
 * Event published when analysis (ingest) of a data source is completed.
 */
public final class DataSourceAnalysisCompletedEvent extends DataSourceAnalysisEvent implements Serializable {

    /**
     * The reason why the analysis of the data source completed.
     */
    public enum Reason {

        ANALYSIS_COMPLETED,
        ANALYSIS_CANCELLED
    }

    private static final long serialVersionUID = 1L;
    private final Reason reason;

    /**
     * Constructs an event published when analysis (ingest) of a data source is
     * completed.
     *
     * @param ingestJobId The identifier of the ingest job. For a multi-user
     *                    case, this ID is only unique on the host where the
     *                    ingest job is running.
     * @param dataSource  The data source.
     * @param reason      The reason analysis completed.
     */
    public DataSourceAnalysisCompletedEvent(long ingestJobId, Content dataSource, Reason reason) {
        super(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED, ingestJobId, dataSource);
        this.reason = reason;
    }

    /**
     * Constructs an event published when analysis (ingest) of a data source is
     * completed.
     *
     * @param ingestJobId The identifier of the ingest job. For a multi-user
     *                    case, this ID is only unique on the host where the
     *                    ingest job is running.
     * @param unused      Unused.
     * @param dataSource  The data source.
     * @param reason      The reason analysis completed.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public DataSourceAnalysisCompletedEvent(long ingestJobId, long unused, Content dataSource, Reason reason) {
        this(ingestJobId, dataSource, reason);
    }

    /**
     * Gets the reason why the analysis of the data source completed.
     *
     * @return The reason.
     */
    public Reason getResult() {
        return reason;
    }

}
