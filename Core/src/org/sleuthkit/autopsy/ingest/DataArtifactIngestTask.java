/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021-2021 Basis Technology Corp.
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

import org.sleuthkit.datamodel.DataArtifact;

/**
 * A data artifact ingest task that will be executed by an ingest thread using a
 * given ingest job executor.
 */
final class DataArtifactIngestTask extends IngestTask {

    private final DataArtifact artifact;

    /**
     * Constructs a data artifact ingest task that will be executed by an ingest
     * thread using a given ingest job executor.
     *
     * @param ingestJobExecutor The ingest job executor to use to execute the
     *                          task.
     * @param artifact          The data artifact to be processed.
     */
    DataArtifactIngestTask(IngestJobExecutor ingestJobExecutor, DataArtifact artifact) {
        super(artifact.getName(), ingestJobExecutor);
        this.artifact = artifact;
    }
    
    /**
     * Gets the data artifact for this task.
     *
     * @return The data artifact.
     */
    DataArtifact getDataArtifact() {
        return artifact;
    }

    @Override
    void execute(long threadId) {
        super.setThreadId(threadId);
        getIngestJobExecutor().execute(this);
    }    
    
}
