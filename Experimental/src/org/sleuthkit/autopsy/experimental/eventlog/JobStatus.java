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

import java.util.Optional;
import java.util.stream.Stream;

/**
 * The status of the job.
 */
public enum JobStatus {
    PENDING(0), RUNNING(1), DONE(2);
    private int dbVal;

    /**
     * Constructor.
     * @param dbVal The integer value used to represent the status in the database. 
     */
    JobStatus(int dbVal) {
        this.dbVal = dbVal;
    }

    /**
     * @return The integer value used to represent the status in the database. 
     */
    public int getDbVal() {
        return dbVal;
    }

    /**
     * Returns the enum value signified by the integer value.
     * @param dbVal The integer value used to represent the status in the database. 
     * @return The job status if found or empty if not.
     */
    public static Optional<JobStatus> getFromDbVal(Integer dbVal) {
        if (dbVal == null) {
            return Optional.empty();
        }
        return Stream.of(JobStatus.values()).filter(s -> s.getDbVal() == dbVal).findFirst();
    }
    
}
