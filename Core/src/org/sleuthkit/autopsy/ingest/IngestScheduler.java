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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_TYPE_ENUM;

/**
 * Schedules data source (images, file-sets, etc) and files with their associated modules for ingest, and
 * manage queues of the scheduled tasks.
 *
 * Currently a singleton object only (as there is one pipeline at a time)
 *
 * Contains internal schedulers for content objects into data source and and file ingest
 * pipelines.
 *
 */
class IngestScheduler {
    private static IngestScheduler instance;
    private static final Logger logger = Logger.getLogger(IngestScheduler.class.getName());
    private final DataSourceScheduler dataSourceScheduler = new DataSourceScheduler();
    private final FileScheduler fileScheduler = new FileScheduler();

    private IngestScheduler() {
    }

    /**
     * Get ingest scheduler singleton instance
     *
     * @return
     */
    static synchronized IngestScheduler getInstance() {
        if (instance == null) {
            instance = new IngestScheduler();
        }

        return instance;
    }

    DataSourceScheduler getDataSourceScheduler() {
        return dataSourceScheduler;
    }

    FileScheduler getFileScheduler() {
        return fileScheduler;
    }

    /**
     * FileScheduler ingest scheduler
     *
     * Supports addition ScheduledTasks - tuples of (data-source, modules)
     *
     * Enqueues files and modules, and sorts the files by priority. Maintains
     * only top level directories in memory (not all children files of the scheduled container content objects)
     *
     * getNext() will return next FileTask - tuple of (file, modules)
     *
     */
    static class FileScheduler implements Iterator<FileScheduler.FileTask> {
        //root folders enqueued
        private TreeSet<FileTask> rootProcessTasks;
        
        //stack of current dirs to be processed recursively
        private List<FileTask> curDirProcessTasks;
        
        //list of files being processed in the currently processed directory
        private LinkedList<FileTask> curFileProcessTasks; //need to add to start and end quickly
        
        //estimated total files to be enqueued for currently scheduled content objects
        private int filesEnqueuedEst;
        private int filesDequeued;
        private final static int FAT_NTFS_FLAGS =
                TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue()
                | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue()
                | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue()
                | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();

        private FileScheduler() {
            rootProcessTasks = new TreeSet<>(new RootTaskComparator());
            curDirProcessTasks = new ArrayList<>();
            curFileProcessTasks = new LinkedList<>();
            resetCounters();
        }
        
        private void resetCounters() {
            filesEnqueuedEst = 0;
            filesDequeued = 0;
        }

        @Override
        public synchronized String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(NbBundle.getMessage(this.getClass(), "IngestScheduler.FileSched.toString.rootDirs.text",
                                          rootProcessTasks.size()));
            for (FileTask task : rootProcessTasks) {
                sb.append(task.toString()).append(" ");
            }
            sb.append(NbBundle.getMessage(this.getClass(), "IngestScheduler.FileSched.toString.curDirs.text",
                                          curDirProcessTasks.size()));
            for (FileTask task : curDirProcessTasks) {
                sb.append(task.toString()).append(" ");
            }
            sb.append(NbBundle.getMessage(this.getClass(), "IngestScheduler.FileSched.toString.curFiles.text",
                                          curFileProcessTasks.size()));
            for (FileTask task : curFileProcessTasks) {
                sb.append(task.toString()).append(" ");
            }
            return sb.toString();
        }

        float getPercentageDone() {
            if (filesEnqueuedEst == 0) {
                return 0;
            }

            return ((100.f) * filesDequeued) / filesEnqueuedEst;

        }

        /**
         * query num files enqueued total num of files to be enqueued.
         *
         * Counts all files for all the sources currently in the queues.
         *
         * @return approx. total num of files enqueued (or to be enqueued)
         */
        private synchronized int queryNumFilesinEnqueuedContents() {
            int totalFiles = 0;
            List<Content> contents = this.getSourceContent();

            final GetFilesCountVisitor countVisitor =
                    new GetFilesCountVisitor();
            for (Content content : contents) {
                totalFiles += content.accept(countVisitor);
            }

            logger.log(Level.INFO, "Total files to queue up: " + totalFiles);

            return totalFiles;
        }

        /**
         * get total est. number of files to be enqueued for current ingest
         * input sources in queues
         *
         * @return total number of files
         */
        int getFilesEnqueuedEst() {
            return filesEnqueuedEst;
        }

        /**
         * Get number of files dequeued so far. This is reset after the same
         * content is enqueued that is already in a queue
         *
         * @return number of files dequeued so far
         */
        int getFilesDequeued() {
            return filesDequeued;
        }

        /**
         * Task for a specific file to process. More specific than the
         * higher-level DataSourceTask.
         */
        static class FileTask {
            private final AbstractFile file;
            private final DataSourceTask dataSourceTask;

            public FileTask(AbstractFile file, DataSourceTask dataSourceTask) {
                this.file = file;
                this.dataSourceTask = dataSourceTask;
            }
            
            public DataSourceTask getDataSourceTask() {
                return dataSourceTask;
            }
            
            public AbstractFile getFile() {
                return file;
            }

            @Override
            public String toString() {
                try {
                    return NbBundle.getMessage(this.getClass(), "IngestScheduler.fileTask.toString.long", file.getId(),
                                               file.getUniquePath()); // + ", dataSourceTask=" + dataSourceTask + '}';
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Cound not get unique path of file in queue, ", ex);
                }
                return NbBundle.getMessage(this.getClass(), "IngestScheduler.fileTask.toString.short", file.getId(),
                                           file.getName());
            }

            /**
             * two process tasks are equal when the file/dir and modules are the
             * same this enables are not to queue up the same file/dir, modules
             * tuples into the root dir set
             *
             * @param obj
             * @return
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final FileTask other = (FileTask) obj;
                if (this.file != other.file && (this.file == null || !this.file.equals(other.file))) {
                    return false;
                }
                DataSourceTask<IngestModuleAbstractFile> thisTask = this.getDataSourceTask();
                DataSourceTask<IngestModuleAbstractFile> otherTask = other.getDataSourceTask();

                if (thisTask != otherTask
                        && (thisTask == null || !thisTask.equals(otherTask))) {
                    return false;
                }
                return true;
            }


            /**
             * Create 1 or more ProcessTasks for each root dir in the Content from
             * the context supplied
             *
             * @param context the original ingest context
             * @return
             */
            private static List<FileTask> createFromScheduledTask(DataSourceTask<IngestModuleAbstractFile> scheduledTask) {
                final Content scheduledContent = scheduledTask.getContent();
                Collection<AbstractFile> rootObjects = scheduledContent.accept(new GetRootDirVisitor());
                List<AbstractFile> firstLevelFiles = new ArrayList<>();
                if (rootObjects.isEmpty() && scheduledContent instanceof AbstractFile) {
                    //add the root, which is a leaf itself
                    firstLevelFiles.add((AbstractFile) scheduledContent);
                } else {
                    for (AbstractFile root : rootObjects) {
                        //TODO the type-specific AbstractFile getChildren() method
                        List<Content> children;
                        try {
                            children = root.getChildren();
                            if (children.isEmpty()) {
                                //add the root itself, could be unalloc file, child of volume or image
                                firstLevelFiles.add(root);
                            } else {
                                //root for fs root dir, schedule children dirs/files
                                for (Content child : children) {
                                    if (child instanceof AbstractFile) {
                                        firstLevelFiles.add((AbstractFile) child);
                                    }
                                }
                            }
                        } catch (TskCoreException ex) {
                            logger.log(Level.WARNING, "Could not get children of root to enqueue: "
                                    + root.getId() + ": " + root.getName(), ex);
                        }

                    }
                }

                List<FileTask> processTasks = new ArrayList<>();
                for (AbstractFile firstLevelFile : firstLevelFiles) {
                    FileTask newTask = new FileTask(firstLevelFile, scheduledTask);
                    if (shouldEnqueueTask(newTask)) {
                        processTasks.add(newTask);
                    }
                }
                return processTasks;
            }
        }

        /**
         * Remove duplicated tasks from previous ingest enqueue currently it
         * removes all previous tasks scheduled in queues for this Content
         *
         * @param task tasks similar to this one should be removed
         */
        private void removeDupTasks(DataSourceTask task) {
            final Content inputContent = task.getContent();

            //remove from root queue
            List<FileTask> toRemove = new ArrayList<>();
            for (FileTask pt : rootProcessTasks) {
                if (pt.getDataSourceTask().getContent().equals(inputContent)) {
                    toRemove.add(pt);
                }
            }
            rootProcessTasks.removeAll(toRemove);

            //remove from dir stack
            toRemove = new ArrayList<>();
            for (FileTask pt : curDirProcessTasks) {
                if (pt.getDataSourceTask().getContent().equals(inputContent)) {
                    toRemove.add(pt);
                }
            }
            curDirProcessTasks.removeAll(toRemove);

            //remove from file queue
            toRemove = new ArrayList<>();
            for (FileTask pt : curFileProcessTasks) {
                if (pt.getDataSourceTask().getContent().equals(inputContent)) {
                    toRemove.add(pt);
                }
            }
            curFileProcessTasks.removeAll(toRemove);


        }

        /**
         * Schedule a file to the file ingest, with associated modules. This
         * will add the file to beginning of the file queue. The method is
         * intended for rescheduling a file that is a derivative of another
         * content object that has already ingested and produced this file. As a
         * result, the derivative file will be scheduled with the same priority
         * as the parent origin file.
         *
         * @param file file to be scheduled
         * @param originalContext original content schedule context that was used
         * to schedule the parent origin content, with the modules, settings, etc.
         */
        synchronized void schedule(AbstractFile file, PipelineContext<IngestModuleAbstractFile> originalContext) {
            DataSourceTask<IngestModuleAbstractFile> originalTask = originalContext.getDataSourceTask();

            //skip if task contains no modules
            if (originalTask.getModules().isEmpty()) {
                return;
            }

            FileTask fileTask = new FileTask(file, originalContext.getDataSourceTask());
            if (shouldEnqueueTask(fileTask)) {
                this.curFileProcessTasks.addFirst(fileTask);
                ++filesEnqueuedEst;
            }


        }

        /**
         * Schedule new Content object for a file ingest with associated
         * modules.
         *
         * @param context context to schedule, with scheduled task containing content to process and modules
         */
        synchronized void schedule(DataSourceTask<IngestModuleAbstractFile> task) {
            //skip if task contains no modules
            if (task.getModules().isEmpty()) {
                return;
            }

            final Content contentToSchedule = task.getContent();

            if (getSourceContent().contains(contentToSchedule)) {
                //reset counters if the same content enqueued twice
                //Note, not very accurate, because we may have processed some files from 
                //another content
                this.filesDequeued = 0;
            }

            //remove duplicate scheduled tasks still in queues for this content if enqueued previously
            removeDupTasks(task);

            List<FileTask> rootTasks = FileTask.createFromScheduledTask(task);

            //adds and resorts the tasks
            this.rootProcessTasks.addAll(rootTasks);

            //update approx count of files to process in queues
            this.filesEnqueuedEst = this.queryNumFilesinEnqueuedContents();

            //reshuffle/update the dir and file level queues if needed
            updateQueues();

        }

        @Override
        public synchronized boolean hasNext() {
            if (curFileProcessTasks.isEmpty()) {
                resetCounters();
                return false;
            }
            return true;
        }

        @Override
        public synchronized FileTask next() {
            if (!hasNext()) {
                throw new IllegalStateException(
                        NbBundle.getMessage(this.getClass(), "IngestScheduler.FileTask.next.exception.msg"));
            }

            //dequeue the last in the list
            final FileTask task = curFileProcessTasks.pollLast();
            filesDequeued++;
            updateQueues();
            
            return task;
        }

        /** 
         * Shuffle the queues so that there are files in the files queue.
         * @returns true if no more data in queue
         */
        private synchronized void updateQueues() {
            
            // we loop because we could have a directory that has all files
            // that do not get enqueued
            while (true) {
                // There are files in the queue, we're done
                if (this.curFileProcessTasks.isEmpty() == false) {
                    return;
                }
                
                // fill in the directory queue if it is empty. 
                if (this.curDirProcessTasks.isEmpty()) {
                    // bail out if root is also empty -- we are done
                    if (rootProcessTasks.isEmpty()) {
                        return;
                    }
                    FileTask rootTask = this.rootProcessTasks.pollFirst();
                    curDirProcessTasks.add(rootTask);
                }

                //pop and push AbstractFile directory children if any
                //add the popped and its leaf children onto cur file list
                FileTask parentTask = curDirProcessTasks.remove(curDirProcessTasks.size() - 1);
                final AbstractFile parentFile = parentTask.file;

                // add itself to the file list
                if (shouldEnqueueTask(parentTask)) {
                    this.curFileProcessTasks.addLast(parentTask);
                }

                // add its children to the file and directory lists
                try {
                    List<Content> children = parentFile.getChildren();
                    for (Content c : children) {
                        if (c instanceof AbstractFile) {
                            AbstractFile childFile = (AbstractFile) c;
                            FileTask childTask = new FileTask(childFile, parentTask.getDataSourceTask());

                            if (childFile.hasChildren()) {
                                this.curDirProcessTasks.add(childTask);
                            } 
                            else if (shouldEnqueueTask(childTask)) {
                                this.curFileProcessTasks.addLast(childTask);
                            }
                        }
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Could not get children of file and update file queues: "
                            + parentFile.getName(), ex);
                }
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException(
                    NbBundle.getMessage(this.getClass(), "IngestScheduler.remove.exception.notSupported.msg"));
        }

        /**
         * Return list of content objects that are in the queue to be processed. 
         *
         * Helpful to determine whether ingest for particular input Content is
         * active
         *
         * @return list of parent source content objects for files currently
         * enqueued
         */
        synchronized List<Content> getSourceContent() {
            final Set<Content> contentSet = new HashSet<Content>();

            for (FileTask task : rootProcessTasks) {
                contentSet.add(task.getDataSourceTask().getContent());
            }
            for (FileTask task : curDirProcessTasks) {
                contentSet.add(task.getDataSourceTask().getContent());
            }
            for (FileTask task : curFileProcessTasks) {
                contentSet.add(task.getDataSourceTask().getContent());
            }

            return new ArrayList<Content>(contentSet);
        }

        /**
         * Determine if a module is in a pipeline in the queue.
         * @param module
         * @return true if it is in the queue.
         */
        synchronized boolean hasModuleEnqueued(IngestModuleAbstractFile module) {
            for (FileTask task : rootProcessTasks) {
                List<IngestModuleAbstractFile> modules = task.getDataSourceTask().getModules();
                for (IngestModuleAbstractFile m : modules) {
                    if (m.getName().equals(module.getName())) {
                        return true;
                    }
                }
            }

            for (FileTask task : curDirProcessTasks) {
                List<IngestModuleAbstractFile> modules = task.getDataSourceTask().getModules();
                for (IngestModuleAbstractFile m : modules) {
                    if (m.getName().equals(module.getName())) {
                        return true;
                    }
                }
            }

            for (FileTask task : curFileProcessTasks) {
                List<IngestModuleAbstractFile> modules = task.getDataSourceTask().getModules();
                for (IngestModuleAbstractFile m : modules) {
                    if (m.getName().equals(module.getName())) {
                        return true;
                    }
                }
            }

            return false;
        }

        synchronized void empty() {
            this.rootProcessTasks.clear();
            this.curDirProcessTasks.clear();
            this.curFileProcessTasks.clear();
        }

        /**
         * Check if the file is a special file that we should skip
         *
         * @param processTask a task whose file to check if should be queued of
         * skipped
         * @return true if should be enqueued, false otherwise
         */
        private static boolean shouldEnqueueTask(final FileTask processTask) {
            final AbstractFile aFile = processTask.file;

            //if it's unalloc file, skip if so scheduled
            if (processTask.getDataSourceTask().isProcessUnalloc() == false
                    && aFile.getType().equals(TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS //unalloc files
                    )) {
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
                    logger.log(Level.SEVERE, "Could not get FileSystem for " + f, ex);
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
                    logger.log(Level.WARNING, "Could not check if should enqueue the file: " + f.getName(), ex);
                }

                if (isInRootDir && f.getMetaAddr() < 32) {
                    String name = f.getName();

                    if (name.length() > 0
                            && name.charAt(0) == '$'
                            && name.contains(":")) {
                        return false;
                    }
                } else {
                    return true;
                }

            }

            return true;
        }

        /**
         * Root dir sorter
         */
        private static class RootTaskComparator implements Comparator<FileTask> {

            @Override
            public int compare(FileTask q1, FileTask q2) {
                AbstractFilePriotity.Priority p1 = AbstractFilePriotity.getPriority(q1.file);
                AbstractFilePriotity.Priority p2 = AbstractFilePriotity.getPriority(q2.file);
                if (p1 == p2) {
                    return (int) (q2.file.getId() - q1.file.getId());
                } else {
                    return p2.ordinal() - p1.ordinal();
                }

            }

            /**
             * Priority determination for sorted AbstractFile, used by
             * RootDirComparator
             */
            private static class AbstractFilePriotity {

                enum Priority {

                    LAST, LOW, MEDIUM, HIGH
                };
                static final List<Pattern> LAST_PRI_PATHS = new ArrayList<Pattern>();
                static final List<Pattern> LOW_PRI_PATHS = new ArrayList<Pattern>();
                static final List<Pattern> MEDIUM_PRI_PATHS = new ArrayList<Pattern>();
                static final List<Pattern> HIGH_PRI_PATHS = new ArrayList<Pattern>();

                /* prioritize root directory folders based on the assumption that we are
                 * looking for user content. Other types of investigations may want different
                 * priorities. */
                static {
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
                 * @param abstractFile
                 * @return 
                 */
                static AbstractFilePriotity.Priority getPriority(final AbstractFile abstractFile) {
                    if (!abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.FS)) {
                        //quickly filter out unstructured content
                        //non-fs virtual files and dirs, such as representing unalloc space
                        return AbstractFilePriotity.Priority.LAST;
                    }

                    //determine the fs files priority by name
                    final String path = abstractFile.getName();

                    if (path == null) {
                        return AbstractFilePriotity.Priority.MEDIUM;
                    }

                    for (Pattern p : HIGH_PRI_PATHS) {
                        Matcher m = p.matcher(path);
                        if (m.find()) {
                            return AbstractFilePriotity.Priority.HIGH;
                        }
                    }

                    for (Pattern p : MEDIUM_PRI_PATHS) {
                        Matcher m = p.matcher(path);
                        if (m.find()) {
                            return AbstractFilePriotity.Priority.MEDIUM;
                        }
                    }

                    for (Pattern p : LOW_PRI_PATHS) {
                        Matcher m = p.matcher(path);
                        if (m.find()) {
                            return AbstractFilePriotity.Priority.LOW;
                        }
                    }

                    for (Pattern p : LAST_PRI_PATHS) {
                        Matcher m = p.matcher(path);
                        if (m.find()) {
                            return AbstractFilePriotity.Priority.LAST;
                        }
                    }

                    //default is medium
                    return AbstractFilePriotity.Priority.MEDIUM;
                }
            }
        }

        /**
         * Get counts of ingestable files/dirs for the content input source.
         *
         * Note, also includes counts of all unalloc children files (for the fs, image, volume) even
         * if ingest didn't ask for them
         */
        static class GetFilesCountVisitor extends ContentVisitor.Default<Long> {

            @Override
            public Long visit(FileSystem fs) {
                //recursion stop here
                //case of a real fs, query all files for it

                SleuthkitCase sc = Case.getCurrentCase().getSleuthkitCase();

                StringBuilder queryB = new StringBuilder();
                queryB.append("( (fs_obj_id = ").append(fs.getId());
                //queryB.append(") OR (fs_obj_id = NULL) )");
                queryB.append(") )");
                queryB.append(" AND ( (meta_type = ").append(TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue());
                queryB.append(") OR (meta_type = ").append(TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue());
                queryB.append(" AND (name != '.') AND (name != '..')");
                queryB.append(") )");

                //queryB.append( "AND (type = ");
                //queryB.append(TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType());
                //queryB.append(")");
                try {
                    final String query = queryB.toString();
                    logger.log(Level.INFO, "Executing count files query: " + query);
                    return sc.countFilesWhere(query);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Couldn't get count of all files in FileSystem", ex);
                    return 0L;
                }
            }

            @Override
            public Long visit(LayoutFile lf) {
                //recursion stop here
                //case of LayoutFile child of Image or Volume
                return 1L;
            }

            private long getCountFromChildren(Content content) {
                long count = 0;
                try {
                    List<Content> children = content.getChildren();
                    if (children.size() > 0) {
                        for (Content child : children) {
                            count += child.accept(this);
                        }
                    } else {
                        count = 1;
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Could not get count of objects from children to get num of total files to be ingested", ex);
                }
                return count;
            }

            @Override
            protected Long defaultVisit(Content cntnt) {
                //recurse assuming this is image/vs/volume 
                //recursion stops at fs or unalloc file
                return getCountFromChildren(cntnt);
            }
        }

        /**
         * Visitor that gets a collection of top level objects to be scheduled,
         * such as root Dirs (if there is FS) or LayoutFiles and virtual
         * directories, also if there is no FS.
         */
        static class GetRootDirVisitor extends GetFilesContentVisitor {

            @Override
            public Collection<AbstractFile> visit(VirtualDirectory ld) {
                //case when we hit a layout directoryor local file container, not under a real FS
                //or when root virt dir is scheduled 
                Collection<AbstractFile> ret = new ArrayList<AbstractFile>();
                ret.add(ld);
                return ret;
            }

            @Override
            public Collection<AbstractFile> visit(LayoutFile lf) {
                //case when we hit a layout file, not under a real FS
                Collection<AbstractFile> ret = new ArrayList<AbstractFile>();
                ret.add(lf);
                return ret;
            }

            @Override
            public Collection<AbstractFile> visit(Directory drctr) {
                //we hit a real directory, a child of real FS

                Collection<AbstractFile> ret = new ArrayList<AbstractFile>();

                ret.add(drctr);

                return ret;
            }

            @Override
            public Collection<AbstractFile> visit(FileSystem fs) {
                return getAllFromChildren(fs);

            }

            @Override
            public Collection<AbstractFile> visit(File file) {
                //can have derived files
                return getAllFromChildren(file);
            }

            @Override
            public Collection<AbstractFile> visit(DerivedFile derivedFile) {
                //can have derived files
                //TODO test this and overall scheduler with derived files
                return getAllFromChildren(derivedFile);
            }

            @Override
            public Collection<AbstractFile> visit(LocalFile localFile) {
                //can have local files
                //TODO test this and overall scheduler with local files
                return getAllFromChildren(localFile);
            }
        }
    }

    /**
     * DataSourceScheduler ingest scheduler
     */
    static class DataSourceScheduler implements Iterator<DataSourceTask<IngestModuleDataSource>> {

        private LinkedList<DataSourceTask<IngestModuleDataSource>> tasks;

        DataSourceScheduler() {
            tasks = new LinkedList<DataSourceTask<IngestModuleDataSource>>();
        }

        synchronized void schedule(DataSourceTask<IngestModuleDataSource> task) {

            //skip if task contains no modules
            if (task.getModules().isEmpty()) {
                return;
            }

            try {
                if (task.getContent().getParent() != null) {
                    //only accepting parent-less content objects (Image, parentless VirtualDirectory)
                    logger.log(Level.SEVERE, "Only parent-less Content (data sources) can be scheduled for DataSource ingest, skipping: " + task.getContent());
                    return;
                }
            } catch (TskCoreException e) {
                logger.log(Level.SEVERE, "Error validating data source to be scheduled for DataSource ingest" + task.getContent(), e);
                return;
            }

            // see if we already have a task for this data source
            DataSourceTask<IngestModuleDataSource> existTask = null;
            for (DataSourceTask<IngestModuleDataSource> curTask : tasks) {
                if (curTask.getContent().equals(task.getContent())) {
                    existTask = curTask;
                    break;
                }
            }

            // add these modules to the existing task for the data source
            // @@@ BC: I'm not sure I like this and it will probably break a more formal pipeline structure
            // @@@ TODO: Verify that if this is called mid-way during ingest that all of the already ingested files get scheduled with the new modules...
            if (existTask != null) {
                //merge modules for the data source task
                existTask.addModules(task.getModules());
            } else {
                //enqueue a new task
                tasks.addLast(task);
            }
        }

        @Override
        public synchronized DataSourceTask<IngestModuleDataSource> next() throws IllegalStateException {
            if (!hasNext()) {
                throw new IllegalStateException(
                        NbBundle.getMessage(this.getClass(), "IngestScheduler.DataSourceScheduler.exception.next.msg"));
            }

            final DataSourceTask<IngestModuleDataSource> ret = tasks.pollFirst();
            return ret;
        }

        /**
         * get all data source that are scheduled to process
         *
         * @return list of data sources in the queue scheduled to process
         */
        synchronized List<org.sleuthkit.datamodel.Content> getContents() {
            List<org.sleuthkit.datamodel.Content> contents = new ArrayList<org.sleuthkit.datamodel.Content>();
            for (DataSourceTask<IngestModuleDataSource> task : tasks) {
                contents.add(task.getContent());
            }
            return contents;
        }

        @Override
        public synchronized boolean hasNext() {
            return !tasks.isEmpty();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException(
                    NbBundle.getMessage(this.getClass(), "IngestScheduler.DataSourceScheduler.exception.remove.msg"));
        }

        synchronized void empty() {
            tasks.clear();
        }

        synchronized int getCount() {
            return tasks.size();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(NbBundle.getMessage(this.getClass(), "IngestScheduler.DataSourceScheduler.toString.size",
                                          getCount()));
            for (DataSourceTask<IngestModuleDataSource> task : tasks) {
                sb.append(task.toString()).append(" ");
            }
            return sb.toString();
        }
    }
}
