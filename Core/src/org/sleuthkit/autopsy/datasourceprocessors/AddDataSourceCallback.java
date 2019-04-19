/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors;

import java.util.List;
import java.util.UUID;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.datamodel.Content;


/**
 * A "callback" that collects the results of running a data source processor on
 * a data source and unblocks the job processing thread when the data source
 * processor finishes running in its own thread.
 */
@Immutable
public class AddDataSourceCallback extends DataSourceProcessorCallback {

    private final Case caseForJob;
    private final AutoIngestDataSource dataSourceInfo;
    private final UUID taskId;
    private final Object lock;

    /**
     * Constructs a "callback" that collects the results of running a data
     * source processor on a data source and unblocks the job processing thread
     * when the data source processor finishes running in its own thread.
     *
     * @param caseForJob The case for the current job.
     * @param dataSourceInfo The data source
     * @param taskId The task id to associate with ingest job events.
     * @param lock   The DSP lock
     */
    public AddDataSourceCallback(Case caseForJob, AutoIngestDataSource dataSourceInfo, UUID taskId, Object lock) {
        this.caseForJob = caseForJob;
        this.dataSourceInfo = dataSourceInfo;
        this.taskId = taskId;
        this.lock = lock;
    }

    /**
     * Called by the data source processor when it finishes running in its own
     * thread.
     *
     * @param result The result code for the processing of the data source.
     * @param errorMessages Any error messages generated during the processing
     * of the data source.
     * @param dataSourceContent The content produced by processing the data
     * source.
     */
    @Override
    public void done(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errorMessages, List<Content> dataSourceContent) {
        if (!dataSourceContent.isEmpty()) {
            caseForJob.notifyDataSourceAdded(dataSourceContent.get(0), taskId);
        } else {
            caseForJob.notifyFailedAddingDataSource(taskId);
        }
        dataSourceInfo.setDataSourceProcessorOutput(result, errorMessages, dataSourceContent);
        dataSourceContent.addAll(dataSourceContent);
        synchronized (lock) {
            lock.notify();
        }
    }

    /**
     * Called by the data source processor when it finishes running in its own
     * thread, if that thread is the AWT (Abstract Window Toolkit) event
     * dispatch thread (EDT).
     *
     * @param result The result code for the processing of the data source.
     * @param errorMessages Any error messages generated during the processing
     * of the data source.
     * @param dataSources The content produced by processing the data
     * source.
     */
    @Override
    public void doneEDT(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errorMessages, List<Content> dataSources) {
        done(result, errorMessages, dataSources);
    }

}
