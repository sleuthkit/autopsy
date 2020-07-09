/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
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
    private int nodesCount = 0;
    private int casesCount = 0;

    /**
     * Constucts an instance of a task for deleting case coordination service
     * nodes for which there is no longer a corresponding case.
     *
     * @param progress
     */
    DeleteOrphanCaseNodesTask(ProgressIndicator progress) {
        this.progress = progress;
    }

    /**
     * Retrieves an instance of the coordination service in order to fetch
     * znodes and potentially delete.
     *
     * @return The coordination service or null on error.
     */
    private CoordinationService getCoordinationService() {
        progress.progress(Bundle.DeleteOrphanCaseNodesTask_progress_connectingToCoordSvc());
        logger.log(Level.INFO, Bundle.DeleteOrphanCaseNodesTask_progress_connectingToCoordSvc());
        CoordinationService coordinationService = null;
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationService.CoordinationServiceException ex) {
            logger.log(Level.SEVERE, "Error connecting to the coordination service", ex); //NON-NLS
        }
        return coordinationService;
    }

    /**
     * Retrieves node paths for cases.
     *
     * @param coordinationService The coordination service to use in order to
     *                            fetch the node paths.
     *
     * @return The list of node paths for cases.
     */
    private List<String> getNodePaths(CoordinationService coordinationService) {
        progress.progress(Bundle.DeleteOrphanCaseNodesTask_progress_gettingCaseZnodes());
        logger.log(Level.INFO, Bundle.DeleteOrphanCaseNodesTask_progress_gettingCaseZnodes());
        List<String> nodePaths = null;
        try {
            nodePaths = coordinationService.getNodeList(CoordinationService.CategoryNode.CASES);
            // in the event that getNodeList returns null (but still successful) return empty list
            if (nodePaths == null) {
                return new ArrayList<String>();
            }
        } catch (CoordinationService.CoordinationServiceException ex) {
            logger.log(Level.SEVERE, "Error getting case znode list", ex); //NON-NLS
        } catch (InterruptedException unused) {
            logger.log(Level.WARNING, "Task cancelled while getting case znode list"); //NON-NLS
        }

        return nodePaths;
    }

    private void addIfExists(List<String> paths, String path) {
        if (path != null && !path.isEmpty()) {
            paths.add(path);
        }
    }

    /**
     * Determines orphaned znode paths.
     *
     * @param nodePaths The list of case node paths.
     *
     * @return The list of orphaned node paths.
     */
    private Map<String, List<String>> getOrphanedNodes(List<String> nodePaths) {
        progress.progress(Bundle.DeleteOrphanCaseNodesTask_progress_lookingForOrphanedCaseZnodes());
        logger.log(Level.INFO, Bundle.DeleteOrphanCaseNodesTask_progress_lookingForOrphanedCaseZnodes());
        Map<String, List<String>> nodePathsToDelete = new HashMap<>();
        for (String caseNodePath : nodePaths) {
            if (isCaseNameNodePath(caseNodePath) || isCaseResourcesNodePath(caseNodePath) || isCaseAutoIngestLogNodePath(caseNodePath)) {
                continue;
            }

            final Path caseDirectoryPath = Paths.get(caseNodePath);
            final File caseDirectory = caseDirectoryPath.toFile();
            if (!caseDirectory.exists()) {
                String caseName = CoordinationServiceUtils.getCaseNameNodePath(caseDirectoryPath);
                List<String> paths = new ArrayList<>();

                addIfExists(paths, CoordinationServiceUtils.getCaseNameNodePath(caseDirectoryPath));
                addIfExists(paths, CoordinationServiceUtils.getCaseResourcesNodePath(caseDirectoryPath));
                addIfExists(paths, CoordinationServiceUtils.getCaseAutoIngestLogNodePath(caseDirectoryPath));
                addIfExists(paths, CoordinationServiceUtils.getCaseDirectoryNodePath(caseDirectoryPath));
                nodePathsToDelete.put(caseName, paths);
            }
        }
        return nodePathsToDelete;
    }

    /**
     * Boxed boolean so that promptUser method can set a value on a final object
     * from custom jdialog message.
     */
    private class PromptResult {

        private boolean value = false;

        boolean isValue() {
            return value;
        }

        void setValue(boolean value) {
            this.value = value;
        }

    }

    /**
     * prompts the user with a list of orphaned znodes.
     *
     * @param orphanedNodes The orphaned znode cases.
     *
     * @return True if the user would like to proceed deleting the znodes.
     */
    private boolean promptUser(Collection<String> orphanedNodes) {
        final PromptResult dialogResult = new PromptResult();
        try {
            SwingUtilities.invokeAndWait(() -> {
                DeleteOrphanCaseNodesDialog dialog = new DeleteOrphanCaseNodesDialog(orphanedNodes);
                dialog.display();
                dialogResult.setValue(dialog.isOkSelected());
            });

            return dialogResult.isValue();
        } catch (InterruptedException | InvocationTargetException e) {
            logger.log(Level.WARNING, "Task cancelled while confirming case znodes to delete"); //NON-NLS
            return false;
        }
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
            CoordinationService coordinationService = getCoordinationService();
            if (coordinationService == null) {
                return;
            }

            List<String> nodePaths = getNodePaths(coordinationService);
            if (nodePaths == null) {
                return;
            }

            Map<String, List<String>> orphanedNodes = getOrphanedNodes(nodePaths);
            boolean continueDelete = promptUser(orphanedNodes.keySet());

            if (continueDelete) {
                deleteNodes(coordinationService, orphanedNodes);
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
     * Deletes the orphaned znodes provided in the 'orphanedNodes' variable.
     *
     * @param coordinationService The coordination service to use for deletion.
     * @param orphanedNodes       A mapping of case to the orphaned znodes.
     *
     * @throws InterruptedException If the thread executing this task is
     *                              interrupted during the delete operation.
     */
    private void deleteNodes(CoordinationService coordinationService, Map<String, List<String>> orphanedNodes) {
        String caseName = null;
        String nodePath = null;
        try {
            for (Entry<String, List<String>> caseNodePaths : orphanedNodes.entrySet()) {
                caseName = caseNodePaths.getKey();
                for (String path : caseNodePaths.getValue()) {
                    nodePath = path;
                    deleteNode(coordinationService, caseName, nodePath);
                }
                ++casesCount;
            }
        } catch (InterruptedException unused) {
            logger.log(Level.WARNING, String.format("Task cancelled while deleting orphaned znode %s for %s", nodePath, caseName)); //NON-NLS
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
