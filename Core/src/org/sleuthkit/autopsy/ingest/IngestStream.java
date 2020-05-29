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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;


/**
 * Interface for classes that handle adding files from a data source processor
 * to the ingest pipeline.
 */
public interface IngestStream {
    /**
     * Call when the data source has been completely added to the case database.
     * 
     * @param dataSourceObjectId  The object ID of the new data source
     * 
     * @throws IngestStreamClosedException 
     */
    void addDataSource(long dataSourceObjectId) throws IngestStreamClosedException;

    /**
     * Get the data source associated with this ingest stream.
     * 
     * @return The data source or null if it has not been added yet.
     * 
     * @throws TskCoreException 
     */
    DataSource getDataSource() throws TskCoreException;
    
    /**
     * Adds a set of file object IDs that are ready for ingest.
     * 
     * @param fileObjectIds List of file object IDs.
     * 
     * @throws IngestStreamClosedException 
     * @throws TskCoreException
     */
    void addFiles(List<Long> fileObjectIds) throws IngestStreamClosedException, TskCoreException;
    
    /**
     * Returns the next set of files that are ready for ingest.
     * Abstract files will be returned in the order they were added through
     * addFiles().
     * 
     * @param numberOfFiles Maximum number of files to return.
     * 
     * @return A list of abstract files for ingest. List may be empty or contain less
     *         files than requested.
     * 
     * @throws TskCoreException 
     */
    List<AbstractFile> getNextFiles(int numberOfFiles) throws TskCoreException;

    /**
     * Closes the ingest stream.
     * Adding a data source or a set of files to a closed ingest stream
     * will generate an error, but getDataSource() and getNextFiles() will work.
     * 
     * @param completed True if the data source processing was complete, false 
     *                  if canceled or an error occurred
     */
    void close(boolean completed);
    
    /**
     * Check whether the ingest stream is closed.
     * 
     * @return True if closed, false otherwise.
     */
    boolean isClosed();
}
