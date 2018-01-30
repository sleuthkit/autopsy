/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
import java.util.concurrent.ThreadFactory;
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
                 * an unconditional wait.:
                 */
            }
        }
    }

    /**
     * A thread factory that allows for the creation of distinctly named task
     * threads by an ExecutorService constructed using an Executors factory
     * method. Each thread created using the factory will be named using the
     * thread name and a numerical suffix.
     */
    public static class NamedThreadFactory implements ThreadFactory {

        private final String threadName;

        /**
         * Contructs a thread factory that allows for the creation of distinctly
         * named task threads by an ExecutorService constructed using an
         * Executors factory method. Each thread created using the factory will
         * be named using the thread name and a numerical suffix.
         *
         * @param threadName The name of the threads.
         */
        public NamedThreadFactory(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public Thread newThread(Runnable task) {
            return new Thread(task, threadName);
        }

    }

    private ThreadUtils() {
    }
}
