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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.Content;

final class DataSourceIngestTaskScheduler implements IngestTaskQueue {

    private static final DataSourceIngestTaskScheduler instance = new DataSourceIngestTaskScheduler();
    private final List<DataSourceIngestTask> tasks = new ArrayList<>();  // Guarded by this
    private final LinkedBlockingQueue<DataSourceIngestTask> tasksQueue = new LinkedBlockingQueue<>();

    static DataSourceIngestTaskScheduler getInstance() {
        return instance;
    }

    private DataSourceIngestTaskScheduler() {
    }

    synchronized void scheduleTask(IngestJob job, Content dataSource) throws InterruptedException {
        DataSourceIngestTask task = new DataSourceIngestTask(job, dataSource);
        tasks.add(task);
        try {
            // Should not block, queue is (theoretically) unbounded.
            tasksQueue.put(task);
        } catch (InterruptedException ex) {
            tasks.remove(task);
            Logger.getLogger(DataSourceIngestTaskScheduler.class.getName()).log(Level.FINE, "Interruption of unexpected block on tasks queue", ex); //NON-NLS
            throw ex;
        }
    }

    @Override
    public IngestTask getNextTask() throws InterruptedException {
        return tasksQueue.take();
    }

    synchronized void notifyTaskCompleted(DataSourceIngestTask task) {
        tasks.remove(task);
    }

    synchronized boolean hasIncompleteTasksForIngestJob(IngestJob job) {
        long jobId = job.getId();
        for (DataSourceIngestTask task : tasks) {
            if (task.getIngestJob().getId() == jobId) {
                return true;
            }
        }
        return false;
    }
}
