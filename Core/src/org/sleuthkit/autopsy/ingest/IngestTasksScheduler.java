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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
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
 * Creates ingest tasks for data source ingest jobs, queueing the tasks in
 * priority order for execution by the ingest manager's ingest threads.
 */
final class IngestTasksScheduler {

    private static final int FAT_NTFS_FLAGS = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();
    private static final Logger logger = Logger.getLogger(IngestTasksScheduler.class.getName());
    private static IngestTasksScheduler instance;
    private final List<DataSourceIngestTask> activeDataSourceTasks;
    private final DataSourceIngestTaskQueue dataSourceTaskQueue;
    private final TreeSet<FileIngestTask> rootFileTasks;
    private final Deque<FileIngestTask> directoryFileTasks;
    private final Deque<FileIngestTask> activeFileTasks;
    private final FileIngestTaskQueue fileTaskQueue;

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
        this.activeDataSourceTasks = new ArrayList<>();
        this.dataSourceTaskQueue = new DataSourceIngestTaskQueue();
        this.rootFileTasks = new TreeSet<>(new RootDirectoryTaskComparator());
        this.directoryFileTasks = new LinkedList<>();
        this.activeFileTasks = new LinkedList<>();
        this.fileTaskQueue = new FileIngestTaskQueue();
    }

    /**
     * Gets the data source level ingest tasks queue. The queue is a blocking
     * queue intended for use by data source ingest threads.
     *
     * @return The queue.
     */
    IngestTaskQueue getDataSourceIngestTaskQueue() {
        return this.dataSourceTaskQueue;
    }

    /**
     * Gets the file level ingest tasks queue for file ingest threads. The queue
     * is a blocking queue intended for use by file ingest threads.
     *
     * @return The queue.
     */
    IngestTaskQueue getFileIngestTaskQueue() {
        return this.fileTaskQueue;
    }

    /**
     * Schedules a data source level ingest task and file level ingest tasks for
     * a data source ingest job. Either all of the files in the data source or a
     * given subset of the files will be scheduled.
     *
     * @param job   The data source ingest job.
     * @param files A subset of the files for the data source, possibly empty.
     */
    synchronized void scheduleIngestTasks(DataSourceIngestJob job) {
        if (!job.isCancelled()) {
            /*
             * Scheduling of both the data source ingest task and the initial
             * file ingest tasks for a job must be an atomic operation.
             * Otherwise, the data source task might be completed before the
             * file tasks are scheduled, resulting in a potential false positive
             * when another thread checks whether or not all the tasks for the
             * job are completed.
             */
            this.scheduleDataSourceIngestTask(job);
            this.scheduleFileIngestTasks(job);
        }
    }

    /**
     * Schedules a data source level ingest task for a data source ingest job.
     *
     * @param job The data source ingest job.
     */
    synchronized void scheduleDataSourceIngestTask(DataSourceIngestJob job) {
        if (!job.isCancelled()) {
            DataSourceIngestTask task = new DataSourceIngestTask(job);
            this.activeDataSourceTasks.add(task);
            try {
                this.dataSourceTaskQueue.add(task);
            } catch (InterruptedException ex) {
                IngestTasksScheduler.logger.log(Level.INFO, "Ingest cancelled while data source ingest thread blocked on a full queue", ex);
                this.activeDataSourceTasks.remove(task);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Schedules file level ingest tasks for a data source ingest job. Either
     * all of the files in the data source or a given subset of the files will
     * be scheduled.
     *
     * @param job   The data source ingest job.
     * @param files A subset of the files for the data source, possibly empty.
     */
    synchronized void scheduleFileIngestTasks(DataSourceIngestJob job) {
        if (!job.isCancelled()) {
            List<AbstractFile> candidateFiles = getTopLevelFiles(job.getDataSource());
            for (AbstractFile file : candidateFiles) {
                FileIngestTask task = new FileIngestTask(job, file);
                if (IngestTasksScheduler.shouldEnqueueFileTask(task)) {
                    this.rootFileTasks.add(task);
                }
            }
            shuffleFileTaskQueues();
        }
    }

    /**
     * Schedules file level ingest tasks for a subset of the files in a data
     * source ingest job.
     *
     * @param job   The data source ingest job.
     * @param files A subset of the files for the data source.
     */
    synchronized void scheduleFileIngestTasks(DataSourceIngestJob job, Collection<AbstractFile> files) {
        if (!job.isCancelled()) {
            final Deque<FileIngestTask> newTasksForIngestThreads = new LinkedList<>();
            for (AbstractFile file : files) {
                /*
                 * The file will be added directly to the front of the queue for
                 * the ingest threads.
                 */
                FileIngestTask task = new FileIngestTask(job, file);
                if (shouldEnqueueFileTask(task)) {
                    this.activeFileTasks.addLast(task); // RJCTODO: CHeck this in other method
                    newTasksForIngestThreads.addLast(task);
                }

                /*
                 * Add the children of the file, if any, either to the front of
                 * the queue for the ingest threads, in front of the parent
                 * directory task, or to the end directory task queue.
                 */
                try {
                    for (Content child : file.getChildren()) {
                        if (child instanceof AbstractFile) {
                            AbstractFile childFile = (AbstractFile) child;
                            FileIngestTask childTask = new FileIngestTask(job, childFile);
                            if (childFile.hasChildren()) {
                                this.directoryFileTasks.add(childTask);
                            } else if (shouldEnqueueFileTask(childTask)) {
                                this.activeFileTasks.addLast(childTask);
                                newTasksForIngestThreads.addFirst(childTask);
                            }
                        }
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Error getting the children of %s (objId=%d)", file.getName(), file.getId()), ex);  //NON-NLS
                }
            }

            /*
             * Add the newly active tasks into the queue for the file ingest
             * threads, AFTER the higher priority tasks that are already queued.
             */
            for (FileIngestTask newTask : newTasksForIngestThreads) {
                try {
                    this.fileTaskQueue.tasks.putLast(newTask);
                } catch (InterruptedException ex) {
                    IngestTasksScheduler.logger.log(Level.INFO, "Ingest cancelled while data source ingest thread blocked on a full queue", ex); // RJCTODO: Should this propagate? Correct message
                    this.activeFileTasks.remove(newTask);
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
        this.activeDataSourceTasks.remove(task);
        shuffleFileTaskQueues();
    }

    /**
     * Allows an ingest thread to notify this ingest task scheduler that a file
     * level task has been completed.
     *
     * @param task
     */
    synchronized void notifyTaskCompleted(FileIngestTask task) {
        this.activeFileTasks.remove(task);
        shuffleFileTaskQueues();
    }

    /**
     * Queries the task scheduler to determine whether or not all of the ingest
     * tasks for an ingest job have been completed.
     *
     * @param job The data source ingest job
     *
     * @return True or false.
     */
    synchronized boolean tasksForJobAreCompleted(DataSourceIngestJob job) {
        return !hasTasksForJob(this.activeDataSourceTasks, job)
                && !hasTasksForJob(this.rootFileTasks, job)
                && !hasTasksForJob(this.directoryFileTasks, job)
                && !hasTasksForJob(this.activeFileTasks, job);
    }

    /**
     * Clears the "upstream" task scheduling queues for a data source ingest
     * job, but does nothing about tasks that have already been activated, i.e.,
     * moved into the queue that is consumed by the file ingest threads.
     *
     * @param job The data source ingest job
     */
    synchronized void cancelPendingTasksForIngestJob(DataSourceIngestJob job) {
        this.removeTasksForJob(this.rootFileTasks, job);
        this.removeTasksForJob(this.directoryFileTasks, job);
    }

    /**
     * Gets the top level files such as file system root directories, layout
     * files and virtual directories for a data source. Used to create file
     * tasks to put into the root directories queue.
     *
     * @param dataSource    The data source.
     * @param topLevelFiles The top level files are added to this list.
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
     * Intelligently queues file ingest tasks for the ingest manager's file
     * ingest threads by "shuffling" them through a sequence of queues that
     * allows for the interleaving of tasks from different data source ingest
     * jobs based on priority. The sequence of queues is:
     *
     * 1. The root file tasks priority queue, which contains the tasks for the
     * roots of data source content sub trees that are being analyzed for the
     * data source ingest jobs. For example, typical root tasks for a disk image
     * data source would be the tasks for the contents of the root directories
     * of the file systems. This queue is a priority queue that attempts to
     * ensure that user directory content is analyzed before general file system
     * content. It feeds into the directory tasks queue.
     *
     * 2. The directory file tasks queue, which contains directory tasks
     * discovered in the descent through the content sub trees that are being
     * analyzed for the data source ingest jobs. It feeds into the active tasks
     * queue.
     *
     * 3. The active file tasks queue, a queue of the file tasks that are either
     * in the tasks queue for the file ingest threads or are in the process of
     * being analyzed in a file ingest thread.
     *
     * 4. The file tasks queue for the ingest manager's file ingest threads.
     */
    synchronized private void shuffleFileTaskQueues() {
        final Deque<FileIngestTask> newTasksForIngestThreads = new LinkedList<>();
        while (this.activeFileTasks.isEmpty()) {
            /*
             * If the directory file task queue is empty, move the highest
             * priority root file task, if there is one, into it. If both the
             * root and the directory file task queuess are empty, there is
             * nothing left to do.
             */
            if (this.directoryFileTasks.isEmpty()) {
                if (!this.rootFileTasks.isEmpty()) {
                    this.directoryFileTasks.add(this.rootFileTasks.pollFirst());
                } else {
                    return;
                }
            }

            /*
             * Try to move the next task from the directory task queue into the
             * active file tasks tracking queue, if it passes the filter for the
             * job.
             */
            final FileIngestTask directoryTask = this.directoryFileTasks.pollLast();
            if (shouldEnqueueFileTask(directoryTask)) {
                this.activeFileTasks.addFirst(directoryTask);
                newTasksForIngestThreads.addFirst(directoryTask);
            }

            /*
             * If the file or directory from the next that was just activated
             * has children, try to queue tasks for the children. Each child
             * will go into the directory task queue if it is a directory, or
             * into the active file tasks tracking queue if it passes the filter
             * for the job.
             */
            final AbstractFile directory = directoryTask.getFile();
            try {
                for (Content child : directory.getChildren()) {
                    if (child instanceof AbstractFile) {
                        AbstractFile childFile = (AbstractFile) child;
                        FileIngestTask childTask = new FileIngestTask(directoryTask.getIngestJob(), childFile);
                        if (childFile.hasChildren()) {
                            this.directoryFileTasks.add(childTask);
                        } else if (shouldEnqueueFileTask(childTask)) {
                            this.activeFileTasks.add(directoryTask);
                            /*
                             * Queue the child file tasks for the ingest threads
                             * in front of parent directory tasks.
                             */
                            newTasksForIngestThreads.addFirst(directoryTask);
                        }
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Error getting the children of %s (objId=%d)", directory.getName(), directory.getId()), ex);  //NON-NLS
            }

            /*
             * Add the newly active tasks into the queue for the file ingest
             * threads, AFTER the higher priority tasks that are already queued.
             */
            for (FileIngestTask newTask : newTasksForIngestThreads) {
                try {
                    this.fileTaskQueue.tasks.putLast(newTask);
                } catch (InterruptedException ex) {
                    /**
                     * The current thread was interrupted while blocked on a
                     * full queue. Discard the task and reset the interrupted
                     * flag.
                     */
                    IngestTasksScheduler.logger.log(Level.INFO, "Ingest cancelled while data source ingest thread blocked on a full queue", ex); // RJCTODO: Should this propagate? Correct message
                    this.activeFileTasks.remove(newTask);
                    Thread.currentThread().interrupt();
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
        final AbstractFile file = task.getFile();

        // Skip the task if the file is actually the pseudo-file for the parent
        // or current directory.
        String fileName = file.getName();

        if (fileName.equals(".") || fileName.equals("..")) {
            return false;
        }

        /*
         * Check if the file is a member of the file ingest filter that is being
         * applied to the current run of ingest, checks if unallocated space
         * should be processed inside call to fileIsMemberOf
         */
        if (file.isFile() && task.getIngestJob().getFileIngestFilter().fileIsMemberOf(file) == null) {
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
     * Checks whether or not a collection of ingest tasks includes a task for a
     * given data source ingest job.
     *
     * @param tasks The tasks.
     * @param job   The data source ingest job.
     *
     * @return True if there are no tasks for the job, false otherwise.
     */
    synchronized private boolean hasTasksForJob(Collection<? extends IngestTask> tasks, DataSourceIngestJob job) {
        long jobId = job.getId();
        for (IngestTask task : tasks) {
            if (task.getIngestJob().getId() == jobId) {
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
     * @param job   THe data source ingest job.
     */
    synchronized private void removeTasksForJob(Collection<? extends IngestTask> tasks, DataSourceIngestJob job) {
        long jobId = job.getId();
        Iterator<? extends IngestTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            IngestTask task = iterator.next();
            if (task.getIngestJob().getId() == jobId) {
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
        Iterator<? extends IngestTask> iterator = queue.iterator();
        int count = 0;
        while (iterator.hasNext()) {
            IngestTask task = iterator.next();
            if (task.getIngestJob().getId() == jobId) {
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
     * Wraps access to pending data source ingest tasks in the interface
     * required by the ingest threads.
     */
    private final class DataSourceIngestTaskQueue implements IngestTaskQueue {

        private final BlockingQueue<DataSourceIngestTask> tasks = new LinkedBlockingQueue<>();

        @Override
        public IngestTask getNextTask() throws InterruptedException {
            return tasks.take();
        }

        private void add(DataSourceIngestTask task) throws InterruptedException {
            this.tasks.put(task);
        }

    }

    /**
     * Wraps access to pending file ingest tasks in the interface required by
     * the ingest threads.
     */
    private final class FileIngestTaskQueue implements IngestTaskQueue {

        private final BlockingDeque<FileIngestTask> tasks = new LinkedBlockingDeque<>();

        @Override
        public IngestTask getNextTask() throws InterruptedException {
            return tasks.takeFirst();
        }

    }

    /**
     * A snapshot of ingest tasks data for an ingest job.
     */
    class IngestJobTasksSnapshot {

        private final long jobId;
        private final long dsQueueSize;
        private final long rootQueueSize;
        private final long dirQueueSize;
        private final long fileQueueSize;
        private final long runningListSize;

        /**
         * Constructs a snapshot of ingest tasks data for an ingest job.
         *
         * @param jobId The identifier associated with the job.
         */
        IngestJobTasksSnapshot(long jobId) {
            this.jobId = jobId;
            this.rootQueueSize = countTasksForJob(IngestTasksScheduler.this.rootFileTasks, jobId);
            this.dirQueueSize = countTasksForJob(IngestTasksScheduler.this.directoryFileTasks, jobId);
            this.fileQueueSize = countTasksForJob(IngestTasksScheduler.this.fileTaskQueue.tasks, jobId);
            this.dsQueueSize = countTasksForJob(IngestTasksScheduler.this.dataSourceTaskQueue.tasks, jobId);
            this.runningListSize = countTasksForJob(IngestTasksScheduler.this.activeDataSourceTasks, jobId) + countTasksForJob(IngestTasksScheduler.this.activeFileTasks, jobId);
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

        long getDsQueueSize() {
            return dsQueueSize;
        }

        long getRunningListSize() {
            return runningListSize;
        }

    }

}
