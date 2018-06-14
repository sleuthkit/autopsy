/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2018 Basis Technology Corp.
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

import javax.annotation.concurrent.ThreadSafe;

/*
 * An ingest task queue that provides a blocking get next task method.
 */
@ThreadSafe
interface BlockingIngestTaskQueue {

    /**
     * Gets the next ingest task in the queue, blocking if the queue is empty.
     *
     * @return The next ingest task in the queue.
     *
     * @throws InterruptedException If the thread getting the task is
     *                              interrupted while blocked on a queue empty
     *                              condition.
     */
    IngestTask getNextTask() throws InterruptedException;

}
