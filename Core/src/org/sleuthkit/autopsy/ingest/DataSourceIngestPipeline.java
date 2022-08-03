/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2021 Basis Technology Corp.
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

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;

/**
 * A pipeline of data source level ingest modules for executing data source
 * level ingest tasks for an ingest job.
 */
final class DataSourceIngestPipeline extends IngestPipeline<DataSourceIngestTask> {

    private static final Logger logger = Logger.getLogger(DataSourceIngestPipeline.class.getName());
    private static final IngestManager ingestManager = IngestManager.getInstance();

    /**
     * Constructs a pipeline of data source level ingest modules for performing
     * data source level ingest tasks for an ingest job.
     *
     * @param ingestJobExecutor The ingest job executor for this pipeline.
     * @param moduleTemplates   The ingest module templates to be used to
     *                          construct the ingest modules for this pipeline.
     *                          May be an empty list if this type of pipeline is
     *                          not needed for the ingest job.
     */
    DataSourceIngestPipeline(IngestJobExecutor ingestJobExecutor, List<IngestModuleTemplate> moduleTemplates) {
        super(ingestJobExecutor, moduleTemplates);
    }

    @Override
    Optional<IngestPipeline.PipelineModule<DataSourceIngestTask>> acceptModuleTemplate(IngestModuleTemplate template) {
        Optional<IngestPipeline.PipelineModule<DataSourceIngestTask>> module = Optional.empty();
        if (template.isDataSourceIngestModuleTemplate()) {
            DataSourceIngestModule ingestModule = template.createDataSourceIngestModule();
            module = Optional.of(new DataSourcePipelineModule(ingestModule, template.getModuleName()));
        }
        return module;
    }

    @Override
    void prepareForTask(DataSourceIngestTask task) {
    }

    @Override
    void cleanUpAfterTask(DataSourceIngestTask task) {
        ingestManager.setIngestTaskProgressCompleted(task);
    }

    /**
     * A wrapper that adds ingest infrastructure operations to a data source
     * level ingest module.
     */
    static final class DataSourcePipelineModule extends IngestPipeline.PipelineModule<DataSourceIngestTask> {

        private final DataSourceIngestModule module;

        /**
         * Constructs a wrapper that adds ingest infrastructure operations to a
         * data source level ingest module.
         */
        DataSourcePipelineModule(DataSourceIngestModule module, String displayName) {
            super(module, displayName);
            this.module = module;
        }

        @Override
        void process(IngestJobExecutor ingestJobExecutor, DataSourceIngestTask task) throws IngestModuleException {
            Content dataSource = task.getDataSource();
            String progressBarDisplayName = NbBundle.getMessage(this.getClass(), "IngestJob.progress.dataSourceIngest.displayName", getDisplayName(), dataSource.getName());
            ingestJobExecutor.changeDataSourceIngestProgressBarTitle(progressBarDisplayName);
            ingestJobExecutor.switchDataSourceIngestProgressBarToIndeterminate();
            ingestManager.setIngestTaskProgress(task, getDisplayName());
            logger.log(Level.INFO, "{0} analysis of {1} starting", new Object[]{getDisplayName(), dataSource.getName()}); //NON-NLS
            module.process(dataSource, new DataSourceIngestModuleProgress(ingestJobExecutor));
            logger.log(Level.INFO, "{0} analysis of {1} finished", new Object[]{getDisplayName(), dataSource.getName()}); //NON-NLS            
            if (!ingestJobExecutor.isCancelled() && ingestJobExecutor.currentDataSourceIngestModuleIsCancelled()) {
                ingestJobExecutor.currentDataSourceIngestModuleCancellationCompleted(getDisplayName());
            }
        }

    }

}
