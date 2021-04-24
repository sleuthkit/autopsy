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
 * A set of callbacks to be called during the process of adding a data source to
 * the case database. This implementation of the interface is suitable for
 * streaming ingest use cases.
 */
class StreamingAddDataSourceCallbacks implements AddDataSourceCallbacks {

    private final Logger logger = Logger.getLogger(StreamingAddDataSourceCallbacks.class.getName());
    private final IngestStream ingestStream;

    /**
     * Constructs a set of callbacks to be called during the process of adding a
     * data source to the case database. This implementation of the interface is
     * suitable for streaming ingest use cases.
     *
     * @param stream The IngestStream to send data to
     */
    StreamingAddDataSourceCallbacks(IngestStream stream) {
        ingestStream = stream;
    }

    @Override
    public void onFilesAdded(List<Long> fileObjectIds) {
        if (ingestStream.wasStopped()) {
            return;
        }

        try {
            ingestStream.addFiles(fileObjectIds);
        } catch (IngestStreamClosedException ex) {
            if (!ingestStream.wasStopped()) {
                // If the ingest stream is closed but not stopped log the error.
                // This state should only happen once the data source is completely
                // added which means it's a severe error that files are still being added.
                logger.log(Level.SEVERE, "Error adding files to ingest stream - ingest stream is closed");
            }
        }
    }
}
