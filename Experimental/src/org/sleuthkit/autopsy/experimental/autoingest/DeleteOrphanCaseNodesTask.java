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
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.multiusercases.CoordinationServiceUtils;
import static org.sleuthkit.autopsy.casemodule.multiusercases.CoordinationServiceUtils.isCaseAutoIngestLogNodePath;
import static org.sleuthkit.autopsy.casemodule.multiusercases.CoordinationServiceUtils.isCaseNameNodePath;
import static org.sleuthkit.autopsy.casemodule.multiusercases.CoordinationServiceUtils.isCaseResourcesNodePath;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * Task for deleting case coordination service nodes for which there is no
 * longer a corresponding case.
 */
final class DeleteOrphanCaseNodesTask implements Runnable {

    private static final Logger logger = AutoIngestDashboardLogger.getLogger();
    private final ProgressIndicator progress;
    private int nodesCount;
    private int casesCount;

    /**
     * Constucts an instance of a task for deleting case coordination service
     * nodes for which there is no longer a corresponding case.
     *
     * @param progress
     */
    DeleteOrphanCaseNodesTask(ProgressIndicator progress) {
        this.progress = progress;
    }

    @Override
    @NbBundle.Messages({
        "DeleteOrphanCaseNodesTask.progress.startMessage=Starting orphaned case znode cleanup",
        "DeleteOrphanCaseNodesTask.progress.connectingToCoordSvc=Connecting to the coordination service",
        "DeleteOrphanCaseNodesTask.progress.gettingCaseZnodes=Querying the coordination service for case znodes",
        "DeleteOrphanCaseNodesTask.progress.lookingForOrphanedCaseZnodes=Looking for orphaned case znodes"
    })
    public void run() {
        progress.start(Bundle.DeleteOrphanCaseNodesTask_progress_startMessage());
        try {
            progress.progress(Bundle.DeleteOrphanCaseNodesTask_progress_connectingToCoordSvc());
            logger.log(Level.INFO, Bundle.DeleteOrphanCaseNodesTask_progress_connectingToCoordSvc());
            CoordinationService coordinationService;
            try {
                coordinationService = CoordinationService.getInstance();
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, "Error connecting to the coordination service", ex); //NON-NLS
                return;
            }

            progress.progress(Bundle.DeleteOrphanCaseNodesTask_progress_gettingCaseZnodes());
            logger.log(Level.INFO, Bundle.DeleteOrphanCaseNodesTask_progress_gettingCaseZnodes());
            List<String> nodePaths;
            try {
                nodePaths = coordinationService.getNodeList(CoordinationService.CategoryNode.CASES);
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, "Error getting case znode list", ex); //NON-NLS
                return;
            } catch (InterruptedException unused) {
                logger.log(Level.WARNING, "Task cancelled while getting case znode list"); //NON-NLS
                return;
            }

            progress.progress(Bundle.DeleteOrphanCaseNodesTask_progress_lookingForOrphanedCaseZnodes());
            logger.log(Level.INFO, Bundle.DeleteOrphanCaseNodesTask_progress_lookingForOrphanedCaseZnodes());
            for (String caseNodePath : nodePaths) {
                if (isCaseNameNodePath(caseNodePath) || isCaseResourcesNodePath(caseNodePath) || isCaseAutoIngestLogNodePath(caseNodePath)) {
                    continue;
                }

                final Path caseDirectoryPath = Paths.get(caseNodePath);
                final File caseDirectory = caseDirectoryPath.toFile();
                if (!caseDirectory.exists()) {
                    String caseName = CoordinationServiceUtils.getCaseNameNodePath(caseDirectoryPath);
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

                        ++casesCount;
                        
                        /*
                         * Back to looking for orphans...
                         */
                        progress.progress(Bundle.DeleteOrphanCaseNodesTask_progress_lookingForOrphanedCaseZnodes());
                        logger.log(Level.INFO, Bundle.DeleteOrphanCaseNodesTask_progress_lookingForOrphanedCaseZnodes());

                    } catch (InterruptedException unused) {
                        logger.log(Level.WARNING, String.format("Task cancelled while deleting orphaned znode %s for %s", nodePath, caseName)); //NON-NLS
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
            logger.log(Level.SEVERE, "Unexpected error during orphan case znode cleanup", ex); //NON-NLS
            throw ex;

        } finally {
            logger.log(Level.INFO, String.format("Deleted %d orphaned case znodes for %d cases", nodesCount, casesCount));
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
        "# {0} - node path", "DeleteOrphanCaseNodesTask.progress.deletingOrphanedCaseNode=Deleting orphaned case znode {0}"
    })
    private void deleteNode(CoordinationService coordinationService, String caseName, String nodePath) throws InterruptedException {
        try {
            progress.progress(Bundle.DeleteOrphanCaseNodesTask_progress_deletingOrphanedCaseNode(nodePath));
            logger.log(Level.INFO, String.format("Deleting orphaned case node %s for case %s", nodePath, caseName)); //NON-NLS
            coordinationService.deleteNode(CoordinationService.CategoryNode.CASES, nodePath);
            ++nodesCount;
        } catch (CoordinationService.CoordinationServiceException ex) {
            if (!DeleteCaseUtils.isNoNodeException(ex)) {
                logger.log(Level.SEVERE, String.format("Error deleting orphaned case node %s for case %s", nodePath, caseName), ex); //NON-NLS
            }
        }
    }

}
