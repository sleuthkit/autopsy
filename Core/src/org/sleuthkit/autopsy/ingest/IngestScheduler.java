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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Creates ingest jobs and their constituent ingest tasks, enabled the tasks for
 * execution by the ingest manager's ingest threads.
 */
final class IngestScheduler {

    private static final Logger logger = Logger.getLogger(IngestScheduler.class.getName());
    private static final int FAT_NTFS_FLAGS = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();
    private static IngestScheduler instance = null;
    private final AtomicLong nextIngestJobId = new AtomicLong(0L);
    private final ConcurrentHashMap<Long, IngestJob> ingestJobsById = new ConcurrentHashMap<>();
    private volatile boolean enabled = false;
//    private volatile boolean cancellingAllTasks = false; TODO: Uncomment this with related code, if desired
    private final DataSourceIngestTaskQueue dataSourceTaskDispenser = new DataSourceIngestTaskQueue();
    private final FileIngestTaskQueue fileTaskDispenser = new FileIngestTaskQueue();
    // The following five collections lie at the heart of the scheduler.
    //
    // The pending tasks queues are used to schedule tasks for an ingest job. If 
    // multiple jobs are scheduled, tasks from different jobs may become 
    // interleaved in these queues. Data source tasks go into a simple FIFO 
    // queue that is consumed by the ingest threads. File tasks are "shuffled" 
    // through root directory (priority queue), directory (LIFO), and file tasks 
    // queues (LIFO). If a file task makes it into the pending file tasks queue,
    // it is consumed by the ingest threads. 
    //
    // The "tasks in progress" list is used to determine when an ingest job is 
    // completed and should be shut down, i.e., the job should shut down its 
    // ingest pipelines and finish its progress bars. Tasks stay in the "tasks
    // in progress" list either until discarded by the scheduler or the ingest 
    // thread that is working on the task notifies the scheduler that the task 
    // is completed. 
    private final LinkedBlockingQueue<DataSourceIngestTask> pendingDataSourceTasks = new LinkedBlockingQueue<>();
    private final TreeSet<FileIngestTask> pendingRootDirectoryTasks = new TreeSet<>(new RootDirectoryTaskComparator()); // Guarded by this
    private final List<FileIngestTask> pendingDirectoryTasks = new ArrayList<>();  // Guarded by this
    private final BlockingDeque<FileIngestTask> pendingFileTasks = new LinkedBlockingDeque<>();
    private final List<IngestTask> tasksInProgress = new ArrayList<>();  // Guarded by this 

    synchronized static IngestScheduler getInstance() {
        if (instance == null) {
            instance = new IngestScheduler();
        }
        return instance;
    }

    private IngestScheduler() {
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Creates an ingest job for a data source.
     *
     * @param dataSource The data source to ingest.
     * @param ingestModuleTemplates The ingest module templates to use to create
     * the ingest pipelines for the job.
     * @param processUnallocatedSpace Whether or not the job should include
     * processing of unallocated space.
     * @return A collection of ingest module start up errors, empty on success.
     * @throws InterruptedException
     */
    List<IngestModuleError> startIngestJob(Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) throws InterruptedException {
        List<IngestModuleError> errors = new ArrayList<>();
        if (enabled) {
            long jobId = nextIngestJobId.incrementAndGet();
            IngestJob job = new IngestJob(jobId, dataSource, processUnallocatedSpace);
            errors = job.start(ingestModuleTemplates);
            if (errors.isEmpty() && (job.hasDataSourceIngestPipeline() || job.hasFileIngestPipeline())) {
                ingestJobsById.put(jobId, job);
                IngestManager.getInstance().fireIngestJobStarted(jobId);
                scheduleIngestTasks(job);
                logger.log(Level.INFO, "Ingest job {0} started", jobId);
            }
        }
        return errors;
    }

    synchronized private void scheduleIngestTasks(IngestJob job) throws InterruptedException {
        // This is synchronized to guard the task queues and make enabled for 
        // a job an an atomic operation. Otherwise, the data source task might 
        // be completed before the file tasks were scheduled, resulting in a 
        // false positive for a job completion check.
        if (job.hasDataSourceIngestPipeline()) {
            scheduleDataSourceIngestTask(job);
        }
        if (job.hasFileIngestPipeline()) {
            scheduleFileIngestTasks(job);
        }
    }

    synchronized private void scheduleDataSourceIngestTask(IngestJob job) throws InterruptedException {
        DataSourceIngestTask task = new DataSourceIngestTask(job);
        tasksInProgress.add(task);
        try {
            // Should not block, queue is (theoretically) unbounded.
            pendingDataSourceTasks.put(task);
        } catch (InterruptedException ex) {
            tasksInProgress.remove(task);
            Logger.getLogger(IngestScheduler.class.getName()).log(Level.SEVERE, "Interruption of unexpected block on pending data source tasks queue", ex); //NON-NLS
            throw ex;
        }
    }

    synchronized private void scheduleFileIngestTasks(IngestJob job) throws InterruptedException {
        List<AbstractFile> topLevelFiles = getTopLevelFiles(job.getDataSource());
        for (AbstractFile firstLevelFile : topLevelFiles) {
            FileIngestTask task = new FileIngestTask(job, firstLevelFile);
            if (shouldEnqueueFileTask(task)) {
                tasksInProgress.add(task);
                pendingRootDirectoryTasks.add(task);
            }
        }
        updatePendingFileTasksQueues();
    }

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

    synchronized private void updatePendingFileTasksQueues() throws InterruptedException {
        // This is synchronized to guard the pending file tasks queues and make 
        // this an atomic operation.
        if (enabled) {
            while (true) {
                // Loop until either the pending file tasks queue is NOT empty
                // or the upstream queues that feed into it ARE empty.
                if (pendingFileTasks.isEmpty() == false) {
                    return;
                }
                if (pendingDirectoryTasks.isEmpty()) {
                    if (pendingRootDirectoryTasks.isEmpty()) {
                        return;
                    }
                    pendingDirectoryTasks.add(pendingRootDirectoryTasks.pollFirst());
                }

                // Try to add the most recently added from the pending directory tasks queue to 
                // the pending file tasks queue. 
                boolean tasksEnqueuedForDirectory = false;
                FileIngestTask directoryTask = pendingDirectoryTasks.remove(pendingDirectoryTasks.size() - 1);
                if (shouldEnqueueFileTask(directoryTask)) {
                    addToPendingFileTasksQueue(directoryTask);
                    tasksEnqueuedForDirectory = true;
                } else {
                    tasksInProgress.remove(directoryTask);
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
                                // pending directory tasks queue.
                                tasksInProgress.add(childTask);
                                pendingDirectoryTasks.add(childTask);
                                tasksEnqueuedForDirectory = true;
                            } else if (shouldEnqueueFileTask(childTask)) {
                                // Found a file, put the task directly into the
                                // pending file tasks queue.
                                tasksInProgress.add(childTask);
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
                    if (ingestJobIsComplete(job)) {
                        finishIngestJob(job);
                    }
                }
            }
        }
    }

    private static boolean shouldEnqueueFileTask(final FileIngestTask processTask) {
        final AbstractFile aFile = processTask.getFile();
        //if it's unalloc file, skip if so scheduled
        if (processTask.getIngestJob().shouldProcessUnallocatedSpace() == false && aFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return false;
        }
        String fileName = aFile.getName();
        if (fileName.equals(".") || fileName.equals("..")) {
            return false;
        } else if (aFile instanceof org.sleuthkit.datamodel.File) {
            final org.sleuthkit.datamodel.File f = (File) aFile;
            //skip files in root dir, starting with $, containing : (not default attributes)
            //with meta address < 32, i.e. some special large NTFS and FAT files
            FileSystem fs = null;
            try {
                fs = f.getFileSystem();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get FileSystem for " + f, ex); //NON-NLS
            }
            TskData.TSK_FS_TYPE_ENUM fsType = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_UNSUPP;
            if (fs != null) {
                fsType = fs.getFsType();
            }
            if ((fsType.getValue() & FAT_NTFS_FLAGS) == 0) {
                //not fat or ntfs, accept all files
                return true;
            }
            boolean isInRootDir = false;
            try {
                isInRootDir = f.getParentDirectory().isRoot();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Could not check if should enqueue the file: " + f.getName(), ex); //NON-NLS
            }
            if (isInRootDir && f.getMetaAddr() < 32) {
                String name = f.getName();
                if (name.length() > 0 && name.charAt(0) == '$' && name.contains(":")) {
                    return false;
                }
            } else {
                return true;
            }
        }
        return true;
    }

    synchronized private void addToPendingFileTasksQueue(FileIngestTask task) throws IllegalStateException {
        try {
            // Should not block, queue is (theoretically) unbounded.
            /* add to top of list because we had one image that had a folder with
             * lots of zip files.  This queue had thousands of entries because
             * it just kept on getting bigger and bigger. So focus on pushing out
             * the ZIP file contents out of the queue to try to keep it small.
             */
            pendingFileTasks.addFirst(task);
        } catch (IllegalStateException ex) {
            tasksInProgress.remove(task);
            Logger.getLogger(IngestScheduler.class.getName()).log(Level.SEVERE, "Interruption of unexpected block on pending file tasks queue", ex); //NON-NLS
            throw ex;
        }
    }

    void scheduleAdditionalFileIngestTask(IngestJob job, AbstractFile file) throws InterruptedException {
        if (enabled) {
            FileIngestTask task = new FileIngestTask(job, file);
            if (shouldEnqueueFileTask(task)) {
                // Send the file task directly to file tasks queue, no need to
                // update the pending root directory or pending directory tasks 
                // queues.
                tasksInProgress.add(task);
                addToPendingFileTasksQueue(task);
            }
        }
    }

    IngestTaskQueue getDataSourceIngestTaskQueue() {
        return dataSourceTaskDispenser;
    }

    IngestTaskQueue getFileIngestTaskQueue() {
        return fileTaskDispenser;
    }

    void notifyTaskCompleted(IngestTask task) {
        boolean jobIsCompleted;
        IngestJob job = task.getIngestJob();
        synchronized (this) {
            tasksInProgress.remove(task);
            jobIsCompleted = ingestJobIsComplete(job);
        }
        if (jobIsCompleted) {
            // The lock does not need to be held for the job shut down.
            finishIngestJob(job);
            // TODO: Uncomment this code to make sure that there is no more work
            // being done by the ingest threads. The wait() call is in 
            // cancelAllTasks(). 
//            if (cancellingAllTasks) {
//                synchronized (ingestJobsById) {
//                    ingestJobsById.notify();
//                }
//            }
        }
    }

    boolean ingestJobsAreRunning() {
        return !ingestJobsById.isEmpty();
    }

    synchronized void cancelIngestJob(IngestJob job) {
        long jobId = job.getId();
        removeAllPendingTasksForJob(pendingRootDirectoryTasks, jobId);
        removeAllPendingTasksForJob(pendingDirectoryTasks, jobId);
        removeAllPendingTasksForJob(pendingFileTasks, jobId);
        removeAllPendingTasksForJob(pendingDataSourceTasks, jobId);
        job.cancel();
        if (ingestJobIsComplete(job)) {
            finishIngestJob(job);
        }
    }

    synchronized private <T> void removeAllPendingTasksForJob(Collection<T> taskQueue, long jobId) {
        Iterator<T> iterator = taskQueue.iterator();
        while (iterator.hasNext()) {
            IngestTask task = (IngestTask) iterator.next();
            if (task.getIngestJob().getId() == jobId) {
                tasksInProgress.remove((IngestTask) task);
                iterator.remove();
            }
        }
    }

    void cancelAllIngestJobs() {
        synchronized (this) {
            removeAllPendingTasks(pendingRootDirectoryTasks);
            removeAllPendingTasks(pendingDirectoryTasks);
            removeAllPendingTasks(pendingFileTasks);
            removeAllPendingTasks(pendingDataSourceTasks);
            for (IngestJob job : ingestJobsById.values()) {
                job.cancel();
                if (ingestJobIsComplete(job)) {
                    finishIngestJob(job);
                }
            }
        }
        // TODO: Uncomment this code to make sure that there is no more work
        // being done by the ingest threads. The notify() call is in 
        // notifyTaskCompleted()
//        cancellingAllTasks = true;
//        synchronized (ingestJobsById) {
//            while (ingestJobsById.isEmpty() == false) {
//                try {
//                    ingestJobsById.wait();
//                } catch (InterruptedException ex) {
//                    Logger.getLogger(IngestScheduler.class.getName()).log(Level.FINE, "Unexpected interruption of wait on ingest jobs collection", ex); //NON-NLS
//                }
//            }
//        }
//        cancellingAllTasks = false;
    }

    synchronized private <T> void removeAllPendingTasks(Collection<T> taskQueue) {
        Iterator<T> iterator = taskQueue.iterator();
        while (iterator.hasNext()) {
            tasksInProgress.remove((IngestTask) iterator.next());
            iterator.remove();
        }
    }

    synchronized private boolean ingestJobIsComplete(IngestJob job) {
        for (IngestTask task : tasksInProgress) {
            if (task.getIngestJob().getId() == job.getId()) {
                return false;
            }
        }
        return true;
    }

    private void finishIngestJob(IngestJob job) {
        job.finish();
        long jobId = job.getId();
        ingestJobsById.remove(jobId);
        if (!job.isCancelled()) {
            logger.log(Level.INFO, "Ingest job {0} completed", jobId);            
            IngestManager.getInstance().fireIngestJobCompleted(job.getId());
        } else {
            logger.log(Level.INFO, "Ingest job {0} cancelled", jobId);            
            IngestManager.getInstance().fireIngestJobCancelled(job.getId());
        }
    }

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
            /* prioritize root directory folders based on the assumption that we are
             * looking for user content. Other types of investigations may want different
             * priorities. */

            static /* prioritize root directory folders based on the assumption that we are
             * looking for user content. Other types of investigations may want different
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

    private final class DataSourceIngestTaskQueue implements IngestTaskQueue {

        @Override
        public IngestTask getNextTask() throws InterruptedException {
            return pendingDataSourceTasks.take();
        }
    }

    private final class FileIngestTaskQueue implements IngestTaskQueue {

        @Override
        public IngestTask getNextTask() throws InterruptedException {
            FileIngestTask task = pendingFileTasks.takeFirst();
            updatePendingFileTasksQueues();
            return task;
        }
    }
}
