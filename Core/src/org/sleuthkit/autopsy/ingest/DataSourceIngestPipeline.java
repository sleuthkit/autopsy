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
 * A pipeline of data source level ingest modules for performing data source
 * level ingest tasks for an ingest job.
 */
final class DataSourceIngestPipeline extends IngestTaskPipeline<DataSourceIngestTask> {

    private static final Logger logger = Logger.getLogger(DataSourceIngestPipeline.class.getName());
    private static final IngestManager ingestManager = IngestManager.getInstance();

    /**
     * Constructs a pipeline of data source level ingest modules for performing
     * data source level ingest tasks for an ingest job.
     *
     * @param ingestJobPipeline The ingest job pipeline that owns this pipeline.
     * @param moduleTemplates   The ingest module templates that define this
     *                          pipeline.
     */
    DataSourceIngestPipeline(IngestJobPipeline ingestJobPipeline, List<IngestModuleTemplate> moduleTemplates) {
        super(ingestJobPipeline, moduleTemplates);
    }

    @Override
    Optional<IngestTaskPipeline.PipelineModule<DataSourceIngestTask>> acceptModuleTemplate(IngestModuleTemplate template) {
        Optional<IngestTaskPipeline.PipelineModule<DataSourceIngestTask>> module = Optional.empty();
        if (template.isDataSourceIngestModuleTemplate()) {
            DataSourceIngestModule ingestModule = template.createDataSourceIngestModule();
            module = Optional.of(new DataSourcePipelineModule(ingestModule, template.getModuleName()));
        }
        return module;
    }

    @Override
    void prepareTask(DataSourceIngestTask task) {
    }

    @Override
    void completeTask(DataSourceIngestTask task) {
        ingestManager.setIngestTaskProgressCompleted(task);
    }

    /**
     * A wrapper that adds ingest infrastructure operations to a data source
     * level ingest module.
     */
    static final class DataSourcePipelineModule extends IngestTaskPipeline.PipelineModule<DataSourceIngestTask> {

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
        void performTask(IngestJobPipeline ingestJobPipeline, DataSourceIngestTask task) throws IngestModuleException {
            Content dataSource = task.getDataSource();
            String progressBarDisplayName = NbBundle.getMessage(this.getClass(), "IngestJob.progress.dataSourceIngest.displayName", getDisplayName(), dataSource.getName());
            ingestJobPipeline.updateDataSourceIngestProgressBarDisplayName(progressBarDisplayName);
            ingestJobPipeline.switchDataSourceIngestProgressBarToIndeterminate();
            ingestManager.setIngestTaskProgress(task, getDisplayName());
            logger.log(Level.INFO, "{0} analysis of {1} starting", new Object[]{getDisplayName(), dataSource.getName()}); //NON-NLS
            ProcessResult result = module.process(dataSource, new DataSourceIngestModuleProgress(ingestJobPipeline));
            logger.log(Level.INFO, "{0} analysis of {1} finished", new Object[]{getDisplayName(), dataSource.getName()}); //NON-NLS            
            if (!ingestJobPipeline.isCancelled() && ingestJobPipeline.currentDataSourceIngestModuleIsCancelled()) {
                ingestJobPipeline.currentDataSourceIngestModuleCancellationCompleted(getDisplayName());
            }
            // See JIRA-7449            
//            if (result == ProcessResult.ERROR) {
//                throw new IngestModuleException(String.format("%s experienced an error analyzing %s (data source objId = %d)", getDisplayName(), dataSource.getName(), dataSource.getId())); //NON-NLS
//            }            
        }

    }

}
