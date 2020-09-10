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
 * Interface for classes that handle adding files from a data source processor
 * to the ingest pipeline.
 */
public interface IngestStream {

    /**
     * Adds a set of file object IDs that are ready for ingest.
     *
     * @param fileObjectIds List of file object IDs.
     *
     * @throws IngestStreamClosedException
     */
    void addFiles(List<Long> fileObjectIds) throws IngestStreamClosedException;
    
    /**
     * Get the ingest job associated with this ingest stream.
     * 
     * @return The IngestJob.
     */
    IngestJob getIngestJob();

    /**
     * Closes the ingest stream. Should be called after all files from data
     * source have been sent to the stream.
     */
    void close();

    /**
     * Check whether the ingest stream is closed.
     *
     * @return True if closed, false otherwise.
     */
    boolean isClosed();

    /**
     * Stops the ingest stream. The stream will no longer accept new data and
     * should no longer be used. Will also close the ingest stream.
     */
    void stop();

    /**
     * Check whether the ingest stream was stopped before completion. If this
     * returns true, data should not be written to or read from the stream.
     *
     * @return True if the ingest stream was stopped, false otherwise.
     */
    boolean wasStopped();
}
