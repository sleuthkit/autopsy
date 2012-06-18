/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import org.netbeans.api.progress.ProgressHandle;

/**
 * Controller for image level ingest services
 * Used by services to check task status and to post progress to
 */
public class IngestImageWorkerController {

    private IngestImageThread worker;
    private ProgressHandle progress;

    /**
     * Instantiate the controller for the worker
     * @param worker underlying image ingest thread
     * @param progress the progress handle
     */
    IngestImageWorkerController(IngestImageThread worker, ProgressHandle progress) {
        this.worker = worker;
        this.progress = progress;
    }

    /**
     * Check if the task has been cancelled.  This should be polled by the service periodically
     * And the service needs to act, i.e. break out of its processing loop and call its stop() to cleanup
     * 
     * @return true if the task has been cancelled, false otherwise
     */
    public boolean isCancelled() {
        return worker.isCancelled();
    }

    /**
     * Update the progress bar and switch to determinate mode once number of total work units is known
     * @param workUnits total number of work units for the image ingest task
     */
    public void switchToDeterminate(int workUnits) {
        if (progress != null) {
            progress.switchToDeterminate(workUnits);
        }
    }

    /**
     * Update the progress bar and switch to non determinate mode if number of work units is not known
     */
    public void switchToInDeterminate() {
        if (progress != null) {
            progress.switchToIndeterminate();
        }
    }

    /**
     * Update the progress bar with the number of work units performed, if in the determinate mode
     * @param workUnits number of work units performed so far by the service
     */
    public void progress(int workUnits) {
        if (progress != null) {
            progress.progress(worker.getImage().getName(), workUnits);
        }
    }
}