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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CategoryNode;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJobNodeData.InvalidDataException;

/**
 * A base class for tasks that delete part or all of a case produced via auto
 * ingest.
 */
abstract class DeleteCaseTask implements Runnable {

    private static final String RESOURCES_LOCK_SUFFIX = "_resources"; //NON-NLS
    private static final Logger logger = AutoIngestDashboardLogger.getLogger();
    private final CaseNodeData caseNodeData;
    private final ProgressIndicator progress;
    private final List<AutoIngestJobNodeData> nodeDataForAutoIngestJobs;
    private final Map<Path, CoordinationService.Lock> manifestFileLocks;
    private CoordinationService coordinationService;

    /**
     * Constructs the base class part of a task that deletes part or all of a
     * case produced via auto ingest.
     *
     * @param caseNodeData The case directory lock coordination service node
     *                     data for the case to be deleted.
     * @param progress     A progress indicator.
     */
    DeleteCaseTask(CaseNodeData caseNodeData, ProgressIndicator progress) {
        this.caseNodeData = caseNodeData;
        this.progress = progress;
        this.nodeDataForAutoIngestJobs = new ArrayList<>();
        this.manifestFileLocks = new HashMap<>();
    }

    @Override
    @NbBundle.Messages({
        "DeleteCaseTask.progress.connectingToCoordSvc=Connecting to the coordination service",
        "DeleteCaseTask.progress.acquiringCaseNameLock=Acquiring exclusive case name lock",
        "DeleteCaseTask.progress.acquiringCaseDirLock=Acquiring exclusive case directory lock",
        "DeleteCaseTask.progress.gettingJobNodeData=Getting node data for auto ingest jobs",
        "DeleteCaseTask.progress.acquiringInputDirLocks=Acquiring exclusive input directory locks"
    })
    public void run() {
        try {
            progress.start(Bundle.DeleteCaseTask_progress_connectingToCoordSvc());
            try {
                coordinationService = CoordinationService.getInstance();
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Failed to connect to the coordination service to delete %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                return;
            }

            /*
             * Acquire an exclusive case name lock. This is the lock that auto
             * ingest nodes acquire when creating or opening a case specified in
             * an auto ingest job manifest file. Acquiring this lock prevents
             * auto ingest nodes from searching for and finding the case
             * directory of the case to be deleted.
             */
            progress.progress(Bundle.DeleteCaseTask_progress_acquiringCaseNameLock());
            logger.log(Level.INFO, String.format("Exclusively locking the case name for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
            final String caseNameLockName = TimeStampUtils.removeTimeStamp(caseNodeData.getName());
            try (CoordinationService.Lock nameLock = coordinationService.tryGetExclusiveLock(CategoryNode.CASES, caseNameLockName)) {
                if (nameLock == null) {
                    logger.log(Level.WARNING, String.format("Failed to exclusively lock the case name for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                    return;
                }

                /*
                 * Acquire an exclusive case directory lock. This is the lock
                 * that is aquired by any node (auto ingest or examiner)
                 * attempting to create or open a case and is held by such a
                 * node for as long as the case is open. Acquiring this lock
                 * ensures that no other node currently has the case to be
                 * deleted open and prevents another node from trying to open
                 * the case as it is being deleted.
                 */
                progress.progress(Bundle.DeleteCaseTask_progress_acquiringCaseDirLock());
                logger.log(Level.INFO, String.format("Exclusively locking the case directory for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                try (CoordinationService.Lock caseLock = CoordinationService.getInstance().tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseNodeData.getDirectory().toString())) {
                    if (caseLock == null) {
                        logger.log(Level.WARNING, String.format("Failed to exclusively lock the case directory for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                        return;
                    }

                    progress.progress(Bundle.DeleteCaseTask_progress_gettingJobNodeData());
                    logger.log(Level.INFO, String.format("Fetching auto ingest job node data for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                    try {
                        getAutoIngestJobNodeData();
                    } catch (CoordinationServiceException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to fetch auto ingest job node data for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                        return;
                    }

                    if (!nodeDataForAutoIngestJobs.isEmpty()) {
                        progress.progress(Bundle.DeleteCaseTask_progress_acquiringInputDirLocks());
                        logger.log(Level.INFO, String.format("Exclusively locking the case directories for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                        getInputDirectoryLocks();
                        if (manifestFileLocks.isEmpty()) {
                            logger.log(Level.WARNING, String.format("Failed to exclusively lock the input directories for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                            return;
                        }
                    } else {
                        logger.log(Level.INFO, String.format("No auto ingest job node data found for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                    }

                    deleteWhileHoldingAllLocks();

                } catch (CoordinationServiceException ex) {
                    logger.log(Level.SEVERE, String.format("Error acquiring exclusive case directory lock for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);
                }

            } catch (CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Error acquiring exclusive case name lock for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);
            }

            deleteAfterCaseLocksReleased();
            releaseInputDirectoryLocks();
            deleteAfterAllLocksReleased();

        } catch (Throwable ex) {
            /*
             * Unexpected runtime exceptions firewall.
             */
            logger.log(Level.SEVERE, String.format("Unexpected error deleting %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);

        } finally {
            progress.finish();
        }

    }

    /**
     * Deletes the parts of the case that need to be deleted while holding the
     * following exclusive locks: case name lock, case directory lock, and all
     * input directory locks.
     */
    abstract void deleteWhileHoldingAllLocks();

    /**
     * Deletes the parts of the case that need to be deleted after releasing
     * exclusive locks on the case name and case directory, but while still
     * holding exclusive locks on all of the input directories.
     */
    abstract void deleteAfterCaseLocksReleased();

    /**
     * Deletes the parts of the case that need to be deleted after all of the
     * locks are released.
     */
    abstract void deleteAfterAllLocksReleased();

    /**
     * Deletes the auto ingest job input directories for the case. Should only
     * be called when holding all of the exclusive locks for the case.
     */
    @NbBundle.Messages({
        "DeleteCaseTask.progress.deletingInputDirs=Deleting input directory",
        "# {0} - input directory name", "DeleteCaseTask.progress.deletingInputDir=Deleting input directory {0}"
    })
    protected void deleteInputDirectories() {
        progress.progress(Bundle.DeleteCaseTask_progress_deletingInputDirs());
        logger.log(Level.INFO, String.format("Deleting input directories for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
        for (AutoIngestJobNodeData jobNodeData : nodeDataForAutoIngestJobs) {
            final Path inputDirPath = jobNodeData.getManifestFilePath().getParent();
            progress.progress(Bundle.DeleteCaseTask_progress_deletingInputDir(inputDirPath));
            logger.log(Level.INFO, String.format("Deleting input directory %s for %s (%s) in %s", inputDirPath, caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
            if (FileUtil.deleteDir(new File(inputDirPath.toString()))) {
                logger.log(Level.WARNING, String.format("Failed to delete the input directory %s for %s (%s) in %s", inputDirPath, caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                // RJCTODO: Update deletion flags
            }
        }
    }

    /**
     * Deletes the case directory, the case database, and the text index for the
     * case. Should only be called when holding all of the exclusive locks for
     * the case.
     */
    @NbBundle.Messages({
        "DeleteCaseTask.progress.locatingCaseMetadataFile=Locating case metadata file",
        "DeleteCaseTask.progress.deletingCaseOutput=Deleting case output"
    })
    protected void deleteCaseOutput() {
        progress.progress(Bundle.DeleteCaseTask_progress_locatingCaseMetadataFile());
        logger.log(Level.INFO, String.format("Locating metadata file for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
        String metadataFilePath = null;
        final File caseDirectory = caseNodeData.getDirectory().toFile();
        final File[] filesInDirectory = caseDirectory.listFiles();
        if (filesInDirectory != null) {
            for (File file : filesInDirectory) {
                if (file.getName().toLowerCase().endsWith(CaseMetadata.getFileExtension()) && file.isFile()) {
                    metadataFilePath = file.getPath();
                }
            }
        }

        if (metadataFilePath != null) {
            progress.progress(Bundle.DeleteCaseTask_progress_deletingCaseOutput());
            logger.log(Level.INFO, String.format("Deleting output for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
            try {
                Case.deleteCase(new CaseMetadata(Paths.get(metadataFilePath)), false, progress);
            } catch (CaseMetadata.CaseMetadataException | CaseActionException ex) {
                // RJCTODO: Set delete flags? 
                logger.log(Level.WARNING, String.format("Error deleting output for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
            }
        } else {
            logger.log(Level.WARNING, String.format("Failed to locate metadata file for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
            // RJCTODO: Set delete flags 
        }
    }

    /**
     * Deletes the case name, case directory, case resources, and case auto
     * ingest log for the case. Should only be called when holding the exclusive
     * locks for all of the input directories for the case.
     */
    @Messages({
        "DeleteCaseTask.progress.deletingJobLogLockNode=Deleting auto ingest job log lock node",
        "DeleteCaseTask.progress.deletingResourcesLockNode=Deleting case resources lock node",
        "DeleteCaseTask.progress.deletingDirLockNode=Deleting case directory lock node",
        "DeleteCaseTask.progress.deletingNameLockNode=Deleting case name lock node"
    })
    protected void deleteCaseLockNodes() {
        progress.progress(Bundle.DeleteCaseTask_progress_deletingJobLogLockNode());
        logger.log(Level.INFO, String.format("Deleting case auto ingest job log lock node for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
        Path logFilePath = AutoIngestJobLogger.getLogPath(caseNodeData.getDirectory());
        try {
            coordinationService.deleteNode(CategoryNode.CASES, logFilePath.toString());
        } catch (CoordinationServiceException ex) {
            logger.log(Level.WARNING, String.format("Error deleting auto ingest job log lock node for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);
            // RJCTODO: Set delete flags 
        }

        progress.progress(Bundle.DeleteCaseTask_progress_deletingResourcesLockNode());
        logger.log(Level.INFO, String.format("Deleting case resources log lock node for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
        String resourcesLockNodePath = caseNodeData.getDirectory().toString() + RESOURCES_LOCK_SUFFIX;
        try {
            coordinationService.deleteNode(CategoryNode.CASES, resourcesLockNodePath);
        } catch (CoordinationServiceException ex) {
            logger.log(Level.WARNING, String.format("Error deleting case resources lock node for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);
            // RJCTODO: Set delete flags 
        }

        progress.progress(Bundle.DeleteCaseTask_progress_deletingDirLockNode());
        logger.log(Level.INFO, String.format("Deleting case directory lock node for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
        final Path caseDirectoryPath = caseNodeData.getDirectory();
        try {
            coordinationService.deleteNode(CategoryNode.CASES, caseDirectoryPath.toString());
        } catch (CoordinationServiceException ex) {
            logger.log(Level.WARNING, String.format("Error deleting case directory lock node for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);
            // RJCTODO: Set delete flags 
        }

        progress.progress(Bundle.DeleteCaseTask_progress_deletingNameLockNode());
        logger.log(Level.INFO, String.format("Deleting case name lock node for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
        final String caseNameLockName = TimeStampUtils.removeTimeStamp(caseNodeData.getName());
        try {
            coordinationService.deleteNode(CategoryNode.CASES, caseNameLockName);
        } catch (CoordinationServiceException ex) {
            logger.log(Level.WARNING, String.format("Error deleting case name lock node for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);
            // RJCTODO: Set delete flags 
        }
    }

    /**
     * Deletes the input directory lock nodes for the case. Should only be
     * called after releasing all of the locks for the case.
     */
    @Messages({
        "DeleteCaseTask.progress.deletingInputDirLockNodes=Deleting input directory lock nodes"
    })
    protected void deleteInputDirectoryLockNodes() {
        progress.progress(Bundle.DeleteCaseTask_progress_deletingInputDirLockNodes());
        logger.log(Level.INFO, String.format("Deleting input directory lock nodes for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
        for (AutoIngestJobNodeData jobNodeData : nodeDataForAutoIngestJobs) {
            try {
                logger.log(Level.INFO, String.format("Deleting manifest file lock node for %s for %s (%s) in %s", jobNodeData.getManifestFilePath(), caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                coordinationService.deleteNode(CoordinationService.CategoryNode.MANIFESTS, jobNodeData.getManifestFilePath().toString());
            } catch (CoordinationServiceException ex) {
                Path inputDirPath = jobNodeData.getManifestFilePath().getParent();
                logger.log(Level.WARNING, String.format("Error deleting input directory lock node %s for %s (%s) in %s", inputDirPath, caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);
                // RJCTODO: Set delete flags 
            }
        }
    }

    /**
     * Fetches all of the auto ingest job (manifest file) lock coordination
     * service node data for a case.
     *
     * @param caseName The name of the case.
     *
     * @return A list of auto ingest job (manifest file) lock node data for the
     *         case.
     *
     * @throws CoordinationServiceException If there is an error getting a list
     *                                      of the auto ingest job (manifest
     *                                      file) lock nodes from the
     *                                      coordination service.
     */
    private void getAutoIngestJobNodeData() throws CoordinationServiceException {
        String caseName = caseNodeData.getName();
        final List<String> nodes = coordinationService.getNodeList(CoordinationService.CategoryNode.MANIFESTS);
        for (String nodeName : nodes) {
            try {
                byte[] nodeBytes = coordinationService.getNodeData(CoordinationService.CategoryNode.CASES, nodeName);
                if (nodeBytes == null || nodeBytes.length <= 0) {
                    continue;
                }
                AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(nodeBytes);
                if (caseName.equals(nodeData.getCaseName())) {
                    nodeDataForAutoIngestJobs.add(nodeData);
                }
            } catch (CoordinationService.CoordinationServiceException | InterruptedException | InvalidDataException ex) {
                logger.log(Level.WARNING, String.format("Failed to get coordination service node data for %s", nodeName), ex);
            }
        }
    }

    /**
     * Acquires either all or none of the input directory locks for a case.
     *
     * @param caseNodeData              The case node data from the case
     *                                  directory lock node for the case.
     * @param autoIngestJobNodeDataList The auto ingest job node data from the
     *                                  input directory lock nodes for the case.
     *
     * @return A mapping of manifest file paths to input directory lcoks for all
     *         input directories for the case; will be empty if all of the locks
     *         could not be obtained.
     */
    @NbBundle.Messages({
        "# {0} - input directory name", "DeleteCaseTask.progress.lockingInputDir=Acquiring exclusive lock on input directory {0}",})
    private void getInputDirectoryLocks() {
        for (AutoIngestJobNodeData autoIngestJobNodeData : nodeDataForAutoIngestJobs) {
            final Path inputDirPath = autoIngestJobNodeData.getManifestFilePath().getParent();
            try {
                progress.progress(Bundle.DeleteCaseTask_progress_lockingInputDir(inputDirPath));
                final CoordinationService.Lock inputDirLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, autoIngestJobNodeData.getManifestFilePath().toString());
                if (null != inputDirLock) {
                    manifestFileLocks.put(autoIngestJobNodeData.getManifestFilePath(), inputDirLock);
                } else {
                    logger.log(Level.WARNING, String.format("Failed to exclusively lock the input directory %s for %s (%s) in %s", inputDirPath, caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                    releaseInputDirectoryLocks();
                    manifestFileLocks.clear();
                }
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Failed to exclusively lock the input directory %s for %s (%s) in %s", inputDirPath, caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);
                releaseInputDirectoryLocks();
                manifestFileLocks.clear();
            }
        }
    }

    /**
     * Releases any manifest file coordination service locks that were acquired
     * for the case.
     */
    @NbBundle.Messages({
        "DeleteCaseTask.progress.releasingManifestLocks=Acquiring exclusive manifest file locks",
        "# {0} - manifest file path", "DeleteCaseTask.progress.releasingManifestLock=Releasing the exclusive lock on manifest file {0}"
    })
    private void releaseInputDirectoryLocks() {
        if (!manifestFileLocks.isEmpty()) {
            progress.progress(Bundle.DeleteCaseTask_progress_releasingManifestLocks());
            for (Map.Entry<Path, CoordinationService.Lock> entry : manifestFileLocks.entrySet()) {
                final Path manifestFilePath = entry.getKey();
                final CoordinationService.Lock manifestFileLock = entry.getValue();
                try {
                    progress.progress(Bundle.DeleteCaseTask_progress_releasingManifestLock(manifestFilePath));
                    manifestFileLock.release();
                } catch (CoordinationServiceException ex) {
                    logger.log(Level.SEVERE, String.format("Error re3leasing exclusive lock on %s for %s (%s) in %s", manifestFilePath, caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);
                }
            }
        }
    }

}
