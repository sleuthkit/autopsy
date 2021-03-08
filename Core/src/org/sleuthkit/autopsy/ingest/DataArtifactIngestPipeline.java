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

import java.util.List;
import java.util.Optional;

/**
 * A pipeline of data artifact ingest modules for performing data artifact
 * ingest tasks for an ingest job.
 */
final class DataArtifactIngestPipeline extends IngestTaskPipeline<DataArtifactIngestTask> {

    private final IngestPipeline ingestJobPipeline;

    /**
     * Constructs a pipeline of data artifact ingest modules for performing data
     * artifact ingest tasks for an ingest job.
     *
     * @param ingestJobPipeline The ingest job pipeline that owns this pipeline.
     * @param moduleTemplates   The ingest module templates that define this
     *                          pipeline.
     */
    DataArtifactIngestPipeline(IngestPipeline ingestJobPipeline, List<IngestModuleTemplate> moduleTemplates) {
        super(ingestJobPipeline, moduleTemplates);
        this.ingestJobPipeline = ingestJobPipeline;
    }

    @Override
    Optional<PipelineModule<DataArtifactIngestTask>> acceptModuleTemplate(IngestModuleTemplate ingestModuleTemplate) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    void prepareTask(DataArtifactIngestTask task) throws IngestTaskPipelineException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    void completeTask(DataArtifactIngestTask task) throws IngestTaskPipelineException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * A wrapper that adds ingest infrastructure operations to a data artifact
     * ingest module.
     */
    static final class DataArtifactIngestPipelineModule extends IngestTaskPipeline.PipelineModule<FileIngestTask> {

        private final DataArtifactIngestModule module;

        /**
         * Constructs a wrapper that adds ingest infrastructure operations to a
         * file ingest module.
         *
         *
         * @param module      The module.
         * @param displayName The display name of the module.
         */
        DataArtifactIngestPipelineModule(DataArtifactIngestModule module, String displayName) {
            super(module, displayName);
            this.module = module;
        }

        /**
         * RJCTODO
         *
         * @param ingestJobPipeline
         * @param task
         *
         * @throws
         * org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
         */
        @Override
        void performTask(IngestPipeline ingestJobPipeline, FileIngestTask task) throws IngestModule.IngestModuleException {
            // RJCTODO: Fill in and change to executeTask()
        }

    }

}
