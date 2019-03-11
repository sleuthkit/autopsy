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
import java.io.IOException;
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
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseCoordinationServiceUtils;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CategoryNode;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJobNodeData.InvalidDataException;

/**
 * A base class for tasks that delete part or all of a given case.
 */
abstract class DeleteCaseTask implements Runnable {

    private static final Logger logger = AutoIngestDashboardLogger.getLogger();
    private final CaseNodeData caseNodeData;
    private final String caseDisplayName;
    private final String caseUniqueName;
    private final Path caseDirectoryPath;
    private final ProgressIndicator progress;
    private final List<AutoIngestJobNodeData> nodeDataForAutoIngestJobs;
    private final Map<String, CoordinationService.Lock> manifestFileLocks;
    private CoordinationService coordinationService;

    /**
     * Constructs the base class part of a task that deletes part or all of a
     * given case.
     *
     * @param caseNodeData The case directory lock coordination service node
     *                     data for the case.
     * @param progress     A progress indicator.
     */
    DeleteCaseTask(CaseNodeData caseNodeData, ProgressIndicator progress) {
        this.caseNodeData = caseNodeData;
        this.progress = progress;
        /*
         * Design Decision Note: It was decided to add the following state to
         * instances of this class make it easier to access given that the class
         * design favors instance methods over static methods.
         */
        this.caseDisplayName = caseNodeData.getDisplayName();
        this.caseUniqueName = caseNodeData.getName();
        this.caseDirectoryPath = caseNodeData.getDirectory();
        this.nodeDataForAutoIngestJobs = new ArrayList<>();
        this.manifestFileLocks = new HashMap<>();
    }

    @Override
    @NbBundle.Messages({
        "DeleteCaseTask.progress.startMessage=Preparing for deletion..."
    })
    public void run() {
        try {
            progress.start(Bundle.DeleteCaseTask_progress_startMessage());
            logger.log(Level.INFO, String.format("Beginning deletion of %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
            deleteCase();
            logger.log(Level.SEVERE, String.format("Deletion of %s (%s) in %s completed", caseDisplayName, caseUniqueName, caseDirectoryPath));

        } catch (Throwable ex) {
            /*
             * Unexpected runtime exceptions firewall. This task is designed to
             * be able to be run in an executor service thread pool without
             * calling get() on the task's Future<Void>, so this ensures that
             * such errors do get ignored.
             */
            logger.log(Level.INFO, String.format("Unexpected error deleting %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);

        } finally {
            progress.finish();
        }

    }

    /**
     * Deletes part or all of the given case.
     */
    @NbBundle.Messages({
        "DeleteCaseTask.progress.connectingToCoordSvc=Connecting to the coordination service...",
        "DeleteCaseTask.progress.acquiringCaseNameLock=Acquiring an exclusive case name lock...",
        "DeleteCaseTask.progress.acquiringCaseDirLock=Acquiring an exclusive case directory lock...",
        "DeleteCaseTask.progress.gettingJobNodeData=Getting node data for auto ingest jobs...",
        "DeleteCaseTask.progress.acquiringManifestLocks=Acquiring exclusive manifest file locks..."
    })
    private void deleteCase() {
        progress.progress(Bundle.DeleteCaseTask_progress_connectingToCoordSvc());
        logger.log(Level.INFO, String.format("Connecting to coordination service for deletion of %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationService.CoordinationServiceException ex) {
            logger.log(Level.SEVERE, String.format("Failed to connect to the coordination service, cannot delete %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
            return;
        }

        if (Thread.currentThread().isInterrupted()) {
            logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath));
            return;
        }

        /*
         * Acquire an exclusive case name lock. This is the lock that auto
         * ingest nodes acquire exclusively when creating or opening a case
         * specified in an auto ingest job manifest file to ensure that only one
         * auto ingest node at a time can search the auto ingest output
         * directory for an existing case matching the one in the manifest file.
         * Acquiring this lock effectively locks auto ingest node job processing
         * tasks out of the case to be deleted.
         */
        progress.progress(Bundle.DeleteCaseTask_progress_acquiringCaseNameLock());
        logger.log(Level.INFO, String.format("Acquiring an exclusive case name lock for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
        String caseNameLockNodeName = CaseCoordinationServiceUtils.getCaseLockName(caseDirectoryPath);
        try (CoordinationService.Lock nameLock = coordinationService.tryGetExclusiveLock(CategoryNode.CASES, caseNameLockNodeName)) {
            if (nameLock == null) {
                logger.log(Level.INFO, String.format("Could not delete %s (%s) in %s because a case name lock was held by another host", caseDisplayName, caseUniqueName, caseDirectoryPath));
                return;
            }

            if (Thread.currentThread().isInterrupted()) {
                logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath));
                return;
            }

            /*
             * Acquire an exclusive case directory lock. A shared case directory
             * lock is acquired by any node (auto ingest or examiner) when it
             * opens a case and is held by the node for as long as the case is
             * open. Acquiring this lock exclusively ensures that no other node
             * currently has the case to be deleted open and prevents another
             * node from trying to open the case while it is being deleted.
             */
            progress.progress(Bundle.DeleteCaseTask_progress_acquiringCaseDirLock());
            logger.log(Level.INFO, String.format("Acquiring an exclusive case directory lock for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
            String caseDirLockNodeName = CaseCoordinationServiceUtils.getCaseDirectoryLockName(caseDirectoryPath);
            try (CoordinationService.Lock caseDirLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseDirLockNodeName)) {
                if (caseDirLock == null) {
                    logger.log(Level.INFO, String.format("Could not delete %s (%s) in %s because a case directory lock was held by another host", caseDisplayName, caseUniqueName, caseDirectoryPath));
                    return;
                }

                if (Thread.currentThread().isInterrupted()) {
                    logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath));
                    return;
                }

                progress.progress(Bundle.DeleteCaseTask_progress_gettingJobNodeData());
                logger.log(Level.INFO, String.format("Fetching auto ingest job node data for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
                try {
                    getAutoIngestJobNodeData();
                } catch (CoordinationServiceException ex) {
                    logger.log(Level.SEVERE, String.format("Error fetching auto ingest job node data for %s (%s) in %s, cannot delete case", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
                    return;
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
                    return;
                }

                if (Thread.currentThread().isInterrupted()) {
                    logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath));
                    return;
                }

                if (!nodeDataForAutoIngestJobs.isEmpty()) {
                    progress.progress(Bundle.DeleteCaseTask_progress_acquiringManifestLocks());
                    logger.log(Level.INFO, String.format("Acquiring exclusive manifest file locks for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
                    getManifestFileLocks();
                    if (manifestFileLocks.isEmpty()) {
                        logger.log(Level.INFO, String.format("Could not delete %s (%s) in %s because a case directory lock was held by another host", caseDisplayName, caseUniqueName, caseDirectoryPath));
                        return;
                    }
                } else {
                    logger.log(Level.INFO, String.format("No auto ingest job node data found for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
                }

                if (Thread.currentThread().isInterrupted()) {
                    logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath));
                    releaseManifestFileLocks();
                    return;
                }

                try {
                    deleteWhileHoldingAllLocks();
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
                    return;
                }

                releaseManifestFileLocks();

                try {
                    deleteAfterManifestLocksReleased();
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
                    return;
                }

            } catch (CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Error acquiring exclusive case directory lock for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
            }

            if (Thread.currentThread().isInterrupted()) {
                logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath));
                return;
            }

            try {
                deleteAfterCaseDirectoryLockReleased();
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
                return;
            }

        } catch (CoordinationServiceException ex) {
            logger.log(Level.SEVERE, String.format("Error acquiring exclusive case name lock for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
        }

        try {
            deleteAfterCaseNameLockReleased();
        } catch (InterruptedException ex) {
            logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
            return;
        }
    }

    /**
     * Deletes the parts of the case that need to be deleted while holding all
     * of the exclusive locks: the case name lock, the case directory lock, amd
     * the manifest file locks. Note that the locks are acquired in that order
     * and released in the opposite order.
     */
    abstract void deleteWhileHoldingAllLocks() throws InterruptedException;

    /**
     * Deletes the parts of the case that need to be deleted after the release
     * of the exclusive manifest file locks, while still holding the exclusive
     * case name and case directory locks; the manifest file locks are the first
     * locks released.
     */
    abstract void deleteAfterManifestLocksReleased() throws InterruptedException;

    /**
     * Deletes the parts of the case that need to be deleted after the release
     * of the exclusive manifest file locks and case directory lock, while still
     * holding the exclusive case name; the case name lock is the last lock
     * released.
     */
    abstract void deleteAfterCaseDirectoryLockReleased() throws InterruptedException;

    /**
     * Deletes the parts of the case that need to be deleted after the release
     * of all of the exclusive locks; the case name lock is the last lock
     * released.
     */
    abstract void deleteAfterCaseNameLockReleased() throws InterruptedException;

    /**
     * Deletes the auto ingest job input directories for the case. Intended to
     * be called by subclasses, if required, in their customization of the
     * deleteWhileHoldingAllLocks step of the case deletion algorithm.
     */
    @NbBundle.Messages({
        "# {0} - input directory name", "DeleteCaseTask.progress.deletingInputDir=Deleting input directory {0}..."
    })
    protected void deleteInputDirectories() {
        boolean allInputDirsDeleted = true;
        for (AutoIngestJobNodeData jobNodeData : nodeDataForAutoIngestJobs) {
            Path inputDirPath = jobNodeData.getManifestFilePath().getParent();
            File inputDir = inputDirPath.toFile();
            if (inputDir.exists()) {
                progress.progress(Bundle.DeleteCaseTask_progress_deletingInputDir(inputDirPath));
                logger.log(Level.INFO, String.format("Deleting input directory %s for %s (%s) in %s", inputDirPath, caseDisplayName, caseUniqueName, caseDirectoryPath));
                if (!FileUtil.deleteDir(new File(inputDirPath.toString()))) {
                    logger.log(Level.WARNING, String.format("Failed to delete the input directory %s for %s (%s) in %s", inputDirPath, caseDisplayName, caseUniqueName, caseDirectoryPath));
                    allInputDirsDeleted = false;
                }
            }
        }
        if (allInputDirsDeleted) {
            setDeletedItemFlag(CaseNodeData.DeletedFlags.DATA_SOURCES);
        }
    }

    /**
     * Deletes the case database, the text index, and the case directory for the
     * case. Intended to be called by subclasses, if required, in their
     * customization of the deleteWhileHoldingAllLocks step of the case deletion
     * algorithm.
     */
    @NbBundle.Messages({
        "DeleteCaseTask.progress.locatingCaseMetadataFile=Locating case metadata file...",
        "DeleteCaseTask.progress.deletingCaseOutput=Deleting case database, text index, and directory...",
        "DeleteCaseTask.progress.deletingJobLogLockNode=Deleting case auto ingest job log lock node..."
    })
    protected void deleteCaseOutput() {
        progress.progress(Bundle.DeleteCaseTask_progress_locatingCaseMetadataFile());
        logger.log(Level.INFO, String.format("Locating metadata file for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
        CaseMetadata caseMetadata = null;
        final File caseDirectory = caseDirectoryPath.toFile();
        final File[] filesInDirectory = caseDirectory.listFiles();
        if (filesInDirectory != null) {
            for (File file : filesInDirectory) {
                if (file.getName().toLowerCase().endsWith(CaseMetadata.getFileExtension()) && file.isFile()) {
                    try {
                        caseMetadata = new CaseMetadata(Paths.get(file.getPath()));
                    } catch (CaseMetadata.CaseMetadataException ex) {
                        logger.log(Level.WARNING, String.format("Error getting opening case metadata file for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
                    }
                    break;
                }
            }
        }

        if (caseMetadata != null) {
            progress.progress(Bundle.DeleteCaseTask_progress_deletingCaseOutput());
            logger.log(Level.INFO, String.format("Deleting output for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
            Case.deleteMultiUserCase(caseNodeData, caseMetadata, progress); // RJCTODO: Make this method throw the interrupted exception.
        } else {
            logger.log(Level.WARNING, String.format("Failed to locate metadata file for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
        }

        progress.progress(Bundle.DeleteCaseTask_progress_deletingJobLogLockNode());
        logger.log(Level.INFO, String.format("Deleting case auto ingest job log lock node for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
        Path logFilePath = AutoIngestJobLogger.getLogPath(caseDirectoryPath); //RJCTODO: USe util here
        try {
            coordinationService.deleteNode(CategoryNode.CASES, logFilePath.toString());
        } catch (CoordinationServiceException ex) {
            logger.log(Level.WARNING, String.format("Error deleting case auto ingest job log lock node for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
        } catch (InterruptedException ex) {
            logger.log(Level.INFO, String.format("Deletion of %s (%s) in %s cancelled", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
        }
    }

    /**
     * Deletes the manifest file lock coordination service nodes for the case.
     * Intended to be called by subclasses, if required, in their customization
     * of the deleteAfterManifestLocksReleased step of the case deletion
     * algorithm.
     */
    @Messages({
        "DeleteCaseTask.progress.deletingManifestFileLockNodes=Deleting manifest file lock nodes..."
    })
    protected void deleteManifestFileLockNodes() throws InterruptedException {
        boolean allInputDirsDeleted = true;
        progress.progress(Bundle.DeleteCaseTask_progress_deletingManifestFileLockNodes());
        logger.log(Level.INFO, String.format("Deleting manifest file lock nodes for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath));
        for (AutoIngestJobNodeData jobNodeData : nodeDataForAutoIngestJobs) {
            try {
                logger.log(Level.INFO, String.format("Deleting manifest file lock node for %s for %s (%s) in %s", jobNodeData.getManifestFilePath(), caseDisplayName, caseUniqueName, caseDirectoryPath));
                coordinationService.deleteNode(CoordinationService.CategoryNode.MANIFESTS, jobNodeData.getManifestFilePath().toString());
            } catch (CoordinationServiceException ex) {
                logger.log(Level.WARNING, String.format("Error deleting manifest file lock node %s for %s (%s) in %s", jobNodeData.getManifestFilePath(), caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
                allInputDirsDeleted = false;
            }
        }
        if (allInputDirsDeleted) {
            setDeletedItemFlag(CaseNodeData.DeletedFlags.MANIFEST_FILE_LOCK_NODES);
        }
        // RJCTODO: Expand case type in case metadata to include auto ingest cases.
        // Disable delete menu item for auto ingest cases, and possibly also add data source
        // capability.
    }

    /**
     * Deletes the case directory coordination service lock node for the case.
     * Intended to be called by subclasses, if required, in their customization
     * of the deleteAfterCaseDirectoryLockReleased step of the case deletion
     * algorithm.
     */
    @Messages({
        "DeleteCaseTask.progress.deletingDirLockNode=Deleting case directory lock coordination service node..."
    })
    protected void deleteCaseDirectoryLockNode() throws InterruptedException {
        progress.progress(Bundle.DeleteCaseTask_progress_deletingDirLockNode());
        try {
            Case.deleteCaseDirectoryLockNode(caseNodeData, progress); // RJCTODO: Case does not need to expose this?
        } catch (CoordinationServiceException ex) {
            logger.log(Level.WARNING, String.format("Error deleting case directory lock node for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
        }
    }

    /**
     * Deletes the case name coordination service lock node for the case.
     * Intended to be called by subclasses, if required, in their customization
     * of the deleteAfterCaseNameLockReleased step of the case deletion
     * algorithm.
     *
     * @throws InterruptedException
     */
    @Messages({
        "DeleteCaseTask.progress.deletingNameLockNode=Deleting case name lock node..." // RJCTODO: Use consistent terminology
    })
    protected void deleteCaseNameLockNode() throws InterruptedException {
        progress.progress(Bundle.DeleteCaseTask_progress_deletingNameLockNode());
        try {
            String caseNameLockNodeName = CaseCoordinationServiceUtils.getCaseLockName(caseDirectoryPath);
            coordinationService.deleteNode(CategoryNode.CASES, caseNameLockNodeName);
        } catch (CoordinationServiceException ex) {
            logger.log(Level.WARNING, String.format("Error deleting case name lock node for %s (%s) in %s", caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
        }
    }

    /**
     * Fetches the auto ingest job data from the manifest file lock coordination
     * service nodes for a case.
     *
     * @throws CoordinationServiceException If there is an error interacting
     *                                      with the coordination service.
     * @throws InterruptedException         If the current thread is interrupted
     *                                      while waiting for the coordination
     *                                      service.
     */
    private void getAutoIngestJobNodeData() throws CoordinationServiceException, InterruptedException {
        String caseName = caseDisplayName;
        final List<String> nodeNames = coordinationService.getNodeList(CoordinationService.CategoryNode.MANIFESTS);
        for (String nodeName : nodeNames) {
            try {
                byte[] nodeBytes = coordinationService.getNodeData(CoordinationService.CategoryNode.CASES, nodeName);
                if (nodeBytes == null || nodeBytes.length <= 0) {
                    logger.log(Level.WARNING, String.format("Missing auto ingest job node data for manifest file lock node %s, deleting node", nodeName));
                    try {
                    coordinationService.deleteNode(CategoryNode.MANIFESTS, nodeName);
                    } catch (CoordinationServiceException ex) {
                    logger.log(Level.WARNING, String.format("Failed to delete empty manifest file lock node %s", nodeName));                        
                    }
                    continue;
                }
                AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(nodeBytes);
                if (caseName.equals(nodeData.getCaseName())) {
                    nodeDataForAutoIngestJobs.add(nodeData);
                }
            } catch (CoordinationService.CoordinationServiceException | InvalidDataException ex) {
                logger.log(Level.WARNING, String.format("Failed to get auto ingest job node data for %s", nodeName), ex);
            }
        }
    }

    /**
     * Acquires either all or none of the manifest file locks for a case.
     */
    @NbBundle.Messages({
        "# {0} - manifest file name", "DeleteCaseTask.progress.lockingManifestFile=Acquiring exclusive lock on manifest {0}..."
    })
    private void getManifestFileLocks() {
        for (AutoIngestJobNodeData autoIngestJobNodeData : nodeDataForAutoIngestJobs) {
            String manifestPath = autoIngestJobNodeData.getManifestFilePath().toString();
            try {
                progress.progress(Bundle.DeleteCaseTask_progress_lockingManifestFile(manifestPath));
                logger.log(Level.INFO, String.format("Exclusively locking the manifest %s for %s (%s) in %s", manifestPath, caseDisplayName, caseUniqueName, caseDirectoryPath));
                CoordinationService.Lock inputDirLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifestPath);
                if (null != inputDirLock) {
                    manifestFileLocks.put(manifestPath, inputDirLock);
                } else {
                    logger.log(Level.INFO, String.format("Failed to exclusively lock the manifest %s for %s (%s) in %s", manifestPath, caseDisplayName, caseUniqueName, caseDirectoryPath));
                    releaseManifestFileLocks();
                    manifestFileLocks.clear();
                    break;
                }
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Error exclusively locking the manifest %s for %s (%s) in %s", manifestPath, caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
                releaseManifestFileLocks();
                manifestFileLocks.clear();
                break;
            }
        }
    }

    /**
     * Releases any manifest file coordination service locks that were acquired
     * for the case.
     */
    @NbBundle.Messages({
        "# {0} - manifest file path", "DeleteCaseTask.progress.releasingManifestLock=Releasing the exclusive lock on manifest file {0}..."
    })
    private void releaseManifestFileLocks() {
        if (!manifestFileLocks.isEmpty()) {
            for (Map.Entry<String, CoordinationService.Lock> entry : manifestFileLocks.entrySet()) {
                String manifestFilePath = entry.getKey();
                CoordinationService.Lock manifestFileLock = entry.getValue();
                try {
                    progress.progress(Bundle.DeleteCaseTask_progress_releasingManifestLock(manifestFilePath));
                    logger.log(Level.INFO, String.format("Releasing the exclusive lock on the manifest file %s for %s (%s) in %s", manifestFilePath, caseDisplayName, caseUniqueName, caseDirectoryPath));
                    manifestFileLock.release();
                } catch (CoordinationServiceException ex) {
                    logger.log(Level.SEVERE, String.format("Error releasing exclusive lock on the manifest file %s for %s (%s) in %s", manifestFilePath, caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
                }
            }
        }
    }

    /**
     * Sets a deleted item flag for the case.
     *
     * @param flag The flag to set.
     */
    private void setDeletedItemFlag(CaseNodeData.DeletedFlags flag) {
        try {
            caseNodeData.setDeletedFlag(flag);
            coordinationService.setNodeData(CategoryNode.CASES, caseDirectoryPath.toString(), caseNodeData.toArray());
        } catch (IOException | CoordinationServiceException | InterruptedException ex) {
            logger.log(Level.SEVERE, String.format("Error updating deleted item flag %s for %s (%s) in %s", flag.name(), caseDisplayName, caseUniqueName, caseDirectoryPath), ex);
        }
    }

}
