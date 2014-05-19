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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import org.sleuthkit.datamodel.Content;

final class DataSourceIngestTaskScheduler implements IngestTaskQueue{

    private static final DataSourceIngestTaskScheduler instance = new DataSourceIngestTaskScheduler();
    private final Set<Long> tasksInProgress = new HashSet<>(); // Guarded by this
    private final LinkedBlockingQueue<DataSourceIngestTask> dataSourceTasks = new LinkedBlockingQueue<>();

    static DataSourceIngestTaskScheduler getInstance() {
        return instance;
    }

    private DataSourceIngestTaskScheduler() {
    }
    
    synchronized void addDataSourceTask(IngestJob job, Content dataSource) throws InterruptedException {
        tasksInProgress.add(job.getId());
        dataSourceTasks.put(new DataSourceIngestTask(job, dataSource));
    }
        
    @Override
    public IngestTask getNextTask() throws InterruptedException {
        return dataSourceTasks.take();
    }
    
    synchronized void taskIsCompleted(DataSourceIngestTask task) {
        tasksInProgress.remove(task.getIngestJob().getId());
    }
    
    synchronized boolean hasIncompleteTasks(IngestJob job) {
        return tasksInProgress.contains(job.getId());
    }
}
