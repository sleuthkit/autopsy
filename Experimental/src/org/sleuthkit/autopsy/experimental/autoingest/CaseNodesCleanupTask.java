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
import java.util.concurrent.Callable;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeDataCollector;
import org.sleuthkit.autopsy.casemodule.multiusercases.CoordinationServiceUtils;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * Task for cleaning up case coordination service nodes for which there is no
 * longer a corresponding case.
 */
final class CaseNodesCleanupTask implements Runnable {

    private static final Logger logger = AutoIngestDashboardLogger.getLogger();
    private final ProgressIndicator progress;

    /**
     * Constucts an instance of a task for cleaning up case coordination service
     * nodes for which there is no longer a corresponding case.
     *
     * @param progress
     */
    CaseNodesCleanupTask(ProgressIndicator progress) {
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

        List<CaseNodeData> nodeDataList;
        try {
            nodeDataList = CaseNodeDataCollector.getNodeData();
        } catch (CoordinationService.CoordinationServiceException ex) {
            logger.log(Level.WARNING, "Error collecting case node data", ex); // NON-NLS
            return;
        } catch (InterruptedException ex) {
            logger.log(Level.WARNING, "Unexpected interrupt while collecting case node data", ex); // NON-NLS
            return;
        }

        for (CaseNodeData nodeData : nodeDataList) {
            final Path caseDirectoryPath = nodeData.getDirectory();
            final File caseDirectory = caseDirectoryPath.toFile();
            if (!caseDirectory.exists()) {
                String caseName = nodeData.getDisplayName();
                String nodePath = ""; // NON-NLS
                try {
                    nodePath = CoordinationServiceUtils.getCaseNameNodePath(caseDirectoryPath);
                    deleteNode(coordinationService, caseName, nodePath);

                    nodePath = CoordinationServiceUtils.getCaseResourcesNodePath(caseDirectoryPath);
                    deleteNode(coordinationService, caseName, nodePath);

                    nodePath = CoordinationServiceUtils.getCaseAutoIngestLogNodePath(caseDirectoryPath);
                    deleteNode(coordinationService, caseName, nodePath);

                    nodePath = CoordinationServiceUtils.getCaseDirectoryNodePath(caseDirectoryPath);
                    deleteNode(coordinationService, caseName, nodePath);

                } catch (InterruptedException ex) {
                    logger.log(Level.WARNING, String.format("Unexpected interrupt while deleting znode %s for %s", nodePath, caseName), ex);  // NON-NLS
                    return;
                }
            }
        }
    }

    /**
     * Attempts to delete a case coordination service node.
     *
     * @param coordinationService The ccordination service.
     * @param caseName            The case name.
     * @param nodePath            The path of the node to delete.
     *
     * @throws InterruptedException If the thread executing this task is
     *                              interrupted during the delete operation.
     */
    private static void deleteNode(CoordinationService coordinationService, String caseName, String nodePath) throws InterruptedException {
        try {
            coordinationService.deleteNode(CoordinationService.CategoryNode.CASES, nodePath);
        } catch (CoordinationService.CoordinationServiceException ex) {
            if (!DeleteCaseUtils.isNoNodeException(ex)) {
                logger.log(Level.SEVERE, String.format("Error deleting %s znode for %s", nodePath, caseName), ex);  // NON-NLS
            }
        }
    }

}
