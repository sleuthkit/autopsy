/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TreeSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Creates ingest tasks for ingest jobs, queueing the tasks in priority order
 * for execution by the ingest manager's ingest threads.
 */
@ThreadSafe
final class IngestTasksScheduler {

    private static final int FAT_NTFS_FLAGS = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();
    private static final Logger logger = Logger.getLogger(IngestTasksScheduler.class.getName());
    @GuardedBy("IngestTasksScheduler.this")
    private static IngestTasksScheduler instance;
    private final IngestTaskTrackingQueue dataSourceIngestTasksQueue;
    @GuardedBy("this")
    private final TreeSet<FileIngestTask> topLevelFileIngestTasksQueue;
    @GuardedBy("this")
    private final Deque<FileIngestTask> batchedFileIngestTasksQueue;
    @GuardedBy("this")
    private final Queue<FileIngestTask> streamedFileIngestTasksQueue;
    private final IngestTaskTrackingQueue fileIngestTasksQueue;
    private final IngestTaskTrackingQueue artifactIngestTasksQueue;

    /**
     * Gets the ingest tasks scheduler singleton that creates ingest tasks for
     * ingest jobs, queueing the tasks in priority order for execution by the
     * ingest manager's ingest threads.
     */
    synchronized static IngestTasksScheduler getInstance() {
        if (IngestTasksScheduler.instance == null) {
            IngestTasksScheduler.instance = new IngestTasksScheduler();
        }
        return IngestTasksScheduler.instance;
    }

    /**
     * Constructs an ingest tasks scheduler that creates ingest tasks for ingest
     * jobs, queueing the tasks in priority order for execution by the ingest
     * manager's ingest threads.
     */
    private IngestTasksScheduler() {
        dataSourceIngestTasksQueue = new IngestTaskTrackingQueue();
        topLevelFileIngestTasksQueue = new TreeSet<>(new RootDirectoryTaskComparator());
        batchedFileIngestTasksQueue = new LinkedList<>();
        fileIngestTasksQueue = new IngestTaskTrackingQueue();
        streamedFileIngestTasksQueue = new LinkedList<>();
        artifactIngestTasksQueue = new IngestTaskTrackingQueue();
    }

    /**
     * Gets the data source level ingest tasks queue. This queue is a blocking
     * queue consumed by the ingest manager's data source level ingest thread.
     *
     * @return The queue.
     */
    BlockingIngestTaskQueue getDataSourceIngestTaskQueue() {
        return dataSourceIngestTasksQueue;
    }

    /**
     * Gets the file level ingest tasks queue. This queue is a blocking queue
     * consumed by the ingest manager's file level ingest threads.
     *
     * @return The queue.
     */
    BlockingIngestTaskQueue getFileIngestTaskQueue() {
        return fileIngestTasksQueue;
    }

    /**
     * Gets the data artifact ingest tasks queue. This queue is a blocking queue
     * consumed by the ingest manager's data artifact ingest thread.
     *
     * @return The queue.
     */
    BlockingIngestTaskQueue getResultIngestTaskQueue() {
        return artifactIngestTasksQueue;
    }

    /**
     * Schedules ingest tasks based on the types of ingest modules that the
     * ingest pipeline that will exedute tasks has. Scheduling these tasks
     * atomically means that it is valid to call currentTasksAreCompleted()
     * immediately after calling this method. Note that the may cause some or
     * even all of any file tasks to be discarded.
     *
     * @param ingestPipeline The ingest pipeline that will execute the scheduled
     *                       tasks. A reference to the pipeline is added to each
     *                       task so that when the task is dequeued by an ingest
     *                       thread the task can pass the target Content of the
     *                       task to the pipeline for processing by the
     *                       pipeline's ingest modules.
     */
    synchronized void scheduleIngestTasks(IngestJobExecutor ingestPipeline) {
        if (!ingestPipeline.isCancelled()) {
            if (ingestPipeline.hasDataSourceIngestModules()) {
                scheduleDataSourceIngestTask(ingestPipeline);
            }
            if (ingestPipeline.hasFileIngestModules()) {
                scheduleFileIngestTasks(ingestPipeline, Collections.emptyList());
            }
            if (ingestPipeline.hasDataArtifactIngestModules()) {
                scheduleDataArtifactIngestTasks(ingestPipeline);
            }
        }
    }

    /**
     * Schedules a data source level ingest task for an ingest job. The data
     * source is obtained from the ingest pipeline passed in.
     *
     * @param ingestPipeline The ingest pipeline that will execute the scheduled
     *                       task. A reference to the pipeline is added to the
     *                       task so that when the task is dequeued by an ingest
     *                       thread the task can pass the target Content of the
     *                       task to the pipeline for processing by the
     *                       pipeline's ingest modules.
     */
    synchronized void scheduleDataSourceIngestTask(IngestJobExecutor ingestPipeline) {
        if (!ingestPipeline.isCancelled()) {
            DataSourceIngestTask task = new DataSourceIngestTask(ingestPipeline);
            try {
                dataSourceIngestTasksQueue.putLast(task);
            } catch (InterruptedException ex) {
                IngestTasksScheduler.logger.log(Level.INFO, String.format("Ingest tasks scheduler interrupted while blocked adding a task to the data source level ingest task queue (pipelineId={%d)", ingestPipeline.getIngestJobId()), ex);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Schedules file tasks for either all the files, or a given subset of the
     * files, for a data source. The data source is obtained from the ingest
     * pipeline passed in.
     *
     * @param ingestPipeline The ingest pipeline that will execute the scheduled
     *                       tasks. A reference to the pipeline is added to each
     *                       task so that when the task is dequeued by an ingest
     *                       thread the task can pass the target Content of the
     *                       task to the pipeline for processing by the
     *                       pipeline's ingest modules.
     * @param files          A subset of the files from the data source; if
     *                       empty, then all if the files from the data source
     *                       are candidates for scheduling.
     */
    synchronized void scheduleFileIngestTasks(IngestJobExecutor ingestPipeline, Collection<AbstractFile> files) {
        if (!ingestPipeline.isCancelled()) {
            Collection<AbstractFile> candidateFiles;
            if (files.isEmpty()) {
                candidateFiles = getTopLevelFiles(ingestPipeline.getDataSource());
            } else {
                candidateFiles = files;
            }
            for (AbstractFile file : candidateFiles) {
                FileIngestTask task = new FileIngestTask(ingestPipeline, file);
                if (IngestTasksScheduler.shouldEnqueueFileTask(task)) {
                    topLevelFileIngestTasksQueue.add(task);
                }
            }
            refillFileIngestTasksQueue();
        }
    }

    /**
     * Schedules file tasks for a collection of "streamed" files for a streaming
     * ingest job.
     *
     * @param ingestPipeline The ingest pipeline for the job. A reference to the
     *                       pipeline is added to each task so that when the
     *                       task is dequeued by an ingest thread and the task's
     *                       execute() method is called, execute() can pass the
     *                       target Content of the task to the pipeline for
     *                       processing by the pipeline's ingest modules.
     * @param files          A list of file object IDs for the streamed files.
     */
    synchronized void scheduleStreamedFileIngestTasks(IngestJobExecutor ingestPipeline, List<Long> fileIds) {
        if (!ingestPipeline.isCancelled()) {
            for (long id : fileIds) {
                /*
                 * Create the file ingest task. Note that we do not do the
                 * shouldEnqueueFileTask() check here in order to delay querying
                 * the case database to construct the AbstractFile object. The
                 * file filter will be applied before the file task makes it to
                 * the task queue consumed by the file ingest threads.
                 */
                FileIngestTask task = new FileIngestTask(ingestPipeline, id);
                streamedFileIngestTasksQueue.add(task);
            }
            refillFileIngestTasksQueue();
        }
    }

    /**
     * Schedules file level ingest tasks for a given set of files for an ingest
     * job by adding them directly to the front of the file tasks queue consumed
     * by the ingest manager's file ingest threads. This method is intended to
     * be used to schedule files that are products of ingest module processing,
     * e.g., extracted files and carved files.
     *
     * @param ingestPipeline The ingest pipeline for the job. A reference to the
     *                       pipeline is added to each task so that when the
     *                       task is dequeued by an ingest thread and the task's
     *                       execute() method is called, execute() can pass the
     *                       target Content of the task to the pipeline for
     *                       processing by the pipeline's ingest modules.
     * @param files          The files.
     */
    synchronized void fastTrackFileIngestTasks(IngestJobExecutor ingestPipeline, Collection<AbstractFile> files) {
        if (!ingestPipeline.isCancelled()) {
            /*
             * Put the files directly into the queue for the file ingest
             * threads, if they pass the file filter for the job. The files are
             * added to the queue for the ingest threads BEFORE the other queued
             * tasks because the use case for this method is scheduling new
             * carved or derived files from a high priority task that is already
             * in progress.
             */
            for (AbstractFile file : files) {
                FileIngestTask fileTask = new FileIngestTask(ingestPipeline, file);
                if (shouldEnqueueFileTask(fileTask)) {
                    try {
                        fileIngestTasksQueue.putFirst(fileTask);
                    } catch (InterruptedException ex) {
                        DataSource dataSource = ingestPipeline.getDataSource();
                        logger.log(Level.WARNING, String.format("Interrupted while enqueuing file tasks for %s (data source object ID = %d)", dataSource.getName(), dataSource.getId()), ex); //NON-NLS
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    /**
     * Schedules data artifact ingest tasks for any data artifacts that have
     * already been added to the case database for a data source. The data
     * source is obtained from the ingest pipeline passed in.
     *
     * @param ingestPipeline The ingest pipeline for the job. A reference to the
     *                       pipeline is added to each task so that when the
     *                       task is dequeued by an ingest thread and the task's
     *                       execute() method is called, execute() can pass the
     *                       target Content of the task to the pipeline for
     *                       processing by the pipeline's ingest modules.
     */
    synchronized void scheduleDataArtifactIngestTasks(IngestJobExecutor ingestPipeline) {
        if (!ingestPipeline.isCancelled()) {
            Blackboard blackboard = Case.getCurrentCase().getSleuthkitCase().getBlackboard();
            try {
                List<DataArtifact> artifacts = blackboard.getDataArtifacts(ingestPipeline.getDataSource().getId(), null);
                scheduleDataArtifactIngestTasks(ingestPipeline, artifacts);
            } catch (TskCoreException ex) {
                DataSource dataSource = ingestPipeline.getDataSource();
                logger.log(Level.SEVERE, String.format("Failed to retrieve data artifacts for %s (data source object ID = %d)", dataSource.getName(), dataSource.getId()), ex); //NON-NLS
            }
        }
    }

    /**
     * Schedules data artifact ingest tasks for an ingest job. This method is
     * intended to be used to schedule artifacts that are products of ingest
     * module processing.
     *
     * @param ingestPipeline The ingest pipeline for the job. A reference to the
     *                       pipeline is added to each task so that when the
     *                       task is dequeued by an ingest thread and the task's
     *                       execute() method is called, execute() can pass the
     *                       target Content of the task to the pipeline for
     *                       processing by the pipeline's ingest modules.
     * @param artifacts      A subset of the data artifacts from the data
     *                       source; if empty, then all of the data artifacts
     *                       from the data source will be scheduled.
     */
    synchronized void scheduleDataArtifactIngestTasks(IngestJobExecutor ingestPipeline, List<DataArtifact> artifacts) {
        if (!ingestPipeline.isCancelled()) {
            for (DataArtifact artifact : artifacts) {
                DataArtifactIngestTask task = new DataArtifactIngestTask(ingestPipeline, artifact);
                try {
                    this.artifactIngestTasksQueue.putLast(task);
                } catch (InterruptedException ex) {
                    DataSource dataSource = ingestPipeline.getDataSource();
                    logger.log(Level.WARNING, String.format("Interrupted while enqueuing data artifact tasks for %s (data source object ID = %d)", dataSource.getName(), dataSource.getId()), ex); //NON-NLS
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Allows an ingest thread to notify this ingest task scheduler that a data
     * source level task has been completed.
     *
     * @param task The completed task.
     */
    synchronized void notifyTaskCompleted(DataSourceIngestTask task) {
        dataSourceIngestTasksQueue.taskCompleted(task);
    }

    /**
     * Allows an ingest thread to notify this ingest task scheduler that a file
     * level task has been completed.
     *
     * @param task The completed task.
     */
    synchronized void notifyTaskCompleted(FileIngestTask task) {
        fileIngestTasksQueue.taskCompleted(task);
        refillFileIngestTasksQueue();
    }

    /**
     * Allows an ingest thread to notify this ingest task scheduler that a data
     * artifact ingest task has been completed.
     *
     * @param task The completed task.
     */
    synchronized void notifyTaskCompleted(DataArtifactIngestTask task) {
        artifactIngestTasksQueue.taskCompleted(task);
    }

    /**
     * Queries the task scheduler to determine whether or not all of the ingest
     * tasks for an ingest job have been completed.
     *
     * @param ingestPipeline The ingest pipeline for the job.
     *
     * @return True or false.
     */
    synchronized boolean currentTasksAreCompleted(IngestJobExecutor ingestPipeline) {
        long pipelineId = ingestPipeline.getIngestJobId();
        return !(dataSourceIngestTasksQueue.hasTasksForJob(pipelineId)
                || hasTasksForJob(topLevelFileIngestTasksQueue, pipelineId)
                || hasTasksForJob(batchedFileIngestTasksQueue, pipelineId)
                || hasTasksForJob(streamedFileIngestTasksQueue, pipelineId)
                || fileIngestTasksQueue.hasTasksForJob(pipelineId)
                || artifactIngestTasksQueue.hasTasksForJob(pipelineId));
    }

    /**
     * Cancels the pending file ingest tasks for an ingest job, where the
     * pending tasks are the file ingest tasks that are in the upstream
     * scheduling queues (batch and streaming) that feed into the queue consumed
     * by the ingest manager's file ingest threads.
     *
     * Note that the "normal" way to cancel an ingest job is to mark the job as
     * cancelled, which causes the execute() methods of the ingest tasks for the
     * job to return immediately when called, leading to flushing all of the
     * tasks for the job out of the ingest task queues by the ingest threads and
     * an orderly progression through IngestTaskTrackingQueue bookkeeping and
     * the ingest job stages to early job completion. However, this method is a
     * cancellation speed booster. For example, it eliminates the creation of
     * what could be a large number of child tasks for both the top level files
     * in the batch root file tasks queue and any directories in the batch root
     * children file tasks queue.
     *
     * @param ingestJobPipeline The ingest pipeline for the job.
     */
    synchronized void cancelPendingFileTasksForIngestJob(IngestJobExecutor ingestJobPipeline) {
        long jobId = ingestJobPipeline.getIngestJobId();
        removeTasksForJob(topLevelFileIngestTasksQueue, jobId);
        removeTasksForJob(batchedFileIngestTasksQueue, jobId);
        removeTasksForJob(streamedFileIngestTasksQueue, jobId);
    }

    /**
     * Gets the top level files for a data source, such as file system root
     * directories, layout files, and virtual directories.
     *
     * @param dataSource The data source.
     *
     * @return The top level files.
     */
    private static List<AbstractFile> getTopLevelFiles(Content dataSource) {
        List<AbstractFile> topLevelFiles = new ArrayList<>();
        Collection<AbstractFile> rootObjects = dataSource.accept(new GetRootDirectoryVisitor());
        if (rootObjects.isEmpty() && dataSource instanceof AbstractFile) {
            // The data source is itself a file to be processed.
            topLevelFiles.add((AbstractFile) dataSource);
        } else {
            for (AbstractFile root : rootObjects) {
                List<Content> children;
                try {
                    children = root.getChildren();
                    if (children.isEmpty()) {
                        // Add the root object itself, it could be an unallocated
                        // space file, or a child of a volume or an image.
                        topLevelFiles.add(root);
                    } else {
                        // The root object is a file system root directory, get
                        // the files within it.
                        for (Content child : children) {
                            if (child instanceof AbstractFile) {
                                topLevelFiles.add((AbstractFile) child);
                            }
                        }
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Could not get children of root to enqueue: " + root.getId() + ": " + root.getName(), ex); //NON-NLS
                }
            }
        }
        return topLevelFiles;
    }

    /**
     * Refills the file ingest tasks queue consumed by the ingest manager's file
     * ingest threads with tasks from the upstream file task scheduling queues
     * (streamed and batch). Files from the streamed file ingest tasks queue are
     * prioritized. Applies the file filter for the ingest job and attempts to
     * move as many tasks as there are ingest threads.
     */
    synchronized private void refillFileIngestTasksQueue() {
        try {
            takeFromStreamingFileTasksQueue();
            takeFromBatchTasksQueues();
        } catch (InterruptedException ex) {
            IngestTasksScheduler.logger.log(Level.INFO, "Ingest tasks scheduler interrupted while blocked adding a task to the file level ingest task queue", ex);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Moves tasks from the upstream streamed file ingest tasks queue into the
     * file ingest tasks queue consumed by the ingest manager's file ingest
     * threads. Applies the file filter for the ingest job and attempts to move
     * as many tasks as there are ingest threads.
     */
    synchronized private void takeFromStreamingFileTasksQueue() throws InterruptedException {
        while (fileIngestTasksQueue.isEmpty()) {
            int taskCount = 0;
            while (taskCount < IngestManager.getInstance().getNumberOfFileIngestThreads()) {
                final FileIngestTask streamingTask = streamedFileIngestTasksQueue.poll();
                if (streamingTask == null) {
                    return; // No streaming tasks are queued right now
                }
                if (shouldEnqueueFileTask(streamingTask)) {
                    fileIngestTasksQueue.putLast(streamingTask);
                    taskCount++;
                }
            }
        }
    }

    /**
     * Moves tasks from the upstream batched file ingest task queues into the
     * file ingest tasks queue consumed by the ingest manager's file ingest
     * threads. A sequence of two upstream queues is used to interleave tasks
     * from different ingest jobs based on priority. Applies the file filter for
     * the ingest job and attempts to move as many tasks as there are ingest
     * threads.
     *
     * The upstream batched file task queues are:
     *
     * 1. The top level file tasks queue, which contains file tasks for the root
     * objects of data sources. For example, the top level file tasks for a disk
     * image data source are typically the tasks for the contents of the root
     * directories of the file systems. This queue is a priority queue that
     * attempts to ensure that user content is analyzed before general file
     * system content. It feeds into the batched file ingest tasks queue.
     *
     * 2. The batch file tasks queue, which contains top level file tasks moved
     * in from the top level file tasks queue, plus tasks for child files in the
     * descent from the root tasks to the final leaf tasks in the content trees
     * that are being analyzed for any given data source. This queue is a FIFO
     * queue that attempts to throttle the total number of file tasks by
     * deferring queueing of tasks for the children of files until the queue for
     * the file ingest threads is emptied.
     */
    synchronized private void takeFromBatchTasksQueues() throws InterruptedException {

        while (fileIngestTasksQueue.isEmpty()) {
            /*
             * If the batched file task queue is empty, move the highest
             * priority top level file task into it.
             */
            if (batchedFileIngestTasksQueue.isEmpty()) {
                final FileIngestTask topLevelTask = topLevelFileIngestTasksQueue.pollFirst();
                if (topLevelTask != null) {
                    batchedFileIngestTasksQueue.addLast(topLevelTask);
                }
            }

            /*
             * Try to move the next task from the batched file tasks queue into
             * the queue for the file ingest threads.
             */
            final FileIngestTask nextTask = batchedFileIngestTasksQueue.pollFirst();
            if (nextTask == null) {
                return;
            }
            if (shouldEnqueueFileTask(nextTask)) {
                fileIngestTasksQueue.putLast(nextTask);
            }

            /*
             * If the task that was just queued for the file ingest threads has
             * children, queue tasks for the children as well.
             */
            AbstractFile file = null;
            try {
                file = nextTask.getFile();
                for (Content child : file.getChildren()) {
                    if (child instanceof AbstractFile) {
                        AbstractFile childFile = (AbstractFile) child;
                        FileIngestTask childTask = new FileIngestTask(nextTask.getIngestJobPipeline(), childFile);
                        if (childFile.hasChildren()) {
                            batchedFileIngestTasksQueue.add(childTask);
                        } else if (shouldEnqueueFileTask(childTask)) {
                            fileIngestTasksQueue.putLast(childTask);
                        }
                    }
                }
            } catch (TskCoreException ex) {
                if (file != null) {
                    logger.log(Level.SEVERE, String.format("Error getting the children of %s (object ID = %d)", file.getName(), file.getId()), ex); //NON-NLS
                } else {
                    logger.log(Level.SEVERE, "Error loading file with object ID = {0}", nextTask.getFileId()); //NON-NLS
                }
            }
        }
    }

    /**
     * Evaluates the file for a file ingest task to determine whether or not the
     * file should be processed and therefore whether or not the task should be
     * enqueued. The evaluation includes applying the file filter for the task's
     * parent ingest job.
     *
     * @param task The task.
     *
     * @return True or false.
     */
    private static boolean shouldEnqueueFileTask(final FileIngestTask task) {
        AbstractFile file;
        try {
            file = task.getFile();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading file with ID {0}", task.getFileId());
            return false;
        }

        // Skip the task if the file is actually the pseudo-file for the parent
        // or current directory.
        String fileName = file.getName();

        if (fileName.equals(".") || fileName.equals("..")) {
            return false;
        }

        /*
         * Ensures that all directories, files which are members of the ingest
         * file filter, and unallocated blocks (when processUnallocated is
         * enabled) all continue to be processed. AbstractFiles which do not
         * meet one of these criteria will be skipped.
         *
         * An additional check to see if unallocated space should be processed
         * is part of the FilesSet.fileIsMemberOf() method.
         *
         * This code may need to be updated when
         * TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS comes into use by Autopsy.
         */
        if (!file.isDir() && !shouldBeCarved(task) && !fileAcceptedByFilter(task)) {
            return false;
        }

        // Skip the task if the file is one of a select group of special, large
        // NTFS or FAT file system files.
        if (file instanceof org.sleuthkit.datamodel.File) {
            final org.sleuthkit.datamodel.File f = (org.sleuthkit.datamodel.File) file;

            // Get the type of the file system, if any, that owns the file.
            TskData.TSK_FS_TYPE_ENUM fsType = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_UNSUPP;
            try {
                FileSystem fs = f.getFileSystem();
                if (fs != null) {
                    fsType = fs.getFsType();
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error querying file system for " + f, ex); //NON-NLS
            }

            // If the file system is not NTFS or FAT, don't skip the file.
            if ((fsType.getValue() & FAT_NTFS_FLAGS) == 0) {
                return true;
            }

            // Find out whether the file is in a root directory. 
            boolean isInRootDir = false;
            try {
                AbstractFile parent = f.getParentDirectory();
                if (parent == null) {
                    isInRootDir = true;
                } else {
                    isInRootDir = parent.isRoot();
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error querying parent directory for" + f.getName(), ex); //NON-NLS
            }

            // If the file is in the root directory of an NTFS or FAT file 
            // system, check its meta-address and check its name for the '$'
            // character and a ':' character (not a default attribute).
            if (isInRootDir && f.getMetaAddr() < 32) {
                String name = f.getName();
                if (name.length() > 0 && name.charAt(0) == '$' && name.contains(":")) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks whether or not a file should be carved for an ingest job.
     *
     * @param task The file ingest task for the file.
     *
     * @return True or false.
     */
    private static boolean shouldBeCarved(final FileIngestTask task) {
        try {
            AbstractFile file = task.getFile();
            return task.getIngestJobPipeline().shouldProcessUnallocatedSpace() && file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS);
        } catch (TskCoreException ex) {
            return false;
        }
    }

    /**
     * Checks whether or not a file is accepted (passes) the file filter for an
     * ingest job.
     *
     * @param task The file ingest task for the file.
     *
     * @return True or false.
     */
    private static boolean fileAcceptedByFilter(final FileIngestTask task) {
        try {
            AbstractFile file = task.getFile();
            return !(task.getIngestJobPipeline().getFileIngestFilter().fileIsMemberOf(file) == null);
        } catch (TskCoreException ex) {
            return false;
        }
    }

    /**
     * Checks whether or not a collection of ingest tasks includes a task for a
     * given ingest job.
     *
     * @param tasks      The tasks.
     * @param pipelineId The ID of the ingest pipeline for the job.
     *
     * @return True if there are no tasks for the job, false otherwise.
     */
    synchronized private static boolean hasTasksForJob(Collection<? extends IngestTask> tasks, long pipelineId) {
        for (IngestTask task : tasks) {
            if (task.getIngestJobPipeline().getIngestJobId() == pipelineId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes all of the ingest tasks associated with an ingest job from a
     * collection of tasks.
     *
     * @param tasks      The tasks.
     * @param pipelineId The ID of the ingest pipeline for the job.
     */
    private static void removeTasksForJob(Collection<? extends IngestTask> tasks, long pipelineId) {
        Iterator<? extends IngestTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            IngestTask task = iterator.next();
            if (task.getIngestJobPipeline().getIngestJobId() == pipelineId) {
                iterator.remove();
            }
        }
    }

    /**
     * Counts the number of ingest tasks in a collection of tasks for a given
     * ingest job.
     *
     * @param tasks      The tasks.
     * @param pipelineId The ID of the ingest pipeline for the job.
     *
     * @return The count.
     */
    private static int countTasksForJob(Collection<? extends IngestTask> tasks, long pipelineId) {
        int count = 0;
        for (IngestTask task : tasks) {
            if (task.getIngestJobPipeline().getIngestJobId() == pipelineId) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns a snapshot of the states of the tasks in progress for an ingest
     * job.
     *
     * @param jobId The identifier assigned to the job.
     *
     * @return
     */
    synchronized IngestJobTasksSnapshot getTasksSnapshotForJob(long jobId) {
        return new IngestJobTasksSnapshot(jobId, dataSourceIngestTasksQueue.countQueuedTasksForJob(jobId),
                countTasksForJob(topLevelFileIngestTasksQueue, jobId),
                countTasksForJob(batchedFileIngestTasksQueue, jobId),
                fileIngestTasksQueue.countQueuedTasksForJob(jobId),
                dataSourceIngestTasksQueue.countRunningTasksForJob(jobId) + fileIngestTasksQueue.countRunningTasksForJob(jobId) + artifactIngestTasksQueue.countRunningTasksForJob(jobId),
                countTasksForJob(streamedFileIngestTasksQueue, jobId),
                artifactIngestTasksQueue.countQueuedTasksForJob(jobId));
    }

    /**
     * Prioritizes tasks for the root directories file ingest tasks queue (file
     * system root directories, layout files and virtual directories).
     */
    private static class RootDirectoryTaskComparator implements Comparator<FileIngestTask> {

        @Override
        public int compare(FileIngestTask q1, FileIngestTask q2) {
            // In practice the case where one or both calls to getFile() fails
            // should never occur since such tasks would not be added to the queue.
            AbstractFile file1 = null;
            AbstractFile file2 = null;
            try {
                file1 = q1.getFile();
            } catch (TskCoreException ex) {
                // Do nothing - the exception has been logged elsewhere
            }

            try {
                file2 = q2.getFile();
            } catch (TskCoreException ex) {
                // Do nothing - the exception has been logged elsewhere
            }

            if (file1 == null) {
                if (file2 == null) {
                    return (int) (q2.getFileId() - q1.getFileId());
                } else {
                    return 1;
                }
            } else if (file2 == null) {
                return -1;
            }

            AbstractFilePriority.Priority p1 = AbstractFilePriority.getPriority(file1);
            AbstractFilePriority.Priority p2 = AbstractFilePriority.getPriority(file2);
            if (p1 == p2) {
                return (int) (file2.getId() - file1.getId());
            } else {
                return p2.ordinal() - p1.ordinal();
            }
        }

        /**
         * Used to prioritize file ingest tasks in the root tasks queue so that
         * user content is processed first.
         */
        private static class AbstractFilePriority {

            private AbstractFilePriority() {
            }

            enum Priority {

                LAST, LOW, MEDIUM, HIGH
            }

            static final List<Pattern> LAST_PRI_PATHS = new ArrayList<>();

            static final List<Pattern> LOW_PRI_PATHS = new ArrayList<>();

            static final List<Pattern> MEDIUM_PRI_PATHS = new ArrayList<>();

            static final List<Pattern> HIGH_PRI_PATHS = new ArrayList<>();

            /*
             * prioritize root directory folders based on the assumption that we
             * are looking for user content. Other types of investigations may
             * want different priorities.
             */
            static /*
             * prioritize root directory folders based on the assumption that we
             * are looking for user content. Other types of investigations may
             * want different priorities.
             */ {
                // these files have no structure, so they go last
                //unalloc files are handled as virtual files in getPriority()
                //LAST_PRI_PATHS.schedule(Pattern.compile("^\\$Unalloc", Pattern.CASE_INSENSITIVE));
                //LAST_PRI_PATHS.schedule(Pattern.compile("^\\Unalloc", Pattern.CASE_INSENSITIVE));
                LAST_PRI_PATHS.add(Pattern.compile("^pagefile", Pattern.CASE_INSENSITIVE));
                LAST_PRI_PATHS.add(Pattern.compile("^hiberfil", Pattern.CASE_INSENSITIVE));
                // orphan files are often corrupt and windows does not typically have
                // user content, so put them towards the bottom
                LOW_PRI_PATHS.add(Pattern.compile("^\\$OrphanFiles", Pattern.CASE_INSENSITIVE));
                LOW_PRI_PATHS.add(Pattern.compile("^Windows", Pattern.CASE_INSENSITIVE));
                // all other files go into the medium category too
                MEDIUM_PRI_PATHS.add(Pattern.compile("^Program Files", Pattern.CASE_INSENSITIVE));
                // user content is top priority
                HIGH_PRI_PATHS.add(Pattern.compile("^Users", Pattern.CASE_INSENSITIVE));
                HIGH_PRI_PATHS.add(Pattern.compile("^Documents and Settings", Pattern.CASE_INSENSITIVE));
                HIGH_PRI_PATHS.add(Pattern.compile("^home", Pattern.CASE_INSENSITIVE));
                HIGH_PRI_PATHS.add(Pattern.compile("^ProgramData", Pattern.CASE_INSENSITIVE));
            }

            /**
             * Get the enabled priority for a given file.
             *
             * @param abstractFile
             *
             * @return
             */
            static AbstractFilePriority.Priority getPriority(final AbstractFile abstractFile) {
                if (!abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.FS)) {
                    //quickly filter out unstructured content
                    //non-fs virtual files and dirs, such as representing unalloc space
                    return AbstractFilePriority.Priority.LAST;
                }
                //determine the fs files priority by name
                final String path = abstractFile.getName();
                if (path == null) {
                    return AbstractFilePriority.Priority.MEDIUM;
                }
                for (Pattern p : HIGH_PRI_PATHS) {
                    Matcher m = p.matcher(path);
                    if (m.find()) {
                        return AbstractFilePriority.Priority.HIGH;
                    }
                }
                for (Pattern p : MEDIUM_PRI_PATHS) {
                    Matcher m = p.matcher(path);
                    if (m.find()) {
                        return AbstractFilePriority.Priority.MEDIUM;
                    }
                }
                for (Pattern p : LOW_PRI_PATHS) {
                    Matcher m = p.matcher(path);
                    if (m.find()) {
                        return AbstractFilePriority.Priority.LOW;
                    }
                }
                for (Pattern p : LAST_PRI_PATHS) {
                    Matcher m = p.matcher(path);
                    if (m.find()) {
                        return AbstractFilePriority.Priority.LAST;
                    }
                }
                //default is medium
                return AbstractFilePriority.Priority.MEDIUM;
            }
        }
    }

    /**
     * A blocking ingest task queue for the ingest manager's ingest threads that
     * keeps tracks of the tasks that are queued and in progress.
     */
    @ThreadSafe
    private class IngestTaskTrackingQueue implements BlockingIngestTaskQueue {

        private final BlockingDeque<IngestTask> taskQueue = new LinkedBlockingDeque<>();
        @GuardedBy("this")
        private final List<IngestTask> queuedTasks = new LinkedList<>();
        @GuardedBy("this")
        private final List<IngestTask> tasksInProgress = new LinkedList<>();

        /**
         * Adds an ingest task to the front of the queue, blocking if the queue
         * is full.
         *
         * @param task The ingest task.
         *
         * @throws InterruptedException If the thread adding the task is
         *                              interrupted while blocked on a queue
         *                              full condition.
         */
        void putFirst(IngestTask task) throws InterruptedException {
            synchronized (this) {
                this.queuedTasks.add(task);
            }
            try {
                this.taskQueue.putFirst(task);
            } catch (InterruptedException ex) {
                synchronized (this) {
                    this.queuedTasks.remove(task);
                }
                throw ex;
            }
        }

        /**
         * Adds an ingest task to the back of the queue, blocking if the queue
         * is full.
         *
         * @param task The ingest task.
         *
         * @throws InterruptedException If the thread adding the task is
         *                              interrupted while blocked on a queue
         *                              full condition.
         */
        void putLast(IngestTask task) throws InterruptedException {
            synchronized (this) {
                this.queuedTasks.add(task);
            }
            try {
                this.taskQueue.putLast(task);
            } catch (InterruptedException ex) {
                synchronized (this) {
                    this.queuedTasks.remove(task);
                }
                throw ex;
            }
        }

        /**
         * Gets the next ingest task in the queue, blocking if the queue is
         * empty.
         *
         * @return The next ingest task in the queue.
         *
         * @throws InterruptedException If the thread getting the task is
         *                              interrupted while blocked on a queue
         *                              empty condition.
         */
        @Override
        public IngestTask getNextTask() throws InterruptedException {
            IngestTask task = taskQueue.takeFirst();
            synchronized (this) {
                this.queuedTasks.remove(task);
                this.tasksInProgress.add(task);
            }
            return task;
        }

        /**
         * Checks whether the queue is empty.
         *
         * @return True or false.
         */
        boolean isEmpty() {
            synchronized (this) {
                return this.queuedTasks.isEmpty();
            }
        }

        /**
         * Handles the completion of an ingest task by removing it from the
         * tasks in progress list.
         *
         * @param task The completed task.
         */
        void taskCompleted(IngestTask task) {
            synchronized (this) {
                this.tasksInProgress.remove(task);
            }
        }

        /**
         * Checks whether there are any ingest tasks are queued and/or running
         * for a given ingest job.
         *
         * @param pipelineId The ID of the ingest pipeline for the job.
         *
         * @return
         */
        boolean hasTasksForJob(long pipelineId) {
            synchronized (this) {
                return IngestTasksScheduler.hasTasksForJob(queuedTasks, pipelineId) || IngestTasksScheduler.hasTasksForJob(tasksInProgress, pipelineId);
            }
        }

        /**
         * Gets a count of the queued ingest tasks for a given ingest job.
         *
         * @param pipelineId The ID of the ingest pipeline for the job.
         *
         * @return
         */
        int countQueuedTasksForJob(long pipelineId) {
            synchronized (this) {
                return IngestTasksScheduler.countTasksForJob(queuedTasks, pipelineId);
            }
        }

        /**
         * Gets a count of the running ingest tasks for a given ingest job.
         *
         * @param pipelineId The ID of the ingest pipeline for the job.
         *
         * @return
         */
        int countRunningTasksForJob(long pipelineId) {
            synchronized (this) {
                return IngestTasksScheduler.countTasksForJob(tasksInProgress, pipelineId);
            }
        }

    }

    /**
     * A snapshot of ingest tasks data for an ingest job.
     */
    static final class IngestJobTasksSnapshot implements Serializable {

        private static final long serialVersionUID = 1L;
        private final long jobId;
        private final long dsQueueSize;
        private final long rootQueueSize;
        private final long dirQueueSize;
        private final long fileQueueSize;
        private final long runningListSize;
        private final long streamingQueueSize;
        private final long artifactsQueueSize;

        /**
         * RJCTODO
         *
         * Constructs a snapshot of ingest tasks data for an ingest job.
         *
         * @param jobId              The identifier associated with the job.
         * @param dsQueueSize
         * @param rootQueueSize
         * @param dirQueueSize
         * @param fileQueueSize
         * @param runningListSize
         * @param streamingQueueSize
         * @param artifactsQueueSize
         */
        IngestJobTasksSnapshot(long jobId, long dsQueueSize, long rootQueueSize, long dirQueueSize, long fileQueueSize,
                long runningListSize, long streamingQueueSize, long artifactsQueueSize) {
            this.jobId = jobId;
            this.dsQueueSize = dsQueueSize;
            this.rootQueueSize = rootQueueSize;
            this.dirQueueSize = dirQueueSize;
            this.fileQueueSize = fileQueueSize;
            this.runningListSize = runningListSize;
            this.streamingQueueSize = streamingQueueSize;
            this.artifactsQueueSize = artifactsQueueSize;
        }

        /**
         * Gets the identifier associated with the ingest job for which this
         * snapshot was created.
         *
         * @return The ingest job identifier.
         */
        long getJobId() {
            return jobId;
        }

        /**
         * Gets the number of file ingest tasks associated with the job that are
         * in the root directories queue.
         *
         * @return The tasks count.
         */
        long getRootQueueSize() {
            return rootQueueSize;
        }

        /**
         * Gets the number of file ingest tasks associated with the job that are
         * in the root directories queue.
         *
         * @return The tasks count.
         */
        long getDirectoryTasksQueueSize() {
            return dirQueueSize;
        }

        long getFileQueueSize() {
            return fileQueueSize;
        }

        long getStreamingQueueSize() {
            return streamingQueueSize;
        }

        long getDsQueueSize() {
            return dsQueueSize;
        }

        long getRunningListSize() {
            return runningListSize;
        }
        
        long getArtifactsQueueSize() {
            return artifactsQueueSize;
        }

    }

}
