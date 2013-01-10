/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestScheduler.FileScheduler.ProcessTask;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.TSK_FS_META_TYPE_ENUM;

/**
 * Schedules images and files with their associated modules for ingest, and
 * manage queues of the scheduled tasks.
 *
 * Currently a singleton object only.
 *
 * Contains internal schedulers for image and file ingests.
 *
 */
class IngestScheduler {

    private static IngestScheduler instance;
    private static Logger logger = Logger.getLogger(IngestScheduler.class.getName());
    private final ImageScheduler imageScheduler = new ImageScheduler();
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

    ImageScheduler getImageScheduler() {
        return imageScheduler;
    }

    FileScheduler getFileScheduler() {
        return fileScheduler;
    }

    /**
     * FileScheduler ingest scheduler
     *
     * Supports addition ScheduledTasks - tuples of (image, modules)
     *
     * Enqueues files and modules, and sorts the files by priority. Maintains
     * only top level directories in memory, not all files in image.
     *
     * getNext() will return next ProcessTask - tuple of (file, modules)
     *
     */
    static class FileScheduler implements Iterator<FileScheduler.ProcessTask> {
        //root folders enqueued

        private TreeSet<ProcessTask> rootProcessTasks;
        //stack of current dirs to be processed recursively
        private List<ProcessTask> curDirProcessTasks;
        //list of files being processed in the currently processed directory
        private List<ProcessTask> curFileProcessTasks;
        //estimated files to be enqueued for current images
        private int filesEnqueuedEst;
        private int filesDequeued;
        private final static int FAT_NTFS_FLAGS =
                TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue()
                | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue()
                | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue()
                | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();

        private FileScheduler() {
            rootProcessTasks = new TreeSet<ProcessTask>(new RootTaskComparator());
            curDirProcessTasks = new ArrayList<ProcessTask>();
            curFileProcessTasks = new ArrayList<ProcessTask>();
            filesEnqueuedEst = 0;
            filesDequeued = 0;
        }

        @Override
        public synchronized String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\nRootDirs(sorted), size: ").append(rootProcessTasks.size());
            for (ProcessTask task : rootProcessTasks) {
                sb.append(task.toString()).append(" ");
            }
            sb.append("\nCurDirs(stack), size: ").append(curDirProcessTasks.size());
            for (ProcessTask task : curDirProcessTasks) {
                sb.append(task.toString()).append(" ");
            }
            sb.append("\nCurFiles, size: ").append(curFileProcessTasks.size());
            for (ProcessTask task : curFileProcessTasks) {
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
         * Checks files for all the images currently in the queues.
         *
         * @return approx. total num of files enqueued (or to be enqueued)
         */
        private synchronized int queryNumFiles() {
            int totalFiles = 0;
            List<Image> images = getImages();

            final GetImageFilesCountVisitor countVisitor =
                    new GetImageFilesCountVisitor();
            for (Image image : images) {
                totalFiles += image.accept(countVisitor);
            }

            logger.log(Level.INFO, "Total files to queue up: " + totalFiles);

            return totalFiles;
        }

        /**
         * get total est. number of files to be enqueued for current images in
         * queues
         *
         * @return total number of files
         */
        int getFilesEnqueuedEst() {
            return filesEnqueuedEst;
        }

        /**
         * Get number of files dequeued so far This is reset after the same
         * image is enqueued that is already in a queue
         *
         * @return number of files dequeued so far
         */
        int getFilesDequeued() {
            return filesDequeued;
        }

        /**
         * Scheduled task added to the scheduler
         */
        static class ScheduledTask {

            Image image;
            List<IngestModuleAbstractFile> modules;
            boolean processUnalloc;

            public ScheduledTask(Image image, List<IngestModuleAbstractFile> modules, boolean processUnalloc) {
                this.image = image;
                this.modules = modules;
                this.processUnalloc = processUnalloc;
            }

            @Override
            public String toString() {
                return "ScheduledTask{" + "image=" + image + ", modules=" + modules + ", processUnalloc=" + processUnalloc + '}';
            }

            /**
             * Two scheduled tasks are equal when the image and modules are the
             * same. This enables us not to enqueue the equal schedules tasks
             * twice into the queue/set
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
                final ScheduledTask other = (ScheduledTask) obj;
                if (this.image != other.image && (this.image == null || !this.image.equals(other.image))) {
                    return false;
                }
                if (this.modules != other.modules && (this.modules == null || !this.modules.equals(other.modules))) {
                    return false;
                }
                if (this.processUnalloc != other.processUnalloc) {
                    return false;
                }
                return true;
            }
        }

        /**
         * Task to process returned by FileScheduler.getNext()
         */
        static class ProcessTask {

            AbstractFile file;
            ScheduledTask scheduledTask;

            public ProcessTask(AbstractFile file, ScheduledTask scheduledTask) {
                this.file = file;
                this.scheduledTask = scheduledTask;
            }

            @Override
            public String toString() {
                try {
                    return "ProcessTask{" + "file=" + file.getId() + ": "
                            + file.getUniquePath() + "}"; // + ", scheduledTask=" + scheduledTask + '}';
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Cound not get unique path of file in queue, ", ex);
                }
                return "ProcessTask{" + "file=" + file.getId() + ": "
                        + file.getName() + ", scheduledTask=" + scheduledTask + '}';
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
                final ProcessTask other = (ProcessTask) obj;
                if (this.file != other.file && (this.file == null || !this.file.equals(other.file))) {
                    return false;
                }
                if (this.scheduledTask != other.scheduledTask && (this.scheduledTask == null || !this.scheduledTask.equals(other.scheduledTask))) {
                    return false;
                }
                return true;
            }

            //constructor that converts from enqueued process task in dir stack
            //to enqueued processtask in file queue
            ProcessTask(ProcessTask orig, AbstractFile childFile) {
                this.file = childFile;;
                this.scheduledTask = orig.scheduledTask;
            }

            /**
             * Create 1 or more ProcessTasks for each root dir in the image in
             * the ScheduledTask supplied
             *
             * @param scheduledTask
             * @return
             */
            private static List<ProcessTask> createFromScheduledTask(ScheduledTask scheduledTask) {
                Collection<AbstractFile> rootObjects = scheduledTask.image.accept(new GetRootDirVisitor());
                List<AbstractFile> firstLevelFiles = new ArrayList<AbstractFile>();
                for (AbstractFile root : rootObjects) {
                    //TODO use more specific get AbstractFile children method
                    List<Content> children;
                    try {
                        children = root.getChildren();
                        if (children.isEmpty()) {
                            //add the root itself, could be unalloc file, child of volume or image
                            firstLevelFiles.add(root);
                        } else {
                            //root for fs root dir, add children dirs/files
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

                List<ProcessTask> processTasks = new ArrayList<ProcessTask>();
                for (AbstractFile firstLevelFile : firstLevelFiles) {
                    ProcessTask newTask = new ProcessTask(firstLevelFile, scheduledTask);
                    if (shouldEnqueueTask(newTask)) {
                        processTasks.add(newTask);
                    }
                }
                return processTasks;
            }
        }

        /**
         * Remove duplicated tasks from previous ingest enqueue currently it
         * removes all previous tasks scheduled in queues for this image
         *
         * @param task tasks similar to this one should be removed
         */
        private void removeDupTasks(ScheduledTask task) {
            Image image = task.image;

            //remove from root queue
            List<ProcessTask> toRemove = new ArrayList<ProcessTask>();
            for (ProcessTask pt : rootProcessTasks) {
                if (pt.scheduledTask.image.equals(image)) {
                    toRemove.add(pt);
                }
            }
            rootProcessTasks.removeAll(toRemove);

            //remove from dir stack
            toRemove = new ArrayList<ProcessTask>();
            for (ProcessTask pt : curDirProcessTasks) {
                if (pt.scheduledTask.image.equals(image)) {
                    toRemove.add(pt);
                }
            }
            curDirProcessTasks.removeAll(toRemove);

            //remove from file queue
            toRemove = new ArrayList<ProcessTask>();
            for (ProcessTask pt : curFileProcessTasks) {
                if (pt.scheduledTask.image.equals(image)) {
                    toRemove.add(pt);
                }
            }
            curFileProcessTasks.removeAll(toRemove);


        }

        /**
         * Enqueue new file ingest ScheduledTask If there are
         *
         * @param task
         */
        synchronized void add(ScheduledTask task) {
            //skip if task contains no modules
            if (task.modules.isEmpty()) {
                return;
            }
            
            if (getImages().contains(task.image)) {
                //reset counters if the same image enqueued twice
                //Note, not very accurate, because we may have processed some files from 
                //another image
                this.filesDequeued = 0;
            }
            
            //remove duplicate scheduled tasks for this image if enqueued previously
            removeDupTasks(task);

            List<ProcessTask> rootTasks = ProcessTask.createFromScheduledTask(task);

            //adds and resorts the tasks
            this.rootProcessTasks.addAll(rootTasks);
            
            this.filesEnqueuedEst = queryNumFiles();

            //update the dir and file level queues if needed
            updateQueues();

        }

        @Override
        public synchronized boolean hasNext() {
            boolean hasNext = !this.curFileProcessTasks.isEmpty();
            
            if (!hasNext) {
                //reset counters
                filesDequeued = 0;
                filesEnqueuedEst = 0;
            }
            
            return hasNext;
        }

        @Override
        public synchronized ProcessTask next() {
            if (!hasNext()) {
                throw new IllegalStateException("No next ProcessTask, check hasNext() first!");
            }

            //dequeue the last in the list
            ProcessTask task = curFileProcessTasks.remove(curFileProcessTasks.size() - 1);

            //continue shifting to file queue until not empty
            while (curFileProcessTasks.isEmpty()
                    && !(this.rootProcessTasks.isEmpty() && this.curDirProcessTasks.isEmpty())) {
                updateQueues();
            }

            ++filesDequeued;
            
            return task;

        }

        private synchronized void updateQueues() {
            //if file queue is empty, grab the next one from the dir stack
            //if dir stack is empty, grab one from root dir queue first
            //when pop from dir stack, get children of popped, and push them back onto stack

            if (!this.curFileProcessTasks.isEmpty()) {
                return;
            }

            //no file queue tasks
            //grab from dir stack, if available
            if (this.curDirProcessTasks.isEmpty()) {
                //grab from root dir sorted queue
                if (!rootProcessTasks.isEmpty()) {
                    ProcessTask rootTask = this.rootProcessTasks.pollFirst();
                    curDirProcessTasks.add(rootTask);
                }
            }

            if (!this.curDirProcessTasks.isEmpty()) {
                //pop and push AbstractFile directory children if any
                //add the popped and its leaf children onto cur file list
                ProcessTask parentTask = curDirProcessTasks.remove(curDirProcessTasks.size() - 1);
                final AbstractFile parentFile = parentTask.file;
                //add popped to file list
                if (shouldEnqueueTask(parentTask)) {
                    this.curFileProcessTasks.add(parentTask);
                }
                try {
                    //get children, and if leafs, add to file queue
                    //otherwise push to curDir stack

                    //TODO use the new more specific method to get list of AbstractFile
                    List<Content> children = parentFile.getChildren();
                    for (Content c : children) {
                        if (c instanceof AbstractFile) {
                            AbstractFile childFile = (AbstractFile) c;
                            ProcessTask childTask = new ProcessTask(parentTask, childFile);

                            if (childFile.isDir()) {
                                this.curDirProcessTasks.add(childTask);
                            } else {
                                if (shouldEnqueueTask(childTask)) {
                                    this.curFileProcessTasks.add(childTask);
                                }
                            }

                        }
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Could not get children of file and update file queues: "
                            + parentFile.getName(), ex);
                }

            }

            //logger.info("\nAAA ROOTS " + this.rootProcessTasks);
            //logger.info("\nAAA STACK " + this.curDirProcessTasks);
            //logger.info("\nAAA CURFILES " + this.curFileProcessTasks);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported.");
        }

        /**
         * Return list of images associated with the file/dir objects in the
         * queue scheduler to be processed Helpful to determine whether ingest
         * for particular image is active
         *
         * @return list of images for files currently enqueued
         */
        synchronized List<Image> getImages() {
            Set<Image> imageSet = new HashSet<Image>();

            try {
                for (ProcessTask task : rootProcessTasks) {
                    imageSet.add(task.file.getImage());
                }
                for (ProcessTask task : curDirProcessTasks) {
                    imageSet.add(task.file.getImage());
                }
                for (ProcessTask task : curFileProcessTasks) {
                    imageSet.add(task.file.getImage());
                }
            } catch (TskCoreException e) {
                logger.log(Level.SEVERE, "Could not  get images for files scheduled for ingest", e);
            }

            return new ArrayList<Image>(imageSet);
        }

        synchronized boolean hasModuleEnqueued(IngestModuleAbstractFile module) {
            for (ProcessTask task : rootProcessTasks) {
                for (IngestModuleAbstractFile m : task.scheduledTask.modules) {
                    if (m.getName().equals(module.getName())) {
                        return true;
                    }
                }
            }

            for (ProcessTask task : curDirProcessTasks) {
                for (IngestModuleAbstractFile m : task.scheduledTask.modules) {
                    if (m.getName().equals(module.getName())) {
                        return true;
                    }
                }
            }

            for (ProcessTask task : curFileProcessTasks) {
                for (IngestModuleAbstractFile m : task.scheduledTask.modules) {
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
         * Check if the file meets criteria to be enqueued, or is a special file
         * that we should skip
         *
         * @param processTask a task whose file to check if should be qneueued
         * of skipped
         * @return true if should be enqueued, false otherwise
         */
        private static boolean shouldEnqueueTask(ProcessTask processTask) {
            final AbstractFile aFile = processTask.file;

            //if it's unalloc file, skip if so scheduled
            if (processTask.scheduledTask.processUnalloc == false) {
                if (aFile.isVirtual() == true) {
                    return false;
                }
            }

            String fileName = aFile.getName();
            if (fileName.equals(".") || fileName.equals("..")) {
                return false;
            }
            if (aFile.isVirtual() == false && aFile.isFile() == true) {
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
        private static class RootTaskComparator implements Comparator<ProcessTask> {

            @Override
            public int compare(ProcessTask q1, ProcessTask q2) {
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
                    //LAST_PRI_PATHS.add(Pattern.compile("^\\$Unalloc", Pattern.CASE_INSENSITIVE));
                    //LAST_PRI_PATHS.add(Pattern.compile("^\\Unalloc", Pattern.CASE_INSENSITIVE));
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
         * Get counts of ingestable files/dirs for image/filesystem Only call
         * accept() for Image object Do not use on any other objects
         *
         * Includes counts of all unalloc files (for the fs, image, volume) even
         * if ingest didn't ask for them
         */
        @SuppressWarnings("deprecation")
        static class GetImageFilesCountVisitor extends ContentVisitor.Default<Integer> {

            @Override
            public Integer visit(FileSystem fs) {
                //recursion stop here
                //case of a real fs, query all files for it

                SleuthkitCase sc = Case.getCurrentCase().getSleuthkitCase();

                StringBuilder queryB = new StringBuilder();
                queryB.append("SELECT COUNT(*) FROM tsk_files WHERE ( (fs_obj_id = ").append(fs.getId());
                //queryB.append(") OR (fs_obj_id = NULL) )");
                queryB.append(") )");
                queryB.append(" AND ( (meta_type = ").append(TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue());
                queryB.append(") OR (meta_type = ").append(TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue());
                queryB.append(" AND (name != '.') AND (name != '..')");
                queryB.append(") )");

                //queryB.append( "AND (type = ");
                //queryB.append(TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType());
                //queryB.append(")");

                ResultSet rs = null;
                try {
                    final String query = queryB.toString();
                    logger.log(Level.INFO, "Executing query: " + query);
                    rs = sc.runQuery(query);
                    if (rs.next()) {
                        return rs.getInt(1);
                    } else {
                        throw new RuntimeException("Count not get count for file system files");
                    }

                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Couldn't get count of all files in FileSystem", ex);
                    return 0;
                } finally {
                    if (rs != null) {
                        try {
                            sc.closeRunQuery(rs);
                        } catch (SQLException ex) {
                            logger.log(Level.WARNING, "Couldn't close result set after getting count of all files in FileSystem", ex);
                        }
                    }
                }

            }

            @Override
            public Integer visit(LayoutFile lf) {
                //recursion stop here
                //case of LayoutFile child of Image or Volume
                return 1;
            }

            private int getCountFromChildren(Content content) {
                int count = 0;
                try {
                    for (Content child : content.getChildren()) {
                        count += child.accept(this);
                    }
                } catch (TskCoreException ex) {
                    Exceptions.printStackTrace(ex);
                    return 0;
                }
                return count;
            }

            @Override
            protected Integer defaultVisit(Content cntnt) {
                //recurse assuming this is image/vs/volume 
                //recursion stops at fs or unalloc file
                return getCountFromChildren(cntnt);
            }
        }

        /**
         * Visitor that gets a collection of Root Dirs (if there is FS) Or
         * LayoutFiles (if there is no FS)
         */
        static class GetRootDirVisitor extends GetFilesContentVisitor {

            @Override
            public Collection<AbstractFile> visit(VirtualDirectory ld) {
                //case when we hit a layout directory, not under a real FS
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
                throw new IllegalStateException("Should not happen, file cannot be a direct child or Fs, Volume, or Image");
            }
        }
    }

    /**
     * ImageScheduler ingest scheduler
     */
    static class ImageScheduler implements Iterator<ImageScheduler.Task> {

        private List<Task> tasks;

        ImageScheduler() {
            tasks = new ArrayList<Task>();
        }

        /**
         * Scheduled task
         */
        static class Task {

            private org.sleuthkit.datamodel.Image image;
            private List<IngestModuleImage> modules;

            public Task(org.sleuthkit.datamodel.Image image, List<IngestModuleImage> modules) {
                this.image = image;
                this.modules = modules;
            }

            public Task(org.sleuthkit.datamodel.Image image, IngestModuleImage module) {
                this.image = image;
                this.modules = new ArrayList<IngestModuleImage>();
                modules.add(module);
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final Task other = (Task) obj;
                if (this.image != other.image && (this.image == null || !this.image.equals(other.image))) {
                    return false;
                }
                return true;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                for (IngestModuleImage module : modules) {
                    sb.append(module.getName()).append(" ");
                }
                return "Task{" + "image=" + image.getName() + ", modules=" + sb.toString() + '}';
            }

            public org.sleuthkit.datamodel.Image getImage() {
                return image;
            }

            public List<IngestModuleImage> getModules() {
                return modules;
            }

            private void addModule(IngestModuleImage newModule) {
                if (!modules.contains(newModule)) {
                    modules.add(newModule);
                }
            }

            private void addModules(List<IngestModuleImage> newModules) {
                for (IngestModuleImage newModule : newModules) {
                    if (!modules.contains(newModule)) {
                        modules.add(newModule);
                    }
                }
            }
        }

        synchronized void add(Task task) {
            //skip if task contains no modules
            if (task.modules.isEmpty()) {
                return;
            }
            
            Task existTask = null;
            for (Task curTask : tasks) {
                if (curTask.image.equals(task.image)) {
                    existTask = curTask;
                    break;
                }
            }

            if (existTask != null) {
                //merge modules for the image task
                existTask.addModules(task.getModules());
            } else {
                //enqueue a new task
                tasks.add(task);
            }
        }

        @Override
        public synchronized Task next() throws IllegalStateException {
            if (!hasNext()) {
                throw new IllegalStateException("There is image tasks in the queue, check hasNext()");
            }

            final Task ret = tasks.get(0);
            tasks.remove(0);
            return ret;
        }

        /**
         * get all images that are scheduled to process
         *
         * @return list of images in the queue scheduled to process
         */
        synchronized List<org.sleuthkit.datamodel.Image> getImages() {
            List<org.sleuthkit.datamodel.Image> images = new ArrayList<org.sleuthkit.datamodel.Image>();
            for (Task task : tasks) {
                images.add(task.getImage());
            }
            return images;
        }

        @Override
        public synchronized boolean hasNext() {
            return !tasks.isEmpty();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
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
            sb.append("ImageQueue, size: ").append(getCount());
            for (Task task : tasks) {
                sb.append(task.toString()).append(" ");
            }
            return sb.toString();
        }
    }
}
