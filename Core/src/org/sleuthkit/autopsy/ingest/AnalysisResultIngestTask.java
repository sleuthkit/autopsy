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

import org.sleuthkit.datamodel.AnalysisResult;

/**
 * An analysis result ingest task that will be executed by an ingest thread
 * using a given ingest job executor.
 */
final class AnalysisResultIngestTask extends IngestTask {

    private final AnalysisResult analysisResult;

    /**
     * Constructs an analysis result ingest task that will be executed by an
     * ingest thread using a given ingest job executor.
     *
     * @param ingestJobExecutor The ingest job executor to use to execute the
     *                          task.
     * @param analysisResult    The analysis result to be processed.
     */
    AnalysisResultIngestTask(IngestJobExecutor ingestJobExecutor, AnalysisResult analysisResult) {
        super(analysisResult.getName(), ingestJobExecutor);
        this.analysisResult = analysisResult;
    }

    /**
     * Gets the analysis result for this task.
     *
     * @return The analysis result.
     */
    AnalysisResult getAnalysisResult() {
        return analysisResult;
    }

    @Override
    void execute(long threadId) {
        super.setThreadId(threadId);
        getIngestJobExecutor().execute(this);
    }

}
