/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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

import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.sleuthkit.datamodel.Content;

/**
 * Used by data source ingest modules to report progress and detect data source
 * ingest job cancellation.
 */
public class DataSourceIngestModuleStatusHelper {

    private final SwingWorker worker;
    private final ProgressHandle progress;
    private final Content dataSource;
    private final String moduleDisplayName;

    DataSourceIngestModuleStatusHelper(SwingWorker worker, ProgressHandle progress, Content dataSource, String moduleDisplayName) {
        this.worker = worker;
        this.progress = progress;
        this.dataSource = dataSource;
        this.moduleDisplayName = moduleDisplayName;
    }

    /**
     * Checks for ingest job cancellation. This should be polled by the module
     * in its process() method. If the ingest task is canceled, the module
     * should return from its process() method as quickly as possible.
     *
     * @return True if the task has been canceled, false otherwise.
     */
    public boolean isIngestJobCancelled() {
        return worker.isCancelled();
    }

    /**
     * Updates the progress bar and switches it to determinate mode. This should
     * be called by the module as soon as the number of total work units
     * required to process the data source is known.
     *
     * @param workUnits Total number of work units for the processing of the
     * data source.
     */
    public void switchToDeterminate(int workUnits) { // RJCTODO: Fix this
        if (progress != null) {
            progress.switchToDeterminate(workUnits);
        }
    }

    /**
     * Switches the progress bar to indeterminate mode. This should be called if
     * the total work units to process the data source is unknown.
     */
    public void switchToIndeterminate() {
        if (progress != null) {
            progress.switchToIndeterminate();
        }
    }

    /**
     * Updates the progress bar with the number of work units performed, if in
     * the determinate mode.
     *
     * @param workUnits Number of work units performed so far by the module.
     */
    public void progress(int workUnits) {
        if (progress != null) {
            progress.progress(this.moduleDisplayName, workUnits);
        }
    }
}