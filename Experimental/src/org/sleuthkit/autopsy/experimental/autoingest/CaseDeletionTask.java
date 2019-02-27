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
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJobNodeData.InvalidDataException;

/**
 * A task that deletes one or more cases, entirely or in part, depending on the
 * use case.
 */
final class CaseDeletionTask implements Runnable {

    private static final Logger logger = Logger.getLogger(CaseDeletionTask.class.getName());
    private final List<CaseNodeData> caseNodeDataList;
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
     * Constructs a task that deletes one or more cases, entirely or in part,
     * depending on the use case.
     *
     * @param caseNodeDataList The coordination service node data for the cases
     *                         to delete.
     * @param deletionTaskType The extent of the deletion to attempt for each
     *                         case.
     * @param progress         A progress indicator.
     */
    CaseDeletionTask(List<CaseNodeData> caseNodeDataList, CaseDeletionTaskType deletionTaskType, ProgressIndicator progress) {
        this.caseNodeDataList = caseNodeDataList;
        this.deletionTaskType = deletionTaskType;
        this.progress = progress;
    }

    @Override
    @NbBundle.Messages({
        "CaseDeletionTask.progress.connectingToCoordSvc=Connecting to coordination service (ZooKeeper)...",
        "# {0} - exception message", "CaseDeletionTask.progress.errorConnectingToCoordSvc=Failed to connect to coordination service (see log for details): {0}",
        "# {0} - case name", "CaseDeletionTask.progress.deletingCase=Deleting {0}...",
        "CaseDeletionTask.progress.lockingCase=Acquiring exclusive lock for case...",
        "# {0} - exception message", "CaseDeletionTask.progress.errorLockingCase=A coordination service error occurred while trying to lock the case (see log for details): {0}.",
        "CaseDeletionTask.progress.failedToLockCase=Failed to exclusively lock the case, it may be in use, did not delete.",
        "CaseDeletionTask.progress.gettingJobNodeData=Getting coordination service node data for the auto ingest jobs...",
        "CaseDeletionTask.progress.lockingJobs=Acquiring exclusive locks on all of the auto ingest job directories...",
        "# {0} - exception message", "CaseDeletionTask.progress.errorDeletingOutput=The following error occurred deleting the case output (see log for details): {0}",})
    public void run() {
        progress.start(Bundle.CaseDeletionTask_progress_connectingToCoordSvc());
        try {
            /*
             * Connect to the coordination service.
             */
            try {
                coordinationService = CoordinationService.getInstance();
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, "Failed to connect to the coordination service", ex);
                progress.progress(Bundle.CaseDeletionTask_progress_errorConnectingToCoordSvc(ex.getMessage()));
                return;
            }

            // RJCTODO: Get the auto ingest job nodes and bucket them by case
            
            for (CaseNodeData caseNodeData : caseNodeDataList) {
                progress.progress(Bundle.CaseDeletionTask_progress_deletingCase(caseNodeData.getDisplayName()));
                progress.progress(Bundle.CaseDeletionTask_progress_lockingCase());

                /*
                 * Get an exclusive lock on the case.
                 *
                 * RJCTODO: Should the case name lock also be obtained and
                 * deleted?
                 */
                try (CoordinationService.Lock caseLock = CoordinationService.getInstance().tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseNodeData.getDirectory().toString())) {
                    if (caseLock == null) {
                        logger.log(Level.WARNING, String.format("Failed to get exclusive lock on %s, skipping", caseNodeData.getDisplayName()));
                        progress.progress(Bundle.CaseDeletionTask_progress_failedToLockCase());
                        continue;
                    }

                    /*
                     * Get all of the auto ingest job coordination service nodes for the case.
                     */
                    progress.progress(Bundle.CaseDeletionTask_progress_gettingJobNodeData());
                    List<AutoIngestJobNodeData> jobNodeDataList = getJobNodeData(caseNodeData.getName());
                    
                    /*
                     * Lock all of the auto ingest job directories for the case.
                     */
                    progress.progress(Bundle.CaseDeletionTask_progress_lockingJobs());
                    Map<Path, CoordinationService.Lock> jobLocks = getJobLocks(jobNodeDataList);
                                        
                    for (AutoIngestJobNodeData jobNodeData : jobNodeDataList) {
                        CoordinationService.Lock jobLock = jobLocks.remove(jobNodeData.getManifestFilePath());

                        if (deletionTaskType == CaseDeletionTaskType.INPUT_ONLY || deletionTaskType == CaseDeletionTaskType.FULL) {
                            deleteAutoIngestNodeDirectory(jobNodeData);
                        }

                        jobLock.release();

                        if (deletionTaskType == CaseDeletionTaskType.OUTPUT_ONLY || deletionTaskType == CaseDeletionTaskType.FULL) {
                            coordinationService.deleteNode(CoordinationService.CategoryNode.MANIFESTS, jobNodeData.getManifestFilePath().toString());
                        }

                    }

                    if (deletionTaskType == CaseDeletionTaskType.OUTPUT_ONLY || deletionTaskType == CaseDeletionTaskType.FULL) {
                        // RJCTODO: Progress message
                        String metadataFilePath = null;
                        File caseDirectory = caseNodeData.getDirectory().toFile();
                        File[] filesInDirectory = caseDirectory.listFiles();
                        if (filesInDirectory != null) {
                            for (File file : filesInDirectory) {
                                if (file.getName().toLowerCase().endsWith(CaseMetadata.getFileExtension()) && file.isFile()) {
                                    metadataFilePath = file.getPath();
                                }
                            }
                        }

                        if (metadataFilePath == null) {
                            continue; // RJCTODO: Or blow away the directory?
                        }

                        try {
                            Case.deleteCase(new CaseMetadata(Paths.get(metadataFilePath)), progress);
                        } catch (CaseMetadata.CaseMetadataException | CaseActionException ex) {
                            // RJCTODO: 
                        }
                        
                    }

                } catch (CoordinationServiceException ex) {
                    logger.log(Level.SEVERE, String.format("Error attempting to acquire exclusive case directory lock for %s", caseNodeData.getName()), ex);
                    progress.progress(Bundle.CaseDeletionTask_progress_errorLockingCase(ex.getMessage()));
                }
            }

        } finally {
            progress.finish();
        }
    }

    /**
     * RJCTODO
     *
     * @param caseName
     *
     * @return
     *
     * @throws CoordinationServiceException If there is a
     */
    @NbBundle.Messages({
        "AutoIngestJobsDeletionTask.error.failedToGetJobNodes=Failed to get auto ingest job node data from coordination service."
    })
    private List<AutoIngestJobNodeData> getJobNodeData(String caseName) throws CoordinationServiceException {
        final List<String> nodes;
        nodes = coordinationService.getNodeList(CoordinationService.CategoryNode.MANIFESTS);
        final List<AutoIngestJobNodeData> jobNodeData = new ArrayList<>();
        for (String nodeName : nodes) {
            try {
                byte[] nodeBytes = coordinationService.getNodeData(CoordinationService.CategoryNode.CASES, nodeName);
                if (nodeBytes == null || nodeBytes.length <= 0) {
                    // Empty node data, indicate
                    // RJCTODO: Delete empty node, indicate success or failure
                    //coordinationService.deleteNode(CoordinationService.CategoryNode.MANIFESTS, nodeName);
                    continue;
                }
                AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(nodeBytes);
                if (caseName.equals(nodeData.getCaseName())) {
                    jobNodeData.add(nodeData);
                }
            } catch (CoordinationService.CoordinationServiceException | InterruptedException | InvalidDataException ex) {
                // RJCTODO: Failed to get node data for a node, indicate
                logger.log(Level.SEVERE, String.format("Error getting coordination service node data for %s", nodeName), ex);
            }
        }
        return jobNodeData;
    }

    /**
     * RJCTODO
     * @param jobNodeData
     * @return 
     */
    private Map<Path, CoordinationService.Lock> getJobLocks(List<AutoIngestJobNodeData> jobNodeData) {
        Map<Path, CoordinationService.Lock> jobLocks = new HashMap<>();
        for (AutoIngestJobNodeData nodeData : jobNodeData) {
            try {
                CoordinationService.Lock lock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, nodeData.getManifestFilePath().toString());
                if (null != lock) {
                    jobLocks.put(nodeData.getManifestFilePath(), lock);
                } else {
                    // RJCTODO: release the locks already obtained and
                    // throw an exception indicating all locks cannot be obtained.
                }
            } catch (CoordinationService.CoordinationServiceException ex) {
                // RJCTODO: Release the locks already obtained and rethrow
            }
        }
        return jobLocks;
    }

    private void deleteAutoIngestNodeDirectory(AutoIngestJobNodeData nodeData) {
        // RJCTODO: Need to strip of file name 
        if (FileUtil.deleteDir(new File(nodeData.getManifestFilePath().toString()))) {
            // RJCTODO: 
        }
    }

}
