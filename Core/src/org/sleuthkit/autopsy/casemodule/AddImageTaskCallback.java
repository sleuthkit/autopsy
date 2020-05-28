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
import org.sleuthkit.autopsy.ingest.IngestStream;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult;
import org.sleuthkit.autopsy.ingest.IngestStreamClosedException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Called on completion of the add image task.
 */
class AddImageTaskCallback {
    private final IngestStream ingestStream;
    private final DataSourceProcessorCallback dspCallback;
    
    /**
     * Callback to use with an ingest stream.
     * 
     * @param ingestStream The ingest stream that data is being sent to.
     * @param dspCallback  The callback for non-ingest stream related processing.
     */
    AddImageTaskCallback(IngestStream ingestStream, DataSourceProcessorCallback dspCallback) {
        this.ingestStream = ingestStream;
        this.dspCallback = dspCallback;
    }
   
    /**
     * Callback to use without an ingest stream.
     * Will create a default ingest stream object that does nothing.
     * 
     * @param dspCallback  The callback for non-ingest stream related processing.
     */    
    AddImageTaskCallback(DataSourceProcessorCallback dspCallback) {
        this.ingestStream = new DefaultIngestStream();
        this.dspCallback = dspCallback;
    }   

    /**
     * Called when the add image task is completed.
     * 
     * @param result   The result from the data source processor.
     * @param errList  The list of errors.
     * @param newDataSources  The list of new data sources.
     */
    void onCompleted(DataSourceProcessorResult result, List<String> errList, List<Content> newDataSources) {
        System.out.println("### In AddImageTaskCallback with result: " + result.toString());
        if (result.equals(DataSourceProcessorResult.CRITICAL_ERRORS)) {
            ingestStream.close(false);
        } else {
            ingestStream.close(true);
        }
        dspCallback.done(result, errList, newDataSources); // TODO - doneEDT???
    }
}
