/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2014 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Creates ingest tasks for ingest jobs, queuing the tasks in priority order for
 * execution by the ingest manager's ingest threads.
 */
final class IngestTasksScheduler {

    private static final Logger logger = Logger.getLogger(IngestTasksScheduler.class.getName());
    private static final int FAT_NTFS_FLAGS = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();
    private static IngestTasksScheduler instance;

    // Scheduling of data source ingest tasks is accomplished by putting them
    // in a FIFO queue to be consumed by the ingest threads. The pending data 
    // tasks queue is therefore wrapped in a "dispenser" that implements the 
    // IngestTaskQueue interface and is exposed via a getter method.
    private final LinkedBlockingQueue<DataSourceIngestTask> pendingDataSourceTasks;
    private final DataSourceIngestTaskQueue dataSourceTasksDispenser;

    // Scheduling of file ingest tasks is accomplished by "shuffling" them 
    // through a sequence of internal queues that allows for the interleaving of 
    // tasks from different ingest jobs based on priority. These scheduling
    // queues are: 
    //    1. root directory tasks (priority queue)
    //    2. directory tasks (FIFO queue)
    //    3. pending file tasks (LIFO queue)
    // Tasks in the pending file tasks queue are ready to be consumed by the 
    // ingest threads. The pending file tasks queue is therefore wrapped in a 
    // "dispenser" that implements the IngestTaskQueue interface and is exposed 
    // via a getter method.
    private final TreeSet<FileIngestTask> rootDirectoryTasks;
    private final List<FileIngestTask> directoryTasks;
    private final BlockingDeque<FileIngestTask> pendingFileTasks;
    private final FileIngestTaskQueue fileTasksDispenser;

    // The ingest scheduler is responsible for notifying an ingest jobs whenever
    // all of the ingest tasks currently associated with the job are complete. 
    // To make this possible, the ingest tasks scheduler needs to keep track not 
    // only of the tasks in its queues, but also of the tasks that have been 
    // handed out for processing by code running on the ingest manager's ingest 
    // threads. Therefore all ingest tasks are added to this list and are not 
    // removed when an ingest thread takes an ingest task. Instead, the ingest 
    // thread calls back into the scheduler when the task is completed, at 
    // which time the task will be removed from this list.
    private final List<IngestTask> tasksInProgressAndPending;

    /**
     * Gets the ingest tasks scheduler singleton.
     */
    synchronized static IngestTasksScheduler getInstance() {
        if (IngestTasksScheduler.instance == null) {
            IngestTasksScheduler.instance = new IngestTasksScheduler();
        }
        return IngestTasksScheduler.instance;
    }

    /**
     * Constructs an ingest tasks scheduler.
     */
    private IngestTasksScheduler() {
        this.pendingDataSourceTasks = new LinkedBlockingQueue<>();
        this.dataSourceTasksDispenser = new DataSourceIngestTaskQueue();
        this.rootDirectoryTasks = new TreeSet<>(new RootDirectoryTaskComparator());
        this.directoryTasks = new ArrayList<>();
        this.pendingFileTasks = new LinkedBlockingDeque<>();
        this.fileTasksDispenser = new FileIngestTaskQueue();
        this.tasksInProgressAndPending = new ArrayList<>();
    }

    /**
     * Gets this ingest task scheduler's implementation of the IngestTaskQueue
     * interface for data source ingest tasks.
     *
     * @return The data source ingest tasks queue.
     */
    IngestTaskQueue getDataSourceIngestTaskQueue() {
        return this.dataSourceTasksDispenser;
    }

    /**
     * Gets this ingest task scheduler's implementation of the IngestTaskQueue
     * interface for file ingest tasks.
     *
     * @return The file ingest tasks queue.
     */
    IngestTaskQueue getFileIngestTaskQueue() {
        return this.fileTasksDispenser;
    }

    /**
     * Schedules a data source ingest task and file ingest tasks for an ingest
     * job.
     *
     * @param job The job for which the tasks are to be scheduled.
     * @throws InterruptedException if the calling thread is blocked due to a
     * full tasks queue and is interrupted.
     */
    synchronized void scheduleIngestTasks(IngestJob job) throws InterruptedException {
        // The initial ingest scheduling for a job an an atomic operation. 
        // Otherwise, the data source task might be completed before the file 
        // tasks are created, resulting in a potential false positive when this
        // task scheduler checks whether or not all the tasks for the job are
        // completed.
        if (job.hasDataSourceIngestPipeline()) {
            scheduleDataSourceIngestTask(job);
        }
        if (job.hasFileIngestPipeline()) {
            scheduleFileIngestTasks(job);
        }
    }

    /**
     * Schedules a data source ingest task for an ingest job.
     *
     * @param job The job for which the tasks are to be scheduled.
     * @throws InterruptedException if the calling thread is blocked due to a
     * full tasks queue and is interrupted.
     */
    synchronized void scheduleDataSourceIngestTask(IngestJob job) throws InterruptedException {
        // Create a data source ingest task for the data source associated with
        // the ingest job and add the task to the pending data source tasks
        // queue. Data source tasks are scheduled on a first come, first served
        // basis.
        DataSourceIngestTask task = new DataSourceIngestTask(job);
        this.tasksInProgressAndPending.add(task);
        try {
            // This call should not block because the queue is (theoretically) 
            // unbounded.
            this.pendingDataSourceTasks.put(task);
        } catch (InterruptedException ex) {
            this.tasksInProgressAndPending.remove(task);
            IngestTasksScheduler.logger.log(Level.SEVERE, "Interruption of unexpected block on pending data source tasks queue", ex); //NON-NLS
            throw ex;
        }
    }

    /**
     * Schedules file ingest tasks for an ingest job.
     *
     * @param job The job for which the tasks are to be scheduled.
     * @throws InterruptedException if the calling thread is blocked due to a
     * full tasks queue and is interrupted.
     */
    synchronized void scheduleFileIngestTasks(IngestJob job) throws InterruptedException {
        // Get the top level files for the data source associated with this job
        // and add them to the root directories priority queue. The file tasks
        // may be interleaved with file tasks from other jobs, based on priority. 
        List<AbstractFile> topLevelFiles = getTopLevelFiles(job.getDataSource());
        for (AbstractFile firstLevelFile : topLevelFiles) {
            FileIngestTask task = new FileIngestTask(job, firstLevelFile);
            if (IngestTasksScheduler.shouldEnqueueFileTask(task)) {
                this.tasksInProgressAndPending.add(task);
                this.rootDirectoryTasks.add(task);
            }
        }
        shuffleFileTaskQueues();
    }

    /**
     * Schedules a file ingest task for an ingest job.
     *
     * @param job The job for which the tasks are to be scheduled.
     * @param file The file associated with the task.
     * @throws InterruptedException if the calling thread is blocked due to a
     * full tasks queue and is interrupted.
     */
    void scheduleFileIngestTask(IngestJob job, AbstractFile file) throws InterruptedException, IllegalStateException {
        FileIngestTask task = new FileIngestTask(job, file);
        if (IngestTasksScheduler.shouldEnqueueFileTask(task)) {
            // This synchronized method sends the file task directly to the 
            // pending file tasks queue. This is done to prioritize derived 
            // and carved files generated by a file ingest task in progress.                 
            addToPendingFileTasksQueue(task);
        }
    }

    /**
     * Allows an ingest thread to notify this ingest task scheduler that a task
     * has been completed.
     *
     * @param task The completed task.
     */
    synchronized void notifyTaskCompleted(IngestTask task) throws InterruptedException {
        tasksInProgressAndPending.remove(task);
        IngestJob job = task.getIngestJob();
        if (this.tasksForJobAreCompleted(job)) {
            job.notifyTasksCompleted();
        }
    }

    /**
     * Clears the task scheduling queues for an ingest job, but does nothing
     * about tasks that have already been taken by ingest threads. Those tasks
     * will be flushed out when the ingest threads call back with their task
     * completed notifications.
     *
     * @param job The job for which the tasks are to to canceled.
     */
    synchronized void cancelPendingTasksForIngestJob(IngestJob job) {
        // The scheduling queues are cleared of tasks for the job, and the tasks
        // that are removed from the scheduling queues are also removed from the
        // tasks in progress list. However, a tasks in progress check for the 
        // job may still return true since the tasks that have been taken by the 
        // ingest threads are still in the tasks in progress list.
        long jobId = job.getId();
        this.removeTasksForJob(this.rootDirectoryTasks, jobId);
        this.removeTasksForJob(this.directoryTasks, jobId);
        this.removeTasksForJob(this.pendingFileTasks, jobId);
        this.removeTasksForJob(this.pendingDataSourceTasks, jobId);
        if (this.tasksForJobAreCompleted(job)) {
            job.notifyTasksCompleted();
        }
    }

    /**
     * A helper that gets the top level files such as file system root
     * directories, layout files and virtual directories for a data source. Used
     * to create file tasks to put into the root directories queue.
     *
     * @param dataSource The data source.
     * @return A list of top level files.
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
     * A helper that "shuffles" the file task queues to ensure that there is at
     * least one task in the pending file ingest tasks queue, as long as there
     * are still file ingest tasks to be performed.
     *
     * @throws InterruptedException if the calling thread is blocked due to a
     * full tasks queue and is interrupted.
     */
    synchronized private void shuffleFileTaskQueues() throws InterruptedException, IllegalStateException {
        // This is synchronized because it is called both by synchronized 
        // methods of this ingest scheduler and an unsynchronized method of its
        // file tasks "dispenser".
        while (true) {
            // Loop until either the pending file tasks queue is NOT empty
            // or the upstream queues that feed into it ARE empty.
            if (!this.pendingFileTasks.isEmpty()) {
                // There are file tasks ready to be consumed, exit.
                return;
            }
            if (this.directoryTasks.isEmpty()) {
                if (this.rootDirectoryTasks.isEmpty()) {
                    // There are no root directory tasks to move into the
                    // directory queue, exit.
                    return;
                } else {
                    // Move the next root directory task into the 
                    // directories queue. Note that the task was already 
                    // added to the tasks in progress list when the task was
                    // created in scheduleFileIngestTasks().
                    this.directoryTasks.add(this.rootDirectoryTasks.pollFirst());
                }
            }

            // Try to add the most recently added directory from the 
            // directory tasks queue to the pending file tasks queue. Note
            // the removal of the task from the tasks in progress list. If
            // the task is enqueued, it will be put back in the list by
            // the addToPendingFileTasksQueue() helper.
            boolean tasksEnqueuedForDirectory = false;
            FileIngestTask directoryTask = this.directoryTasks.remove(this.directoryTasks.size() - 1);
            this.tasksInProgressAndPending.remove(directoryTask);
            if (shouldEnqueueFileTask(directoryTask)) {
                addToPendingFileTasksQueue(directoryTask);
                tasksEnqueuedForDirectory = true;
            }

            // If the directory contains subdirectories or files, try to 
            // enqueue tasks for them as well. 
            final AbstractFile directory = directoryTask.getFile();
            try {
                for (Content child : directory.getChildren()) {
                    if (child instanceof AbstractFile) {
                        AbstractFile file = (AbstractFile) child;
                        FileIngestTask childTask = new FileIngestTask(directoryTask.getIngestJob(), file);
                        if (file.hasChildren()) {
                            // Found a subdirectory, put the task in the 
                            // pending directory tasks queue. Note the 
                            // addition of the task to the tasks in progress
                            // list. This is necessary because this is the
                            // first appearance of this task in the queues.
                            this.tasksInProgressAndPending.add(childTask);
                            this.directoryTasks.add(childTask);
                            tasksEnqueuedForDirectory = true;
                        } else if (shouldEnqueueFileTask(childTask)) {
                            // Found a file, put the task directly into the
                            // pending file tasks queue. The new task will 
                            // be put into the tasks in progress list by the
                            // addToPendingFileTasksQueue() helper.
                            addToPendingFileTasksQueue(childTask);
                            tasksEnqueuedForDirectory = true;
                        }
                    }
                }
            } catch (TskCoreException ex) {
                String errorMessage = String.format("An error occurred getting the children of %s", directory.getName()); //NON-NLS
                logger.log(Level.SEVERE, errorMessage, ex);
            }

            // In the case where the directory task is not pushed into the
            // the pending file tasks queue and has no children, check to 
            // see if the job is completed - the directory task might have 
            // been the last task for the job.                
            if (!tasksEnqueuedForDirectory) {
                IngestJob job = directoryTask.getIngestJob();
                if (this.tasksForJobAreCompleted(job)) {
                    job.notifyTasksCompleted();
                }
            }
        }
    }

    /**
     * A helper method that examines the file associated with a file ingest task
     * to determine whether or not the file should be processed and therefore
     * the task should be enqueued.
     *
     * @param task The task to be scrutinized.
     * @return True or false.
     */
    private static boolean shouldEnqueueFileTask(final FileIngestTask task) {
        final AbstractFile file = task.getFile();

        // Skip the task if the file is an unallocated space file and the
        // process unallocated space flag is not set for this job.
        if (!task.getIngestJob().shouldProcessUnallocatedSpace()
                && file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return false;
        }

        // Skip the task if the file is actually the pseudo-file for the parent
        // or current directory.
        String fileName = file.getName();
        if (fileName.equals(".") || fileName.equals("..")) {
            return false;
        }

        // Skip the task if the file is one of a select group of special, large
        // NTFS or FAT file system files.
        // the file is in the root directory, has a file name
        // starting with $, containing : (not default attributes)
        //with meta address < 32, i.e. some special large NTFS and FAT files
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
                isInRootDir = parent.isRoot();
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
     * A helper method to safely add a file ingest task to the blocking pending
     * tasks queue.
     *
     * @param task
     * @throws IllegalStateException
     */
    synchronized private void addToPendingFileTasksQueue(FileIngestTask task) throws IllegalStateException {
        tasksInProgressAndPending.add(task);
        try {
            // The file is added to the front of the pending files queue because 
            // at least one image has been processed that had a folder full of 
            // archive files. The queue grew to have thousands of entries, so 
            // this (might) help with pushing those files through ingest. 
            this.pendingFileTasks.addFirst(task);
        } catch (IllegalStateException ex) {
            tasksInProgressAndPending.remove(task);
            Logger.getLogger(IngestTasksScheduler.class.getName()).log(Level.SEVERE, "Pending file tasks queue is full", ex); //NON-NLS
            throw ex;
        }
    }

    /**
     * Determines whether or not all current ingest tasks for an ingest job are 
     * completed.
     *
     * @param job The job for which the query is to be performed.
     * @return True or false.
     */
    private boolean tasksForJobAreCompleted(IngestJob job) {
        for (IngestTask task : tasksInProgressAndPending) {
            if (task.getIngestJob().getId() == job.getId()) {
                return false;
            }
        }
        return true;
    }

    /**
     * A helper that removes all of the ingest tasks associated with an ingest
     * job from a tasks queue. The task is removed from the the tasks in
     * progress list as well.
     *
     * @param taskQueue The queue from which to remove the tasks.
     * @param jobId The id of the job for which the tasks are to be removed.
     */
    private void removeTasksForJob(Collection<? extends IngestTask> taskQueue, long jobId) {
        Iterator<? extends IngestTask> iterator = taskQueue.iterator();
        while (iterator.hasNext()) {
            IngestTask task = iterator.next();
            if (task.getIngestJob().getId() == jobId) {
                this.tasksInProgressAndPending.remove(task);
                iterator.remove();
            }
        }
    }

    /**
     * A helper that counts the number of ingest tasks in a task queue for a
     * given job.
     *
     * @param queue The queue for which to count tasks.
     * @param jobId The id of the job for which the tasks are to be counted.
     * @return The count.
     */
    private static int countTasksForJob(Collection<? extends IngestTask> queue, long jobId) {
        Iterator<? extends IngestTask> iterator = queue.iterator();
        int count = 0;
        while (iterator.hasNext()) {
            IngestTask task = (IngestTask) iterator.next();
            if (task.getIngestJob().getId() == jobId) {
                count++;
            }
        }
        return count;
    }

    /**
     * RJCTODO
     * 
     * @param jobId
     * @return 
     */
    synchronized IngestJobTasksSnapshot getTasksSnapshotForJob(long jobId) {
        return new IngestJobTasksSnapshot(jobId);
    }

    /**
     * Prioritizes tasks for the root directories file ingest tasks queue (file
     * system root directories, layout files and virtual directories).
     */
    private static class RootDirectoryTaskComparator implements Comparator<FileIngestTask> {

        @Override
        public int compare(FileIngestTask q1, FileIngestTask q2) {
            AbstractFilePriority.Priority p1 = AbstractFilePriority.getPriority(q1.getFile());
            AbstractFilePriority.Priority p2 = AbstractFilePriority.getPriority(q2.getFile());
            if (p1 == p2) {
                return (int) (q2.getFile().getId() - q1.getFile().getId());
            } else {
                return p2.ordinal() - p1.ordinal();
            }
        }

        private static class AbstractFilePriority {

            enum Priority {

                LAST, LOW, MEDIUM, HIGH
            }

            static final List<Pattern> LAST_PRI_PATHS = new ArrayList<>();

            static final List<Pattern> LOW_PRI_PATHS = new ArrayList<>();

            static final List<Pattern> MEDIUM_PRI_PATHS = new ArrayList<>();

            static final List<Pattern> HIGH_PRI_PATHS = new ArrayList<>();
            /* prioritize root directory folders based on the assumption that we
             * are
             * looking for user content. Other types of investigations may want
             * different
             * priorities. */

            static /* prioritize root directory
             * folders based on the assumption that we are
             * looking for user content. Other types of investigations may want
             * different
             * priorities. */ {
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
     * Wraps access to pending data source ingest tasks in the interface
     * required by the ingest threads.
     */
    private final class DataSourceIngestTaskQueue implements IngestTaskQueue {

        /**
         * @inheritDoc
         */
        @Override
        public IngestTask getNextTask() throws InterruptedException {
            return IngestTasksScheduler.this.pendingDataSourceTasks.take();
        }
    }

    /**
     * Wraps access to pending file ingest tasks in the interface required by
     * the ingest threads.
     */
    private final class FileIngestTaskQueue implements IngestTaskQueue {

        /**
         * @inheritDoc
         */
        @Override
        public IngestTask getNextTask() throws InterruptedException {
            FileIngestTask task = IngestTasksScheduler.this.pendingFileTasks.takeFirst();
            shuffleFileTaskQueues();
            return task;
        }

    }

    /**
     * A snapshot of ingest tasks data for an ingest job.  
     */
    class IngestJobTasksSnapshot {
        private final long jobId;
        private final long rootQueueSize;
        private final long dirQueueSize;
        private final long fileQueueSize;
        private final long dsQueueSize;
        private final long runningListSize;

        /**
         * RJCTODO
         * @param jobId 
         */
        IngestJobTasksSnapshot(long jobId) {
            this.jobId = jobId;
            this.rootQueueSize = countTasksForJob(IngestTasksScheduler.this.rootDirectoryTasks, jobId);
            this.dirQueueSize = countTasksForJob(IngestTasksScheduler.this.directoryTasks, jobId);
            this.fileQueueSize = countTasksForJob(IngestTasksScheduler.this.pendingFileTasks, jobId);
            this.dsQueueSize = countTasksForJob(IngestTasksScheduler.this.pendingDataSourceTasks, jobId);
            this.runningListSize = countTasksForJob(IngestTasksScheduler.this.tasksInProgressAndPending, jobId) - fileQueueSize - dsQueueSize;            
        }
        
        /**
         * RJCTODO
         * @return 
         */
        long getJobId() {
            return jobId;
        }

        /**
         * RJCTODO
         * @return 
         */
        long getRootQueueSize() {
            return rootQueueSize;
        }

        /**
         * RJCTODO
         * @return 
         */
        long getDirQueueSize() {
            return dirQueueSize;
        }

        /**
         * RJCTODO
         * @return 
         */
        long getFileQueueSize() {
            return fileQueueSize;
        }

        /**
         * RJCTODO
         * @return 
         */
        long getDsQueueSize() {
            return dsQueueSize;
        }

        /**
         * RJCTODO
         * @return 
         */
        long getRunningListSize() {
            return runningListSize;
        }        
    }
    
}
