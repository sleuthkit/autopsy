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

final class IngestScheduler {

    private static final IngestScheduler instance = new IngestScheduler();
    private static final Logger logger = Logger.getLogger(IngestScheduler.class.getName());
    private static final int FAT_NTFS_FLAGS = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();
    private final LinkedBlockingQueue<DataSourceIngestTask> dataSourceTasks = new LinkedBlockingQueue<>();
    private final TreeSet<FileIngestTask> rootDirectoryTasks = new TreeSet<>(new RootDirectoryTaskComparator()); // Guarded by this
    private final List<FileIngestTask> directoryTasks = new ArrayList<>();  // Guarded by this
    private final LinkedBlockingQueue<FileIngestTask> fileTasks = new LinkedBlockingQueue<>();  // Guarded by this
    private final List<IngestTask> dataSourceTasksInProgress = new ArrayList<>();  // Guarded by this
    private final List<IngestTask> fileTasksInProgress = new ArrayList<>();  // Guarded by this
    private final DataSourceIngestTaskQueue dataSourceTaskDispenser = new DataSourceIngestTaskQueue();
    private final FileIngestTaskQueue fileTaskDispenser = new FileIngestTaskQueue();

    static IngestScheduler getInstance() {
        return instance;
    }

    private IngestScheduler() {
    }

    void addDataSourceTask(IngestJob job, Content dataSource) throws InterruptedException {
        dataSourceTasks.put(new DataSourceIngestTask(job, dataSource));
    }

    synchronized boolean addFileTasks(IngestJob job, Content dataSource) throws InterruptedException {
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
                        // Add the root object itself, it could be an unallocated space 
                        // file, or a child of a volume or an image.
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

        if (!topLevelFiles.isEmpty()) {
            // Enqueue file ingest tasks for the top level files.
            for (AbstractFile firstLevelFile : topLevelFiles) {
                FileIngestTask fileTask = new FileIngestTask(job, firstLevelFile);
                if (shouldEnqueueFileTask(fileTask)) {
                    rootDirectoryTasks.add(fileTask);
                }
            }
            updateFileTaskQueues();
            return true;            
        } else {
            return false;
        }        
   }

    void addFileTask(IngestJob job, AbstractFile file) {
        FileIngestTask task = new FileIngestTask(job, file);
        if (shouldEnqueueFileTask(task)) {
            try {
                fileTasks.put(task);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.log(Level.FINE, "Task scheduling for ingest job interrupted", ex); //NON-NLS
            }
        }
    }

    synchronized void removeQueuedTasksForIngestJob(long ingestJobId) {
        // Remove all tasks for this ingest job that are not in progress. 
        Iterator<FileIngestTask> fileTasksIterator = fileTasks.iterator();
        while (fileTasksIterator.hasNext()) {
            if (fileTasksIterator.next().getIngestJob().getId() == ingestJobId) {
                fileTasksIterator.remove();
            }
        }
        Iterator<FileIngestTask> directoryTasksIterator = directoryTasks.iterator();
        while (directoryTasksIterator.hasNext()) {
            if (directoryTasksIterator.next().getIngestJob().getId() == ingestJobId) {
                directoryTasksIterator.remove();
            }
        }
        Iterator<FileIngestTask> rootDirectoryTasksIterator = rootDirectoryTasks.iterator();
        while (rootDirectoryTasksIterator.hasNext()) {
            if (rootDirectoryTasksIterator.next().getIngestJob().getId() == ingestJobId) {
                rootDirectoryTasksIterator.remove();
            }
        }
        Iterator<DataSourceIngestTask> dataSourceTasksIterator = dataSourceTasks.iterator();
        while (dataSourceTasksIterator.hasNext()) {
            if (dataSourceTasksIterator.next().getIngestJob().getId() == ingestJobId) {
                dataSourceTasksIterator.remove();
            }
        }
    }

    private synchronized void updateFileTaskQueues() throws InterruptedException {
        // we loop because we could have a directory that has all files
        // that do not get enqueued
        while (true) {
            // There are files in the queue, we're done
            if (fileTasks.isEmpty() == false) {
                return;
            }
            // fill in the directory queue if it is empty.
            if (this.directoryTasks.isEmpty()) {
                // bail out if root is also empty -- we are done
                if (rootDirectoryTasks.isEmpty()) {
                    return;
                }
                FileIngestTask rootTask = rootDirectoryTasks.pollFirst();
                directoryTasks.add(rootTask);
            }
            //pop and push AbstractFile directory children if any
            //add the popped and its leaf children onto cur file list
            FileIngestTask parentTask = directoryTasks.remove(directoryTasks.size() - 1);
            final AbstractFile parentFile = parentTask.getFile();
            // add itself to the file list
            if (shouldEnqueueFileTask(parentTask)) {
                try {
                    fileTasks.put(parentTask);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    logger.log(Level.FINE, "Task scheduling for ingest job interrupted", ex); //NON-NLS
                    return;
                }
            }
            // add its children to the file and directory lists
            try {
                List<Content> children = parentFile.getChildren();
                for (Content c : children) {
                    if (c instanceof AbstractFile) {
                        AbstractFile childFile = (AbstractFile) c;
                        FileIngestTask childTask = new FileIngestTask(parentTask.getIngestJob(), childFile);
                        if (childFile.hasChildren()) {
                            directoryTasks.add(childTask);
                        } else if (shouldEnqueueFileTask(childTask)) {
                            try {
                                fileTasks.put(childTask);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                logger.log(Level.FINE, "Task scheduling for ingest job interrupted", ex); //NON-NLS
                                return;
                            }
                        }
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get children of file and update file queues: " + parentFile.getName(), ex); //NON-NLS
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

    IngestTaskQueue getDataSourceIngestTaskQueue() {
        return dataSourceTaskDispenser;
    }

    IngestTaskQueue getFileIngestTaskQueue() {
        return fileTaskDispenser;
    }

    synchronized boolean wasLastTaskForIngestJob(IngestTask completedTask) {
        tasksInProgress.remove(completedTask);
        IngestJob job = completedTask.getIngestJob();
        long jobId = job.getId();
        for (IngestTask task : tasksInProgress) {
            if (task.getIngestJob().getId() == jobId) {
                return false;
            }
        }
        for (FileIngestTask task : fileTasks) {
            if (task.getIngestJob().getId() == jobId) {
                return false;
            }
        }
        for (FileIngestTask task : directoryTasks) {
            if (task.getIngestJob().getId() == jobId) {
                return false;
            }
        }
        for (FileIngestTask task : rootDirectoryTasks) {
            if (task.getIngestJob().getId() == jobId) {
                return false;
            }
        }
        for (DataSourceIngestTask task : dataSourceTasks) {
            if (task.getIngestJob().getId() == jobId) {
                return false;
            }
        }
        return true;
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

    private class DataSourceIngestTaskQueue implements IngestTaskQueue {

        @Override
        public IngestTask getNextTask() throws InterruptedException { // RJCTODO: Does this need to be synchronized?
            DataSourceIngestTask task = dataSourceTasks.take();
            dataSourceTasksInProgress.add(task);
            return task;
        }
    }

    private class FileIngestTaskQueue implements IngestTaskQueue {

        @Override
        public IngestTask getNextTask() throws InterruptedException {  // RJCTODO: Does this need to be synchronized?
            FileIngestTask task = fileTasks.take();
            fileTasksInProgress.add(task);
            updateFileTaskQueues();
            return task;
        }
    }
}
