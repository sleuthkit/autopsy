/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2014 Basis Technology Corp.
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

import java.util.concurrent.LinkedBlockingQueue;

final class DataSourceIngestTaskScheduler {

    private static DataSourceIngestTaskScheduler instance = new DataSourceIngestTaskScheduler();
    private final LinkedBlockingQueue<DataSourceIngestTask> tasks = new LinkedBlockingQueue<>();

    static DataSourceIngestTaskScheduler getInstance() {
        return instance;
    }

    private DataSourceIngestTaskScheduler() {
    }

    synchronized void addTask(DataSourceIngestTask task) throws InterruptedException {
        task.getIngestJob().notifyTaskPending();
        try {
            tasks.put(task);
        }
        catch (InterruptedException ex) {
            // RJCTOD: Need a safety notification to undo above
        }
    }

    DataSourceIngestTask getNextTask() throws InterruptedException {
        return tasks.take();
    }

    boolean hasTasksForIngestJob(long jobId) {
        for (DataSourceIngestTask task : tasks) {
            if (task.getIngestJobId() == jobId) {
                return true;
            }
        }
        return false;
    }
}
