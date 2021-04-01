/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
import java.util.Optional;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * An abstract superclass for pipelines of ingest modules for a given ingest
 * task type. Some examples of ingest task types: data source level ingest
 * tasks, file ingest tasks, data artifact ingest tasks, etc. Subclasses need to
 * implement a specialization of the inner PipelineModule abstract superclass
 * for the type of ingest modules that make up the pipeline.
 *
 * @param <T> The ingest task type.
 */
abstract class IngestTaskPipeline<T extends IngestTask> {

    private final IngestJobPipeline ingestJobPipeline;
    private final List<IngestModuleTemplate> moduleTemplates;
    private final List<PipelineModule<T>> modules;
    private volatile Date startTime;
    private volatile boolean running;
    private volatile PipelineModule<T> currentModule;

    /**
     * Constructs an instance of an abstract superclass for pipelines of ingest
     * modules for a given ingest task type. Some examples of ingest task types:
     * data source level ingest tasks, file ingest tasks, data artifact ingest
     * tasks, etc. Subclasses need to implement a specialization of the inner
     * PipelineModule abstract superclass for the type of ingest modules that
     * make up the pipeline.
     *
     * @param ingestJobPipeline The ingest job pipeline that owns this pipeline.
     * @param moduleTemplates   The ingest module templates that define this
     *                          pipeline.
     */
    IngestTaskPipeline(IngestJobPipeline ingestJobPipeline, List<IngestModuleTemplate> moduleTemplates) {
        this.ingestJobPipeline = ingestJobPipeline;
        this.moduleTemplates = moduleTemplates;
        modules = new ArrayList<>();
    }

    /**
     * Indicates whether or not there are any ingest modules in this pipeline.
     *
     * @return True or false.
     */
    boolean isEmpty() {
        return modules.isEmpty();
    }

    /**
     * Queries whether or not this pipeline is running, i.e., started and not
     * shut down.
     *
     * @return True or false.
     */
    boolean isRunning() {
        return running;
    }

    /**
     * Starts up the ingest modules in this pipeline.
     *
     * @return A list of ingest module startup errors, possibly empty.
     */
    List<IngestModuleError> startUp() {
        createIngestModules(moduleTemplates);
        return startUpIngestModules();
    }

    /**
     * Creates the ingest modules for this pipeline.
     *
     * @param moduleTemplates The ingest module templates avaialble to this
     *                        pipeline.
     */
    private void createIngestModules(List<IngestModuleTemplate> moduleTemplates) {
        for (IngestModuleTemplate template : moduleTemplates) {
            Optional<PipelineModule<T>> module = acceptModuleTemplate(template);
            if (module.isPresent()) {
                modules.add(module.get());
            }
        }
    }

    /**
     * Determines if the type of ingest module that can be created from a given
     * ingest module template should be added to this pipeline. If so, the
     * ingest module is created and returned.
     *
     * @param ingestModuleTemplate The ingest module template to be used or
     *                             ignored, as appropriate to the pipeline type.
     *
     * @return An Optional that is either empty or contains a newly created and
     *         wrapped ingest module.
     */
    abstract Optional<PipelineModule<T>> acceptModuleTemplate(IngestModuleTemplate ingestModuleTemplate);

    /**
     * Starts up the ingest modules in the pipeline.
     *
     * @return A list of ingest module startup errors, possibly empty.
     */
    private List<IngestModuleError> startUpIngestModules() {
        startTime = new Date();
        running = true;
        List<IngestModuleError> errors = new ArrayList<>();
        for (PipelineModule<T> module : modules) {
            try {
                module.startUp(new IngestJobContext(ingestJobPipeline));
            } catch (Throwable ex) { // Catch-all exception firewall
                errors.add(new IngestModuleError(module.getDisplayName(), ex));
            }
        }
        return errors;
    }

    /**
     * Returns the start up time of this pipeline.
     *
     * @return The file processing start time, may be null if this pipeline has
     *         not been started yet.
     */
    Date getStartTime() {
        Date reportedStartTime = null;
        if (startTime != null) {
            reportedStartTime = new Date(startTime.getTime());
        }
        return reportedStartTime;
    }

    /**
     * Does any preparation required before performing a task.
     *
     * @param task The task.
     *
     * @throws IngestTaskPipelineException Thrown if there is an error preparing
     *                                     to perform the task.
     */
    abstract void prepareTask(T task) throws IngestTaskPipelineException;

    /**
     * Performs an ingest task using the ingest modules in this pipeline.
     *
     * @param task The task.
     *
     * @return A list of ingest module task processing errors, possibly empty.
     */
    List<IngestModuleError> performTask(T task) {
        List<IngestModuleError> errors = new ArrayList<>();
        if (!this.ingestJobPipeline.isCancelled()) {
            try {
                prepareTask(task);
            } catch (IngestTaskPipelineException ex) {
                errors.add(new IngestModuleError("Ingest Task Pipeline", ex)); //NON-NLS
                return errors;
            }
            for (PipelineModule<T> module : modules) {
                try {
                    currentModule = module;
                    currentModule.setProcessingStartTime();
                    module.performTask(ingestJobPipeline, task);
                } catch (Throwable ex) { // Catch-all exception firewall
                    errors.add(new IngestModuleError(module.getDisplayName(), ex));
                }
                if (ingestJobPipeline.isCancelled()) {
                    break;
                }
            }
        }
        try {
            completeTask(task);
        } catch (IngestTaskPipelineException ex) {
            errors.add(new IngestModuleError("Ingest Task Pipeline", ex)); //NON-NLS
        }
        currentModule = null;
        return errors;
    }

    /**
     * Gets the currently running module.
     *
     * @return The module, possibly null if no module is currently running.
     */
    PipelineModule<T> getCurrentlyRunningModule() {
        return currentModule;
    }

    /**
     * Does any clean up required after performing a task.
     *
     * @param task The task.
     *
     * @throws IngestTaskPipelineException Thrown if there is an error cleaning
     *                                     up after performing the task.
     */
    abstract void completeTask(T task) throws IngestTaskPipelineException;

    /**
     * Shuts down all of the modules in the pipeline.
     *
     * @return A list of shut down errors, possibly empty.
     */
    List<IngestModuleError> shutDown() {
        List<IngestModuleError> errors = new ArrayList<>();
        if (running == true) {
            for (PipelineModule<T> module : modules) {
                try {
                    module.shutDown();
                } catch (Throwable ex) { // Catch-all exception firewall
                    errors.add(new IngestModuleError(module.getDisplayName(), ex));
                    String msg = ex.getMessage();
                    if (msg == null) {
                        /*
                         * Jython run-time errors don't seem to have a message,
                         * but have details in the string returned by
                         * toString().
                         */
                        msg = ex.toString();
                    }
                    MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "FileIngestPipeline.moduleError.title.text", module.getDisplayName()), msg);
                }
            }
        }
        running = false;
        return errors;
    }

    /**
     * An abstract superclass for a wrapper that adds ingest infrastructure
     * operations to an ingest module.
     */
    static abstract class PipelineModule<T extends IngestTask> implements IngestModule {

        private final IngestModule module;
        private final String displayName;
        private volatile Date processingStartTime;

        /**
         * Constructs an instance of an abstract superclass for a wrapper that
         * adds ingest infrastructure operations to an ingest module.
         *
         * @param module      The ingest module to be wrapped.
         * @param displayName The display name for the module.
         */
        PipelineModule(IngestModule module, String displayName) {
            this.module = module;
            this.displayName = displayName;
            this.processingStartTime = new Date();
        }

        /**
         * Gets the class name of the wrapped ingest module.
         *
         * @return The class name.
         */
        String getClassName() {
            return module.getClass().getCanonicalName();
        }

        /**
         * Gets the display name of the wrapped ingest module.
         *
         * @return The display name.
         */
        String getDisplayName() {
            return displayName;
        }

        /**
         * Sets the processing start time for the wrapped module to the system
         * time when this method is called.
         */
        void setProcessingStartTime() {
            processingStartTime = new Date();
        }

        /**
         * Gets the the processing start time for the wrapped module.
         *
         * @return The start time, will be null if the module has not started
         *         processing the data source yet.
         */
        Date getProcessingStartTime() {
            return new Date(processingStartTime.getTime());
        }

        @Override
        public void startUp(IngestJobContext context) throws IngestModuleException {
            module.startUp(context);
        }

        /**
         * Performs an ingest task.
         *
         * @param ingestJobPipeline The ingest job pipeline that owns the ingest
         *                          module pipeline this module belongs to.
         * @param task              The task to process.
         *
         * @throws IngestModuleException Excepton thrown if there is an error
         *                               performing the task.
         */
        abstract void performTask(IngestJobPipeline ingestJobPipeline, T task) throws IngestModuleException;

        @Override
        public void shutDown() {
            module.shutDown();
        }

    }

    /**
     * An exception for the use of ingest task pipelines.
     */
    public static class IngestTaskPipelineException extends Exception {

        private static final long serialVersionUID = 1L;

        public IngestTaskPipelineException(String message) {
            super(message);
        }

        public IngestTaskPipelineException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
