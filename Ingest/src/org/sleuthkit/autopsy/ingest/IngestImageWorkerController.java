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
 * passed to the service as a limited way to control this worker
 * update progress bar, check if job is cancelled 
 */
public class IngestImageWorkerController {

    private IngestImageThread worker;
    private ProgressHandle progress;

    public IngestImageWorkerController(IngestImageThread worker, ProgressHandle progress) {
        this.worker = worker;
        this.progress = progress;
    }

    public boolean isCancelled() {
        return worker.isCancelled();
    }

    public void switchToDeterminate(int workUnits) {
        if (progress != null) {
            progress.switchToDeterminate(workUnits);
        }
    }

    public void switchToInDeterminate() {
        if (progress != null) {
            progress.switchToIndeterminate();
        }
    }

    public void progress(int workUnits) {
        if (progress != null) {
            progress.progress(worker.getImage().getName(), workUnits);
        }
    }
}