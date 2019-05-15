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
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * A task class for cleaning up auto ingest job coordination service nodes for
 * which there is no longer a corresponding manifest file.
 */
final class DeleteOrphanManifestNodesTask implements Runnable {

    private static final Logger logger = AutoIngestDashboardLogger.getLogger();
    private final ProgressIndicator progress;

    /**
     * Constucts an instance of a task for cleaning up case coordination service
     * nodes for which there is no longer a corresponding case.
     *
     * @param progress
     */
    DeleteOrphanManifestNodesTask(ProgressIndicator progress) {
        this.progress = progress;
    }

    @Override
    @NbBundle.Messages({
        "DeleteOrphanManifestNodesTask.progress.startMessage=Starting orphaned manifest file znode cleanup",
        "DeleteOrphanManifestNodesTask.progress.connectingToCoordSvc=Connecting to the coordination service",
        "DeleteOrphanManifestNodesTask.progress.gettingManifestNodes=Querying the coordination service for manifest file znodes",
        "DeleteOrphanManifestNodesTask.progress.lookingForOrphanedManifestFileZnodes=Looking for orphaned manifest file znodes",
        "# {0} - node path", "DeleteOrphanManifestNodesTask.progress.deletingOrphanedManifestNode=Deleting orphaned manifest file znode {0}"
    })
    public void run() {
        progress.start(Bundle.DeleteOrphanManifestNodesTask_progress_startMessage());
        int nodesCount = 0;
        try {
            progress.progress(Bundle.DeleteOrphanManifestNodesTask_progress_connectingToCoordSvc());
            logger.log(Level.INFO, Bundle.DeleteOrphanManifestNodesTask_progress_connectingToCoordSvc());
            CoordinationService coordinationService;
            try {
                coordinationService = CoordinationService.getInstance();
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, "Error connecting to the coordination service", ex); // NON-NLS
                return;
            }

            progress.progress(Bundle.DeleteOrphanManifestNodesTask_progress_gettingManifestNodes());
            logger.log(Level.INFO, Bundle.DeleteOrphanManifestNodesTask_progress_gettingManifestNodes());
            List<String> nodePaths;
            try {
                nodePaths = coordinationService.getNodeList(CoordinationService.CategoryNode.MANIFESTS);
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, "Error getting manifest file znode list", ex); // NON-NLS
                return;
            } catch (InterruptedException unused) {
                logger.log(Level.WARNING, "Task cancelled while getting manifest file znode list"); // NON-NLS
                return;
            }

            progress.progress(Bundle.DeleteOrphanManifestNodesTask_progress_lookingForOrphanedManifestFileZnodes());
            logger.log(Level.INFO, Bundle.DeleteOrphanManifestNodesTask_progress_lookingForOrphanedManifestFileZnodes());
            for (String nodePath : nodePaths) {
                final Path manifestFilePath = Paths.get(nodePath);
                final File manifestFile = manifestFilePath.toFile();
                if (!manifestFile.exists()) {
                    try {
                        progress.progress(Bundle.DeleteOrphanManifestNodesTask_progress_deletingOrphanedManifestNode(manifestFilePath));
                        logger.log(Level.INFO, String.format("Deleting orphaned manifest file znode %s", manifestFilePath));
                        coordinationService.deleteNode(CoordinationService.CategoryNode.MANIFESTS, manifestFilePath.toString());
                        ++nodesCount;
                    } catch (CoordinationService.CoordinationServiceException ex) {
                        if (!DeleteCaseUtils.isNoNodeException(ex)) {
                            logger.log(Level.SEVERE, String.format("Error deleting orphaned manifest file %s", manifestFilePath), ex);  // NON-NLS
                        }
                    } catch (InterruptedException unused) {
                        logger.log(Level.WARNING, String.format("Task cancelled while deleting orphaned manifest file %s", manifestFilePath));  // NON-NLS
                        return;
                    }

                    /*
                     * Back to looking for orphans...
                     */
                    progress.progress(Bundle.DeleteOrphanManifestNodesTask_progress_lookingForOrphanedManifestFileZnodes());
                    logger.log(Level.INFO, Bundle.DeleteOrphanManifestNodesTask_progress_lookingForOrphanedManifestFileZnodes());
                }
            }

        } catch (Exception ex) {
            /*
             * This is an unexpected runtime exceptions firewall. It is here
             * because this task is designed to be able to be run in scenarios
             * where there is no call to get() on a Future<Void> associated with
             * the task, so this ensures that any such errors get logged.
             */
            logger.log(Level.SEVERE, "Unexpected error deleting orphan manifest file znodes", ex); // NON-NLS
            throw ex;

        } finally {
            logger.log(Level.INFO, String.format("Deleted %d orphaned manifest file znodes", nodesCount));
            progress.finish();
        }
    }

}
