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
 * A task that deletes one or more cases, entirely or in part.
 */
final class CaseDeletionTask implements Runnable {

    private static final Logger logger = Logger.getLogger(CaseDeletionTask.class.getName());
    private final List<CaseNodeData> casesToDelete;
    private final CaseDeletionTaskType deletionTaskType;
    private final ProgressIndicator progress;
    private CoordinationService coordinationService;

    /**
     * An enum specifying the work a case deletion task is to do, depending on
     * the use case.
     */
    enum CaseDeletionTaskType {

        /**
         * To delete the auto ingest job input directories only, while leaving
         * behind the auto ingest job coordination service nodes and leaving the
         * cases otherwise intact, specify this deletion type. The use case is
         * freeing space while retaining the option to restore the input
         * directories, effectively restoring the cases.
         */
        INPUT_ONLY,
        /**
         * To delete the auto ingest job coordination service nodes and the
         * cases, while leaving behind the auto ingest job input directories,
         * specify this deletion type. The use case is when it is desirable to
         * reprocess the cases with a clean slate without having to restore the
         * input directories.
         */
        OUTPUT_ONLY,
        /**
         * To delete the auto ingest job input directories and coordination
         * service nodes and all of the output for the cases, specify this
         * deletion type.
         */
        FULL;

    }

    /**
     * Constructs a task that deletes one or more cases, entirely or in part.
     *
     * @param casesToDelete    The case directory lock coordination service node
     *                         data for the cases to delete.
     * @param deletionTaskType The extent of the deletion to attempt for each
     *                         case.
     * @param progress         A progress indicator.
     */
    CaseDeletionTask(List<CaseNodeData> casesToDelete, CaseDeletionTaskType deletionTaskType, ProgressIndicator progress) {
        this.casesToDelete = casesToDelete;
        this.deletionTaskType = deletionTaskType;
        this.progress = progress;
    }

    @Override
    @NbBundle.Messages({
        "CaseDeletionTask.progress.connectingToCoordSvc=Connecting to the coordination service (ZooKeeper)...",
        "# {0} - exception message", "CaseDeletionTask.progress.errorConnectingToCoordSvc=Failed to connect to the coordination service (see log for details): {0}",
        "# {0} - case name", "CaseDeletionTask.progress.deletingCase=Deleting case {0}...",
        "CaseDeletionTask.progress.acquiringCaseNameLock=Acquiring exclusive case name lock for case...",
        "# {0} - exception message", "CaseDeletionTask.progress.errorLockingCaseName=An error occurred while trying to acquire the exclusive name lock for the case (see log for details): {0}.",
        "CaseDeletionTask.progress.acquiringCaseDirLock=Acquiring exclusive case directory lock for case...",
        "# {0} - exception message", "CaseDeletionTask.progress.errorLockingCaseDir=An error occurred while trying to acquire the exclusive directory lock for the case (see log for details): {0}.",
        "CaseDeletionTask.progress.failedToLockCase=Failed to exclusively lock the case, it may be in use, did not delete.",
        "CaseDeletionTask.progress.gettingJobNodeData=Getting coordination service node data for the auto ingest jobs...",
        "# {0} - exception message", "CaseDeletionTask.progress.errorReleasingInputLock=An error occurred releasing the input directory lock (see log for details): {0}",
        "# {0} - exception message", "CaseDeletionTask.progress.errorDeletingOutput=An error occurred deleting the case output (see log for details): {0}"
    })
    public void run() {
        progress.start(Bundle.CaseDeletionTask_progress_connectingToCoordSvc());
        try {
            try {
                coordinationService = CoordinationService.getInstance();
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, "Failed to connect to the coordination service", ex);
                progress.progress(Bundle.CaseDeletionTask_progress_errorConnectingToCoordSvc(ex.getMessage()));
                return;
            }

            for (CaseNodeData caseNodeData : casesToDelete) {
                progress.progress(Bundle.CaseDeletionTask_progress_deletingCase(caseNodeData.getDisplayName()));

                /*
                 * Acquire an exclusive case name lock. This will prevent auto
                 * ingest nodes from attempting to search for the case directory
                 * before it is deleted.
                 */
                progress.progress(Bundle.CaseDeletionTask_progress_acquiringCaseNameLock());
                final String caseNameLockName = TimeStampUtils.removeTimeStamp(caseNodeData.getName());
                try (CoordinationService.Lock nameLock = coordinationService.tryGetExclusiveLock(CategoryNode.CASES, caseNameLockName)) {
                    if (nameLock == null) {
                        logger.log(Level.SEVERE, String.format("Failed to exclusively lock the case name for %s (%s) in %s, skipping deletion", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                        progress.progress(Bundle.CaseDeletionTask_progress_failedToLockCase());
                        continue;
                    }

                    /*
                     * Acquire an exclusive case directory lock. This will
                     * ensure that no other node (host) currently has the case
                     * open and will prevent another node (host) from trying to
                     * open the case as it is being deleted.
                     */
                    progress.progress(Bundle.CaseDeletionTask_progress_acquiringCaseDirLock());
                    try (CoordinationService.Lock caseLock = CoordinationService.getInstance().tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseNodeData.getDirectory().toString())) {
                        if (caseLock == null) {
                            logger.log(Level.SEVERE, String.format("Failed to exclusively lock the case directory for %s (%s) in %s, skipping deletion", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                            progress.progress(Bundle.CaseDeletionTask_progress_failedToLockCase());
                            continue;
                        }

                        // RJCTODO: Get all locks: case name, case directory, input dirs
                        // Delete input folders, if deleting, record success or failure (second set of bits for partial success?)
                        // Delete output, if deleting, record success or failure 
                        // Delete case locks, if deleting
                        // Delete input locks, if deleting - this will allow reprocessing to start
                        
                        deleteCaseInput(caseNodeData);
                        deleteCaseOutput(caseNodeData);
//                        releaseInputDirectoryLocks();

                    } catch (CoordinationServiceException ex) {
                        logger.log(Level.SEVERE, String.format("Error acquiring the case directory lock for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);
                        progress.progress(Bundle.CaseDeletionTask_progress_errorLockingCaseName(ex.getMessage()));
                    }

                } catch (CoordinationServiceException ex) {
                    logger.log(Level.SEVERE, String.format("Error acquiring the case name lock for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);
                    progress.progress(Bundle.CaseDeletionTask_progress_errorLockingCaseDir(ex.getMessage()));
                }
            }

        } finally {
            progress.finish();
        }

    }

    private void deleteCaseInput(CaseNodeData caseNodeData) throws CoordinationServiceException {
        boolean errorsOccurred = false;

        /*
         * Although it is tempting to do this operation for all of the cases in
         * one go, a considerable amount of time may be required to delete each
         * case, so the decision has been made to go case by case.
         */
        progress.progress(Bundle.CaseDeletionTask_progress_gettingJobNodeData());
        List<AutoIngestJobNodeData> jobNodeDataList = getAutoIngestJobNodeData(caseNodeData.getName());

        /*
         * If deleting input directories only or entire cases, acquire exclusive
         * auto ingest job (manifest file) locks and delete the input
         * directories.
         */
        if (deletionTaskType == CaseDeletionTaskType.INPUT_ONLY || deletionTaskType == CaseDeletionTaskType.FULL) {
            progress.progress(Bundle.CaseDeletionTask_progress_lockingInputDirs());
            Map<Path, CoordinationService.Lock> jobLocks = getInputDirectoryLocks(caseNodeData, jobNodeDataList);
            for (AutoIngestJobNodeData jobNodeData : jobNodeDataList) {
                deleteInputDirectory(caseNodeData, jobNodeData);
                CoordinationService.Lock jobLock = jobLocks.remove(jobNodeData.getManifestFilePath());
            }
        }

        /**
         *
         */
        if (deletionTaskType == CaseDeletionTaskType.OUTPUT_ONLY || deletionTaskType == CaseDeletionTaskType.FULL) {
            for (AutoIngestJobNodeData jobNodeData : jobNodeDataList) {
                coordinationService.deleteNode(CoordinationService.CategoryNode.MANIFESTS, jobNodeData.getManifestFilePath().toString());
            }
        }
    }

    private void deleteCaseOutput(CaseNodeData caseNodeData) {
        if (deletionTaskType == CaseDeletionTaskType.OUTPUT_ONLY || deletionTaskType == CaseDeletionTaskType.FULL) {
            // RJCTODO: Progress message
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
                try {
                    Case.deleteCase(new CaseMetadata(Paths.get(metadataFilePath)), false, progress);
                } catch (CaseMetadata.CaseMetadataException | CaseActionException ex) {
                    // RJCTODO: 
                }
            } else {
                // RJCTODO: 
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
    @NbBundle.Messages({
        "AutoIngestJobsDeletionTask.error.failedToGetJobNodes=Failed to get auto ingest job node data from coordination service."
    })
    private List<AutoIngestJobNodeData> getAutoIngestJobNodeData(String caseName) throws CoordinationServiceException {
        /*
         * Although it is tempting to save time by not doing this operation for
         * each case separately, a considerable amount of time may be required
         * to delete each case, so the decision has been made to get a "fresh"
         * list when each case gets its turn on the chopping back.
         */
        final List<String> nodes = coordinationService.getNodeList(CoordinationService.CategoryNode.MANIFESTS);
        final List<AutoIngestJobNodeData> jobNodeData = new ArrayList<>();
        for (String nodeName : nodes) {
            try {
                byte[] nodeBytes = coordinationService.getNodeData(CoordinationService.CategoryNode.CASES, nodeName);
                if (nodeBytes == null || nodeBytes.length <= 0) {
                    // RJCTODO: Log and indicate, delete node?
                    continue;
                }
                AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(nodeBytes);
                if (caseName.equals(nodeData.getCaseName())) {
                    jobNodeData.add(nodeData);
                }
            } catch (CoordinationService.CoordinationServiceException | InterruptedException | InvalidDataException ex) {
                logger.log(Level.SEVERE, String.format("Error getting coordination service node data for %s", nodeName), ex);
                // RJCTODO: Indicate
            }
        }
        return jobNodeData;
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
        "# {0} - input directory name", "CaseDeletionTask.progress.lockingInputDir=Acquiring exclusive lock on input directory {0}...",
        "# {0} - input directory name", "CaseDeletionTask.progress.failedToLockInputDir=Failed to exclusively lock the input directory {0}.",
        "# {0} - input directory name", "# {1} - exception message", "CaseDeletionTask.progress.errorlockingInputDir=An error occurred Acquiring the exclusive lock on input directory {0} (see log for details): {1}"
    })
    private Map<Path, CoordinationService.Lock> getInputDirectoryLocks(CaseNodeData caseNodeData, List<AutoIngestJobNodeData> autoIngestJobNodeDataList) {
        final Map<Path, CoordinationService.Lock> inputDirLocks = new HashMap<>();
        for (AutoIngestJobNodeData autoIngestJobNodeData : autoIngestJobNodeDataList) {
            final Path inputDirPath = autoIngestJobNodeData.getManifestFilePath().getParent();
            try {
                progress.progress(Bundle.CaseDeletionTask_progress_lockingInputDir(inputDirPath));
                final CoordinationService.Lock inputDirLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, autoIngestJobNodeData.getManifestFilePath().toString());
                if (null != inputDirLock) {
                    inputDirLocks.put(autoIngestJobNodeData.getManifestFilePath(), inputDirLock);
                } else {
                    logger.log(Level.SEVERE, String.format("Failed to exclusively lock the input directory %s for %s (%s) in %s", inputDirPath, caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                    progress.progress(Bundle.CaseDeletionTask_progress_failedToLockInputDir(inputDirPath));
                    releaseInputDirectoryLocks(caseNodeData, inputDirLocks);
                    inputDirLocks.clear();
                }
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Error exclusively locking the input directory %s for %s (%s) in %s", inputDirPath, caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                progress.progress(Bundle.CaseDeletionTask_progress_errorlockingInputDir(inputDirPath, ex.getMessage()));
                releaseInputDirectoryLocks(caseNodeData, inputDirLocks);
                inputDirLocks.clear();
            }
        }
        return inputDirLocks;
    }

    /**
     * Deletes a case input directory.
     *
     * @param caseNodeData              The case node data from the case
     *                                  directory lock node for the case.
     * @param autoIngestJobNodeDataList The auto ingest job node data from the
     *                                  input directory (manifest file path)
     *                                  lock node for the input directory..
     */
    @NbBundle.Messages({
        "# {0} - input directory name", "CaseDeletionTask.progress.deletingInputDir=Deleting input directory {0}...",
        "# {0} - input directory name", "CaseDeletionTask.progress.errorDeletingInputDir=An error occurred deleting the input directory at {0}"
    })
    private void deleteInputDirectory(CaseNodeData caseNodeData, AutoIngestJobNodeData autoIngestJobNodeData) {
        final Path inputDirPath = autoIngestJobNodeData.getManifestFilePath().getParent();
        progress.progress(Bundle.CaseDeletionTask_progress_deletingInputDir(inputDirPath));
        if (FileUtil.deleteDir(new File(inputDirPath.toString()))) {
            logger.log(Level.SEVERE, String.format("Failed to delete the input directory %s for %s (%s) in %s", inputDirPath, caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
            progress.progress(Bundle.CaseDeletionTask_progress_errorDeletingInputDir(inputDirPath));
        }
    }

    /**
     * Releases the coordination service locks in a mapping of manifest file
     * paths to input directory locks for a case.
     *
     * @param caseNodeData  The case node data from the case directory lock node
     *                      for the case.
     * @param inputDirLocks The mapping of manifest files paths to to input
     *                      directory locks for the case.
     */
    @NbBundle.Messages({
        "# {0} - input directory name", "CaseDeletionTask.progress.releasingInputDirLock=Releasing the exclusive lock on input directory {0}...",
        "# {0} - input directory name", "# {1} - exception message", "CaseDeletionTask.progress.errorReleasingInputDirLock=An error occurred releasing the exclusive lock on input directory {0} (see log for details): {1}"
    })
    private void releaseInputDirectoryLocks(CaseNodeData caseNodeData, Map<Path, CoordinationService.Lock> inputDirLocks) {
        for (Map.Entry<Path, CoordinationService.Lock> entry : inputDirLocks.entrySet()) {
            final Path manifestFilePath = entry.getKey();
            final Path inputDirPath = manifestFilePath.getParent();
            final CoordinationService.Lock inputDirLock = entry.getValue();
            try {
                progress.progress(Bundle.CaseDeletionTask_progress_releasingInputDirLock(inputDirPath));
                inputDirLock.release();
            } catch (CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Failed to release exclusive lock on the input directory %s for %s (%s) in %s", inputDirPath, caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()));
                progress.progress(Bundle.CaseDeletionTask_progress_errorReleasingInputDirLock(inputDirPath, ex.getMessage()));
            }
        }
    }

}
