/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.threadutils;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A utility that attempts a task a specified number of times with a specified
 * delay before each attempt and an optional timeout for each attempt. If an
 * attempt times out, the attempt will be cancelled and the next attempt, if
 * any, will begin.
 */
public class TaskRetryUtil {

    /**
     * Encapsulates the specification of a task attempt for the attemptTask()
     * utility.
     */
    public static class TaskAttempt {

        private final Long delay;
        private final TimeUnit delayTimeUnit;
        private final Long timeOut;
        private final TimeUnit timeOutTimeUnit;

        /**
         * Constructs an object that encapsulates the specification of a task
         * attempt for the attemptTask() utility.
         *
         * @param delay         The delay before the task should be attempted,
         *                      may be zero or any positive integer.
         * @param delayTimeUnit The time unit for the delay before the task
         *                      should be attempted.
         */
        public TaskAttempt(Long delay, TimeUnit delayTimeUnit) {
            this(delay, delayTimeUnit, null, null);
        }

        /**
         * Constructs an object that encapsulates the specification of a task
         * attempt for the attemptTask() utility.
         *
         * @param delay           The delay before the task should be attempted,
         *                        may be zero or any positive integer.
         * @param delayTimeUnit   The time unit for the delay before the task
         *                        should be attempted.
         * @param timeOut         The timeout for the task attempt, may be zero
         *                        or any positive integer.
         * @param timeOutTimeUnit The time unit for the task timeout.
         */
        public TaskAttempt(Long delay, TimeUnit delayTimeUnit, Long timeOut, TimeUnit timeOutTimeUnit) {
            if (delay == null || delay < 0) {
                throw new IllegalArgumentException(String.format("Argument for delay parameter = %d", delay));
            }
            if (delayTimeUnit == null) {
                throw new IllegalArgumentException("Argument for delayTimeUnit parameter is null");
            }
            if (timeOut != null && timeOut < 0) {
                throw new IllegalArgumentException(String.format("Argument for timeout parameter = %d", delay));
            }
            if (timeOut != null && timeOutTimeUnit == null) {
                throw new IllegalArgumentException("Argument for timeOutTimeUnit parameter is null");
            }
            this.delay = delay;
            this.delayTimeUnit = delayTimeUnit;
            this.timeOut = timeOut;
            this.timeOutTimeUnit = timeOutTimeUnit;
        }

        /**
         * Gets the delay before the task should be attempted, may be zero or
         * any positive integer.
         *
         * @return The delay.
         */
        public Long getDelay() {
            return delay;
        }

        /**
         * Gets the time unit for the delay before the task should be attempted.
         *
         * @return The delay time unit.
         */
        public TimeUnit getDelayTimeUnit() {
            return delayTimeUnit;
        }

        /**
         * Gets the the optional timeout for the task attempt.
         *
         * @return The timeout or null if there is no timeout.
         */
        public Long getTimeout() {
            return timeOut;
        }

        /**
         * Gets the time unit for the optional task attempt timeout.
         *
         * @return The timeout time unit or null if there is no timeout.
         */
        public TimeUnit getTimeoutTimeUnit() {
            return timeOutTimeUnit;
        }

    }

    /**
     * A TaskTerminator can be supplied to the attemptTask() utility. The
     * utility will query the RetryTerminator before starting each task attempt
     */
    public interface Terminator {

        /**
         * RJCTODO
         *
         * @return
         */
        boolean stopTaskAttempts();

    }

    /**
     * Attempts a task a specified number of times with a specified delay before
     * each attempt and an optional timeout for each attempt. If an attempt
     * times out, that particular attempt will be cancelled.
     *
     * @param <T>        The return type of the task.
     * @param task       The task.
     * @param attempts   The details for each attempt of the task.
     * @param executor   The scheduled task executor to be used to attempt the
     *                   task.
     * @param terminator A task terminator that can be used to stop the task
     *                   attempts between attempts. Optional, may be null.
     * @param logger     A logger that will be used to log info messages about
     *                   each task attempt and for error messages. Optional, may
     *                   be null.
     * @param taskDesc   A description of the task for log messages. Optional,
     *                   may be null.
     *
     * @return The task result if the task was completed, null otherwise.
     *
     * @throws InterruptedException
     */
    public static <T> T attemptTask(Callable<T> task, List<TaskAttempt> attempts, ScheduledThreadPoolExecutor executor, Terminator terminator, Logger logger, String taskDesc) throws InterruptedException {
        T result = null;
        int attemptCounter = 0;
        while (result == null && attemptCounter < attempts.size()) {
            if (terminator != null && terminator.stopTaskAttempts()) {
                break;
            }
            TaskAttempt attempt = attempts.get(attemptCounter);
            if (logger != null) {
                if (attempt.getTimeout() != null) {
                    logger.log(Level.INFO, "{0} (attempt = {1}, waited = {2} {3}, timeout = {4} {5})", new Object[]{taskDesc, attemptCounter + 1, attempt.getDelay(), attempt.getDelayTimeUnit(), attempt.getTimeout(), attempt.getTimeoutTimeUnit()});
                } else {
                    logger.log(Level.INFO, "{0} (attempt = {1}, waited = {2} {3}, timeout = {4} {5})", new Object[]{taskDesc, attemptCounter + 1, attempt.getDelay(), attempt.getDelayTimeUnit(), attempt.getTimeout(), attempt.getTimeoutTimeUnit()});
                }
            }
            ScheduledFuture<T> future = executor.schedule(task, attempt.getDelay(), attempt.getDelayTimeUnit());
            try {
                if (attempt.getTimeout() != null) {
                    result = future.get(attempt.getTimeout(), attempt.getTimeoutTimeUnit());
                } else {
                    result = future.get();
                }
            } catch (ExecutionException ex) {
                if (logger != null) {
                    logger.log(Level.SEVERE, String.format("Error executing %s", taskDesc != null ? taskDesc : "task"), ex);
                }
            } catch (TimeoutException ex) {
                if (logger != null) {
                    logger.log(Level.SEVERE, String.format("Time out executing %s, cancelling attempt", taskDesc != null ? taskDesc : "task"), ex);
                }
                future.cancel(true);
            }
            ++attemptCounter;
        }
        return result;
    }

    private TaskRetryUtil() {
        // Private contructor to prevent TaskRetryUtil object instantiation. 
    }

}
