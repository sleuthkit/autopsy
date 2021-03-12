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
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A pipeline of data artifact ingest modules for performing data artifact
 * ingest tasks for an ingest job.
 */
final class DataArtifactIngestPipeline extends IngestTaskPipeline<DataArtifactIngestTask> {

    private static final Logger logger = Logger.getLogger(IngestJobPipeline.class.getName());

    /**
     * Constructs a pipeline of data artifact ingest modules for performing data
     * artifact ingest tasks for an ingest job.
     *
     * @param ingestJobPipeline The ingest job pipeline that owns this pipeline.
     * @param moduleTemplates   The ingest module templates that define this
     *                          pipeline.
     */
    DataArtifactIngestPipeline(IngestJobPipeline ingestJobPipeline, List<IngestModuleTemplate> moduleTemplates) {
        super(ingestJobPipeline, moduleTemplates);
    }

    @Override
    Optional<PipelineModule<DataArtifactIngestTask>> acceptModuleTemplate(IngestModuleTemplate template) {
        Optional<IngestTaskPipeline.PipelineModule<DataArtifactIngestTask>> module = Optional.empty();
        if (template.isDataArtifactIngestModuleTemplate()) {
            DataArtifactIngestModule ingestModule = template.createDataArtifactIngestModule();
            module = Optional.of(new DataArtifactIngestPipelineModule(ingestModule, template.getModuleName()));
        }
        return module;
    }

    @Override
    void prepareForTask(DataArtifactIngestTask task) throws IngestTaskPipelineException {
    }

    @Override
    void cleanUpAfterTask(DataArtifactIngestTask task) throws IngestTaskPipelineException {
    }

    /**
     * A wrapper that adds ingest infrastructure operations to a data artifact
     * ingest module.
     */
    static final class DataArtifactIngestPipelineModule extends IngestTaskPipeline.PipelineModule<DataArtifactIngestTask> {

        private final DataArtifactIngestModule module;

        /**
         * Constructs a wrapper that adds ingest infrastructure operations to a
         * data artifact ingest module.
         *
         * @param module      The module.
         * @param displayName The display name of the module.
         */
        DataArtifactIngestPipelineModule(DataArtifactIngestModule module, String displayName) {
            super(module, displayName);
            this.module = module;
        }

        @Override
        void executeTask(IngestJobPipeline ingestJobPipeline, DataArtifactIngestTask task) throws IngestModuleException {
            DataArtifact artifact = task.getDataArtifact();
            ProcessResult result = module.process(artifact);
            if (result == ProcessResult.ERROR) {
                throw new IngestModuleException(String.format("%s experienced an error analyzing %s artifact (artifact object ID = %d, source object ID = %d)", getDisplayName(), artifact.getDisplayName(), artifact.getId(), artifact.getObjectID())); //NON-NLS
            }
        }

    }

}
