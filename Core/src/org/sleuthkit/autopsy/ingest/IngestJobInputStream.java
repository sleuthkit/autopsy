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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * Implementation of IngestStream. Will collect data from the data source
 * processor to be sent to the ingest pipeline.
 */
class IngestJobInputStream implements IngestStream {
    private final IngestJob ingestJob;
    private long dataSourceObjectId = -1;
    private DataSource dataSource = null;
    private final Queue<Long> fileIdQueue = new LinkedList<>();
    private boolean isClosed = false;

    /**
     * Create an ingest stream object, saving a reference to the associated IngestJob;
     * 
     * @param ingestJob The IngestJob associated with this stream.
     */
    IngestJobInputStream(IngestJob ingestJob) {
        this.ingestJob = ingestJob;
    }
    
    @Override
    public void addDataSource(long dataSourceObjectId) throws IngestStreamClosedException {
        synchronized(this) {
            if (isClosed) {
               throw new IngestStreamClosedException("Can not add data source - ingest stream is closed");
            }
            this.dataSourceObjectId = dataSourceObjectId;
            ingestJob.start();
        }
    }

    @Override
    public DataSource getDataSource() throws TskCoreException {
        synchronized(this) {
            // If we've already loaded it, return it
            if (dataSource != null) {
                return dataSource;
            }
            
            // Make sure the data source object ID has been set
            if (dataSourceObjectId == -1) {
                throw new TskCoreException("Data source object ID has not been set");
            }
            
            try {
                dataSource = Case.getCurrentCaseThrows().getSleuthkitCase().getDataSource(dataSourceObjectId);
                return dataSource;
            } catch (NoCurrentCaseException ex) {
                throw new TskCoreException("Case is closed");
            } catch (TskDataException ex) {
                throw new TskCoreException("Error loading data source with object ID " + dataSourceObjectId, ex);
            }
        }
    }

    @Override
    public void addFiles(List<Long> fileObjectIds) throws IngestStreamClosedException {
        synchronized(this) {
            if (isClosed) {
                throw new IngestStreamClosedException("Can not add files - ingest stream is closed");
            }
            if (dataSource == null) {
                throw new IllegalStateException("Files can not be added before a data source");
            }
            fileIdQueue.addAll(fileObjectIds);
        }
    }

    @Override
    public List<AbstractFile> getNextFiles(int numberOfFiles) throws TskCoreException {
        synchronized(this) {
            List<AbstractFile> nextFiles = new ArrayList<>();
            
            for (int i = 0;i < numberOfFiles;i++) {
                Long nextId = fileIdQueue.poll();
                if (nextId == null) {
                    // Reached the end of the queue
                    break;
                }
                
                try {
                    nextFiles.add(Case.getCurrentCaseThrows().getSleuthkitCase().getAbstractFileById(nextId));
                } catch (NoCurrentCaseException ex) {
                    throw new TskCoreException("Case is closed");
                }
            }
            return nextFiles;
        }
    }

    @Override
    public void close(boolean completed) {
        synchronized(this) {
            isClosed = true;
            if (! completed) {
                ingestJob.cancel(IngestJob.CancellationReason.USER_CANCELLED);
            }
        }
    }

    @Override
    public boolean isClosed() {
        synchronized(this) {
            return isClosed;
        }
    }
    
}
