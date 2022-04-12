/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.cleanup;

import java.nio.file.Path;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * Interface to perform automated cleanup of auto ingest input and output directories,
 * as well as ZK nodes.
 */
public interface AutoIngestCleanup {
    
        /**
     * Options to support implementing different case deletion use cases.
     */
    public enum DeleteOptions {
        /**
         * Delete the auto ingest job manifests and corresponding data sources,
         * while leaving the manifest file coordination service nodes and the
         * rest of the case intact. The use case is freeing auto ingest input
         * directory space while retaining the option to restore the data
         * sources, effectively restoring the case.
         */
        DELETE_INPUT,
        /**
         * Delete the manifest file coordination service nodes and the output
         * for a case, while leaving the auto ingest job manifests and
         * corresponding data sources intact. The use case is auto ingest
         * reprocessing of a case with a clean slate without having to restore
         * the manifests and data sources.
         */
        DELETE_OUTPUT,
        /**
         * Delete everything.
         */
        DELETE_INPUT_AND_OUTPUT,
        /**
         * Delete only the case components that the application created. This is
         * DELETE_OUTPUT with the additional feature that manifest file
         * coordination service nodes are marked as deleted, rather than
         * actually deleted. This eliminates the requirement that manifests and
         * data sources have to be deleted before deleting the case to avoid an
         * unwanted, automatic reprocessing of the case.
         */
        DELETE_CASE
    }
    
    void runCleanupTask(Path caseOutputDirectoryPath, DeleteOptions deleteOption, ProgressIndicator progress);
}
