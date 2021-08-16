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
package org.sleuthkit.autopsy.experimental.eventlog;

import java.time.Instant;
import java.util.Optional;

/**
 *
 * @author gregd
 */
public class JobRecord {
    
    private final long id;
    private final long caseId;
    private final String caseName;
    private final String dataSourceName;
    private final Optional<Instant> startTime;
    private final Optional<Instant> endTime;
    private final JobStatus status;

    public JobRecord(long id, long caseId, String caseName, String dataSourceName, Optional<Instant> startTime, Optional<Instant> endTime, JobStatus status) {
        this.id = id;
        this.caseId = caseId;
        this.caseName = caseName;
        this.dataSourceName = dataSourceName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public long getCaseId() {
        return caseId;
    }

    public String getCaseName() {
        return caseName;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public Optional<Instant> getStartTime() {
        return startTime;
    }

    public Optional<Instant> getEndTime() {
        return endTime;
    }

    public JobStatus getStatus() {
        return status;
    }
    
}
