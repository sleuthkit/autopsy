/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.cellex.datasourceprocessors;

import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.datamodel.SleuthkitJNI;

/*
 * A Runnable that updates a data source processor progress monitor with the
 * name of the directory currently being processed by a SleuthKit add image
 * process.
 *
 * TODO (JIRA-1578): The sleep code in the run method should be removed. Clients
 * should use a java.util.concurrent.ScheduledThreadPoolExecutor instead to be
 * able to control update frequency and cancellation.
 */
class AddImageProgressReportingTask implements Runnable {

    DataSourceProcessorProgressMonitor progressMonitor;
    SleuthkitJNI.CaseDbHandle.AddImageProcess addImageProcess;

    /**
     * Constructs a Runnable that updates a data source processor progress
     * monitor with the name of the directory currently being processed by a
     * SleuthKit add image process.
     *
     * @param progressMonitor The progress monitor.
     * @param addImageProcess An Sleuth add image process.
     */
    AddImageProgressReportingTask(DataSourceProcessorProgressMonitor progressMonitor, SleuthkitJNI.CaseDbHandle.AddImageProcess addImageProcess) {
        this.progressMonitor = progressMonitor;
        this.addImageProcess = addImageProcess;
    }

    /**
     * Every two seconds, updates the progress monitor with the name of the
     * directory currently being processed by the add image process.
     */
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                String currDir = addImageProcess.currentDirectory();
                if (null != currDir && !currDir.isEmpty()) {
                    progressMonitor.setProgressText("Adding: " + currDir);
                }
                /*
                 * TODO (JIRA-1578): The sleep should be removed here. Clients
                 * should use a java.util.concurrent.ScheduledThreadPoolExecutor
                 * instead to be able to control update frequency and
                 * cancellation,
                 */
                Thread.sleep(2 * 1000);
            }
        } catch (InterruptedException expected) {
        }
    }
}
