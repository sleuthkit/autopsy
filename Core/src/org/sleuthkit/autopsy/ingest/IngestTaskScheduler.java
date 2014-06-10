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
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

final class IngestTaskScheduler {

    private static final IngestTaskScheduler instance = new IngestTaskScheduler();
    private static final Logger logger = Logger.getLogger(IngestTaskScheduler.class.getName());
    private static final int FAT_NTFS_FLAGS = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();
    private final BlockingQueue<DataSourceIngestTask> dataSourceTasks = new LinkedBlockingQueue<>();
    private final TreeSet<FileIngestTask> rootDirectoryTasks = new TreeSet<>(new RootDirectoryTaskComparator()); // Guarded by this
    private final List<FileIngestTask> directoryTasks = new ArrayList<>();  // Guarded by this
    private final BlockingDeque<FileIngestTask> fileTasks = new LinkedBlockingDeque<>();
    private final List<IngestTask> tasksInProgress = new ArrayList<>();  // Guarded by this 
    private final DataSourceIngestTaskQueue dataSourceTaskDispenser = new DataSourceIngestTaskQueue();
    private final FileIngestTaskQueue fileTaskDispenser = new FileIngestTaskQueue();

    static IngestTaskScheduler getInstance() {
        return instance;
    }

    private IngestTaskScheduler() {
    }

    synchronized void scheduleDataSourceIngestTask(IngestJob job, Content dataSource) throws InterruptedException {
        DataSourceIngestTask task = new DataSourceIngestTask(job, dataSource);
        tasksInProgress.add(task);
        try {
            // Should not block, queue is (theoretically) unbounded.
            dataSourceTasks.put(task);
        } catch (InterruptedException ex) {
            tasksInProgress.remove(task);
            Logger.getLogger(IngestTaskScheduler.class.getName()).log(Level.FINE, "Interruption of unexpected block on tasks queue", ex); //NON-NLS
            throw ex;
        }
    }

    void scheduleFileIngestTasks(IngestJob job, Content dataSource) throws InterruptedException {
        // Get the top level files of the data source.
        Collection<AbstractFile> rootObjects = dataSource.accept(new GetRootDirectoryVisitor());
        List<AbstractFile> topLevelFiles = new ArrayList<>();
        if (rootObjects.isEmpty() && dataSource instanceof AbstractFile) {
            // The data source is itself a file.
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

        // Try to enqueue file ingest tasks for the top level files.
        for (AbstractFile firstLevelFile : topLevelFiles) {
            FileIngestTask fileTask = new FileIngestTask(job, firstLevelFile);
            if (shouldEnqueueFileTask(fileTask)) {
                synchronized (this) {
                    rootDirectoryTasks.add(fileTask);
                }
            }
        }
        updateFileTaskQueues();
    }

    void scheduleFileIngestTask(IngestJob job, AbstractFile file) throws InterruptedException {
        FileIngestTask task = new FileIngestTask(job, file);
        if (shouldEnqueueFileTask(task)) {
            // Direct to file tasks queue, no need to update root directory or
            // directory tasks queues.
            enqueueFileTask(task);
        }
    }

    private synchronized void updateFileTaskQueues() throws InterruptedException {
        // Loop until at least one task is added to the file tasks queue or the
        // directory task queues are empty. 
        while (true) {
            // First check for tasks in the file queue. If this queue is not 
            // empty, the update is done.
            if (fileTasks.isEmpty() == false) {
                return;
            }

            // If the directory tasks queue is empty, move the next root
            // directory task, if any, into it. If both directory task queues 
            // are empty and the file tasks queue is empty, the update is done.
            if (directoryTasks.isEmpty()) {
                if (rootDirectoryTasks.isEmpty()) {
                    return;
                }
                directoryTasks.add(rootDirectoryTasks.pollFirst());
            }

            // Try to move a task from the directory queue to the file tasks
            // queue. If the directory contains directories or files, try to 
            // enqueue them as well. Note that it is absolutely necesssary to  
            // add at least one task to the file queue for every root directory
            // that was enqueued, since scheduleFileIngestTasks() returned
            // true for the associated job and the job is expecting to execute
            // at least one task before it calls itself done.
            boolean fileTaskEnqueued = false;
            FileIngestTask directoryTask = directoryTasks.remove(directoryTasks.size() - 1);
            if (shouldEnqueueFileTask(directoryTask)) {
                enqueueFileTask(directoryTask);
                fileTaskEnqueued = true;
            }
            final AbstractFile directory = directoryTask.getFile();
            try {
                List<Content> children = directory.getChildren();
                for (Content child : children) {
                    if (child instanceof AbstractFile) {
                        AbstractFile file = (AbstractFile) child;
                        FileIngestTask fileTask = new FileIngestTask(directoryTask.getIngestJob(), file);
                        if (file.hasChildren()) {
                            directoryTasks.add(fileTask);
                            fileTaskEnqueued = true;
                        } else if (shouldEnqueueFileTask(fileTask)) {
                            enqueueFileTask(fileTask);
                            fileTaskEnqueued = true;
                        }
                    }
                }
            } catch (TskCoreException ex) {
                String errorMessage = String.format("An error occurred getting the children of %s", directory.getName()); //NON-NLS
                logger.log(Level.SEVERE, errorMessage, ex);
            }
            if (!fileTaskEnqueued) {
                enqueueFileTask(new FileIngestTask(directoryTask.getIngestJob(), null));
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

    private synchronized void enqueueFileTask(FileIngestTask task) throws InterruptedException {
        tasksInProgress.add(task);
        // Should not block, queue is (theoretically) unbounded.
        /* add to top of list because we had one image that had a folder with
         * lots of zip files in a folder.  This queue had thousands of entries because 
         * it just kept on getting bigger and bigger. So focus on pushing out 
         * the ZIP file contents out of the queue to try to keep it small.
         */
        fileTasks.addFirst(task);
    }

    synchronized void notifyTaskCompleted(IngestTask task) {
        tasksInProgress.remove(task);
    }

    synchronized boolean hasIncompleteTasksForIngestJob(IngestJob job) {
        long jobId = job.getId();
        for (IngestTask task : tasksInProgress) {
            if (task.getIngestJob().getId() == jobId) {
                return true;
            }
        }
        for (IngestTask task : fileTasks) {
            if (task.getIngestJob().getId() == jobId) {
                return true;
            }
        }
        for (IngestTask task : directoryTasks) {
            if (task.getIngestJob().getId() == jobId) {
                return true;
            }
        }
        for (IngestTask task : rootDirectoryTasks) {
            if (task.getIngestJob().getId() == jobId) {
                return true;
            }
        }
        for (IngestTask task : dataSourceTasks) {
            if (task.getIngestJob().getId() == jobId) {
                return true;
            }
        }
        return false;
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
             * Get the scheduling priority for a given file.
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

    IngestTaskQueue getDataSourceIngestTaskQueue() {
        return this.dataSourceTaskDispenser;
    }

    IngestTaskQueue getFileIngestTaskQueue() {
        return this.fileTaskDispenser;
    }

    private final class DataSourceIngestTaskQueue implements IngestTaskQueue {

        @Override
        public IngestTask getNextTask() throws InterruptedException {
            return dataSourceTasks.take();
        }
    }

    private final class FileIngestTaskQueue implements IngestTaskQueue {

        @Override
        public IngestTask getNextTask() throws InterruptedException {
            FileIngestTask task = fileTasks.takeFirst();
            updateFileTaskQueues(); 
            return task;
        }
    }
}
