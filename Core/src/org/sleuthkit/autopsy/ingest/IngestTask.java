/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import org.sleuthkit.datamodel.Content;

abstract class IngestTask {

    private final static long NOT_SET = Long.MIN_VALUE;
    private final IngestJobPipeline ingestJobPipeline;
    private long threadId;

    IngestTask(IngestJobPipeline ingestJobPipeline) {
        this.ingestJobPipeline = ingestJobPipeline;
        threadId = NOT_SET;
    }

    IngestJobPipeline getIngestJobPipeline() {
        return ingestJobPipeline;
    }

    Content getDataSource() {
        return getIngestJobPipeline().getDataSource();
    }

    long getThreadId() {
        return threadId;
    }

    void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    abstract void execute(long threadId) throws InterruptedException;
}
