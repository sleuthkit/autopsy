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

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
     * @return Thread dump of all live threads
     */
    public static String generateThreadDump() {
        StringBuilder threadDump = new StringBuilder(System.lineSeparator());
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 100);
        for (ThreadInfo threadInfo : threadInfos) {
            // Break the stack trace into lines and then put back together using the
            // appropriate line ending for the system.
            threadDump.append(new BufferedReader(new StringReader(threadInfo.toString()))
                    .lines()
                    .collect(Collectors.joining(System.lineSeparator())));
            threadDump.append(System.lineSeparator());
        }

        long[] deadlockThreadIds = threadMXBean.findDeadlockedThreads();
        if (deadlockThreadIds != null) {
            threadDump.append("-------------------List of Deadlocked Thread IDs ---------------------");
            threadDump.append(System.lineSeparator());
            String idsList = (Arrays
                    .stream(deadlockThreadIds)
                    .boxed()
                    .collect(Collectors.toList()))
                    .stream().map(n -> String.valueOf(n))
                    .collect(Collectors.joining("-", "{", "}"));
            threadDump.append(idsList);
        }
        return threadDump.toString();
    }

    private ThreadUtils() {
    }
}
