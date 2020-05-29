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
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestStream;
import org.sleuthkit.autopsy.ingest.IngestStreamClosedException;
import org.sleuthkit.datamodel.AddDataSourceCallbacks;

/**
 * Callback to send files from the data source processor to the ingest stream.
 */
class AddImageCallbacks implements AddDataSourceCallbacks {
    private final Logger logger = Logger.getLogger(AddImageCallbacks.class.getName());
    private final IngestStream ingestStream;

    /**
     * Create the AddImageCallbacks object.
     * 
     * @param stream The IngestStream to send data to
     */
    AddImageCallbacks(IngestStream stream) {
	ingestStream = stream;
    }

    @Override
    public void onDataSourceAdded(long dataSourceObjectId) {
	try {
	    ingestStream.addDataSource(dataSourceObjectId);
	} catch (IngestStreamClosedException ex) {
	    logger.log(Level.SEVERE, "Error adding data source with ID {0} to ingest stream - ingest stream is closed", dataSourceObjectId);
	}
    }

    @Override
    public void onFilesAdded(List<Long> fileObjectIds) {
	try {
	    ingestStream.addFiles(fileObjectIds);
	} catch (IngestStreamClosedException ex) {
	    logger.log(Level.SEVERE, "Error adding {0} files to ingest stream - ingest stream is closed", fileObjectIds.size());
	}
    }
}
