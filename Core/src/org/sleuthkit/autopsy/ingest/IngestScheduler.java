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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Schedules images and files with their associated modules for ingest
 *
 *
 */
class IngestScheduler {

    private static IngestScheduler instance;
    
    private static Logger logger = Logger.getLogger(IngestScheduler.class.getName());
    
    final Image IMAGE_SCHEDULER = new Image();
    final File FILE_SCHEDULER = new File();
    

    private IngestScheduler() {
    }

    static synchronized IngestScheduler getInstance() {
        if (instance == null) {
            instance = new IngestScheduler();
        }

        return instance;
    }

    /**
     * File ingest scheduler
     * 
     * Supports addition ScheduledTasks -  tuples of (image, modules)
     * 
     * Enqueues files and modules, and sorts the files by priority.
     * Maintains only top level directories in memory, not all files in image.
     * 
     * getNext() will return next ProcessTask - tuple of (file, modules)
     * 
     */
    static class File {
        
        private final int FAT_NTFS_FLAGS = 
                        TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue()
                        | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue()
                        | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue()
                        | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();

        /**
         * Scheduled task
         */
        static class ScheduledTask {

            Image image;
            List<IngestModuleAbstractFile> modules;
        }

        /**
         * Task to process
         */
        static class ProcessTask {

            AbstractFile file;
            List<IngestModuleAbstractFile> modules;
        }

        synchronized void add(ScheduledTask task) {
        }

        synchronized ProcessTask getNext() {
            return null;
        }

        synchronized boolean hasNext() {
            return false;
        }

        synchronized void empty() {
        }
        
         /**
         * Check if the file meets criteria to be enqueued, or is a special file that we should skip
         * @param aFile file to check if should be qneueued of skipped 
         * @return true if should be enqueued, false otherwise
         */
        private boolean shouldEnqueue(AbstractFile aFile) {
            if (aFile.isVirtual() == false && aFile.isFile() == true) {
                final org.sleuthkit.datamodel.File f = (org.sleuthkit.datamodel.File) aFile;
                
                //skip files in root dir, starting with $, containing : (not default attributes)
                //with meta address < 32, i.e. some special large NTFS and FAT files
                final TskData.TSK_FS_TYPE_ENUM fsType = f.getFileSystem().getFs_type();

                if ( (fsType.getValue() & FAT_NTFS_FLAGS) == 0) {
                    //not fat or ntfs, accept all files
                    return true;
                }
                
                boolean isInRootDir = false;
                try {
                    isInRootDir = f.getParentDirectory().isRoot();
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Could not check if should enqueue the file: " + f.getName(), ex );
                }
                
                if (isInRootDir && f.getMeta_addr() < 32) {
                    String name = f.getName();
                    
                    if (name.length() > 0 
                            && name.charAt(0) == '$'
                            && name.contains(":")) {
                        return false;
                    }
                }
                else {
                    return true;
                }
                
            }
            
            
            return true;
        }
    }

    /**
     * Image ingest scheduler
     */
    static class Image {

        private List<Task> tasks;

        Image() {
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
            }
            else {
                //enqueue a new task
                tasks.add(task);
            }
        }

        synchronized Task getNext() throws IllegalStateException {
            if (! hasNext() ) {
                throw new IllegalStateException ("There is image tasks in the queue, check hasNext()");
            }
            
            final Task ret = tasks.get(0);
            tasks.remove(0);
            return ret;
        }

        
        synchronized boolean hasNext() {
            return ! tasks.isEmpty();
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
