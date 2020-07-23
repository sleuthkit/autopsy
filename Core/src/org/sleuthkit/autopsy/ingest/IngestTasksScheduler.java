/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Creates ingest tasks for data source ingest jobs, queueing the tasks in
 * priority order for execution by the ingest manager's ingest threads.
 */
@ThreadSafe
final class IngestTasksScheduler {

    private static final int FAT_NTFS_FLAGS = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();
    private static final Logger logger = Logger.getLogger(IngestTasksScheduler.class.getName());
    @GuardedBy("IngestTasksScheduler.this")
    private static IngestTasksScheduler instance;
    private final IngestTaskTrackingQueue dataSourceIngestThreadQueue;
    @GuardedBy("this")
    private final TreeSet<FileIngestTask> rootFileTaskQueue;
    @GuardedBy("this")
    private final Deque<FileIngestTask> pendingFileTaskQueue;
    @GuardedBy("this")
    private final Queue<FileIngestTask> streamedTasksQueue;
    private final IngestTaskTrackingQueue fileIngestThreadsQueue;

    /**
     * Gets the ingest tasks scheduler singleton that creates ingest tasks for
     * data source ingest jobs, queueing the tasks in priority order for
     * execution by the ingest manager's ingest threads.
     */
    synchronized static IngestTasksScheduler getInstance() {
        if (IngestTasksScheduler.instance == null) {
            IngestTasksScheduler.instance = new IngestTasksScheduler();
        }
        return IngestTasksScheduler.instance;
    }

    /**
     * Constructs an ingest tasks scheduler that creates ingest tasks for data
     * source ingest jobs, queueing the tasks in priority order for execution by
     * the ingest manager's ingest threads.
     */
    private IngestTasksScheduler() {
        this.dataSourceIngestThreadQueue = new IngestTaskTrackingQueue();
        this.rootFileTaskQueue = new TreeSet<>(new RootDirectoryTaskComparator());
        this.pendingFileTaskQueue = new LinkedList<>();
        this.fileIngestThreadsQueue = new IngestTaskTrackingQueue();
	this.streamedTasksQueue = new LinkedList<>();
    }

    /**
     * Gets the data source level ingest tasks queue. This queue is a blocking
     * queue used by the ingest manager's data source level ingest thread.
     *
     * @return The queue.
     */
    BlockingIngestTaskQueue getDataSourceIngestTaskQueue() {
        return this.dataSourceIngestThreadQueue;
    }

    /**
     * Gets the file level ingest tasks queue. This queue is a blocking queue
     * used by the ingest manager's file level ingest threads.
     *
     * @return The queue.
     */
    BlockingIngestTaskQueue getFileIngestTaskQueue() {
        return this.fileIngestThreadsQueue;
    }

    /**
     * Schedules a data source level ingest task and zero to many file level 
     * ingest tasks for an ingest job pipeline.
     *
     * @param ingestJobPipeline The ingest job pipeline.
     */
    synchronized void scheduleIngestTasks(IngestJobPipeline ingestJobPipeline) {
        if (!ingestJobPipeline.isCancelled()) {
            /*
             * Scheduling of both the data source ingest task and the initial
             * file ingest tasks for an ingestJobPipeline must be an atomic operation.
             * Otherwise, the data source task might be completed before the
             * file tasks are scheduled, resulting in a potential false positive
             * when another thread checks whether or not all the tasks for the
             * ingestJobPipeline are completed.
             */
            this.scheduleDataSourceIngestTask(ingestJobPipeline);
            this.scheduleFileIngestTasks(ingestJobPipeline, Collections.emptyList());
        }
    }

    /**
     * Schedules a data source level ingest task for an ingest job pipeline.
     *
     * @param ingestJobPipeline The ingest job pipeline.
     */
    synchronized void scheduleDataSourceIngestTask(IngestJobPipeline ingestJobPipeline) {
        if (!ingestJobPipeline.isCancelled()) {
            DataSourceIngestTask task = new DataSourceIngestTask(ingestJobPipeline);
            try {
                this.dataSourceIngestThreadQueue.putLast(task);
            } catch (InterruptedException ex) {
                IngestTasksScheduler.logger.log(Level.INFO, String.format("Ingest tasks scheduler interrupted while blocked adding a task to the data source level ingest task queue (jobId={%d)", ingestJobPipeline.getId()), ex);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Schedules file tasks for either all the files or a given subset of the
     * files for an ingest job pipeline.
     *
     * @param ingestJobPipeline   The ingest job pipeline.
     * @param files A subset of the files for the data source; if empty, then
     *              file tasks for all files in the data source are scheduled.
     */
    synchronized void scheduleFileIngestTasks(IngestJobPipeline ingestJobPipeline, Collection<AbstractFile> files) {
        if (!ingestJobPipeline.isCancelled()) {
            Collection<AbstractFile> candidateFiles;
            if (files.isEmpty()) {
                candidateFiles = getTopLevelFiles(ingestJobPipeline.getDataSource());
            } else {
                candidateFiles = files;
            }
            for (AbstractFile file : candidateFiles) {
                FileIngestTask task = new FileIngestTask(ingestJobPipeline, file);
                if (IngestTasksScheduler.shouldEnqueueFileTask(task)) {
                    this.rootFileTaskQueue.add(task);
                }
            }
            refillIngestThreadQueue();
        }
    }
    
    /**
     * Schedules file tasks for the given list of file IDs.
     *
     * @param ingestJobPipeline   The ingest job pipeline.
     * @param files A subset of the files for the data source; if empty, then
     *              file tasks for all files in the data source are scheduled.
     */
    synchronized void scheduleStreamedFileIngestTasks(IngestJobPipeline ingestJobPipeline, List<Long> fileIds) {
        if (!ingestJobPipeline.isCancelled()) {
            for (long id : fileIds) {
		// Create the file ingest task. Note that we do not do the shouldEnqueueFileTask() 
		// check here in order to delay loading the AbstractFile object.
                FileIngestTask task = new FileIngestTask(ingestJobPipeline, id);
                this.streamedTasksQueue.add(task);
            }
            refillIngestThreadQueue();
        }
    }    

    /**
     * Schedules file level ingest tasks for a given set of files for an ingest
     * job pipeline by adding them directly to the front of the file tasks
     * queue for the ingest manager's file ingest threads.
     *
     * @param ingestJobPipeline   The ingestJobPipeline.
     * @param files A set of files for the data source.
     */
    synchronized void fastTrackFileIngestTasks(IngestJobPipeline ingestJobPipeline, Collection<AbstractFile> files) {
        if (!ingestJobPipeline.isCancelled()) {
            /*
             * Put the files directly into the queue for the file ingest
             * threads, if they pass the file filter for the job. The files are
             * added to the queue for the ingest threads BEFORE the other queued
             * tasks because the use case for this method is scheduling new
             * carved or derived files from a higher priority task that is
             * already in progress.
             */
            for (AbstractFile file : files) {
                FileIngestTask fileTask = new FileIngestTask(ingestJobPipeline, file);
                if (shouldEnqueueFileTask(fileTask)) {
                    try {
                        this.fileIngestThreadsQueue.putFirst(fileTask);
                    } catch (InterruptedException ex) {
                        IngestTasksScheduler.logger.log(Level.INFO, String.format("Ingest tasks scheduler interrupted while scheduling file level ingest tasks (jobId={%d)", ingestJobPipeline.getId()), ex);
                        Thread.currentThread().interrupt();
                        return;
                    }
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
        this.dataSourceIngestThreadQueue.taskCompleted(task);
    }

    /**
     * Allows an ingest thread to notify this ingest task scheduler that a file
     * level task has been completed.
     *
     * @param task The completed task.
     */
    synchronized void notifyTaskCompleted(FileIngestTask task) {
        this.fileIngestThreadsQueue.taskCompleted(task);
        refillIngestThreadQueue();
    }

    /**
     * Queries the task scheduler to determine whether or not all of the ingest
     * tasks for an ingest job pipeline have been completed.
     *
     * @param ingestJobPipeline The ingestJobPipeline.
     *
     * @return True or false.
     */
    synchronized boolean currentTasksAreCompleted(IngestJobPipeline ingestJobPipeline) {
        long jobId = ingestJobPipeline.getId();

        return !(this.dataSourceIngestThreadQueue.hasTasksForJob(jobId)
                || hasTasksForJob(this.rootFileTaskQueue, jobId)
                || hasTasksForJob(this.pendingFileTaskQueue, jobId)
                || hasTasksForJob(this.streamedTasksQueue, jobId)
                || this.fileIngestThreadsQueue.hasTasksForJob(jobId));
    }

    /**
     * Clears the "upstream" task scheduling queues for an ingest pipeline,
     * but does nothing about tasks that have already been moved into the
     * queue that is consumed by the file ingest threads.
     *
     * @param ingestJobPipeline The ingestJobPipeline.
     */
    synchronized void cancelPendingTasksForIngestJob(IngestJobPipeline ingestJobPipeline) {
        long jobId = ingestJobPipeline.getId();
        IngestTasksScheduler.removeTasksForJob(rootFileTaskQueue, jobId);
        IngestTasksScheduler.removeTasksForJob(pendingFileTaskQueue, jobId);
        IngestTasksScheduler.removeTasksForJob(streamedTasksQueue, jobId);
    }

    /**
     * Gets the top level files such as file system root directories, layout
     * files and virtual directories for a data source. Used to create file
     * tasks to put into the root directories queue.
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
                    logger.log(Level.WARNING, "Could not get children of root to enqueue: " + root.getId() + ": " + root.getName(), ex); //NON-NLS
                }
            }
        }
        return topLevelFiles;
    }
    
    /**
     * Schedules file ingest tasks for the ingest manager's file ingest threads.
     * Files from streaming ingest will be prioritized.
     */
    synchronized private void refillIngestThreadQueue() {
        try {
            takeFromStreamingTaskQueue();
            takeFromBatchTasksQueues();
        } catch (InterruptedException ex) {
            IngestTasksScheduler.logger.log(Level.INFO, "Ingest tasks scheduler interrupted while blocked adding a task to the file level ingest task queue", ex);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Move tasks from the streamedTasksQueue into the fileIngestThreadsQueue.
     * Will attempt to move as many tasks as there are ingest threads.
     */
    synchronized private void takeFromStreamingTaskQueue() throws InterruptedException {
        /*
         * Schedule files from the streamedTasksQueue
         */
        while (fileIngestThreadsQueue.isEmpty()) {
            /*
             * We will attempt to schedule as many tasks as there are ingest
             * queues.
             */
            int taskCount = 0;
            while (taskCount < IngestManager.getInstance().getNumberOfFileIngestThreads()) {
                final FileIngestTask streamingTask = streamedTasksQueue.poll();
                if (streamingTask == null) {
                    return; // No streaming tasks are queued right now
                }

                if (shouldEnqueueFileTask(streamingTask)) {
                    fileIngestThreadsQueue.putLast(streamingTask);
                    taskCount++;
                }
            }
        }
    }

    /**
     * Schedules file ingest tasks for the ingest manager's file ingest threads
     * by "shuffling" them through a sequence of three queues that allows for
     * the interleaving of tasks from different data source ingest jobs based on
     * priority, while limiting the number of queued tasks by only expanding
     * directories one at a time. The sequence of queues is:
     *
     * 1. The root file tasks priority queue, which contains file tasks for the
     * root objects of the data sources that are being analyzed. For example,
     * the root tasks for a disk image data source are typically the tasks for
     * the contents of the root directories of the file systems. This queue is a
     * priority queue that attempts to ensure that user content is analyzed
     * before general file system content. It feeds into the pending tasks
     * queue.
     *
     * 2. The pending file tasks queue, which contains root file tasks shuffled
     * out of the root tasks queue, plus tasks for files with children
     * discovered in the descent from the root tasks to the final leaf tasks in
     * the content trees that are being analyzed for the data source ingest
     * jobs. This queue is a FIFO queue that attempts to throttle the total
     * number of file tasks by deferring queueing tasks for the children of
     * files until the queue for the file ingest threads is emptied. It feeds
     * into the file tasks queue for the ingest manager's file ingest threads.
     *
     * 3. The file tasks queue for the ingest manager's file ingest threads.
     * This queue is a blocking deque that is FIFO during a shuffle to maintain
     * task prioritization, but LIFO when adding derived files to it directly
     * during ingest. The reason for the LIFO additions is to give priority to
     * files derived from prioritized files.
     */
    synchronized private void takeFromBatchTasksQueues() throws InterruptedException {
	
        while (this.fileIngestThreadsQueue.isEmpty()) {	    
            /*
             * If the pending file task queue is empty, move the highest
             * priority root file task, if there is one, into it.
             */
            if (this.pendingFileTaskQueue.isEmpty()) {
                final FileIngestTask rootTask = this.rootFileTaskQueue.pollFirst();
                if (rootTask != null) {
                    this.pendingFileTaskQueue.addLast(rootTask);
                }
            }

            /*
             * Try to move the next task from the pending task queue into the
             * queue for the file ingest threads, if it passes the filter for
             * the job.
             */
            final FileIngestTask pendingTask = this.pendingFileTaskQueue.pollFirst();
            if (pendingTask == null) {
                return;
            }
            if (shouldEnqueueFileTask(pendingTask)) {
		/*
		 * The task is added to the queue for the ingest threads
		 * AFTER the higher priority tasks that preceded it.
		 */
		this.fileIngestThreadsQueue.putLast(pendingTask);
            }

            /*
             * If the task that was just queued for the file ingest threads has
             * children, try to queue tasks for the children. Each child task
             * will go into either the directory queue if it has children of its
             * own, or into the queue for the file ingest threads, if it passes
             * the filter for the job.
             */
            AbstractFile file = null;
            try {
                file = pendingTask.getFile();
                for (Content child : file.getChildren()) {
                    if (child instanceof AbstractFile) {
                        AbstractFile childFile = (AbstractFile) child;
                        FileIngestTask childTask = new FileIngestTask(pendingTask.getIngestJobPipeline(), childFile);
                        if (childFile.hasChildren()) {
                            this.pendingFileTaskQueue.add(childTask);
                        } else if (shouldEnqueueFileTask(childTask)) {
                            this.fileIngestThreadsQueue.putLast(childTask);
                        }
                    }
                }
            } catch (TskCoreException ex) {
                if (file != null) {
                    logger.log(Level.SEVERE, String.format("Error getting the children of %s (objId=%d)", file.getName(), file.getId()), ex);  //NON-NLS
                } else {
                    // In practice, the task would have already returned false from the call to shouldEnqueueFileTask()
                    logger.log(Level.SEVERE, "Error loading file with object ID {0}", pendingTask.getFileId());
                }
            }
        }
    }

    /**
     * Examines the file associated with a file ingest task to determine whether
     * or not the file should be processed and therefore whether or not the task
     * should be enqueued.
     *
     * @param task The task to be scrutinized.
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
     * Check whether or not a file should be carved for a data source ingest  
     * ingest job.
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
     * Checks whether or not a file is accepted (passes) the file filter for a data  
     * source ingest job.
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
     * given data source ingest job.
     *
     * @param tasks The tasks.
     * @param jobId The data source ingest job id.
     *
     * @return True if there are no tasks for the job, false otherwise.
     */
    synchronized private static boolean hasTasksForJob(Collection<? extends IngestTask> tasks, long jobId) {
        for (IngestTask task : tasks) {
            if (task.getIngestJobPipeline().getId() == jobId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes all of the ingest tasks associated with a data source ingest job
     * from a tasks collection.
     *
     * @param tasks The collection from which to remove the tasks.
     * @param jobId The data source ingest job id.
     */
    private static void removeTasksForJob(Collection<? extends IngestTask> tasks, long jobId) {
        Iterator<? extends IngestTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            IngestTask task = iterator.next();
            if (task.getIngestJobPipeline().getId() == jobId) {
                iterator.remove();
            }
        }
    }

    /**
     * Counts the number of ingest tasks in a tasks collection for a given job.
     *
     * @param queue The queue for which to count tasks.
     * @param jobId The id of the job for which the tasks are to be counted.
     *
     * @return The count.
     */
    private static int countTasksForJob(Collection<? extends IngestTask> queue, long jobId) {
        int count = 0;
        for (IngestTask task : queue) {
            if (task.getIngestJobPipeline().getId() == jobId) {
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
        return new IngestJobTasksSnapshot(jobId, this.dataSourceIngestThreadQueue.countQueuedTasksForJob(jobId),
                countTasksForJob(this.rootFileTaskQueue, jobId),
                countTasksForJob(this.pendingFileTaskQueue, jobId),
                this.fileIngestThreadsQueue.countQueuedTasksForJob(jobId),
                this.dataSourceIngestThreadQueue.countRunningTasksForJob(jobId) + this.fileIngestThreadsQueue.countRunningTasksForJob(jobId),
                countTasksForJob(this.streamedTasksQueue, jobId));
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
            } else  if (file2 == null) {
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
         * running tasks list.
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
         * for a given data source ingest job.
         *
         * @param jobId The id of the data source ingest job.
         *
         * @return
         */
        boolean hasTasksForJob(long jobId) {
            synchronized (this) {
                return IngestTasksScheduler.hasTasksForJob(this.queuedTasks, jobId) || IngestTasksScheduler.hasTasksForJob(this.tasksInProgress, jobId);
            }
        }

        /**
         * Gets a count of the queued ingest tasks for a given data source
         * ingest job.
         *
         * @param jobId
         *
         * @return
         */
        int countQueuedTasksForJob(long jobId) {
            synchronized (this) {
                return IngestTasksScheduler.countTasksForJob(this.queuedTasks, jobId);
            }
        }

        /**
         * Gets a count of the running ingest tasks for a given data source
         * ingest job.
         *
         * @param jobId
         *
         * @return
         */
        int countRunningTasksForJob(long jobId) {
            synchronized (this) {
                return IngestTasksScheduler.countTasksForJob(this.tasksInProgress, jobId);
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

        /**
         * Constructs a snapshot of ingest tasks data for an ingest job.
         *
         * @param jobId The identifier associated with the job.
         */
        IngestJobTasksSnapshot(long jobId, long dsQueueSize, long rootQueueSize, long dirQueueSize, long fileQueueSize, 
                long runningListSize, long streamingQueueSize) {
            this.jobId = jobId;
            this.dsQueueSize = dsQueueSize;
            this.rootQueueSize = rootQueueSize;
            this.dirQueueSize = dirQueueSize;
            this.fileQueueSize = fileQueueSize;
            this.runningListSize = runningListSize;
            this.streamingQueueSize = streamingQueueSize;
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

    }

}
