/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021-2021 Basis Technology Corp.
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
import org.sleuthkit.datamodel.DataArtifact;

/**
 * A pipeline of data artifact ingest modules used to perform data artifact
 * ingest tasks for an ingest job.
 */
final class DataArtifactIngestPipeline extends IngestPipeline<DataArtifactIngestTask> {

    /**
     * Constructs a pipeline of data artifact ingest modules used to perform
     * data artifact ingest tasks for an ingest job.
     *
     * @param ingestJobExecutor The ingest job executor for this pipeline.
     * @param moduleTemplates   The ingest module templates to be used to
     *                          construct the ingest modules for this pipeline.
     *                          May be an empty list if this type of pipeline is
     *                          not needed for the ingest job.
     */
    DataArtifactIngestPipeline(IngestJobExecutor ingestJobExecutor, List<IngestModuleTemplate> moduleTemplates) {
        super(ingestJobExecutor, moduleTemplates);
    }

    @Override
    Optional<PipelineModule<DataArtifactIngestTask>> acceptModuleTemplate(IngestModuleTemplate template) {
        Optional<IngestPipeline.PipelineModule<DataArtifactIngestTask>> module = Optional.empty();
        if (template.isDataArtifactIngestModuleTemplate()) {
            DataArtifactIngestModule ingestModule = template.createDataArtifactIngestModule();
            module = Optional.of(new DataArtifactIngestPipelineModule(ingestModule, template.getModuleName()));
        }
        return module;
    }

    @Override
    void prepareForTask(DataArtifactIngestTask task) throws IngestPipelineException {
    }

    @Override
    void cleanUpAfterTask(DataArtifactIngestTask task) throws IngestPipelineException {
        IngestManager.getInstance().setIngestTaskProgressCompleted(task);
    }

    /**
     * A decorator that adds ingest infrastructure operations to a data artifact
     * ingest module.
     */
    static final class DataArtifactIngestPipelineModule extends IngestPipeline.PipelineModule<DataArtifactIngestTask> {

        private final DataArtifactIngestModule module;

        /**
         * Constructs a decorator that adds ingest infrastructure operations to
         * a data artifact ingest module.
         *
         * @param module      The module.
         * @param displayName The display name of the module.
         */
        DataArtifactIngestPipelineModule(DataArtifactIngestModule module, String displayName) {
            super(module, displayName);
            this.module = module;
        }

        @Override
        void process(IngestJobExecutor ingestJobExecutor, DataArtifactIngestTask task) throws IngestModuleException {
            DataArtifact artifact = task.getDataArtifact();
            IngestManager.getInstance().setIngestTaskProgress(task, getDisplayName());
            module.process(artifact);
        }

    }

}
