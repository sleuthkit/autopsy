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

/**
 * Used by data source ingest modules to report progress.
 */
public class DataSourceIngestModuleProgress {

    private final IngestJob ingestJob;
    private final String moduleDisplayName;

    DataSourceIngestModuleProgress(IngestJob ingestJob, String moduleDisplayName) {
        this.ingestJob = ingestJob;
        this.moduleDisplayName = moduleDisplayName;
    }

    /**
     * Updates the progress bar and switches it to determinate mode. This should
     * be called by the module as soon as the number of total work units
     * required to process the data source is known.
     *
     * @param workUnits Total number of work units for the processing of the
     * data source.
     */
    public void switchToDeterminate(int workUnits) {
        ingestJob.getDataSourceTaskProgressBar().switchToDeterminate(workUnits);
    }

    /**
     * Switches the progress bar to indeterminate mode. This should be called if
     * the total work units to process the data source is unknown.
     */
    public void switchToIndeterminate() {
        ingestJob.getDataSourceTaskProgressBar().switchToIndeterminate();
    }

    /**
     * Updates the progress bar with the number of work units performed, if in
     * the determinate mode.
     *
     * @param workUnits Number of work units performed so far by the module.
     */
    public void progress(int workUnits) {
        ingestJob.getDataSourceTaskProgressBar().progress(this.moduleDisplayName, workUnits);
    }
}