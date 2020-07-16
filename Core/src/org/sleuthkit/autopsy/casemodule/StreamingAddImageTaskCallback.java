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
import org.sleuthkit.datamodel.Content;

/**
 * A callback to be called on completion of an add image task. This
 * implementation of the interface is suitable for streaming ingest use cases.
 * It closes the ingest stream and then calls the data source processor done
 * callback.
 */
class StreamingAddImageTaskCallback implements AddImageTaskCallback {

    private final IngestStream ingestStream;
    private final DataSourceProcessorCallback dspCallback;

    /**
     * Constructs a callback to be called on completion of an add image task.
     * This implementation of the interface is suitable for streaming ingest use
     * cases. It closes the ingest stream and then calls the data source
     * processor done callback.
     *
     * @param ingestStream The ingest stream that data is being sent to.
     * @param dspCallback  The callback for non-ingest stream related
     *                     processing.
     */
    StreamingAddImageTaskCallback(IngestStream ingestStream, DataSourceProcessorCallback dspCallback) {
        this.ingestStream = ingestStream;
        this.dspCallback = dspCallback;
    }

    /**
     * Called when the add image task is completed.
     *
     * @param result         The result from the data source processor.
     * @param errList        The list of errors.
     * @param newDataSources The list of new data sources.
     */
    @Override
    public void onCompleted(DataSourceProcessorResult result, List<String> errList, List<Content> newDataSources) {
        ingestStream.close();
        dspCallback.done(result, errList, newDataSources);
    }
}
