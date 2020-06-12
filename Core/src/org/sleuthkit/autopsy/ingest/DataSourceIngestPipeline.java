/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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
import org.sleuthkit.datamodel.Content;

/**
 * This class manages a sequence of data source level ingest modules for an
 *  ingestJobPipeline. It starts the modules, runs data sources through them, and
 * shuts them down when data source level ingest is complete.
 * <p>
 * This class is thread-safe.
 */
final class DataSourceIngestPipeline {

    private static final IngestManager ingestManager = IngestManager.getInstance();
    private static final Logger logger = Logger.getLogger(DataSourceIngestPipeline.class.getName());
    private final IngestJobPipeline ingestJobPipeline;
    private final List<PipelineModule> modules = new ArrayList<>();
    private volatile PipelineModule currentModule;

    /**
     * Constructs an object that manages a sequence of data source level ingest
     * modules. It starts the modules, runs data sources through them, and shuts
     * them down when data source level ingest is complete.
     *
     * @param ingestJobPipeline  The ingestJobPipeline that owns this pipeline.
     * @param moduleTemplates Templates for the creating the ingest modules that
     *                        make up this pipeline.
     */
    DataSourceIngestPipeline(IngestJobPipeline ingestJobPipeline, List<IngestModuleTemplate> moduleTemplates) {
        this.ingestJobPipeline = ingestJobPipeline;
        for (IngestModuleTemplate template : moduleTemplates) {
            if (template.isDataSourceIngestModuleTemplate()) {
                PipelineModule module = new PipelineModule(template.createDataSourceIngestModule(), template.getModuleName());
                modules.add(module);
            }
        }
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
     * Starts up the ingest modules in this pipeline.
     *
     * @return A list of ingest module startup errors, possibly empty.
     */
    synchronized List<IngestModuleError> startUp() {
        List<IngestModuleError> errors = new ArrayList<>();
        for (PipelineModule module : modules) {
            try {
                module.startUp(new IngestJobContext(this.ingestJobPipeline));
            } catch (Throwable ex) { // Catch-all exception firewall
                errors.add(new IngestModuleError(module.getDisplayName(), ex));
            }
        }
        return errors;
    }

    /**
     * Runs a data source through the ingest modules in sequential order.
     *
     * @param task A data source level ingest task containing a data source to
     *             be processed.
     *
     * @return A list of processing errors, possible empty.
     */
    synchronized List<IngestModuleError> process(DataSourceIngestTask task) {
        List<IngestModuleError> errors = new ArrayList<>();
        if (!this.ingestJobPipeline.isCancelled()) {
            Content dataSource = task.getDataSource();
            for (PipelineModule module : modules) {
                try {
                    this.currentModule = module;
                    String displayName = NbBundle.getMessage(this.getClass(),
                            "IngestJob.progress.dataSourceIngest.displayName",
                            module.getDisplayName(), dataSource.getName());
                    this.ingestJobPipeline.updateDataSourceIngestProgressBarDisplayName(displayName);
                    this.ingestJobPipeline.switchDataSourceIngestProgressBarToIndeterminate();
                    DataSourceIngestPipeline.ingestManager.setIngestTaskProgress(task, module.getDisplayName());
                    logger.log(Level.INFO, "{0} analysis of {1} (pipeline={2}) starting", new Object[]{module.getDisplayName(), ingestJobPipeline.getDataSource().getName(), ingestJobPipeline.getId()}); //NON-NLS
                    module.process(dataSource, new DataSourceIngestModuleProgress(this.ingestJobPipeline));
                    logger.log(Level.INFO, "{0} analysis of {1} (pipeline={2}) finished", new Object[]{module.getDisplayName(), ingestJobPipeline.getDataSource().getName(), ingestJobPipeline.getId()}); //NON-NLS
                } catch (Throwable ex) { // Catch-all exception firewall
                    errors.add(new IngestModuleError(module.getDisplayName(), ex));
                }
                if (this.ingestJobPipeline.isCancelled()) {
                    break;
                } else if (this.ingestJobPipeline.currentDataSourceIngestModuleIsCancelled()) {
                    this.ingestJobPipeline.currentDataSourceIngestModuleCancellationCompleted(currentModule.getDisplayName());
                }
            }
        }
        this.currentModule = null;
        ingestManager.setIngestTaskProgressCompleted(task);
        return errors;
    }

    /**
     * Gets the currently running module.
     *
     * @return The module, possibly null if no module is currently running.
     */
    PipelineModule getCurrentlyRunningModule() {
        return this.currentModule;
    }

    /**
     * This class decorates a data source level ingest module with a display
     * name and a processing start time.
     */
    static class PipelineModule implements DataSourceIngestModule {

        private final DataSourceIngestModule module;
        private final String displayName;
        private volatile Date processingStartTime;

        /**
         * Constructs an object that decorates a data source level ingest module
         * with a display name and a processing start time.
         *
         * @param module      The data source level ingest module to be
         *                    decorated.
         * @param displayName The display name.
         */
        PipelineModule(DataSourceIngestModule module, String displayName) {
            this.module = module;
            this.displayName = displayName;
            this.processingStartTime = new Date();
        }

        /**
         * Gets the class name of the decorated ingest module.
         *
         * @return The class name.
         */
        String getClassName() {
            return this.module.getClass().getCanonicalName();
        }

        /**
         * Gets the display of the decorated ingest module.
         *
         * @return The display name.
         */
        String getDisplayName() {
            return this.displayName;
        }

        /**
         * Gets the time the decorated ingest module started processing the data
         * source.
         *
         * @return The start time, will be null if the module has not started
         *         processing the data source yet.
         */
        Date getProcessingStartTime() {
            return this.processingStartTime;
        }

        @Override
        public void startUp(IngestJobContext context) throws IngestModuleException {
            this.module.startUp(context);
        }

        @Override
        public IngestModule.ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
            this.processingStartTime = new Date();
            return this.module.process(dataSource, statusHelper);
        }

    }

}
