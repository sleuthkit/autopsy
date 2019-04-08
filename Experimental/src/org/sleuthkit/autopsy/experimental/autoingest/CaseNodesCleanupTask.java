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
import org.openide.util.NbBundle;
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
    @NbBundle.Messages({
        "CaseNodesCleanupTask.progress.startMessage=Starting orphaned case znode cleanup...",
        "CaseNodesCleanupTask.progress.connectingToCoordSvc=Connecting to the coordination service...",
        "CaseNodesCleanupTask.progress.gettingCaseNodesListing=Querying coordination service for case znodes..."
    })
    public void run() {
        progress.start(Bundle.CaseNodesCleanupTask_progress_startMessage());
        try {
            progress.progress(Bundle.CaseNodesCleanupTask_progress_connectingToCoordSvc());
            logger.log(Level.INFO, "Connecting to the coordination service for orphan case node clean up");  // NON-NLS
            CoordinationService coordinationService;
            try {
                coordinationService = CoordinationService.getInstance();
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.WARNING, "Error connecting to the coordination service", ex); // NON-NLS
                return;
            }

            progress.progress(Bundle.CaseNodesCleanupTask_progress_gettingCaseNodesListing());
            logger.log(Level.INFO, "Querying coordination service for case nodes for orphaned case node clean up");  // NON-NLS
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
                        logger.log(Level.WARNING, String.format("Unexpected interrupt while deleting orphaned znode %s for %s", nodePath, caseName), ex); // NON-NLS
                        return;
                    }
                }
            }
        } catch (Exception ex) {
            /*
             * This is an unexpected runtime exceptions firewall. It is here
             * because this task is designed to be able to be run in scenarios
             * where there is no call to get() on a Future<Void> associated with
             * the task, so this ensures that any such errors get logged.
             */
            logger.log(Level.SEVERE, "Unexpected error during orphan case znode cleanup", ex); // NON-NLS
            throw ex;
            
        } finally {
            progress.finish();
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
    @NbBundle.Messages({
        "# {0} - node path", "CaseNodesCleanupTask.progress.deletingOrphanedCaseNode=Deleting orphaned case node {0}..."
    })
    private void deleteNode(CoordinationService coordinationService, String caseName, String nodePath) throws InterruptedException {
        try {
            progress.progress(Bundle.CaseNodesCleanupTask_progress_deletingOrphanedCaseNode(nodePath));
            logger.log(Level.INFO, String.format("Deleting orphaned case node %s for %s", nodePath, caseName));  // NON-NLS
            coordinationService.deleteNode(CoordinationService.CategoryNode.CASES, nodePath);
        } catch (CoordinationService.CoordinationServiceException ex) {
            if (!DeleteCaseUtils.isNoNodeException(ex)) {
                logger.log(Level.SEVERE, String.format("Error deleting orphaned case node %s for %s", nodePath, caseName), ex);  // NON-NLS
            }
        }
    }

}
