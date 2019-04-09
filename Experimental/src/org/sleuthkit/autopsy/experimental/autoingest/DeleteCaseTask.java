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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData.CaseNodeDataException;
import org.sleuthkit.autopsy.casemodule.multiusercases.CoordinationServiceUtils;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CategoryNode;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.Lock;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJobNodeData.InvalidDataException;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A task that deletes part or all of a given case. Note that all logging is
 * directed to the dedicated auto ingest dashboard log instead of to the general
 * application log.
 */
final class DeleteCaseTask implements Runnable {

    private static final int MANIFEST_FILE_LOCKING_TIMEOUT_MINS = 5;
    private static final int MANIFEST_DELETE_TRIES = 3;
    private static final Logger logger = AutoIngestDashboardLogger.getLogger();
    private final CaseNodeData caseNodeData;
    private final DeleteOptions deleteOption;
    private final ProgressIndicator progress;
    private final List<ManifestFileLock> manifestFileLocks;
    private CoordinationService coordinationService;
    private CaseMetadata caseMetadata;

    /**
     * Options to support implementing different case deletion use cases.
     */
    enum DeleteOptions {
        /**
         * Delete the auto ingest job manifests and corresponding data sources,
         * while leaving the manifest file coordination service nodes and the
         * rest of the case intact. The use case is freeing auto ingest input
         * directory space while retaining the option to restore the data
         * sources, effectively restoring the case.
         */
        DELETE_INPUT,
        /**
         * Delete the manifest file coordination service nodes and the output
         * for a case, while leaving the auto ingest job manifests and
         * corresponding data sources intact. The use case is auto ingest
         * reprocessing of a case with a clean slate without having to restore
         * the manifests and data sources.
         */
        DELETE_OUTPUT,
        /**
         * Delete everything.
         */
        DELETE_INPUT_AND_OUTPUT,
        /**
         * Delete only the case components that the application created. This is
         * DELETE_OUTPUT with the additional feature that manifest file
         * coordination service nodes are marked as deleted, rather than
         * actually deleted. This eliminates the requirement that manifests and
         * data sources have to be deleted before deleting the case to avoid an
         * unwanted, automatic reprocessing of the case.
         */
        DELETE_CASE
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
        manifestFileLocks = new ArrayList<>();
    }

    @Override
    @NbBundle.Messages({
        "DeleteCaseTask.progress.startMessage=Starting deletion..."
    })
    public void run() {
        try {
            progress.start(Bundle.DeleteCaseTask_progress_startMessage());
            logger.log(Level.INFO, String.format("Starting deletion of %s (%s)", caseNodeData.getDisplayName(), deleteOption));
            deleteCase();
            logger.log(Level.INFO, String.format("Finished deletion of %s (%s)", caseNodeData.getDisplayName(), deleteOption));

        } catch (CoordinationServiceException | IOException ex) {
            logger.log(Level.SEVERE, String.format("Error deleting %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);

        } catch (InterruptedException ex) {
            logger.log(Level.WARNING, String.format("Deletion of %s cancelled while incomplete", caseNodeData.getDisplayName()), ex);
            Thread.currentThread().interrupt();

        } catch (Exception ex) {
            /*
             * This is an unexpected runtime exceptions firewall. It is here
             * because this task is designed to be able to be run in scenarios
             * where there is no call to get() on a Future<Void> associated with
             * the task, so this ensures that any such errors get logged.
             */
            logger.log(Level.SEVERE, String.format("Unexpected error deleting %s", caseNodeData.getDisplayName()), ex);
            throw ex;

        } finally {
            releaseManifestFileLocks();
            progress.finish();
        }
    }

    /**
     * Deletes part or all of the given case.
     *
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    @NbBundle.Messages({
        "DeleteCaseTask.progress.connectingToCoordSvc=Connecting to the coordination service...",
        "DeleteCaseTask.progress.acquiringCaseNameLock=Acquiring exclusive case name lock...",
        "DeleteCaseTask.progress.acquiringCaseDirLock=Acquiring exclusive case directory lock...",
        "DeleteCaseTask.progress.gettingManifestPaths=Getting manifest file paths...",
        "DeleteCaseTask.progress.acquiringManifestLocks=Acquiring exclusive manifest file locks...",
        "DeleteCaseTask.progress.openingCaseMetadataFile=Opening case metadata file...",
        "DeleteCaseTask.progress.deletingResourcesLockNode=Deleting case resources znode...",
        "DeleteCaseTask.progress.deletingJobLogLockNode=Deleting case auto ingest log znode...",
        "DeleteCaseTask.progress.deletingCaseDirCoordSvcNode=Deleting case directory znode...",
        "DeleteCaseTask.progress.deletingCaseNameCoordSvcNode=Deleting case name znode..."
    })
    private void deleteCase() throws CoordinationServiceException, IOException, InterruptedException {
        progress.progress(Bundle.DeleteCaseTask_progress_connectingToCoordSvc());
        logger.log(Level.INFO, String.format("Connecting to the coordination service for deletion of %s", caseNodeData.getDisplayName()));
        coordinationService = CoordinationService.getInstance();
        checkForCancellation();

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
        String caseNameLockName = CoordinationServiceUtils.getCaseNameNodePath(caseNodeData.getDirectory());
        try (CoordinationService.Lock nameLock = coordinationService.tryGetExclusiveLock(CategoryNode.CASES, caseNameLockName)) {
            if (nameLock == null) {
                logger.log(Level.INFO, String.format("Could not delete %s because a case name lock was already held by another host", caseNodeData.getDisplayName()));
                return;
            }
            checkForCancellation();

            /*
             * Acquire an exclusive case directory lock. A shared case directory
             * lock is acquired by each auto ingest node (AIN) and examiner node
             * (EIN) when it opens a case. The shared locks are held by the AINs
             * and EINs for as long as they have the case open. Acquiring this
             * lock exclusively ensures that no AIN or EIN has the case to be
             * deleted open and prevents another node from trying to open the
             * case while it is being deleted.
             */
            progress.progress(Bundle.DeleteCaseTask_progress_acquiringCaseDirLock());
            logger.log(Level.INFO, String.format("Acquiring an exclusive case directory lock for %s", caseNodeData.getDisplayName()));
            String caseDirLockName = CoordinationServiceUtils.getCaseDirectoryNodePath(caseNodeData.getDirectory());
            try (CoordinationService.Lock caseDirLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseDirLockName)) {
                if (caseDirLock == null) {
                    logger.log(Level.INFO, String.format("Could not delete %s because a case directory lock was already held by another host", caseNodeData.getDisplayName()));
                    return;
                }
                checkForCancellation();

                /*
                 * Acquire exclusive locks for the auto ingest job manifest
                 * files for the case, if any. Manifest file locks are acquired
                 * by the auto ingest node (AIN) input directory scanning tasks
                 * when they look for auto ingest jobs to enqueue, and by the
                 * AIN job execution tasks when they do a job. Acquiring these
                 * locks here ensures that the scanning tasks and job execution
                 * tasks cannot do anything with the auto ingest jobs for a case
                 * during case deletion.
                 */
                if (!acquireManifestFileLocks()) {
                    logger.log(Level.INFO, String.format("Could not delete %s because at least one manifest file lock was already held by another host", caseNodeData.getDisplayName()));
                    return;
                }
                checkForCancellation();

                deleteCaseContents();
                checkForCancellation();

                deleteCaseResourcesNode();
                checkForCancellation();

                deleteCaseAutoIngestLogNode();
                checkForCancellation();

                deleteManifestFileNodes();
                checkForCancellation();
            }

            deleteCaseDirectoryNode();
            checkForCancellation();
        }

        deleteCaseNameNode();
    }

    /**
     * Gets the manifest file paths for the case, if there are any.
     *
     * @throws CoordinationServiceException If there is an error completing a
     *                                      coordination service operation.
     * @throws InterruptedException         If the thread in which this task is
     *                                      running is interrupted while blocked
     *                                      waiting for a coordination service
     *                                      operation to complete.
     * @throws IOException                  If there is an error reading the
     *                                      manifests list file.
     */
    private List<Path> getManifestFilePaths() throws IOException, CoordinationServiceException, InterruptedException {
        progress.progress(Bundle.DeleteCaseTask_progress_gettingManifestPaths());
        logger.log(Level.INFO, String.format("Getting manifest file paths for %s", caseNodeData.getDisplayName()));
        final Path manifestsListFilePath = Paths.get(caseNodeData.getDirectory().toString(), AutoIngestManager.getCaseManifestsListFileName());
        final File manifestListsFile = manifestsListFilePath.toFile();
        if (manifestListsFile.exists()) {
            return getManifestPathsFromFile(manifestsListFilePath);
        } else {
            return getManifestPathsFromNodes();
        }
    }

    /**
     * Gets a list of the manifest file paths for the case by reading them from
     * the manifests list file for the case.
     *
     * @param manifestsListFilePath The path of the manifests list file.
     *
     * @return A list of manifest file paths, possibly empty.
     *
     * @throws IOException          If there is an error reading the manifests
     *                              list file.
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    private List<Path> getManifestPathsFromFile(Path manifestsListFilePath) throws IOException, InterruptedException {
        final List<Path> manifestFilePaths = new ArrayList<>();
        try (final Scanner manifestsListFileScanner = new Scanner(manifestsListFilePath)) {
            while (manifestsListFileScanner.hasNextLine()) {
                checkForCancellation();
                final Path manifestFilePath = Paths.get(manifestsListFileScanner.nextLine());
                if (manifestFilePath.toFile().exists()) {
                    manifestFilePaths.add(manifestFilePath);
                }
            }
        }
        return manifestFilePaths;
    }

    /**
     * Gets a list of the manifest file paths for the case by sifting through
     * the node data of the manifest file coordination service nodes and
     * matching on case name.
     *
     * @return A list of manifest file paths, possibly empty.
     *
     * @throws CoordinationServiceException If there is an error completing a
     *                                      coordination service operation.
     * @throws InterruptedException         If the thread in which this task is
     *                                      running is interrupted while blocked
     *                                      waiting for a coordination service
     *                                      operation to complete.
     */
    private List<Path> getManifestPathsFromNodes() throws CoordinationServiceException, InterruptedException {
        /*
         * Get the original, undecorated case name from the case directory. This
         * is necessary because the case display name can be changed and the
         * original case name may have a time stamp added to make it unique,
         * depending on how the case was created. An alternative aproach would
         * be to strip off any time stamp from the case name in the case node
         * data.
         */
        String caseName = CoordinationServiceUtils.getCaseNameNodePath(caseNodeData.getDirectory());
        final List<Path> manifestFilePaths = new ArrayList<>();
        final List<String> nodeNames = coordinationService.getNodeList(CoordinationService.CategoryNode.MANIFESTS);
        for (String manifestNodeName : nodeNames) {
            checkForCancellation();
            try {
                final byte[] nodeBytes = coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestNodeName);
                AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(nodeBytes);
                if (caseName.equals(nodeData.getCaseName())) {
                    Path manifestFilePath = nodeData.getManifestFilePath();
                    if (manifestFilePath.toFile().exists()) {
                        manifestFilePaths.add(manifestFilePath);
                    }
                }
            } catch (CoordinationServiceException | InvalidDataException ex) {
                logger.log(Level.WARNING, String.format("Error getting coordination service node data from %s", manifestNodeName), ex);
            }
        }
        return manifestFilePaths;
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
    private boolean acquireManifestFileLocks() throws IOException, CoordinationServiceException, InterruptedException {
        boolean allLocksAcquired = true;
        List<Path> manifestFilePaths = getManifestFilePaths();
        logger.log(Level.INFO, String.format("Found %d manifest file path(s) for %s", manifestFilePaths.size(), caseNodeData.getDisplayName()));
        if (!manifestFilePaths.isEmpty()) {
            progress.progress(Bundle.DeleteCaseTask_progress_acquiringManifestLocks());
            logger.log(Level.INFO, String.format("Acquiring exclusive manifest file locks for %s", caseNodeData.getDisplayName()));
            /*
             * When acquiring the locks, it is reasonable to block briefly,
             * since the auto ingest node (AIN) input directory scanning tasks
             * do a lot of short-term acquiring and releasing of the same locks.
             * The assumption here is that the originator of this case deletion
             * task is not asking for deletion of a case that has a job that an
             * auto ingest node (AIN) job execution task is working on and that
             * MANIFEST_FILE_LOCKING_TIMEOUT_MINS is not very long anyway, so
             * waiting a bit should be fine.
             */
            try {
                for (Path manifestPath : manifestFilePaths) {
                    checkForCancellation();
                    progress.progress(Bundle.DeleteCaseTask_progress_lockingManifest(manifestPath.toString()));
                    logger.log(Level.INFO, String.format("Exclusively locking the manifest %s for %s", manifestPath, caseNodeData.getDisplayName()));
                    CoordinationService.Lock manifestLock = coordinationService.tryGetExclusiveLock(CoordinationService.CategoryNode.MANIFESTS, manifestPath.toString(), MANIFEST_FILE_LOCKING_TIMEOUT_MINS, TimeUnit.MINUTES);
                    if (null != manifestLock) {
                        manifestFileLocks.add(new ManifestFileLock(manifestPath, manifestLock));
                    } else {
                        logger.log(Level.INFO, String.format("Failed to exclusively lock the manifest %s because it was already held by another host", manifestPath, caseNodeData.getDisplayName()));
                        allLocksAcquired = false;
                        releaseManifestFileLocks();
                        break;
                    }
                }
            } catch (CoordinationServiceException | InterruptedException ex) {
                releaseManifestFileLocks();
                throw ex;
            }
        } else {
            setDeletedItemFlag(CaseNodeData.DeletedFlags.MANIFEST_FILE_NODES);
        }
        return allLocksAcquired;
    }

    /**
     * Deletes case contents, based on the specified deletion option.
     *
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    private void deleteCaseContents() throws InterruptedException {
        final File caseDirectory = caseNodeData.getDirectory().toFile();
        if (caseDirectory.exists()) {
            progress.progress(Bundle.DeleteCaseTask_progress_openingCaseMetadataFile());
            logger.log(Level.INFO, String.format("Opening case metadata file for %s", caseNodeData.getDisplayName()));
            Path caseMetadataPath = CaseMetadata.getCaseMetadataFilePath(caseNodeData.getDirectory());
            if (caseMetadataPath != null) {
                try {
                    caseMetadata = new CaseMetadata(caseMetadataPath);
                    checkForCancellation();
                    if (!manifestFileLocks.isEmpty()) {
                        if (deleteOption == DeleteOptions.DELETE_INPUT || deleteOption == DeleteOptions.DELETE_INPUT_AND_OUTPUT) {
                            deleteAutoIngestInput();
                        } else if (deleteOption == DeleteOptions.DELETE_CASE) {
                            markManifestFileNodesAsDeleted();
                        }
                    }
                    checkForCancellation();
                    if (deleteOption == DeleteOptions.DELETE_OUTPUT || deleteOption == DeleteOptions.DELETE_INPUT_AND_OUTPUT || deleteOption == DeleteOptions.DELETE_CASE) {
                        Case.deleteMultiUserCase(caseNodeData, caseMetadata, progress, logger);
                    }

                } catch (CaseMetadata.CaseMetadataException ex) {
                    logger.log(Level.SEVERE, String.format("Error reading metadata file for %s", caseNodeData.getDisplayName()), ex);
                }

            } else {
                logger.log(Level.WARNING, String.format("No case metadata file found for %s", caseNodeData.getDisplayName()));
            }

        } else {
            setDeletedItemFlag(CaseNodeData.DeletedFlags.CASE_DIR);
            logger.log(Level.INFO, String.format("No case directory found for %s", caseNodeData.getDisplayName()));
        }
    }

    /**
     * Deletes the auto ingest job input manifests for the case along with the
     * corresponding data sources.
     *
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    @NbBundle.Messages({
        "DeleteCaseTask.progress.openingCaseDatabase=Opening the case database...",
        "# {0} - manifest file path", "DeleteCaseTask.progress.parsingManifest=Parsing manifest file {0}..."
    })
    private void deleteAutoIngestInput() throws InterruptedException {
        SleuthkitCase caseDb = null;
        try {
            progress.progress(Bundle.DeleteCaseTask_progress_openingCaseDatabase());
            logger.log(Level.INFO, String.format("Opening the case database for %s", caseNodeData.getDisplayName()));
            caseDb = SleuthkitCase.openCase(caseMetadata.getCaseDatabaseName(), UserPreferences.getDatabaseConnectionInfo(), caseMetadata.getCaseDirectory());
            List<DataSource> dataSources = caseDb.getDataSources();
            checkForCancellation();

            /*
             * For every manifest file associated with the case, attempt to
             * delete both the data source referenced by the manifest and the
             * manifest.
             */
            boolean allInputDeleted = true;
            for (ManifestFileLock lock : manifestFileLocks) {
                checkForCancellation();
                Path manifestFilePath = lock.getManifestFilePath();
                final File manifestFile = manifestFilePath.toFile();
                if (manifestFile.exists()) {
                    Manifest manifest = parseManifestFile(manifestFilePath);
                    if (manifest != null) {
                        if (deleteDataSources(manifest, dataSources) && deleteManifestFile(manifestFile)) {
                            lock.setInputDeleted();
                        } else {
                            allInputDeleted = false;
                        }
                    } else {
                        logger.log(Level.WARNING, String.format("Failed to parse manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
                        allInputDeleted = false;
                    }
                } else {
                    logger.log(Level.WARNING, String.format("Did not find manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
                }
            }
            if (allInputDeleted) {
                setDeletedItemFlag(CaseNodeData.DeletedFlags.DATA_SOURCES);
            }

        } catch (TskCoreException | UserPreferencesException ex) {
            logger.log(Level.INFO, String.format("Failed to open or query the case database for %s", caseNodeData.getDisplayName()), ex);

        } finally {
            if (caseDb != null) {
                caseDb.close();
            }
        }
    }

    /**
     * Parses a manifest file.
     *
     * @param manifestFilePath The manifest file path.
     *
     * @return A manifest, if the parsing is successful, null otherwise.
     */
    private Manifest parseManifestFile(Path manifestFilePath) {
        progress.progress(Bundle.DeleteCaseTask_progress_parsingManifest(manifestFilePath));
        logger.log(Level.INFO, String.format("Parsing manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
        Manifest manifest = null;
        for (ManifestFileParser parser : Lookup.getDefault().lookupAll(ManifestFileParser.class)) {
            if (parser.fileIsManifest(manifestFilePath)) {
                try {
                    manifest = parser.parse(manifestFilePath);
                    break;
                } catch (ManifestFileParser.ManifestFileParserException ex) {
                    logger.log(Level.WARNING, String.format("Error parsing manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()), ex);
                }
            }
        }
        return manifest;
    }

    /**
     * Deletes a manifest file.
     *
     * @param manifestFile The manifest file.
     *
     * @return True if the file was deleted, false otherwise.
     *
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    @NbBundle.Messages({
        "# {0} - manifest file path", "DeleteCaseTask.progress.deletingManifest=Deleting manifest file {0}..."
    })
    private boolean deleteManifestFile(File manifestFile) throws InterruptedException {
        /*
         * Delete the manifest file, allowing a few retries. This is a way to
         * resolve the race condition between this task and auto ingest node
         * (AIN) input directory scanning tasks, which parse manifests (actually
         * all files) before getting a coordination service lock, without
         * resorting to a protocol using locking of the input directory.
         */
        Path manifestFilePath = manifestFile.toPath();
        progress.progress(Bundle.DeleteCaseTask_progress_deletingManifest(manifestFilePath));
        logger.log(Level.INFO, String.format("Deleting manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
        int tries = 0;
        boolean deleted = false;
        while (!deleted && tries < MANIFEST_DELETE_TRIES) {
            deleted = manifestFile.delete();
            if (!deleted) {
                ++tries;
                Thread.sleep(1000);
            }
        }
        if (!deleted) {
            logger.log(Level.WARNING, String.format("Failed to delete manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
        }
        return deleted;
    }

    /**
     * Locates and deletes the data source files referenced by a manifest.
     *
     * @param manifest    A manifest.
     * @param dataSources The data sources in the case as obtained from the case
     *                    database.
     *
     * @return True if all of the data source files werre deleted, false
     *         otherwise.
     */
    @NbBundle.Messages({
        "# {0} - data source path", "DeleteCaseTask.progress.deletingDataSource=Deleting data source {0}..."
    })
    private boolean deleteDataSources(Manifest manifest, List<DataSource> dataSources) {
        final Path dataSourcePath = manifest.getDataSourcePath();
        progress.progress(Bundle.DeleteCaseTask_progress_deletingDataSource(dataSourcePath));
        logger.log(Level.INFO, String.format("Deleting data source %s from %s", dataSourcePath, caseNodeData.getDisplayName()));

        /*
         * There are two possibilities here. The data source may be an image,
         * and if so, it may be split into multiple files. In this case, all of
         * the files for the image need to be deleted. Otherwise, the data
         * source is a single directory or file (a logical file, logical file
         * set, report file, archive file, etc.). In this case, just the file
         * referenced by the manifest will be deleted.
         */
        Set<Path> filesToDelete = new HashSet<>();
        int index = 0;
        while (index < dataSources.size() && filesToDelete.isEmpty()) {
            DataSource dataSource = dataSources.get(index);
            if (dataSource instanceof Image) {
                Image image = (Image) dataSource;
                String[] imageFilePaths = image.getPaths();
                /*
                 * Check for a match between one of the paths for the image
                 * files and the data source file path in the manifest.
                 */
                for (String imageFilePath : imageFilePaths) {
                    Path candidatePath = Paths.get(imageFilePath);
                    if (candidatePath.equals(dataSourcePath)) {
                        /*
                         * If a match is found, add all of the file paths for
                         * the image to the set of files to be deleted.
                         */
                        for (String path : imageFilePaths) {
                            filesToDelete.add(Paths.get(path));
                        }
                        break;
                    }
                }
            }
            ++index;
        }

        /*
         * At a minimum, the data source at the file path given in the manifest
         * should be deleted. If the data source is not a disk image, this will
         * be the path of an archive, a logical file, or a logical directory.
         * TODO-4933: Currently, the contents extracted from an archive are not
         * deleted, nor are any additional files associated with a report data
         * source.
         */
        filesToDelete.add(dataSourcePath);

        /*
         * Delete the file(s).
         */
        boolean allFilesDeleted = true;
        for (Path path : filesToDelete) {
            File fileOrDir = path.toFile();
            if (fileOrDir.exists() && !FileUtil.deleteFileDir(fileOrDir)) {
                allFilesDeleted = false;
                logger.log(Level.WARNING, String.format("Failed to delete data source file at %s for %s", path, caseNodeData.getDisplayName()));
            }
        }

        return allFilesDeleted;
    }

    /**
     * Marks the manifest file coordination service nodes as deleted by setting
     * the auto ingest job processing status field to deleted.
     *
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    private void markManifestFileNodesAsDeleted() throws InterruptedException {
        boolean allNodesMarked = true;
        for (ManifestFileLock manifestFileLock : manifestFileLocks) {
            String manifestFilePath = manifestFileLock.getManifestFilePath().toString();
            try {
                progress.progress(Bundle.DeleteCaseTask_progress_deletingManifestFileNode(manifestFilePath));
                logger.log(Level.INFO, String.format("Marking as deleted the manifest file znode for %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
                final byte[] nodeBytes = coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, manifestFilePath);
                AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(nodeBytes);
                nodeData.setProcessingStatus(AutoIngestJob.ProcessingStatus.DELETED);
                coordinationService.setNodeData(CategoryNode.MANIFESTS, manifestFilePath, nodeData.toArray());
            } catch (CoordinationServiceException | InvalidDataException ex) {
                logger.log(Level.WARNING, String.format("Error marking as deleted the manifest file znode for %s for %s", manifestFilePath, caseNodeData.getDisplayName()), ex);
                allNodesMarked = false;
            }
        }
        if (allNodesMarked) {
            setDeletedItemFlag(CaseNodeData.DeletedFlags.MANIFEST_FILE_NODES);
        }
    }

    /**
     * Deletes the case resources coordination service node.
     *
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    private void deleteCaseResourcesNode() throws InterruptedException {
        if (deleteOption == DeleteOptions.DELETE_OUTPUT || deleteOption == DeleteOptions.DELETE_INPUT_AND_OUTPUT || deleteOption == DeleteOptions.DELETE_CASE) {
            progress.progress(Bundle.DeleteCaseTask_progress_deletingResourcesLockNode());
            logger.log(Level.INFO, String.format("Deleting case resources log znode for %s", caseNodeData.getDisplayName()));
            String resourcesNodePath = CoordinationServiceUtils.getCaseResourcesNodePath(caseNodeData.getDirectory());
            try {
                coordinationService.deleteNode(CategoryNode.CASES, resourcesNodePath);
            } catch (CoordinationServiceException ex) {
                if (!DeleteCaseUtils.isNoNodeException(ex)) {
                    logger.log(Level.SEVERE, String.format("Error deleting case resources znode for %s", caseNodeData.getDisplayName()), ex);
                }
            }
        }
    }

    /**
     * Deletes the case auto ingest log coordination service node.
     *
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    private void deleteCaseAutoIngestLogNode() throws InterruptedException {
        if (deleteOption == DeleteOptions.DELETE_OUTPUT || deleteOption == DeleteOptions.DELETE_INPUT_AND_OUTPUT || deleteOption == DeleteOptions.DELETE_CASE) {
            progress.progress(Bundle.DeleteCaseTask_progress_deletingJobLogLockNode());
            logger.log(Level.INFO, String.format("Deleting case auto ingest job log znode for %s", caseNodeData.getDisplayName()));
            String logFilePath = CoordinationServiceUtils.getCaseAutoIngestLogNodePath(caseNodeData.getDirectory());
            try {
                coordinationService.deleteNode(CategoryNode.CASES, logFilePath);
            } catch (CoordinationServiceException ex) {
                if (!DeleteCaseUtils.isNoNodeException(ex)) {
                    logger.log(Level.SEVERE, String.format("Error deleting case auto ingest job log znode for %s", caseNodeData.getDisplayName()), ex);
                }
            }
        }
    }

    /**
     * Deletes the case directory coordination service node if everything that
     * was supposed to be deleted was deleted. Otherwise, leave the node so that
     * what was and was not deleted can be inspected.
     *
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    private void deleteCaseDirectoryNode() throws InterruptedException {
        if (((deleteOption == DeleteOptions.DELETE_OUTPUT || deleteOption == DeleteOptions.DELETE_INPUT_AND_OUTPUT)
                && caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.DATA_SOURCES)
                && caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.CASE_DB)
                && caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.TEXT_INDEX)
                && caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.CASE_DIR)
                && caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.MANIFEST_FILE_NODES))
                || (deleteOption == DeleteOptions.DELETE_CASE
                && caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.CASE_DB)
                && caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.TEXT_INDEX)
                && caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.CASE_DIR)
                && caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.MANIFEST_FILE_NODES))) {
            progress.progress(Bundle.DeleteCaseTask_progress_deletingCaseDirCoordSvcNode());
            logger.log(Level.INFO, String.format("Deleting case directory znode for %s", caseNodeData.getDisplayName()));
            String caseDirNodePath = CoordinationServiceUtils.getCaseDirectoryNodePath(caseNodeData.getDirectory());
            try {
                coordinationService.deleteNode(CategoryNode.CASES, caseDirNodePath);
            } catch (CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Error deleting case directory lock node for %s", caseNodeData.getDisplayName()), ex);
            }
        }
    }

    /**
     * Deletes the case name coordiation service node.
     *
     * @throws InterruptedException If the thread in which this task is running
     *                              is interrupted while blocked waiting for a
     *                              coordination service operation to complete.
     */
    private void deleteCaseNameNode() throws InterruptedException {
        if (deleteOption == DeleteOptions.DELETE_OUTPUT || deleteOption == DeleteOptions.DELETE_INPUT_AND_OUTPUT || deleteOption == DeleteOptions.DELETE_CASE) {
            progress.progress(Bundle.DeleteCaseTask_progress_deletingCaseNameCoordSvcNode());
            logger.log(Level.INFO, String.format("Deleting case name znode for %s", caseNodeData.getDisplayName()));
            try {
                String caseNameLockNodeName = CoordinationServiceUtils.getCaseNameNodePath(caseNodeData.getDirectory());
                coordinationService.deleteNode(CategoryNode.CASES, caseNameLockNodeName);
            } catch (CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Error deleting case name lock node for %s", caseNodeData.getDisplayName()), ex);
            }
        }
    }

    /**
     * Releases all of the manifest file locks that have been acquired by this
     * task.
     */
    @NbBundle.Messages({
        "# {0} - manifest file path", "DeleteCaseTask.progress.releasingManifestLock=Releasing lock on the manifest file {0}..."
    })
    private void releaseManifestFileLocks() {
        for (ManifestFileLock manifestFileLock : manifestFileLocks) {
            String manifestFilePath = manifestFileLock.getManifestFilePath().toString();
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
        "# {0} - manifest file path", "DeleteCaseTask.progress.deletingManifestFileNode=Deleting the manifest file znode for {0}..."
    })
    private void deleteManifestFileNodes() throws InterruptedException {
        if (deleteOption == DeleteOptions.DELETE_OUTPUT || deleteOption == DeleteOptions.DELETE_INPUT_AND_OUTPUT) {
            boolean allINodesDeleted = true;
            Iterator<ManifestFileLock> iterator = manifestFileLocks.iterator();
            while (iterator.hasNext()) {
                ManifestFileLock manifestFileLock = iterator.next();
                String manifestFilePath = manifestFileLock.getManifestFilePath().toString();
                try {
                    progress.progress(Bundle.DeleteCaseTask_progress_releasingManifestLock(manifestFilePath));
                    logger.log(Level.INFO, String.format("Releasing the lock on the manifest file %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
                    manifestFileLock.release();
                    if (manifestFileLock.isInputDeleted()) {
                        progress.progress(Bundle.DeleteCaseTask_progress_deletingManifestFileNode(manifestFilePath));
                        logger.log(Level.INFO, String.format("Deleting the manifest file znode for %s for %s", manifestFilePath, caseNodeData.getDisplayName()));
                        coordinationService.deleteNode(CoordinationService.CategoryNode.MANIFESTS, manifestFilePath);
                    } else {
                        allINodesDeleted = false;
                    }
                } catch (CoordinationServiceException ex) {
                    allINodesDeleted = false;
                    logger.log(Level.WARNING, String.format("Error deleting the manifest file znode for %s for %s", manifestFilePath, caseNodeData.getDisplayName()), ex);
                }
                iterator.remove();
            }
            if (allINodesDeleted) {
                setDeletedItemFlag(CaseNodeData.DeletedFlags.MANIFEST_FILE_NODES);
            }
        }
    }

    /**
     * Sets a deleted item flag in the coordination service node data for the
     * case.
     *
     * @param flag The flag to set.
     *
     * @throws InterruptedException If the interrupted flag is set.
     */
    private void setDeletedItemFlag(CaseNodeData.DeletedFlags flag) throws InterruptedException {
        try {
            caseNodeData.setDeletedFlag(flag);
            CaseNodeData.writeCaseNodeData(caseNodeData);
        } catch (CaseNodeDataException ex) {
            logger.log(Level.SEVERE, String.format("Error updating deleted item flag %s for %s", flag.name(), caseNodeData.getDisplayName()), ex);
        }
    }

    /**
     * Checks whether the interrupted flag of the current thread is set.
     *
     * @throws InterruptedException If the interrupted flag is set.
     */
    private void checkForCancellation() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Interrupt detected");
        }
    }

    /**
     * A wrapper class that bundles a manifest file coordination service lock
     * with a manifest file path and a flag indicating whether or not the case
     * input (manifest file and data source) associated with the lock has been
     * deleted.
     */
    private static class ManifestFileLock {

        private final Path manifestFilePath;
        private final Lock lock;
        private boolean inputDeleted;

        /**
         * Constructs an instance of a wrapper class that bundles a manifest
         * file coordination service lock with a manifest file path and a flag
         * indicating whether or not the case input (manifest file and data
         * source) associated with the lock has been deleted.
         *
         * @param manifestFilePath The manifest file path.
         * @param lock             The coordination service lock.
         */
        private ManifestFileLock(Path manifestFilePath, Lock lock) {
            this.manifestFilePath = manifestFilePath;
            this.lock = lock;
            this.inputDeleted = false;
        }

        /**
         * Gets the path of the manifest file associated with the lock.
         *
         * @return
         */
        Path getManifestFilePath() {
            return this.manifestFilePath;
        }

        /**
         * Sets the flag that indicates whether or not the case input (manifest
         * file and data source) associated with the lock has been deleted.
         */
        private void setInputDeleted() {
            this.inputDeleted = true;
        }

        /**
         * Gets the value of the flag that indicates whether or not the case
         * input (manifest file and data source) associated with the lock has
         * been deleted.
         *
         * @return True or false.
         */
        private boolean isInputDeleted() {
            return this.inputDeleted;
        }

        /**
         * Releases the manifest file lock.
         *
         * @throws CoordinationServiceException If an error occurs while
         *                                      releasing the lock.
         */
        private void release() throws CoordinationServiceException {
            lock.release();
        }
    }

}
