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
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseCoordinationServiceUtils;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CategoryNode;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.Lock;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJobNodeData.InvalidDataException;

/**
 * A task that deletes part or all of a given case. Note that all logging done
 * by this task is directed to the dedicated auto ingest dashboard log instead
 * of to the general application log.
 */
// RJCTODO: 
// 1. Expand case type in case metadata to include auto ingest cases.
// Disable the delete menu item in the main app menu for auto ingest cases, 
// and possibly also use this to delete the add data source capability. Could use
// this to limit the display of nodes in the in the auto ingest cases dashboard.
// 2. When an instance of this class finishes, publish an event via event bus 
// so that the case browser can refresh.
// 3. Add code to file deletion utilities such that on Wimdows, for paths 
// exceeding 255 chars, robocopy is invoked for the deletion. Make the new file 
// deletion utility throw exceptions instead of return a boolean result code. 
// 4. Make other dashbaord use the dashboard logger.
// 5. Consider moving all of the dashboard code into its own autoingest.dashboard package.
// 6. AutoIngestManager.addCompletedJob node data version updating might be out of date.
// 7. Deal with cancellation during lock releases. Look at using
// https://google.github.io/guava/releases/19.0/api/docs/com/google/common/util/concurrent/Uninterruptibles.html
// getUninterruptibly to do os.
// 8. With the removal of the auto ingest control panel, we can eliminate the 
// completed jobs list and the processing list from AutoIngestManager.
final class DeleteCaseTask implements Runnable {

    private static final int MANIFEST_FILE_LOCKING_TIMEOUT_MINS = 5;
    private static final Logger logger = AutoIngestDashboardLogger.getLogger();
    private final CaseNodeData caseNodeData;
    private final DeleteOptions deleteOption;
    private final ProgressIndicator progress;
    private final List<Lock> manifestFileLocks;
    private CoordinationService coordinationService;

    /*
     * Options to support implementing differnet case deletion uses cases.
     */
    public enum DeleteOptions {
        /**
         * Delete the auto ingest job manifests and corresponding data sources,
         * if any, while leaving the manifest file coordination service nodes
         * and the rest of the case intact. The use case is freeing auto ingest
         * input directory space while retaining the option to restore the data
         * sources, effectively restoring the case.
         */
        DELETE_INPUT,
        /**
         * Delete the auto ingest job coordination service nodes, if any, and
         * the output for a case produced via auto ingest, while leaving the
         * auto ingest job input directories intact. The use case is auto ingest
         * reprocessing of a case with a clean slate without having to restore
         * the input directories.
         */
        DELETE_OUTPUT,
        /**
         * Delete everything.
         */
        DELETE_ALL
    }

    /**
     * Constructs a task that deletes part or all of a given case. Note that all
     * logging is directed to the dedicated auto ingest dashboard log instead of
     * to the general application log.
     *
     * @param caseNodeData The case directory coordination service node data for
     *                     the case.
     * @param deleteOption The deletion option for the task.
     * @param progress     A progress indicator.
     */
    DeleteCaseTask(CaseNodeData caseNodeData, DeleteOptions deleteOption, ProgressIndicator progress) {
        this.caseNodeData = caseNodeData;
        this.deleteOption = deleteOption;
        this.progress = progress;
        this.manifestFileLocks = new ArrayList<>();
    }

    @Override
    @NbBundle.Messages({
        "DeleteCaseTask.progress.startMessage=Starting deletion..."
    })
    public void run() {
        try {
            progress.start(Bundle.DeleteCaseTask_progress_startMessage());
            logger.log(Level.INFO, String.format("Starting attempt to delete %s (%s)", caseNodeData.getDisplayName(), deleteOption));
            deleteCase();
            logger.log(Level.INFO, String.format("Finished attempt to delete %s (%s)", caseNodeData.getDisplayName(), deleteOption));

        } catch (Throwable ex) {
            /*
             * This is an unexpected runtime exceptions firewall. It is here
             * because this task is designed to be able to be run in scenarios
             * where there is no call to get() on a Future<Void> associated with
             * the task, so this ensures that any such errors get logged.
             */
            logger.log(Level.SEVERE, String.format("Unexpected error deleting %s", caseNodeData.getDisplayName()), ex);

        } finally {
            progress.finish();
        }
    }

    /**
     * Deletes part or all of the given case.
     */
    @NbBundle.Messages({
        "DeleteCaseTask.progress.connectingToCoordSvc=Connecting to the coordination service...",
        "DeleteCaseTask.progress.acquiringCaseNameLock=Acquiring exclusive case name lock...",
        "DeleteCaseTask.progress.acquiringCaseDirLock=Acquiring exclusive case directory lock...",
        "DeleteCaseTask.progress.acquiringManifestLocks=Acquiring exclusive manifest file locks...",
        "DeleteCaseTask.progress.deletingDirLockNode=Deleting case directory lock coordination service node...",
        "DeleteCaseTask.progress.deletingNameLockNode=Deleting case name lock coordination service node..."
    })
    private void deleteCase() {
        progress.progress(Bundle.DeleteCaseTask_progress_connectingToCoordSvc());
        logger.log(Level.INFO, String.format("Connecting to the coordination service for deletion of %s", caseNodeData.getDisplayName()));
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationService.CoordinationServiceException ex) {
            logger.log(Level.SEVERE, String.format("Could not delete %s because an error occurred connecting to the coordination service", caseNodeData.getDisplayName()), ex);
            return;
        }

        if (Thread.currentThread().isInterrupted()) {
            logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()));
            return;
        }

        /*
         * Acquire an exclusive case name lock. The case name lock is the lock
         * that auto ingest node (AIN) job processing tasks acquire exclusively
         * when creating or opening a case specified in an auto ingest job
         * manifest file. The reason AINs do this is to ensure that only one of
         * them at a time can search the auto ingest output directory for an
         * existing case matching the one in the manifest file. If a matching
         * case is found, it is opened, otherwise the case is created. Acquiring
         * this lock effectively disables this AIN job processing task behavior
         * while the case is being deleted.
         */
        progress.progress(Bundle.DeleteCaseTask_progress_acquiringCaseNameLock());
        logger.log(Level.INFO, String.format("Acquiring an exclusive case name lock for %s", caseNodeData.getDisplayName()));
        String caseNameLockName = CaseCoordinationServiceUtils.getCaseNameLockName(caseNodeData.getDirectory());
        try (CoordinationService.Lock nameLock = coordinationService.tryGetExclusiveLock(CategoryNode.CASES, caseNameLockName)) {
            if (nameLock == null) {
                logger.log(Level.INFO, String.format("Could not delete %s because a case name lock was already held by another host", caseNodeData.getDisplayName()));
                return;
            }

            if (Thread.currentThread().isInterrupted()) {
                logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()));
                return;
            }

            /*
             * Acquire an exclusive case directory lock. A shared case directory
             * lock is acquired by each auto ingest node (AIN) and examiner node
             * (EIN) when it opens a case. The shared locks are held by the AINs
             * and EINs for as long as they have the case open. Acquiring this
             * lock exclusively ensures that no AIN or EIN has the case to be
             * deleted open and prevents another node from trying to open the
             * case while it is being deleted.
             */
            boolean success = true; // RJCTODO: Instead of having this flag, read the casenodedata instead
            progress.progress(Bundle.DeleteCaseTask_progress_acquiringCaseDirLock());
            logger.log(Level.INFO, String.format("Acquiring an exclusive case directory lock for %s", caseNodeData.getDisplayName()));
            String caseDirLockName = CaseCoordinationServiceUtils.getCaseDirectoryLockName(caseNodeData.getDirectory());
            try (CoordinationService.Lock caseDirLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseDirLockName)) {
                if (caseDirLock == null) {
                    logger.log(Level.INFO, String.format("Could not delete %s because a case directory lock was already held by another host", caseNodeData.getDisplayName()));
                    return;
                }

                if (Thread.currentThread().isInterrupted()) {
                    logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()));
                    return;
                }

                /*
                 * Acquire exclusive locks for the auto ingest job manifest
                 * files for the case, if any. Manifest file locks are acquired
                 * by the auto ingest node (AIN) input directory scanning tasks
                 * when they look for auto ingest jobs to enqueue, and by the
                 * AIN job processing tasks when they execute a job. Acquiring
                 * these locks here ensures that the scanning tasks and job
                 * processing tasks cannot do anything with the auto ingest jobs
                 * for a case during case deletion.
                 */
                progress.progress(Bundle.DeleteCaseTask_progress_acquiringManifestLocks());
                logger.log(Level.INFO, String.format("Acquiring exclusive manifest file locks for %s", caseNodeData.getDisplayName()));
                try {
                    if (!acquireManifestFileLocks()) {
                        logger.log(Level.INFO, String.format("Could not delete %s because a manifest file lock was already held by another host", caseNodeData.getDisplayName()));
                        return;
                    }
                } catch (CoordinationServiceException ex) {
                    logger.log(Level.WARNING, String.format("Could not delete %s because an error occurred acquiring the manifest file locks", caseNodeData.getDisplayName()), ex);
                    return;
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()), ex);
                    return;
                }

                if (Thread.currentThread().isInterrupted()) {
                    logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()));
                    releaseManifestFileLocks();
                    return;
                }

                if (deleteOption == DeleteOptions.DELETE_INPUT || deleteOption == DeleteOptions.DELETE_ALL) {
                    try {
                        logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()));
                        deleteAutoIngestInput();
                    } catch (IOException ex) {
                        // RJCTODO:
                    } catch (InterruptedException ex) {
                        logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()), ex);
                        releaseManifestFileLocks();
                        return;
                    }
                }

                if (Thread.currentThread().isInterrupted()) {
                    logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()));
                    releaseManifestFileLocks();
                    return;
                }

                if (deleteOption == DeleteOptions.DELETE_OUTPUT || deleteOption == DeleteOptions.DELETE_ALL) {
                    try {
                        success = deleteCaseOutput();
                    } catch (InterruptedException ex) {
                        logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()), ex);
                        releaseManifestFileLocks();
                        return;
                    }
                }

                if (Thread.currentThread().isInterrupted()) {
                    logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()));
                    releaseManifestFileLocks();
                    return;
                }

                try {
                    if (deleteOption == DeleteOptions.DELETE_OUTPUT || deleteOption == DeleteOptions.DELETE_ALL) {
                        success = deleteManifestFileNodes();
                    } else {
                        releaseManifestFileLocks();
                    }
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()), ex);
                    return;
                }

            } catch (CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Could not delete %s because an error occurred acquiring the case directory lock", caseNodeData.getDisplayName()), ex);
                return;
            }

            if (Thread.currentThread().isInterrupted()) {
                logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()));
                return;
            }

            /*
             * Now that the case directory lock has been released, the
             * coordination service node for it can be deleted if the use case
             * requires it. However, if something to ge deleted was not deleted,
             * leave the node so that what was and was not deleted can be
             * inspected.
             */
            if (success && (deleteOption == DeleteOptions.DELETE_OUTPUT || deleteOption == DeleteOptions.DELETE_ALL)) {
                progress.progress(Bundle.DeleteCaseTask_progress_deletingDirLockNode());
                try {
                    Case.deleteCaseDirectoryLockNode(caseNodeData, progress);
                } catch (CoordinationServiceException ex) {
                    logger.log(Level.WARNING, String.format("Error deleting case directory lock node for %s", caseNodeData.getDisplayName()), ex);
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()), ex);
                    return;
                }
            }

        } catch (CoordinationServiceException ex) {
            logger.log(Level.SEVERE, String.format("Could not delete %s because an error occurred acquiring the case name lock", caseNodeData.getDisplayName()), ex);
            return;
        }

        if (Thread.currentThread().isInterrupted()) {
            logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()));
            return;
        }

        /*
         * Now that the case name lock has been released, the coordination
         * service node for it can be deleted if the use case requires it.
         */
        if (deleteOption == DeleteOptions.DELETE_OUTPUT || deleteOption == DeleteOptions.DELETE_ALL) {
            progress.progress(Bundle.DeleteCaseTask_progress_deletingNameLockNode());
            try {
                String caseNameLockNodeName = CaseCoordinationServiceUtils.getCaseNameLockName(caseNodeData.getDirectory());
                coordinationService.deleteNode(CategoryNode.CASES, caseNameLockNodeName); // RJCTODO: Should this be a Case method?
            } catch (CoordinationServiceException ex) {
                logger.log(Level.WARNING, String.format("Error deleting case name lock node for %s", caseNodeData.getDisplayName()), ex);
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, String.format("Deletion of %s cancelled", caseNodeData.getDisplayName()), ex);
            }
        }
    }

    /**
     * Acquires either all or none of the auto ingest job manifest file locks
     * for a case.
     *
     * @return True if all of the locks were acquired; false otherwise.
     *
     * @throws CoordinationServiceException If there is an error completing a
     *                                      coordination service operation.
     * @throws InterruptedException         If the thread in which this task is
     *                                      running is interrupted while blocked
     *                                      waiting for a coordination service
     *                                      operation to complete.
     */
    @NbBundle.Messages({
        "# {0} - manifest file path", "DeleteCaseTask.progress.lockingManifest=Locking manifest file {0}..."
    })
    private boolean acquireManifestFileLocks() throws CoordinationServiceException, InterruptedException {
        /*
         * Get the "original" case name that from the case directory. This is
         * necessary because the case display name can be changed and the case
         * name may have a time stamp added to make it unique, depending on how
         * the case was created. An alternative aproach would be to strip the
         * time stamp from the case name in the case node data instead, but the
         * code for that is already in the utility method called here.
         */
        String caseName = CaseCoordinationServiceUtils.getCaseNameLockName(caseNodeData.getDirectory());
        try {
            boolean allLocksAcquired = true;
            // RJCTODO: Read in the list of manifests for the case instead of
            // inspecting the nodes this way, once the recording of the 
            // manifests is in place.
            final List<String> nodeNames = coordinationService.getNodeList(CoordinationService.CategoryNode.MANIFESTS);
            for (String manifestPath : nodeNames) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                byte[] nodeBytes = coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestPath);
                if (nodeBytes == null || nodeBytes.length <= 0) {
                    logger.log(Level.WARNING, String.format("Empty coordination service node data found for %s", manifestPath));
                    continue;
                }

                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                AutoIngestJobNodeData nodeData;
                try {
                    nodeData = new AutoIngestJobNodeData(nodeBytes);
                } catch (InvalidDataException ex) {
                    logger.log(Level.WARNING, String.format("Invalid coordination service node data found for %s", manifestPath), ex);
                    continue;
                }

                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                if (caseName.equals(nodeData.getCaseName())) {
                    /*
                     * When acquiring manifest file locks, it is reasonable to
                     * block while acquiring this lock since the auto ingest
                     * node (AIN) input directory scanning tasks do a lot of
                     * short-term acquiring and releasing of manifest file
                     * locks. The assumption here is that the originator of this
                     * case deletion task is not asking for deletion of a case
                     * that has a job an auto ingest node (AIN) job processing
                     * task is working on and that
                     * MANIFEST_FILE_LOCKING_TIMEOUT_MINS is not very long,
                     * anyway, so we can and should wait a bit.
                     */
                    logger.log(Level.INFO, String.format("Exclusively locking the manifest %s for %s", manifestPath, caseNodeData.getDisplayName()));
                    progress.progress(Bundle.DeleteCaseTask_progress_lockingManifest(manifestPath));
                    CoordinationService.Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifestPath, MANIFEST_FILE_LOCKING_TIMEOUT_MINS, TimeUnit.MINUTES);
                    if (null != manifestLock) {
                        manifestFileLocks.add(manifestLock);
                    } else {
                        allLocksAcquired = false;
                        logger.log(Level.INFO, String.format("Failed to exclusively lock the manifest %s because it was already held by another host", manifestPath, caseNodeData.getDisplayName()));
                        releaseManifestFileLocks();
                        break;
                    }
                }
            }
            return allLocksAcquired;

        } catch (CoordinationServiceException | InterruptedException ex) {
            releaseManifestFileLocks();
            throw ex;
        }
    }

    /**
     * Deletes the auto ingest job input manifests for the case along with the
     * corresponding data sources.
     *
     * @throws IOException          If there is an error opening the case
     *                              manifests list file.
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    @NbBundle.Messages({
        "# {0} - manifest file path", "DeleteCaseTask.progress.deletingManifest=Deleting manifest file {0}..."
    })
    private void deleteAutoIngestInput() throws IOException, InterruptedException {
        boolean allInputDeleted = true;
        final Path manifestsListFilePath = Paths.get(caseNodeData.getDirectory().toString(), AutoIngestManager.getCaseManifestsListFileName());
        final Scanner manifestsListFileScanner = new Scanner(manifestsListFilePath);
        while (manifestsListFileScanner.hasNext()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            final String manifestFilePath = manifestsListFileScanner.next();
            final File manifestFile = new File(manifestFilePath);
            if (manifestFile.exists()) {
                // RJCTODO: Parse file, open case database, delete data sources
                // before deleting manifest file
                progress.progress(Bundle.DeleteCaseTask_progress_deletingManifest(manifestFilePath));
                logger.log(Level.INFO, String.format("Deleting manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
                if (manifestFile.delete()) {
                    logger.log(Level.WARNING, String.format("Failed to delete manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
                    allInputDeleted = false;
                }
            }
            if (allInputDeleted) {
                setDeletedItemFlag(CaseNodeData.DeletedFlags.DATA_SOURCES);
            }
        }
    }

    /**
     * Deletes the case database, the text index, the case directory, and the
     * case resources and auto ingest log coordination service lock nodes for
     * the case.
     *
     * @return If true if all of the case output that was found was deleted,
     *         false otherwise.
     *
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    @NbBundle.Messages({
        "DeleteCaseTask.progress.locatingCaseMetadataFile=Locating case metadata file...",
        "DeleteCaseTask.progress.deletingResourcesLockNode=Deleting case resources coordination service node...",
        "DeleteCaseTask.progress.deletingJobLogLockNode=Deleting case auto ingest job coordination service node..."
    })
    private boolean deleteCaseOutput() throws InterruptedException {
        boolean errorsOccurred = false;
        progress.progress(Bundle.DeleteCaseTask_progress_locatingCaseMetadataFile());
        logger.log(Level.INFO, String.format("Locating metadata file for %s", caseNodeData.getDisplayName()));
        CaseMetadata caseMetadata = null;
        final File caseDirectory = caseNodeData.getDirectory().toFile();
        if (caseDirectory.exists()) {
            final File[] filesInDirectory = caseDirectory.listFiles();
            if (filesInDirectory != null) {
                for (File file : filesInDirectory) {
                    if (file.getName().toLowerCase().endsWith(CaseMetadata.getFileExtension()) && file.isFile()) {
                        try {
                            caseMetadata = new CaseMetadata(Paths.get(file.getPath()));
                        } catch (CaseMetadata.CaseMetadataException ex) {
                            logger.log(Level.WARNING, String.format("Error getting opening case metadata file for %s", caseNodeData.getDisplayName()), ex);
                        }
                        break;
                    }
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (caseMetadata != null) {
                logger.log(Level.INFO, String.format("Deleting output for %s", caseNodeData.getDisplayName()));
                errorsOccurred = Case.deleteMultiUserCase(caseNodeData, caseMetadata, progress, logger); // RJCTODO: CHeck for errors occurred?
            } else {
                logger.log(Level.WARNING, String.format("Failed to locate metadata file for %s", caseNodeData.getDisplayName()));
            }
        }

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        progress.progress(Bundle.DeleteCaseTask_progress_deletingResourcesLockNode());
        try {
            Case.deleteCaseResourcesLockNode(caseNodeData, progress);
        } catch (CoordinationServiceException ex) {
            logger.log(Level.WARNING, String.format("Error deleting case resources coordiation service node for %s", caseNodeData.getDisplayName()), ex);
        }

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        // RJCTODO: Check to see if getNodeData return null if the node does not exist;
        // if so, make use of it
        progress.progress(Bundle.DeleteCaseTask_progress_deletingJobLogLockNode());
        logger.log(Level.INFO, String.format("Deleting case auto ingest job log coordiation service node for %s", caseNodeData.getDisplayName()));
        String logFilePath = CaseCoordinationServiceUtils.getCaseAutoIngestLogLockName(caseNodeData.getDirectory());
        try {
            coordinationService.deleteNode(CategoryNode.CASES, logFilePath);
        } catch (CoordinationServiceException ex) {
            logger.log(Level.WARNING, String.format("Error deleting case auto ingest job log coordiation service node for %s", caseNodeData.getDisplayName()), ex);
        }

        return errorsOccurred;
    }

    /**
     * Releases all of the manifest file locks that have been acquired by this
     * task.
     */
    @NbBundle.Messages({
        "# {0} - manifest file path", "DeleteCaseTask.progress.releasingManifestLock=Releasing the exclusive coordination service lock on the manifest file {0}..."
    })
    private void releaseManifestFileLocks() {
        for (Lock manifestFileLock : manifestFileLocks) {
            String manifestFilePath = manifestFileLock.getNodePath();
            try {
                progress.progress(Bundle.DeleteCaseTask_progress_releasingManifestLock(manifestFilePath));
                logger.log(Level.INFO, String.format("Releasing the exclusive coordination service lock on the manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
                manifestFileLock.release();
            } catch (CoordinationServiceException ex) {
                logger.log(Level.WARNING, String.format("Error releasing the exclusive coordination service lock on the manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()), ex);
            }
        }
        manifestFileLocks.clear();
    }

    /**
     * Releases all of the manifest file locks that have been acquired by this
     * task and attempts to delete the corresponding coordination service nodes.
     *
     * @return True if all of the manifest file coordianiton service nodes have
     *         been deleted, false otherwise.
     *
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    @Messages({
        "# {0} - manifest file path", "DeleteCaseTask.progress.deletingManifestFileNode=Deleting the manifest file coordination service node for {0}..."
    })
    private boolean deleteManifestFileNodes() throws InterruptedException {
        boolean allINodesDeleted = true;
        for (Lock manifestFileLock : manifestFileLocks) {
            String manifestFilePath = manifestFileLock.getNodePath();
            try {
                progress.progress(Bundle.DeleteCaseTask_progress_releasingManifestLock(manifestFilePath));
                logger.log(Level.INFO, String.format("Releasing the exclusive coordination service lock on the manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
                manifestFileLock.release();
                progress.progress(Bundle.DeleteCaseTask_progress_deletingManifestFileNode(manifestFilePath));
                logger.log(Level.INFO, String.format("Deleting the manifest file coordination service node for %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
                coordinationService.deleteNode(CoordinationService.CategoryNode.MANIFESTS, manifestFilePath);
            } catch (CoordinationServiceException ex) {
                allINodesDeleted = false;
                logger.log(Level.WARNING, String.format("Error deleting the manifest file coordination service node for %s for %s", manifestFilePath, caseNodeData.getDisplayName()), ex);
            }
        }
        manifestFileLocks.clear();
        return allINodesDeleted;
    }

    /**
     * Sets a deleted item flag in the coordination service node data for the
     * case.
     *
     * @param flag The flag to set.
     */
    private void setDeletedItemFlag(CaseNodeData.DeletedFlags flag) {
        try {
            caseNodeData.setDeletedFlag(flag);
            coordinationService.setNodeData(CategoryNode.CASES, caseNodeData.getDirectory().toString(), caseNodeData.toArray());
        } catch (IOException | CoordinationServiceException | InterruptedException ex) {
            logger.log(Level.SEVERE, String.format("Error updating deleted item flag %s for %s", flag.name(), caseNodeData.getDisplayName()), ex);
        }
    }

}
