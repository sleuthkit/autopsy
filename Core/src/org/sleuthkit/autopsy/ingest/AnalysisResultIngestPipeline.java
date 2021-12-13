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
import org.sleuthkit.datamodel.AnalysisResult;

/**
 * A pipeline of analysis result ingest modules used to perform analysis result
 * ingest tasks for an ingest job.
 */
public class AnalysisResultIngestPipeline extends IngestPipeline<AnalysisResultIngestTask> {

    /**
     * Constructs a pipeline of analysis result ingest modules used to perform
     * analysis result ingest tasks for an ingest job.
     *
     * @param ingestJobExecutor The ingest job executor for this pipeline.
     * @param moduleTemplates   The ingest module templates to be used to
     *                          construct the ingest modules for this pipeline.
     *                          May be an empty list if this type of pipeline is
     *                          not needed for the ingest job.
     */
    AnalysisResultIngestPipeline(IngestJobExecutor ingestJobExecutor, List<IngestModuleTemplate> moduleTemplates) {
        super(ingestJobExecutor, moduleTemplates);
    }

    @Override
    Optional<PipelineModule<AnalysisResultIngestTask>> acceptModuleTemplate(IngestModuleTemplate template) {
        Optional<IngestPipeline.PipelineModule<AnalysisResultIngestTask>> module = Optional.empty();
        if (template.isAnalysisResultIngestModuleTemplate()) {
            AnalysisResultIngestModule ingestModule = template.createAnalysisResultIngestModule();
            module = Optional.of(new AnalysisResultIngestPipelineModule(ingestModule, template.getModuleName()));
        }
        return module;
    }

    @Override
    void prepareForTask(AnalysisResultIngestTask task) throws IngestPipelineException {
    }

    @Override
    void cleanUpAfterTask(AnalysisResultIngestTask task) throws IngestPipelineException {
        IngestManager.getInstance().setIngestTaskProgressCompleted(task);
    }

    /**
     * A decorator that adds ingest infrastructure operations to an analysis
     * result ingest module.
     */
    static final class AnalysisResultIngestPipelineModule extends IngestPipeline.PipelineModule<AnalysisResultIngestTask> {

        private final AnalysisResultIngestModule module;

        /**
         * Constructs a decorator that adds ingest infrastructure operations to
         * an analysis result ingest module.
         *
         * @param module      The module.
         * @param displayName The display name of the module.
         */
        AnalysisResultIngestPipelineModule(AnalysisResultIngestModule module, String displayName) {
            super(module, displayName);
            this.module = module;
        }

        @Override
        void process(IngestJobExecutor ingestJobExecutor, AnalysisResultIngestTask task) throws IngestModule.IngestModuleException {
            AnalysisResult result = task.getAnalysisResult();
            IngestManager.getInstance().setIngestTaskProgress(task, getDisplayName());
            module.process(result);
        }

    }
    
}
