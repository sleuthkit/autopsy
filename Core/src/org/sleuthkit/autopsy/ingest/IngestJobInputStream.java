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
import org.sleuthkit.datamodel.DataSource;

/**
 * Implementation of IngestStream. Will collect data from the data source
 * processor to be sent to the ingest pipeline.
 */
class IngestJobInputStream implements IngestStream {
    private final IngestJob ingestJob;
    private final DataSource dataSource;
    private boolean isClosed = false;
    private boolean isStopped = false;

    /**
     * Create an ingest stream object, saving a reference to the associated IngestJob;
     * 
     * @param ingestJob The IngestJob associated with this stream.
     */
    IngestJobInputStream(DataSource dataSource, IngestJob ingestJob) {
	this.dataSource = dataSource;
        this.ingestJob = ingestJob;
	ingestJob.start();
    }

    @Override
    public synchronized void addFiles(List<Long> fileObjectIds) throws IngestStreamClosedException {
	if (isClosed) {
	    throw new IngestStreamClosedException("Can not add files - ingest stream is closed");
	}
	ingestJob.addStreamingIngestFiles(fileObjectIds);
    }

    @Override
    public synchronized void close() {
	isClosed = true;
	ingestJob.processStreamingIngestDataSource();
    }

    @Override
    public synchronized boolean isClosed() {
        return isClosed;
    }

    @Override
    public synchronized void stop() {
	this.isClosed = true;
	this.isStopped = true;
	
        ingestJob.cancel(IngestJob.CancellationReason.USER_CANCELLED);
    }

    @Override
    public synchronized boolean wasStopped() {
	return isStopped;
    }
}
