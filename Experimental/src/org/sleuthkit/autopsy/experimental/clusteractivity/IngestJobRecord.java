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
package org.sleuthkit.autopsy.experimental.clusteractivity;

import java.util.Date;
import java.util.Optional;

/**
 * The record in the database for the ingest job.
 */
public class IngestJobRecord {

    private final long id;
    private final long caseId;
    private final String caseName;
    private final String dataSourceName;
    private final Optional<Date> startTime;
    private final Optional<Date> endTime;
    private final IngestJobStatus status;
    private final boolean ingestError;

    /**
     * Main constructor.
     *
     * @param id             The id of the job.
     * @param caseId         The parent case id in the event log.
     * @param caseName       The name of the case.
     * @param dataSourceName The name of the data source.
     * @param startTime      The start time of processing.
     * @param endTime        The end time of processing.
     * @param status         The current status.
     * @param ingestError    There was an error on ingest.
     */
    public IngestJobRecord(long id, long caseId, String caseName, String dataSourceName, Optional<Date> startTime, Optional<Date> endTime, IngestJobStatus status, boolean ingestError) {
        this.id = id;
        this.caseId = caseId;
        this.caseName = caseName;
        this.dataSourceName = dataSourceName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.ingestError = ingestError;
    }

    /**
     * @return The id of the job.
     */
    public long getId() {
        return id;
    }

    /**
     * @return The parent case id in the event log.
     */
    public long getCaseId() {
        return caseId;
    }

    /**
     * @return The name of the case.
     */
    public String getCaseName() {
        return caseName;
    }

    /**
     * @return The name of the data source.
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * @return The start time of processing.
     */
    public Optional<Date> getStartTime() {
        return startTime;
    }

    /**
     * @return The end time of processing.
     */
    public Optional<Date> getEndTime() {
        return endTime;
    }

    /**
     * @return The current status.
     */
    public IngestJobStatus getStatus() {
        return status;
    }

    /**
     * @return There was an error associated with this ingest.
     */
    public boolean getIngestError() {
        return ingestError;
    }

    @Override
    public String toString() {
        return "IngestJobRecord{" + "id=" + id + ", caseId=" + caseId + ", caseName=" + caseName + ", dataSourceName=" + dataSourceName + ", startTime=" + startTime + ", endTime=" + endTime + ", status=" + status + ", ingestError=" + ingestError + '}';
    }
    
    
}
