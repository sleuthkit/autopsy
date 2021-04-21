/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2021 Basis Technology Corp.
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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * Concurrent programming utilities.
 */
final public class ThreadUtils {

    private static final long DEFAULT_TIMEOUT = 5;
    private static final TimeUnit DEFAULT_TIMEOUT_UNITS = TimeUnit.SECONDS;

    /**
     * Shuts down a task executor service, unconditionally waiting until all
     * tasks are terminated.
     *
     * @param executor The executor.
     */
    public static void shutDownTaskExecutor(ExecutorService executor) {
        executor.shutdown();
        boolean tasksCompleted = false;
        while (!tasksCompleted) {
            try {
                tasksCompleted = executor.awaitTermination(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNITS);
            } catch (InterruptedException ignored) {
                /*
                 * Ignore interrupts. The policy implemented by this method is
                 * an unconditional wait.
                 */
            }
        }
    }

    /**
     * Returns the thread info for all live threads with stack trace and
     * synchronization information. Some threads included in the returned array
     * may have been terminated when this method returns.
     *
     * @param lockedMonitors      if true, dump all locked monitors.
     * @param lockedSynchronizers if true, dump all locked ownable
     *                            synchronizers.
     *
     * @return Thread dump of all live threads
     */
    public static String generateThreadDump(boolean lockedMonitors, boolean lockedSynchronizers) {
        StringBuilder threadDump = new StringBuilder(System.lineSeparator());
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers)) {
            threadDump.append(threadInfo.toString());
        }
        return threadDump.toString();
    }

    private ThreadUtils() {
    }
}
