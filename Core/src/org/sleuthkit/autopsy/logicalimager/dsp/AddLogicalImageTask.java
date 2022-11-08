/*
 * Autopsy
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.logicalimager.dsp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import org.apache.commons.io.FileUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.utils.LocalFileImporter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A runnable that - copy the logical image folder to a destination folder - add
 * SearchResults.txt and *_users.txt files to report - add an image data source
 * to the case database.
 */
final class AddLogicalImageTask implements Runnable {

    /**
     * Information about a file including the object id of the file as well as
     * the object id of the data source.
     */
    private static class FileId {

        private final long dataSourceId;
        private final long fileId;

        /**
         * Main constructor.
         *
         * @param dataSourceId Object Id of the data source.
         * @param fileId       Object Id of the file.
         */
        FileId(long dataSourceId, long fileId) {
            this.dataSourceId = dataSourceId;
            this.fileId = fileId;
        }

        /**
         * Returns the data source id of the file.
         *
         * @return The data source id of the file.
         */
        long getDataSourceId() {
            return dataSourceId;
        }

        /**
         * Returns the object id of the file.
         *
         * @return The object id of the file.
         */
        long getFileId() {
            return fileId;
        }
    }
    
    private final static Logger LOGGER = Logger.getLogger(AddLogicalImageTask.class.getName());
    private final static String SEARCH_RESULTS_TXT = "SearchResults.txt"; //NON-NLS
    private final static String USERS_TXT = "_users.txt"; //NON-NLS
    private final static String MODULE_NAME = "Logical Imager"; //NON-NLS
    private final static String ROOT_STR = "root"; // NON-NLS
    private final static String VHD_EXTENSION = ".vhd"; // NON-NLS
    private final static int REPORT_PROGRESS_INTERVAL = 100;
    private final static int POST_ARTIFACT_INTERVAL = 1000;
    private final String deviceId;
    private final String timeZone;
    private final File src;
    private final File dest;
    private final Host host;
    private final DataSourceProcessorCallback callback;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final Blackboard blackboard;
    private final Case currentCase;

    private volatile boolean cancelled;
    private volatile boolean createVHD;
    private long totalFiles;
    private Map<String, Long> imagePathToObjIdMap;

    private final Object addMultipleImagesLock;
    @GuardedBy("addMultipleImagesLock")
    private AddMultipleImagesTask addMultipleImagesTask = null;

    AddLogicalImageTask(String deviceId,
            String timeZone,
            File src, File dest, Host host,
            DataSourceProcessorProgressMonitor progressMonitor,
            DataSourceProcessorCallback callback
    ) throws NoCurrentCaseException {
        this.deviceId = deviceId;
        this.timeZone = timeZone;
        this.src = src;
        this.dest = dest;
        this.host = host;
        this.progressMonitor = progressMonitor;
        this.callback = callback;
        this.currentCase = Case.getCurrentCase();
        this.blackboard = this.currentCase.getServices().getArtifactsBlackboard();
        this.addMultipleImagesLock = new Object();
    }

    /**
     * Add SearchResults.txt and *_users.txt to the case report Adds the image
     * to the case database.
     */
    @Messages({
        "# {0} - src", "# {1} - dest", "AddLogicalImageTask.copyingImageFromTo=Copying image from {0} to {1}",
        "AddLogicalImageTask.doneCopying=Done copying",
        "# {0} - src", "# {1} - dest", "AddLogicalImageTask.failedToCopyDirectory=Failed to copy directory {0} to {1}",
        "# {0} - file", "AddLogicalImageTask.addingToReport=Adding {0} to report",
        "# {0} - file", "AddLogicalImageTask.doneAddingToReport=Done adding {0} to report",
        "AddLogicalImageTask.ingestionCancelled=Ingestion cancelled",
        "# {0} - file", "AddLogicalImageTask.failToGetCanonicalPath=Fail to get canonical path for {0}",
        "# {0} - sparseImageDirectory", "AddLogicalImageTask.directoryDoesNotContainSparseImage=Directory {0} does not contain any images",
        "AddLogicalImageTask.noCurrentCase=No current case",
        "AddLogicalImageTask.addingInterestingFiles=Adding search results as interesting files",
        "AddLogicalImageTask.doneAddingInterestingFiles=Done adding search results as interesting files",
        "# {0} - SearchResults.txt", "# {1} - directory", "AddLogicalImageTask.cannotFindFiles=Cannot find {0} in {1}",
        "# {0} - reason", "AddLogicalImageTask.failedToAddInterestingFiles=Failed to add interesting files: {0}",
        "AddLogicalImageTask.addingExtractedFiles=Adding extracted files",
        "AddLogicalImageTask.doneAddingExtractedFiles=Done adding extracted files",
        "# {0} - reason", "AddLogicalImageTask.failedToGetTotalFilesCount=Failed to get total files count: {0}",
        "AddLogicalImageTask.addImageCancelled=Add image cancelled"
    })
    @Override
    public void run() {
        List<String> errorList = new ArrayList<>();
        List<Content> emptyDataSources = new ArrayList<>();

        try {
            progressMonitor.setProgressText(Bundle.AddLogicalImageTask_copyingImageFromTo(src.toString(), dest.toString()));
            FileUtils.copyDirectory(src, dest);
            progressMonitor.setProgressText(Bundle.AddLogicalImageTask_doneCopying());
        } catch (IOException ex) {
            // Copy directory failed
            String msg = Bundle.AddLogicalImageTask_failedToCopyDirectory(src.toString(), dest.toString());
            errorList.add(msg);
        }

        if (cancelled) {
            // Don't delete destination directory once we started adding interesting files.
            // At this point the database and destination directory are complete.
            deleteDestinationDirectory();
            errorList.add(Bundle.AddLogicalImageTask_addImageCancelled());
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
            return;
        }

        // Add the SearchResults.txt and *_users.txt to the case report
        String resultsFilename;
        if (Paths.get(dest.toString(), SEARCH_RESULTS_TXT).toFile().exists()) {
            resultsFilename = SEARCH_RESULTS_TXT;
        } else {
            errorList.add(Bundle.AddLogicalImageTask_cannotFindFiles(SEARCH_RESULTS_TXT, dest.toString()));
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
            return;
        }

        progressMonitor.setProgressText(Bundle.AddLogicalImageTask_addingToReport(resultsFilename));
        String status = addReport(Paths.get(dest.toString(), resultsFilename), resultsFilename + " " + src.getName());
        if (status != null) {
            errorList.add(status);
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
            return;
        }
        progressMonitor.setProgressText(Bundle.AddLogicalImageTask_doneAddingToReport(resultsFilename));

        // All all *_users.txt files to report
        File[] userFiles = dest.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(USERS_TXT);
            }
        });

        for (File userFile : userFiles) {
            progressMonitor.setProgressText(Bundle.AddLogicalImageTask_addingToReport(userFile.getName()));
            status = addReport(userFile.toPath(), userFile.getName() + " " + src.getName());
            if (status != null) {
                errorList.add(status);
                callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
                return;
            }
            progressMonitor.setProgressText(Bundle.AddLogicalImageTask_doneAddingToReport(userFile.getName()));
        }

        // Get all VHD files in the dest directory
        List<String> imagePaths = new ArrayList<>();
        for (File f : dest.listFiles()) {
            if (f.getName().endsWith(VHD_EXTENSION)) {
                try {
                    imagePaths.add(f.getCanonicalPath());
                } catch (IOException ioe) {
                    String msg = Bundle.AddLogicalImageTask_failToGetCanonicalPath(f.getName());
                    errorList.add(msg);
                    callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
                    return;
                }
            }
        }

        Path resultsPath = Paths.get(dest.toString(), resultsFilename);
        try {
            totalFiles = Files.lines(resultsPath).count() - 1; // skip the header line
        } catch (IOException ex) {
            errorList.add(Bundle.AddLogicalImageTask_failedToGetTotalFilesCount(ex.getMessage()));
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
            return;
        }

        List<Content> newDataSources = new ArrayList<>();
        Map<String, List<FileId>> interestingFileMap = new HashMap<>();

        if (imagePaths.isEmpty()) {
            createVHD = false;
            // No VHD in src directory, try ingest the root directory as local files
            File root = Paths.get(dest.toString(), ROOT_STR).toFile();
            if (root.exists() && root.isDirectory()) {
                imagePaths.add(root.getAbsolutePath());
            } else {
                String msg = Bundle.AddLogicalImageTask_directoryDoesNotContainSparseImage(dest);
                errorList.add(msg);
                callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
                return;
            }

            try {
                progressMonitor.setProgressText(Bundle.AddLogicalImageTask_addingExtractedFiles());
                interestingFileMap = addExtractedFiles(dest, resultsPath, host, newDataSources);
                progressMonitor.setProgressText(Bundle.AddLogicalImageTask_doneAddingExtractedFiles());
            } catch (IOException | TskCoreException ex) {
                errorList.add(ex.getMessage());
                LOGGER.log(Level.SEVERE, String.format("Failed to add datasource: %s", ex.getMessage()), ex); // NON-NLS
                callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
                return;
            }
        } else {
            createVHD = true;
            // ingest the VHDs
            try {
                synchronized (addMultipleImagesLock) {
                    if (cancelled) {
                        LOGGER.log(Level.SEVERE, "Add VHD cancelled"); // NON-NLS
                        errorList.add(Bundle.AddLogicalImageTask_addImageCancelled());
                        callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
                        return;
                    }
                    addMultipleImagesTask = new AddMultipleImagesTask(deviceId, imagePaths, timeZone, host, progressMonitor);
                }
                addMultipleImagesTask.run();
                if (addMultipleImagesTask.getResult() == DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS) {
                    LOGGER.log(Level.SEVERE, "Failed to add VHD datasource"); // NON-NLS
                    callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, addMultipleImagesTask.getErrorMessages(), emptyDataSources);
                    return;
                }
                try {
                    interestingFileMap = getInterestingFileMapForVHD(Paths.get(dest.toString(), resultsFilename));
                } catch (TskCoreException | IOException ex) {
                    errorList.add(Bundle.AddLogicalImageTask_failedToAddInterestingFiles(ex.getMessage()));
                    LOGGER.log(Level.SEVERE, "Failed to add interesting files", ex); // NON-NLS
                    callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.NONCRITICAL_ERRORS, errorList, emptyDataSources);
                }

            } catch (NoCurrentCaseException ex) {
                String msg = Bundle.AddLogicalImageTask_noCurrentCase();
                errorList.add(msg);
                callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
                return;
            }
        }

        if (cancelled) {
            if (!createVHD) {
                // TODO: When 5453 is fixed, we should be able to delete it when adding VHD.
                deleteDestinationDirectory();
            }
            errorList.add(Bundle.AddLogicalImageTask_addImageCancelled());
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
            return;
        }

        try {
            progressMonitor.setProgressText(Bundle.AddLogicalImageTask_addingInterestingFiles());
            addInterestingFiles(interestingFileMap);
            progressMonitor.setProgressText(Bundle.AddLogicalImageTask_doneAddingInterestingFiles());
            if (createVHD) {
                callback.done(addMultipleImagesTask.getResult(), addMultipleImagesTask.getErrorMessages(), addMultipleImagesTask.getNewDataSources());
            } else {
                callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS, errorList, newDataSources);
            }
        } catch (IOException | TskCoreException ex) {
            errorList.add(Bundle.AddLogicalImageTask_failedToAddInterestingFiles(ex.getMessage()));
            LOGGER.log(Level.SEVERE, "Failed to add interesting files", ex); // NON-NLS
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.NONCRITICAL_ERRORS, errorList, emptyDataSources);
        }
    }

    /**
     * Add a file specified by the reportPath to the case report.
     *
     * @param reportPath Path to the report to be added
     * @param reportName Name associated the report
     *
     * @returns null if success, or exception message if failure
     *
     */
    @Messages({
        "# {0} - file", "# {1} - exception message", "AddLogicalImageTask.failedToAddReport=Failed to add report {0}. Reason= {1}"
    })
    private String addReport(Path reportPath, String reportName) {
        if (!reportPath.toFile().exists()) {
            return null; // if the reportPath doesn't exist, just ignore it.
        }
        try {
            Case.getCurrentCase().addReport(reportPath.toString(), "LogicalImager", reportName); //NON-NLS
            return null;
        } catch (TskCoreException ex) {
            String msg = Bundle.AddLogicalImageTask_failedToAddReport(reportPath.toString(), ex.getMessage());
            LOGGER.log(Level.SEVERE, String.format("Failed to add report %s. Reason= %s", reportPath.toString(), ex.getMessage()), ex); // NON-NLS
            return msg;
        }
    }

    /**
     * Attempts to cancel the processing of the input image files. May result in
     * partial processing of the input.
     */
    void cancelTask() {
        LOGGER.log(Level.WARNING, "AddLogicalImageTask cancelled, processing may be incomplete"); // NON-NLS
        synchronized (addMultipleImagesLock) {
            cancelled = true;
            if (addMultipleImagesTask != null) {
                addMultipleImagesTask.cancelTask();
            }
        }
    }

    private Map<String, Long> imagePathsToDataSourceObjId(Map<Long, List<String>> imagePaths) {
        Map<String, Long> imagePathToObjId = new HashMap<>();
        for (Map.Entry<Long, List<String>> entry : imagePaths.entrySet()) {
            Long key = entry.getKey();
            List<String> names = entry.getValue();
            for (String name : names) {
                imagePathToObjId.put(name, key);
            }
        }
        return imagePathToObjId;
    }

    @Messages({
        "# {0} - line number", "# {1} - fields length", "# {2} - expected length", "AddLogicalImageTask.notEnoughFields=File does not contain enough fields at line {0}, got {1}, expecting {2}",
        "# {0} - target image path", "AddLogicalImageTask.cannotFindDataSourceObjId=Cannot find obj_id in tsk_image_names for {0}",
        "# {0} - file number", "# {1} - total files", "AddLogicalImageTask.addingInterestingFile=Adding interesting files ({0}/{1})",
        "AddLogicalImageTask.logicalImagerResults=Logical Imager results"
    })
    private void addInterestingFiles(Map<String, List<FileId>> interestingFileMap) throws IOException, TskCoreException {
        int lineNumber = 0;
        List<BlackboardArtifact> artifacts = new ArrayList<>();

        Iterator<Map.Entry<String, List<FileId>>> iterator = interestingFileMap.entrySet().iterator();
        while (iterator.hasNext()) {

            if (cancelled) {
                // Don't delete destination directory once we started adding interesting files.
                // At this point the database and destination directory are complete.
                break;
            }

            Map.Entry<String, List<FileId>> entry = iterator.next();
            String key = entry.getKey();
            String ruleName;
            String[] split = key.split("\t");
            ruleName = split[1];

            List<FileId> fileIds = entry.getValue();
            for (FileId fileId : fileIds) {
                if (cancelled) {
                    postArtifacts(artifacts);
                    return;
                }
                if (lineNumber % REPORT_PROGRESS_INTERVAL == 0) {
                    progressMonitor.setProgressText(Bundle.AddLogicalImageTask_addingInterestingFile(lineNumber, totalFiles));
                }
                if (lineNumber % POST_ARTIFACT_INTERVAL == 0) {
                    postArtifacts(artifacts);
                    artifacts.clear();
                }
                addInterestingFileToArtifacts(fileId.getFileId(), fileId.getDataSourceId(), Bundle.AddLogicalImageTask_logicalImagerResults(), ruleName, artifacts);
                lineNumber++;
            }
            iterator.remove();
        }
        postArtifacts(artifacts);
    }

    private void addInterestingFileToArtifacts(long fileId, long dataSourceId, String ruleSetName, String ruleName, List<BlackboardArtifact> artifacts) throws TskCoreException {
        BlackboardArtifact artifact;
        try {
            artifact = this.blackboard.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_INTERESTING_ITEM, fileId, dataSourceId,
                    Score.SCORE_LIKELY_NOTABLE,
                    null, ruleSetName, null,
                    Arrays.asList(
                            new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, ruleSetName),
                            new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY, MODULE_NAME, ruleName)
                    ))
                    .getAnalysisResult();
        } catch (Blackboard.BlackboardException ex) {
            throw new TskCoreException("Unable to create analysis result.", ex);
        }

        artifacts.add(artifact);
    }

    @Messages({
        "# {0} - file number", "# {1} - total files", "AddLogicalImageTask.searchingInterestingFile=Searching for interesting files ({0}/{1})"
    })
    private Map<String, List<FileId>> getInterestingFileMapForVHD(Path resultsPath) throws TskCoreException, IOException {
        Map<Long, List<String>> objIdToimagePathsMap = currentCase.getSleuthkitCase().getImagePaths();
        imagePathToObjIdMap = imagePathsToDataSourceObjId(objIdToimagePathsMap);
        Map<String, List<FileId>> interestingFileMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(resultsPath.toFile()), "UTF8"))) { // NON-NLS
            String line;
            br.readLine(); // skip the header line
            int lineNumber = 2;
            while ((line = br.readLine()) != null) {
                if (cancelled) {
                    // Don't delete destination directory once we started adding interesting files.
                    // At this point the database and destination directory are complete.
                    break;
                }
                String[] fields = line.split("\t", -1); // NON-NLS
                if (fields.length != 14) {
                    throw new IOException(Bundle.AddLogicalImageTask_notEnoughFields(lineNumber, fields.length, 14));
                }
                String vhdFilename = fields[0];
//                String fileSystemOffsetStr = fields[1];
                String fileMetaAddressStr = fields[2];
//                String extractStatusStr = fields[3];
                String ruleSetName = fields[4];
                String ruleName = fields[5];
//                String description = fields[6];
                String filename = fields[7];
                String parentPath = fields[8];

                if (lineNumber % REPORT_PROGRESS_INTERVAL == 0) {
                    progressMonitor.setProgressText(Bundle.AddLogicalImageTask_searchingInterestingFile(lineNumber, totalFiles));
                }

                String query = makeQuery(vhdFilename, fileMetaAddressStr, parentPath, filename);
                List<AbstractFile> matchedFiles = Case.getCurrentCase().getSleuthkitCase().findAllFilesWhere(query);
                List<FileId> fileIds = new ArrayList<>();
                for (AbstractFile file : matchedFiles) {
                    fileIds.add(new FileId(file.getDataSourceObjectId(), file.getId()));
                }
                String key = String.format("%s\t%s", ruleSetName, ruleName);
                interestingFileMap.computeIfAbsent(key, (k) -> new ArrayList<>())
                        .addAll(fileIds);
                
                lineNumber++;
            } // end reading file
        }
        return interestingFileMap;
    }

    private void postArtifacts(List<BlackboardArtifact> artifacts) {
        try {
            blackboard.postArtifacts(artifacts, MODULE_NAME, null);
        } catch (Blackboard.BlackboardException ex) {
            LOGGER.log(Level.SEVERE, "Unable to post artifacts to blackboard", ex); //NON-NLS
        }
    }

    @Messages({
        "# {0} - file number", "# {1} - total files", "AddLogicalImageTask.addingExtractedFile=Adding extracted files ({0}/{1})"
    })
    private Map<String, List<FileId>> addExtractedFiles(File src, Path resultsPath, Host host, List<Content> newDataSources) throws TskCoreException, IOException {
        SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
        SleuthkitCase.CaseDbTransaction trans = null;
        Map<String, List<FileId>> interestingFileMap = new HashMap<>();

        try {
            trans = skCase.beginTransaction();
            LocalFilesDataSource localFilesDataSource = skCase.addLocalFilesDataSource(deviceId, this.src.getName(), timeZone, host, trans);
            LocalFileImporter fileImporter = new LocalFileImporter(skCase, trans);

            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    new FileInputStream(resultsPath.toFile()), "UTF8"))) { // NON-NLS
                String line;
                br.readLine(); // skip the header line
                int lineNumber = 2;
                while ((line = br.readLine()) != null) {
                    if (cancelled) {
                        rollbackTransaction(trans);
                        return new HashMap<>();
                    }
                    String[] fields = line.split("\t", -1); // NON-NLS
                    if (fields.length != 14) {
                        rollbackTransaction(trans);
                        throw new IOException(Bundle.AddLogicalImageTask_notEnoughFields(lineNumber, fields.length, 14));
                    }
                    String vhdFilename = fields[0];
//                String fileSystemOffsetStr = fields[1];
//                String fileMetaAddressStr = fields[2];
//                String extractStatusStr = fields[3];
                    String ruleSetName = fields[4];
                    String ruleName = fields[5];
//                String description = fields[6];
                    String filename = fields[7];
                    String parentPath = fields[8];
                    String extractedFilePath = fields[9];
                    String crtime = fields[10];
                    String mtime = fields[11];
                    String atime = fields[12];
                    String ctime = fields[13];
                    parentPath = ROOT_STR + "/" + vhdFilename + "/" + parentPath;

                    if (lineNumber % REPORT_PROGRESS_INTERVAL == 0) {
                        progressMonitor.setProgressText(Bundle.AddLogicalImageTask_addingExtractedFile(lineNumber, totalFiles));
                    }

                    //addLocalFile here
                    AbstractFile fileAdded = fileImporter.addLocalFile(
                            Paths.get(src.toString(), extractedFilePath).toFile(),
                            filename,
                            parentPath,
                            Long.parseLong(ctime),
                            Long.parseLong(crtime),
                            Long.parseLong(atime),
                            Long.parseLong(mtime),
                            localFilesDataSource);
                    String key = String.format("%s\t%s", ruleSetName, ruleName);

                    long dataSourceId = fileAdded.getDataSourceObjectId();
                    long fileId = fileAdded.getId();
                    interestingFileMap.computeIfAbsent(key, (k) -> new ArrayList<>())
                            .add(new FileId(dataSourceId, fileId));
                    lineNumber++;
                } // end reading file
            }
            trans.commit();
            newDataSources.add(localFilesDataSource);
            return interestingFileMap;

        } catch (NumberFormatException | TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error adding extracted files", ex); // NON-NLS
            rollbackTransaction(trans);
            throw new TskCoreException("Error adding extracted files", ex);
        }
    }

    private void rollbackTransaction(SleuthkitCase.CaseDbTransaction trans) throws TskCoreException {
        if (null != trans) {
            try {
                trans.rollback();
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, String.format("Failed to rollback transaction: %s", ex.getMessage()), ex); // NON-NLS
            }
        }
    }

    private boolean deleteDestinationDirectory() {
        try {
            FileUtils.deleteDirectory(dest);
            LOGGER.log(Level.INFO, String.format("Cancellation: Deleted directory %s", dest.toString())); // NON-NLS
            return true;
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, String.format("Cancellation: Failed to delete directory %s", dest.toString()), ex);  // NON-NLS
            return false;
        }
    }

    String makeQuery(String vhdFilename, String fileMetaAddressStr, String parentPath, String filename) throws TskCoreException {
        String query;
        String targetImagePath = Paths.get(dest.toString(), vhdFilename).toString();
        Long dataSourceObjId = imagePathToObjIdMap.get(targetImagePath);
        if (dataSourceObjId == null) {
            throw new TskCoreException(Bundle.AddLogicalImageTask_cannotFindDataSourceObjId(targetImagePath));
        }
        query = String.format("data_source_obj_id = '%s' AND meta_addr = '%s' AND name = '%s'", // NON-NLS
                dataSourceObjId.toString(), fileMetaAddressStr, filename.replace("'", "''"));
        // TODO - findAllFilesWhere should SQL-escape the query
        return query;
    }

}
