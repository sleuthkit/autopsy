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
package org.sleuthkit.autopsy.modules.embeddedfileextractor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.sleuthkit.autopsy.apputils.ApplicationLoggers;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.threadutils.TaskRetryUtil;

/**
 * An executor that will be used for calling java.io.File methods as tasks with
 * retries. Retries are employed here due to observed issues with hangs when
 * attempting these operations on case folders stored on a certain type of
 * network file system. The problem was that calls to File.exists() and
 * File.mkDirs() were never returning and ingest jobs on auto ingest nodes were
 * getting stuck indefinitely (see Jira-6735).
 *
 * This solution is based on
 * https://stackoverflow.com/questions/28279034/java-check-safely-if-a-file-exist-on-a-network-drive.
 * We are presently limiting it to File methods and not using this technique for
 * file writes.
 *
 * IMPORTANT: Stalled threads that have timed out and been cancelled may never
 * complete and system resources may eventually be exhausted. However, this was
 * deemed better than having an auto ingest node hang for nineteen days, as in
 * the Jira story.
 */
class FileTaskExecutor {

    private static final int MIN_THREADS_IN_POOL = 4;
    private static final int MAX_THREADS_IN_POOL = Integer.MAX_VALUE; // Effectively no limit
    private static final String FILE_IO_TASK_THREAD_NAME = "file-io-executor-task-%d";
    private static final String FILE_EXISTS_TASK_DESC_FMT_STR = "Checking if %s already exists";
    private static final String MKDIRS_TASK_DESC_FMT_STR = "Making directory %s";
    private static final String NEW_FILE_TASK_DESC_FMT_STR = "Creating new file %s";
    private static final String FILE_OPS_LOG_NAME = "efe_file_ops";
    private static final Logger logger = ApplicationLoggers.getLogger(FILE_OPS_LOG_NAME);
    private final ScheduledThreadPoolExecutor executor;
    private final TaskTerminator terminator;

    /**
     * Constructs an executor that will be used for calling java.io.File methods
     * as tasks with retries. Retries are employed here due to observed issues
     * with hangs when attempting these operations on case folders stored on a
     * certain type of network file system. The problem was that calls to
     * File.exists() and File.mkDirs() were never returning and ingest jobs on
     * auto ingest nodes were getting stuck indefinitely (see Jira-6735).
     *
     * This solution is based on
     * https://stackoverflow.com/questions/28279034/java-check-safely-if-a-file-exist-on-a-network-drive.
     * We are presently limiting it to File methods and not using this technique
     * for file writes.
     *
     * IMPORTANT: Stalled threads that have timed out and been cancelled may
     * never complete and system resources may eventually be exhausted. However,
     * this was deemed better than having an auto ingest node hang for nineteen
     * days, as in the Jira story.
     *
     * @param context An ingest job context that will be used in a
     *                TaskRetryUtil.Terminator to cut off attempts to do a file
     *                I/O operation if the ingest job is cancelled. Optional,
     *                may be null.
     */
    FileTaskExecutor(IngestJobContext context) {
        executor = new ScheduledThreadPoolExecutor(MIN_THREADS_IN_POOL, new ThreadFactoryBuilder().setNameFormat(FILE_IO_TASK_THREAD_NAME).build());
        executor.setMaximumPoolSize(MAX_THREADS_IN_POOL);
        if (context != null) {
            terminator = new TaskTerminator(context);
        } else {
            terminator = null;
        }
    }

    /**
     * Attempts to check whether a given file exists, retrying several times if
     * necessary.
     *
     * @param file The file.
     *
     * @return True or false.
     *
     * @throws FileTaskFailedException Thrown if the file I/O task could not be
     *                                 completed.
     * @throws InterruptedException    Thrown if the file I/O task is
     *                                 interrupted.
     */
    boolean exists(final File file) throws FileTaskFailedException, InterruptedException {
        Callable<Boolean> task = () -> {
            return file.exists();
        };
        return attemptTask(task, String.format(FILE_EXISTS_TASK_DESC_FMT_STR, file.getPath()));
    }

    /**
     * Attempts to create the parent directories for a given file, retrying
     * several times if necessary.
     *
     * @param file The file.
     *
     * @return True on success or false on failure.
     *
     * @throws FileTaskFailedException Thrown if the file I/O task could not be
     *                                 completed.
     * @throws InterruptedException    Thrown if the file I/O task is
     *                                 interrupted.
     */
    boolean mkdirs(final File file) throws FileTaskFailedException, InterruptedException {
        Callable<Boolean> task = () -> {
            return file.mkdirs();
        };
        return attemptTask(task, String.format(MKDIRS_TASK_DESC_FMT_STR, file.getPath()));
    }

    /**
     * Attempts to create a new empty file, retrying several times if necessary.
     *
     * @param file The file.
     *
     * @return True on success or false on failure.
     *
     * @throws FileTaskFailedException Thrown if the file I/O task could not be
     *                                 completed.
     * @throws InterruptedException    Thrown if the file I/O task is
     *                                 interrupted.
     */
    boolean createNewFile(final File file) throws FileTaskFailedException, InterruptedException {
        Callable<Boolean> task = () -> {
            return file.createNewFile();
        };
        return attemptTask(task, String.format(NEW_FILE_TASK_DESC_FMT_STR, file.getPath()));
    }

    /**
     * Attempts a java.io.File task with retries.
     *
     * @param task     The task.
     * @param taskDesc A description of the task, used for logging.
     *
     * @return True on success or false on failure.
     *
     * @throws FileTaskFailedException Thrown if the task could not be
     *                                 completed.
     * @throws InterruptedException    Thrown if the task is interrupted.
     */
    private boolean attemptTask(Callable<Boolean> task, String taskDesc) throws FileTaskFailedException, InterruptedException {
        List<TaskRetryUtil.TaskAttempt> attempts = new ArrayList<>();
        attempts.add(new TaskRetryUtil.TaskAttempt(0L, 10L, TimeUnit.MINUTES));
        attempts.add(new TaskRetryUtil.TaskAttempt(5L, 10L, TimeUnit.MINUTES));
        attempts.add(new TaskRetryUtil.TaskAttempt(10L, 10L, TimeUnit.MINUTES));
        attempts.add(new TaskRetryUtil.TaskAttempt(15L, 10L, TimeUnit.MINUTES));
        Boolean success = TaskRetryUtil.attemptTask(task, attempts, executor, terminator, logger, taskDesc);
        if (success == null) {
            throw new FileTaskFailedException(taskDesc + " failed");
        }
        return success;
    }

    /**
     * Shuts down this executor.
     *
     * IMPORTANT: No attempt is made to wait for tasks to complete and stalled
     * threads are possible. See class header documentation.
     */
    void shutDown() {
        /*
         * Not waiting for task terminaton because some tasks may never
         * terminate. See class doc.
         */
        executor.shutdownNow();
    }

    /**
     * A TaskRetryUtil.Terminator that uses an ingest job context to cut off
     * attempts to do a java.io.File operation if the ingest job is cancelled.
     */
    private static class TaskTerminator implements TaskRetryUtil.Terminator {

        private final IngestJobContext context;

        /**
         * Construct a TaskRetryUtil.Terminator that uses an ingest job context
         * to cut off attempts to do a java.io.File operation if the ingest job
         * is cancelled.
         *
         * @param context The ingest job context.
         */
        TaskTerminator(IngestJobContext context) {
            this.context = context;
        }

        @Override
        public boolean stopTaskAttempts() {
            /*
             * This works because IngestJobContext.fileIngestIsCancelled() calls
             * IngestJobPipeline.isCancelled(), which returns the value of a
             * volatile variable.
             */
            return context.fileIngestIsCancelled();
        }
    }

    /**
     * Exception thrown when a java.io.File task failed.
     */
    static final class FileTaskFailedException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception thrown when a java.io.File task failed.
         *
         * @param message The exception message.
         */
        private FileTaskFailedException(String message) {
            super(message);
        }

        /**
         * Constructs an exception thrown when a java.io.File task failed.
         *
         * @param message The exception message.
         * @param cause   The cause of the exception.
         */
        private FileTaskFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
