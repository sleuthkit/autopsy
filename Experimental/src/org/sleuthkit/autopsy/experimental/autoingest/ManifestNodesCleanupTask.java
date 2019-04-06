/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * A task class for cleaning up auto ingest job coordination service nodes for
 * which there is no longer a corresponding manifest file.
 */
final class ManifestNodesCleanupTask implements Runnable {

    private static final Logger logger = Logger.getLogger(ManifestNodesCleanupTask.class.getName());
    private final ProgressIndicator progress;

    /**
     * Constucts an instance of a task for cleaning up case coordination service
     * nodes for which there is no longer a corresponding case.
     *
     * @param progress
     */
    ManifestNodesCleanupTask(ProgressIndicator progress) {
        this.progress = progress;
    }

    @Override
    public void run() {

        CoordinationService coordinationService;
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationService.CoordinationServiceException ex) {
            logger.log(Level.WARNING, "Error connecting to the coordination service", ex); // NON-NLS
            return;
        }

        List<AutoIngestJobNodeData> nodeDataList;
        try {
            nodeDataList = AutoIngestJobNodeDataCollector.getNodeData();
        } catch (CoordinationService.CoordinationServiceException ex) {
            logger.log(Level.WARNING, "Error collecting auto ingest job node data", ex); // NON-NLS
            return;
        } catch (InterruptedException ex) {
            logger.log(Level.WARNING, "Unexpected interrupt while collecting auot ingest job node data", ex); // NON-NLS
            return;
        }

        for (AutoIngestJobNodeData nodeData : nodeDataList) {
            final String caseName = nodeData.getCaseName();
            final Path manifestFilePath = nodeData.getManifestFilePath();
            final File manifestFile = manifestFilePath.toFile();
            if (!manifestFile.exists()) {
                try {
                    coordinationService.deleteNode(CoordinationService.CategoryNode.MANIFESTS, manifestFilePath.toString());
                } catch (CoordinationService.CoordinationServiceException ex) {
                    if (!DeleteCaseUtils.isNoNodeException(ex)) {
                        logger.log(Level.SEVERE, String.format("Error deleting %s znode for %s", manifestFilePath, caseName), ex);  // NON-NLS
                    }
                } catch (InterruptedException ex) {
                    logger.log(Level.WARNING, String.format("Unexpected interrupt while deleting %s znode for %s", manifestFilePath, caseName), ex);  // NON-NLS
                    return;
                }
            }
        }
    }

}
