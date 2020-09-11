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
package org.sleuthkit.autopsy.casemodule;

import java.util.List;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestStream;
import org.sleuthkit.autopsy.ingest.IngestStreamClosedException;

/**
 * This is a default ingest stream to use with the data source processors when
 * an IngestStream is not supplied. Adding files/data sources are no-ops.
 */
class DefaultIngestStream implements IngestStream {

    private boolean isClosed = false;
    private boolean isStopped = false;

    @Override
    public void addFiles(List<Long> fileObjectIds) throws IngestStreamClosedException {
        // Do nothing
    }
    
    @Override
    public IngestJob getIngestJob() {
        throw new UnsupportedOperationException("DefaultIngestStream has no associated IngestJob");
    }

    @Override
    public synchronized boolean isClosed() {
        return isClosed;
    }

    @Override
    public synchronized void close() {
        isClosed = true;
    }

    @Override
    public synchronized void stop() {
        isClosed = true;
        isStopped = true;
    }

    @Override
    public synchronized boolean wasStopped() {
        return isStopped;
    }
}
