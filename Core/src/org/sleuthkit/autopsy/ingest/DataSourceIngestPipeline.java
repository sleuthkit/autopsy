/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.Content;

/**
 * A pipeline consisting of a sequence of data source level ingest modules. The
 * pipeline starts the modules, runs them in sequential order, and shuts them
 * down.
 */
final class DataSourceIngestPipeline {

    private static final IngestManager ingestManager = IngestManager.getInstance();
    private final DataSourceIngestJob job;
    private final List<DataSourceIngestModuleDecorator> modules = new ArrayList<>();
    private volatile DataSourceIngestModuleDecorator currentModule;

    /**
     * Constructs a pipeline consisting of a sequence of data source level
     * ingest modules. The pipeline starts the modules, runs them in sequential
     * order, and shuts them down.
     *
     * @param job The ingest job to which this pipeline belongs.
     * @param moduleTemplates The ingest module templates that define the
     * pipeline.
     */
    DataSourceIngestPipeline(DataSourceIngestJob job, List<IngestModuleTemplate> moduleTemplates) {
        this.job = job;

        /**
         * Create a data source level ingest module instance from each ingest
         * module template.
         */
        for (IngestModuleTemplate template : moduleTemplates) {
            if (template.isDataSourceIngestModuleTemplate()) {
                DataSourceIngestModuleDecorator module = new DataSourceIngestModuleDecorator(template.createDataSourceIngestModule(), template.getModuleName());
                modules.add(module);
            }
        }
    }

    /**
     * Indicates whether or not there are any modules in this pipeline.
     *
     * @return True or false.
     */
    boolean isEmpty() {
        return modules.isEmpty();
    }

    /**
     * Starts up the ingest module in this pipeline.
     *
     * @return A list of ingest module startup errors, possibly empty.
     */
    List<IngestModuleError> startUp() {
        List<IngestModuleError> errors = new ArrayList<>();
        for (DataSourceIngestModuleDecorator module : modules) {
            try {
                module.startUp(new IngestJobContext(this.job));
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
     * be processed.
     * @return A list of ingest module errors, possible empty.
     */
    List<IngestModuleError> process(DataSourceIngestTask task) {
        List<IngestModuleError> errors = new ArrayList<>();
        Content dataSource = task.getDataSource();
        for (DataSourceIngestModuleDecorator module : modules) {
            try {
                module.setStartTime();
                this.currentModule = module;
                String displayName = NbBundle.getMessage(this.getClass(),
                        "IngestJob.progress.dataSourceIngest.displayName",
                        module.getDisplayName(), dataSource.getName());
                this.job.updateDataSourceIngestProgressBarDisplayName(displayName);
                this.job.switchDataSourceIngestProgressBarToIndeterminate();
                ingestManager.setIngestTaskProgress(task, module.getDisplayName());
                module.process(dataSource, new DataSourceIngestModuleProgress(this.job));
            } catch (Throwable ex) { // Catch-all exception firewall
                errors.add(new IngestModuleError(module.getDisplayName(), ex));
            }
            if (this.job.isCancelled()) {
                break;
            } else if (this.job.currentDataSourceIngestModuleIsCancelled()) {
                this.job.currentDataSourceIngestModuleCancellationCompleted();
            }
        }
        this.currentModule = null;
        ingestManager.setIngestTaskProgressCompleted(task);
        return errors;
    }

    /**
     * Gets the currently running module.
     */
    DataSourceIngestModuleDecorator getCurrentlyRunningModule() {
        return this.currentModule;
    }
    
    /**
     * This class decorates a data source level ingest module with a display
     * name and a start time.
     */
    static class DataSourceIngestModuleDecorator implements DataSourceIngestModule {

        private final DataSourceIngestModule module;
        private final String displayName;
        private Date startTime;

        /**
         * Constructs an object that decorates a data source level ingest module
         * with a display name and a running time.
         *
         * @param module The data source level ingest module to be decorated.
         * @param displayName
         */
        DataSourceIngestModuleDecorator(DataSourceIngestModule module, String displayName) {
            this.module = module;
            this.displayName = displayName;
            this.startTime = new Date();
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
         * Gets a module name suitable for display in a UI.
         *
         * @return The display name.
         */
        String getDisplayName() {
            return this.displayName;
        }
        
        /**
         * Sets the start time to the current time.
         */
        void setStartTime() {
            this.startTime = new Date();
        }
        
        /**
         * Gets the time the decorated ingest module started processing the data
         * source.
         *
         * @return The start time.
         */
        Date getStartTime() {
            return this.startTime;
        }

        /**
         * @inheritDoc
         */
        @Override
        public void startUp(IngestJobContext context) throws IngestModuleException {
            this.module.startUp(context);
        }

        /**
         * @inheritDoc
         */
        @Override
        public IngestModule.ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
            this.startTime = new Date();
            return this.module.process(dataSource, statusHelper);
        }
        
    }
    
}
