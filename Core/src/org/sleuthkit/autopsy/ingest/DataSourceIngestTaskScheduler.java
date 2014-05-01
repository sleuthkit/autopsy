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

    synchronized void addTask(DataSourceIngestTask task) {
        // The capacity of the tasks queue is not bounded, so the call 
        // to put() should not block except for normal synchronized access. 
        // Still, notify the job that the task has been added first so that 
        // the take() of the task cannot occur before the notification.
        task.getIngestJob().notifyTaskAdded();

        // If the thread executing this code is ever interrupted, it is 
        // because the number of ingest threads has been decreased while
        // ingest jobs are running. This thread will exit in an orderly fashion,
        // but the task still needs to be enqueued rather than lost.
        while (true) {
            try {
                tasks.put(task);
                break;
            } catch (InterruptedException ex) {
                // Reset the interrupted status of the thread so the orderly
                // exit can occur in the intended place.
                Thread.currentThread().interrupt();
            }
        }
    }

    DataSourceIngestTask getNextTask() throws InterruptedException {
        return tasks.take(); 
    }
}
