/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * General purpose actions which can be performed on Threads.
 */
final public class ThreadUtils {

    private static final long DEFAULT_TIMEOUT = 5;
    private static final TimeUnit DEFAULT_TIMEOUT_UNITS = TimeUnit.SECONDS;

    /**
     * Shuts down a task executor service, waiting until all tasks are
     * terminated.
     *
     * @param executor The executor.
     */
    public static void shutDownTaskExecutor(ExecutorService executor) {
        executor.shutdown();
        boolean taskCompleted = false;
        while (!taskCompleted) {
            try {
                taskCompleted = executor.awaitTermination(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNITS);
            } catch (InterruptedException ignored) {
                /*
                 * The current policy is to wait for the task to finish so that
                 * the case can be left in a consistent state.
                 *
                 * For a specific example of the motivation for this policy,
                 * note that a application service (Solr search service)
                 * experienced an error condition when opening case resources
                 * that left the service blocked uninterruptibly on a socket
                 * read. This eventually led to a mysterious "freeze" as the
                 * user-cancelled service task continued to run holdiong a lock
                 * that a UI thread soon tried to acquire. Thus it has been
                 * deemed better to make the "freeze" happen in a more
                 * informative way, i.e., with the progress indicator for the
                 * unfinished task on the screen, if a similar error condition
                 * arises again.
                 */
            }
        }
    }

    private ThreadUtils() {
    }
}
