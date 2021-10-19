/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2021 Basis Technology Corp.
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

import org.sleuthkit.datamodel.Content;

/**
 * An ingest task that will be executed by an ingest thread using a given ingest
 * job executor. Three examples of concrete types of ingest tasks are tasks to
 * analyze a data source, tasks to analyze the files in a data source, and tasks
 * to analyze data artifacts.
 */
abstract class IngestTask {

    private final static long NOT_SET = Long.MIN_VALUE;
    private final IngestJobExecutor ingestJobExecutor;
    private long threadId;

    /**
     * Constructs an ingest task that will be executed by an ingest thread using
     * a given ingest job executor.
     *
     * @param ingestJobExecutor The ingest job executor to use to execute the
     *                          task.
     */
    IngestTask(IngestJobExecutor ingestJobExecutor) {
        this.ingestJobExecutor = ingestJobExecutor;
        threadId = NOT_SET;
    }

    /**
     * Gets the ingest job executor to use to execute this task.
     *
     * @return The ingest job pipeline.
     */
    IngestJobExecutor getIngestJobExecutor() {
        return ingestJobExecutor;
    }

    /**
     * Gets the data source for the ingest job of which this task is a part.
     *
     * @return The data source.
     */
    Content getDataSource() {
        return getIngestJobExecutor().getDataSource();
    }

    /**
     * Gets the thread ID of the ingest thread executing this task.
     *
     * @return The thread ID.
     */
    long getThreadId() {
        return threadId;
    }

    /**
     * Sets the thread ID of the ingest thread executing this task.
     *
     * @param threadId The thread ID.
     */
    void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    /**
     * Records the ingest thread ID of the calling thread and executes this task
     * using the ingest job executor specified when the task was created. The
     * implementation of the method should simply call
     * super.setThreadId(threadId) and getIngestJobPipeline().process(this).
     *
     * @param threadId The numeric ID of the ingest thread executing this task.
     */
    abstract void execute(long threadId);

}
