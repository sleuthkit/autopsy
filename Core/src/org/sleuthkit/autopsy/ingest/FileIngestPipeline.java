/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014-2015 Basis Technology Corp.
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
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * This class manages a sequence of file level ingest modules for an
 * ingest job pipeline. It starts the modules, runs files through them, and shuts them
 * down when file level ingest is complete.
 * <p>
 * This class is thread-safe.
 */
final class FileIngestPipeline {

    private static final IngestManager ingestManager = IngestManager.getInstance();
    private final IngestJobPipeline ingestJobPipeline;
    private final List<PipelineModule> modules = new ArrayList<>();
    private Date startTime;
    private volatile boolean running;

    /**
     * Constructs an object that manages a sequence of file level ingest
     * modules. It starts the modules, runs files through them, and shuts them
     * down when file level ingest is complete.
     *
     * @param ingestJobPipeline  The ingestJobPipeline that owns the pipeline.
     * @param moduleTemplates The ingest module templates that define the
     *                        pipeline.
     */
    FileIngestPipeline(IngestJobPipeline ingestJobPipeline, List<IngestModuleTemplate> moduleTemplates) {
        this.ingestJobPipeline = ingestJobPipeline;
        for (IngestModuleTemplate template : moduleTemplates) {
            if (template.isFileIngestModuleTemplate()) {
                PipelineModule module = new PipelineModule(template.createFileIngestModule(), template.getModuleName());
                modules.add(module);
            }
        }
    }

    /**
     * Queries whether or not there are any ingest modules in this pipeline.
     *
     * @return True or false.
     */
    boolean isEmpty() {
        return this.modules.isEmpty();
    }

    /**
     * Queries whether or not this pipeline is running.
     *
     * @return True or false.
     */
    boolean isRunning() {
        return this.running;
    }

    /**
     * Returns the start up time of this pipeline.
     *
     * @return The file processing start time, may be null if this pipeline has
     *         not been started yet.
     */
    Date getStartTime() {
        return this.startTime;
    }

    /**
     * Starts up all of the ingest modules in the pipeline.
     *
     * @return List of start up errors, possibly empty.
     */
    synchronized List<IngestModuleError> startUp() {
        this.startTime = new Date();
        this.running = true;
        List<IngestModuleError> errors = new ArrayList<>();
        for (PipelineModule module : this.modules) {
            try {
                module.startUp(new IngestJobContext(this.ingestJobPipeline));
            } catch (Throwable ex) { // Catch-all exception firewall
                errors.add(new IngestModuleError(module.getDisplayName(), ex));
            }
        }
        return errors;
    }

    /**
     * Runs a file through the ingest modules in sequential order.
     *
     * @param task A file level ingest task containing a file to be processed.
     *
     * @return A list of processing errors, possible empty.
     */
    synchronized List<IngestModuleError> process(FileIngestTask task) {
        List<IngestModuleError> errors = new ArrayList<>();
        if (!this.ingestJobPipeline.isCancelled()) {
            AbstractFile file;
            try {
                file = task.getFile();
            } catch (TskCoreException ex) {
                // In practice, this task would never have been enqueued since the file
                // lookup would have failed there.
                errors.add(new IngestModuleError("File Ingest Pipeline", ex)); // NON-NLS
                FileIngestPipeline.ingestManager.setIngestTaskProgressCompleted(task);
                return errors;
            }
            for (PipelineModule module : this.modules) {
                try {
                    FileIngestPipeline.ingestManager.setIngestTaskProgress(task, module.getDisplayName());
                    this.ingestJobPipeline.setCurrentFileIngestModule(module.getDisplayName(), task.getFile().getName());
                    module.process(file);
                } catch (Throwable ex) { // Catch-all exception firewall
                    errors.add(new IngestModuleError(module.getDisplayName(), ex));
                }
                if (this.ingestJobPipeline.isCancelled()) {
                    break;
                }
            }
            
            if (!this.ingestJobPipeline.isCancelled()) {
                // Save any properties that have not already been saved to the database
                try{
                    file.save();
                } catch (TskCoreException ex){
                    Logger.getLogger(FileIngestPipeline.class.getName()).log(Level.SEVERE, "Failed to save data for file " + file.getId(), ex); //NON-NLS
                }
                IngestManager.getInstance().fireFileIngestDone(file);
            }
            file.close();
        }
        FileIngestPipeline.ingestManager.setIngestTaskProgressCompleted(task);
        return errors;
    }

    /**
     * Shuts down all of the modules in the pipeline.
     *
     * @return A list of shut down errors, possibly empty.
     */
    synchronized List<IngestModuleError> shutDown() {
        List<IngestModuleError> errors = new ArrayList<>();
        if (this.running == true) { // Don't shut down pipelines that never started
            for (PipelineModule module : this.modules) {
                try {
                    module.shutDown();
                } catch (Throwable ex) { // Catch-all exception firewall
                    errors.add(new IngestModuleError(module.getDisplayName(), ex));
                    String msg = ex.getMessage();
                    // Jython run-time errors don't seem to have a message, but have details in toString.
                    if (msg == null) {
                        msg = ex.toString();
                    }
                    MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "FileIngestPipeline.moduleError.title.text", module.getDisplayName()), msg);
                }
            }
        }
        this.running = false;
        return errors;
    }

    /**
     * This class decorates a file level ingest module with a display name.
     */
    private static final class PipelineModule implements FileIngestModule {

        private final FileIngestModule module;
        private final String displayName;

        /**
         * Constructs an object that decorates a file level ingest module with a
         * display name.
         *
         * @param module      The file level ingest module to be decorated.
         * @param displayName The display name.
         */
        PipelineModule(FileIngestModule module, String displayName) {
            this.module = module;
            this.displayName = displayName;
        }

        /**
         * Gets the class name of the decorated ingest module.
         *
         * @return The class name.
         */
        String getClassName() {
            return module.getClass().getCanonicalName();
        }

        /**
         * Gets the display name of the decorated ingest module.
         *
         * @return The display name.
         */
        String getDisplayName() {
            return displayName;
        }

        @Override
        public void startUp(IngestJobContext context) throws IngestModuleException {
            module.startUp(context);
        }

        @Override
        public IngestModule.ProcessResult process(AbstractFile file) {
            return module.process(file);
        }

        @Override
        public void shutDown() {
            module.shutDown();
        }

    }

}
