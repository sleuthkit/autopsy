/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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

import java.util.List;

/**
 * Implementation of IngestStream. Will collect data from the data source
 * processor to be sent to the ingest pipeline.
 */
class IngestJobInputStream implements IngestStream {

    private final IngestJob ingestJob;
    private boolean closed = false;
    private boolean isStopped = false;
    private final IngestJobStartResult ingestJobStartResult;

    /**
     * Create an ingest stream object, saving a reference to the associated
     * IngestJob;
     *
     * @param ingestJob The IngestJob associated with this stream.
     */
    IngestJobInputStream(IngestJob ingestJob) {
        this.ingestJob = ingestJob;
        ingestJobStartResult = IngestManager.getInstance().startIngestJob(ingestJob);
    }
    
    /**
     * Check the result from starting the ingest jobs.
     * 
     * @return The IngestJobStartResult object returned from IngestManager.startIngestJob().
     */
    IngestJobStartResult getIngestJobStartResult() {
        return ingestJobStartResult;
    }

    @Override
    public synchronized void addFiles(List<Long> fileObjectIds) throws IngestStreamClosedException {
        if (closed) {
            throw new IngestStreamClosedException("Can not add files - ingest stream is closed");
        }
        ingestJob.addStreamedFiles(fileObjectIds);
    }
    
    @Override
    public IngestJob getIngestJob() {
        return ingestJob;
    }

    @Override
    public synchronized void close() {
        closed = true;
        ingestJob.addStreamedDataSource();
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void stop() {
        this.closed = true;
        this.isStopped = true;

        ingestJob.cancel(IngestJob.CancellationReason.USER_CANCELLED);
    }

    @Override
    public synchronized boolean wasStopped() {
        return isStopped;
    }
}
