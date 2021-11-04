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

import static java.lang.Thread.sleep;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * An abstract superclass for pipelines of ingest modules that perform the
 * ingest tasks that make up an ingest job. A pipeline performs a task by
 * passing it sequentially to the process() method of each module in the
 * pipeline.
 *
 * @param <T> The type of ingest tasks the pipeline performs.
 */
abstract class IngestPipeline<T extends IngestTask> {

    /*
     * NOTE ON MULTI-THREADING POLICY: This class is primarily designed for use
     * by one thread at a time. There are a few status fields that are volatile
     * to ensure visibility to threads making ingest progress snapshots, but
     * methods such as startUp(), performTask() and shutDown() are not
     * synchronized.
     */
    private static final Logger logger = Logger.getLogger(IngestPipeline.class.getName());
    private final IngestJobExecutor ingestJobExecutor;
    private final List<IngestModuleTemplate> moduleTemplates;
    private final List<PipelineModule<T>> modules;
    private volatile Date startTime;
    private volatile boolean running;
    private volatile PipelineModule<T> currentModule;

    /**
     * Constructs the superclass part of a pipeline of ingest modules that
     * performs ingest tasks for an ingest job.
     *
     * @param ingestJobExecutor The ingest job executor for this pipeline.
     * @param moduleTemplates   The ingest module templates to be used to
     *                          construct the ingest modules for this pipeline.
     *                          May be an empty list if this type of pipeline is
     *                          not needed for the ingest job.
     */
    IngestPipeline(IngestJobExecutor ingestJobExecutor, List<IngestModuleTemplate> moduleTemplates) {
        this.ingestJobExecutor = ingestJobExecutor;
        this.moduleTemplates = moduleTemplates;
        modules = new ArrayList<>();
    }

    /**
     * Indicates whether or not there are any ingest modules in this ingest
     * pipeline.
     *
     * @return True or false; always true before startUp() is called.
     */
    boolean isEmpty() {
        return modules.isEmpty();
    }

    /**
     * Queries whether or not this ingest pipeline is running, i.e., the
     * startUp() method has been called and the shutDown() method has not been
     * called yet.
     *
     * @return True or false.
     */
    boolean isRunning() {
        return running;
    }

    /**
     * Starts up this ingest pipeline by calling the startUp() methods of the
     * ingest modules in the pipeline.
     *
     * @return A list of ingest module start up errors, possibly empty.
     */
    List<IngestModuleError> startUp() {
        List<IngestModuleError> errors = new ArrayList<>();
        if (!running) {
            /*
             * The creation of ingest modules from the ingest module templates
             * has been deliberately deferred to the startUp() method so that
             * any and all errors in module construction or start up can be
             * reported to the client code.
             */
            createIngestModules();
            errors.addAll(startUpIngestModules());
        } else {
            errors.add(new IngestModuleError("Ingest Task Pipeline", new IngestPipelineException("Pipeline already started"))); //NON-NLS                        
        }
        return errors;
    }

    /**
     * Creates the ingest modules for this ingest pipeline using its ingest
     * module templates.
     */
    private void createIngestModules() {
        if (modules.isEmpty()) {
            for (IngestModuleTemplate template : moduleTemplates) {
                Optional<PipelineModule<T>> module = acceptModuleTemplate(template);
                if (module.isPresent()) {
                    modules.add(module.get());
                }
            }
        }
    }

    /**
     * Determines if one of the types of ingest modules that can be created from
     * a given ingest module template should be added to this ingest pipeline.
     * If so, the ingest module is created and returned.
     *
     * @param template The ingest module template to be used or ignored, as
     *                 appropriate to the pipeline type.
     *
     * @return An Optional that is either empty or contains a newly created
     *         ingest module of type T, wrapped in a PipelineModule decorator.
     */
    abstract Optional<PipelineModule<T>> acceptModuleTemplate(IngestModuleTemplate template);

    /**
     * Starts up the ingest modules in this ingest pipeline.
     *
     * @return A list of ingest module start up errors, possibly empty.
     */
    private List<IngestModuleError> startUpIngestModules() {
        List<IngestModuleError> errors = new ArrayList<>();
        startTime = new Date();
        running = true;
        for (PipelineModule<T> module : modules) {
            try {
                module.startUp(new IngestJobContext(ingestJobExecutor));
            } catch (Throwable ex) {
                /*
                 * A catch-all exception firewall. Start up errors for all of
                 * the ingest modules, whether checked exceptions or runtime
                 * exceptions, are reported to allow correction of all of the
                 * error conditions in one go.
                 */
                errors.add(new IngestModuleError(module.getDisplayName(), ex));
            }
        }
        return errors;
    }

    /**
     * Returns the start up time of this ingest pipeline.
     *
     * @return The start up time, may be null if this pipeline has not been
     *         started yet.
     */
    Date getStartTime() {
        Date reportedStartTime = null;
        if (startTime != null) {
            reportedStartTime = new Date(startTime.getTime());
        }
        return reportedStartTime;
    }

    /**
     * Performs an ingest task by sequentially calling the process() methods of
     * the ingest modules in this ingest pipeline.
     *
     * @param task The task.
     *
     * @return A list of ingest module processing errors, possibly empty.
     */
    List<IngestModuleError> performTask(T task) {
        List<IngestModuleError> errors = new ArrayList<>();
        if (running) {
            if (!ingestJobExecutor.isCancelled()) {
                pauseIfScheduled();
                if (ingestJobExecutor.isCancelled()) {
                    return errors;
                }
                try {
                    prepareForTask(task);
                } catch (IngestPipelineException ex) {
                    errors.add(new IngestModuleError("Ingest Task Pipeline", ex)); //NON-NLS
                    return errors;
                }
                for (PipelineModule<T> module : modules) {
                    pauseIfScheduled();
                    if (ingestJobExecutor.isCancelled()) {
                        break;
                    }
                    try {
                        currentModule = module;
                        currentModule.setProcessingStartTime();
                        module.process(ingestJobExecutor, task);
                    } catch (Throwable ex) {  // Catch-all exception firewall
                        /*
                         * Note that an exception from a module does not stop
                         * processing of the task by the other modules in the
                         * pipeline.
                         */
                        errors.add(new IngestModuleError(module.getDisplayName(), ex));
                    }
                    if (ingestJobExecutor.isCancelled()) {
                        break;
                    }
                }
            }
            try {
                cleanUpAfterTask(task);
            } catch (IngestPipelineException ex) {
                errors.add(new IngestModuleError("Ingest Task Pipeline", ex)); //NON-NLS
            }
        } else {
            errors.add(new IngestModuleError("Ingest Task Pipeline", new IngestPipelineException("Pipeline not started or shut down"))); //NON-NLS                        
        }
        currentModule = null;
        return errors;
    }

    /**
     * Pauses this pipeline if ingest has been configured to be paused weekly at
     * a specified time, for a specified duration. A pipeline can only be paused
     * between calls to module process() methods, i.e., the individual modules
     * themselves cannot be paused in the middle of processing a task.
     */
    private void pauseIfScheduled() {
        if (ScheduledIngestPauseSettings.getPauseEnabled() == true) {
            /*
             * Calculate the date/time for the scheduled pause start by
             * "normalizing" the day of week to the current week and then
             * adjusting the hour and minute to match the scheduled hour and
             * minute.
             */
            LocalDateTime pauseStart = LocalDateTime.now();
            DayOfWeek pauseDayOfWeek = ScheduledIngestPauseSettings.getPauseDayOfWeek();
            while (pauseStart.getDayOfWeek() != pauseDayOfWeek) {
                pauseStart = pauseStart.minusDays(1);
            }
            pauseStart = pauseStart.withHour(ScheduledIngestPauseSettings.getPauseStartTimeHour());
            pauseStart = pauseStart.withMinute(ScheduledIngestPauseSettings.getPauseStartTimeMinute());
            pauseStart = pauseStart.withSecond(0);

            /*
             * Calculate the pause end date/time.
             */
            LocalDateTime pauseEnd = pauseStart.plusMinutes(ScheduledIngestPauseSettings.getPauseDurationMinutes());

            /*
             * Check whether the current date/time is in the pause interval. If
             * it is, register the ingest thread this code is running in so it
             * can be interrupted if the job is canceled, and sleep until
             * whatever time remains in the pause interval has expired.
             */
            LocalDateTime timeNow = LocalDateTime.now();
            if ((timeNow.equals(pauseStart) || timeNow.isAfter(pauseStart)) && timeNow.isBefore(pauseEnd)) {
                ingestJobExecutor.registerPausedIngestThread(Thread.currentThread());
                try {
                    long timeRemainingMillis = ChronoUnit.MILLIS.between(timeNow, pauseEnd);
                    logger.log(Level.INFO, String.format("%s pausing at %s for ~%d minutes", Thread.currentThread().getName(), LocalDateTime.now(), TimeUnit.MILLISECONDS.toMinutes(timeRemainingMillis)));
                    sleep(timeRemainingMillis);
                    logger.log(Level.INFO, String.format("%s resuming at %s", Thread.currentThread().getName(), LocalDateTime.now()));
                } catch (InterruptedException notLogged) {
                    logger.log(Level.INFO, String.format("%s resuming at %s due to sleep interrupt (ingest job canceled)", Thread.currentThread().getName(), LocalDateTime.now()));
                } finally {
                    ingestJobExecutor.unregisterPausedIngestThread(Thread.currentThread());
                }
            }
        }
    }

    /**
     * Does any task-type-specific preparation required before performing an
     * ingest task.
     *
     * @param task The task.
     *
     * @throws IngestPipelineException Thrown if there is an error preparing to
     *                                 perform the task.
     */
    abstract void prepareForTask(T task) throws IngestPipelineException;

    /**
     * Gets the currently running ingest module.
     *
     * @return The module, possibly null, if no module is currently running.
     */
    PipelineModule<T> getCurrentlyRunningModule() {
        return currentModule;
    }

    /**
     * Shuts down all of the ingest modules in this pipeline.
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
     * Does any task-type-specific clean up required after performing an ingest
     * task.
     *
     * @param task The task.
     *
     * @throws IngestPipelineException Thrown if there is an error cleaning up
     *                                 after performing the task.
     */
    abstract void cleanUpAfterTask(T task) throws IngestPipelineException;

    /**
     * An abstract superclass for an ingest module decorator that adds ingest
     * infrastructure operations to an ingest module.
     */
    static abstract class PipelineModule<T extends IngestTask> implements IngestModule {

        private final IngestModule module;
        private final String displayName;
        private volatile Date processingStartTime;

        /**
         * Constructs an instance of an abstract superclass for an ingest module
         * decorator that adds ingest infrastructure operations to an ingest
         * module.
         *
         * @param module      The ingest module to be decorated.
         * @param displayName The display name for the module.
         */
        PipelineModule(IngestModule module, String displayName) {
            this.module = module;
            this.displayName = displayName;
            processingStartTime = new Date();
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

        /**
         * Sets the processing start time for the decorated module to the system
         * time when this method is called.
         */
        void setProcessingStartTime() {
            processingStartTime = new Date();
        }

        /**
         * Gets the the processing start time for the decorated module.
         *
         * @return The start time, not valid if setProcessingStartTime() has not
         *         been called first.
         */
        Date getProcessingStartTime() {
            return new Date(processingStartTime.getTime());
        }

        @Override
        public void startUp(IngestJobContext context) throws IngestModuleException {
            module.startUp(context);
        }

        /**
         * Performs an ingest task using the process() method of the decorated
         * module.
         *
         * @param ingestJobExecutor The ingest job executor that owns the ingest
         *                          pipeline to which this module belongs.
         * @param task              The task to perform.
         *
         * @throws IngestModuleException Exception thrown if there is an error
         *                               performing the task.
         */
        abstract void process(IngestJobExecutor ingestJobExecutor, T task) throws IngestModuleException;

        @Override
        public void shutDown() {
            module.shutDown();
        }

    }

    /**
     * An exception thrown by an ingest pipeline.
     */
    static class IngestPipelineException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception to be thrown by an ingest pipeline.
         *
         * @param message The exception message.
         */
        IngestPipelineException(String message) {
            super(message);
        }

        /**
         * Constructs an exception to be thrown by an ingest pipeline.
         *
         * @param message The exception message.
         * @param cause   The exception cause.
         */
        IngestPipelineException(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
