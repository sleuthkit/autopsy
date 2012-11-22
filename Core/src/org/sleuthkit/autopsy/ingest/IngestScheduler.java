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
import org.sleuthkit.autopsy.ingest.IngestScheduler.FileScheduler.ProcessTask;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LayoutDirectory;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

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
        private final static int FAT_NTFS_FLAGS =
                TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue()
                | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue()
                | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue()
                | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();

        private FileScheduler() {
            rootProcessTasks = new TreeSet<ProcessTask>(new RootTaskComparator());
            curDirProcessTasks = new ArrayList<ProcessTask>();
            curFileProcessTasks = new ArrayList<ProcessTask>();

        }

        /**
         * Scheduled task added to the scheduler
         */
        static class ScheduledTask {

            Image image;
            List<IngestModuleAbstractFile> modules;

            public ScheduledTask(Image image, List<IngestModuleAbstractFile> modules) {
                this.image = image;
                this.modules = modules;
            }

            /**
             * Two scheduled tasks are equal when the image and modules are the
             * same This enables us not to enqueue the equal schedules tasks
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
                //compare modules in 2 lists

                //are all from this present in other
                for (IngestModuleAbstractFile m1 : this.modules) {
                    String name1 = m1.getName();
                    boolean found = false;
                    for (IngestModuleAbstractFile m2 : other.modules) {
                        if (name1.equals(m2.getName())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }

                }

                //are all in other present in this
                for (IngestModuleAbstractFile m1 : other.modules) {
                    String name1 = m1.getName();
                    boolean found = false;
                    for (IngestModuleAbstractFile m2 : this.modules) {
                        if (name1.equals(m2.getName())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
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
                Collection<AbstractFile> rootObjects = new GetRootDirVisitor().visit(scheduledTask.image);

                List<ProcessTask> processTasks = new ArrayList<ProcessTask>();
                for (AbstractFile root : rootObjects) {
                    processTasks.add(new ProcessTask(root, scheduledTask));
                }
                return processTasks;
            }
            /**
             * Create 1 or more ProcessTasks for each child dir in the dir
             * supplied with root level ProcessTask
             *
             * @param scheduledTask
             * @return
             */
            /**
             * static List<ProcessTask> createFromScheduledTask(ProcessTask
             * parentDirProcessTask) { Collection<AbstractFile> rootObjects =
             * new GetRootDirVisitor().visit(scheduledTask.image);
             *
             * List<ProcessTask> processTasks = new ArrayList<ProcessTask>();
             * for (AbstractFile root : rootObjects) { processTasks.add(new
             * ProcessTask(root, scheduledTask)); } return processTasks; } *
             */
        }

        /**
         * Enqueue new file ingest ScheduledTask If there are
         *
         * @param task
         */
        synchronized void add(ScheduledTask task) {
            List<ProcessTask> rootTasks = ProcessTask.createFromScheduledTask(task);

            //TODO handle case when the same root level dirs are in the queue for the same modules
            //? or ignore that case and rerun them again

            //adds and resorts the tasks
            this.rootProcessTasks.addAll(rootTasks);

            //update the dir and file level queues if needed
            updateQueues();

        }

        @Override
        public synchronized boolean hasNext() {
            return !this.curFileProcessTasks.isEmpty();
        }

        @Override
        public ProcessTask next() {
            if (!hasNext()) {
                throw new IllegalStateException("No next ProcessTask, check hasNext() first!");
            }

            //dequeue the last in the list
            ProcessTask task = curFileProcessTasks.remove(curFileProcessTasks.size() - 1);

            updateQueues();

            return task;

        }

        private void updateQueues() {
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
                    //TODO double check, need to dequeue the high priotity one (start or end of the list)
                    ProcessTask rootTask = this.rootProcessTasks.pollFirst();
                    curDirProcessTasks.add(rootTask);
                }
            }

            if (!this.curDirProcessTasks.isEmpty()) {
                //pop and push AbstractFile directory children if any
                //add the popped and its leaf children onto cur file list
                ProcessTask parentTask = curDirProcessTasks.get(curDirProcessTasks.size() - 1);
                AbstractFile parentFile = parentTask.file;
                //add popped to file list
                this.curFileProcessTasks.add(parentTask);
                try {
                    //get children, and if leafs, add to file queue
                    //otherwise push to curDir stack

                    //TODO use the new more specific method to get list of AbstractFile
                    List<Content> children = parentFile.getChildren();
                    for (Content c : children) {
                        if (c instanceof AbstractFile) {
                            AbstractFile childFile = (AbstractFile) c;
                            ProcessTask childTask = new ProcessTask(parentTask, childFile);
                            //TODO
                            if (childFile.isDir()) {
                                this.curDirProcessTasks.add(childTask);
                            } else {
                                this.curFileProcessTasks.add(childTask);
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

        synchronized void empty() {
            this.rootProcessTasks.clear();
            this.curDirProcessTasks.clear();
            this.curFileProcessTasks.clear();
        }

        /**
         * Check if the file meets criteria to be enqueued, or is a special file
         * that we should skip
         *
         * @param aFile file to check if should be qneueued of skipped
         * @return true if should be enqueued, false otherwise
         */
        private static boolean shouldEnqueueFile(AbstractFile aFile) {
            if (aFile.isVirtual() == false && aFile.isFile() == true) {
                final org.sleuthkit.datamodel.File f = (org.sleuthkit.datamodel.File) aFile;

                //skip files in root dir, starting with $, containing : (not default attributes)
                //with meta address < 32, i.e. some special large NTFS and FAT files
                final TskData.TSK_FS_TYPE_ENUM fsType = f.getFileSystem().getFs_type();

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

                if (isInRootDir && f.getMeta_addr() < 32) {
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

                    LOW, MEDIUM, HIGH
                };
                static final List<Pattern> LOW_PRI_PATHS = new ArrayList<Pattern>();
                static final List<Pattern> MEDIUM_PRI_PATHS = new ArrayList<Pattern>();
                static final List<Pattern> HIGH_PRI_PATHS = new ArrayList<Pattern>();

                static {
                    LOW_PRI_PATHS.add(Pattern.compile("^\\/Windows", Pattern.CASE_INSENSITIVE));

                    MEDIUM_PRI_PATHS.add(Pattern.compile("^\\/Program Files", Pattern.CASE_INSENSITIVE));
                    MEDIUM_PRI_PATHS.add(Pattern.compile("^pagefile", Pattern.CASE_INSENSITIVE));
                    MEDIUM_PRI_PATHS.add(Pattern.compile("^hiberfil", Pattern.CASE_INSENSITIVE));

                    HIGH_PRI_PATHS.add(Pattern.compile("^\\/Users", Pattern.CASE_INSENSITIVE));
                    HIGH_PRI_PATHS.add(Pattern.compile("^\\/Documents and Settings", Pattern.CASE_INSENSITIVE));
                    HIGH_PRI_PATHS.add(Pattern.compile("^\\/home", Pattern.CASE_INSENSITIVE));
                    HIGH_PRI_PATHS.add(Pattern.compile("^\\/ProgramData", Pattern.CASE_INSENSITIVE));
                    HIGH_PRI_PATHS.add(Pattern.compile("^\\/Windows\\/Temp", Pattern.CASE_INSENSITIVE));
                }

                static AbstractFilePriotity.Priority getPriority(final AbstractFile abstractFile) {
                    if (!abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.FS)) {
                        //non-fs files, such as representing unalloc space
                        return AbstractFilePriotity.Priority.MEDIUM;
                    }
                    final String path = ((FsContent) abstractFile).getParentPath();

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

                    //default is medium
                    return AbstractFilePriotity.Priority.MEDIUM;
                }
            }
        }

        /**
         * Visitor that gets a collection of Root Dirs (if there is FS) Or
         * LayoutFiles (if there is no FS)
         */
        static class GetRootDirVisitor extends GetFilesContentVisitor {

            @Override
            public Collection<AbstractFile> visit(LayoutDirectory ld) {
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
