/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.LocalDiskDSProcessor;
import org.sleuthkit.autopsy.casemodule.LocalFilesDSProcessor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.datamodel.Content;

/*
 * A runnable that adds an archive data source as well as data sources contained
 * in the archive to the case database.
 */
class AddArchiveTask implements Runnable {

    private final Logger logger = Logger.getLogger(AddArchiveTask.class.getName());
    private final String deviceId;
    private final String archivePath;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final DataSourceProcessorCallback callback;
    private boolean criticalErrorOccurred;
    private final Object archiveDspLock;

    private static final String ARCHIVE_EXTRACTOR_MODULE_OUTPUT_DIR = "Archive Extractor";

    /**
     * Constructs a runnable task that adds an archive as well as data sources
     * contained in the archive to the case database.
     *
     * @param deviceId        An ASCII-printable identifier for the device
     *                        associated with the data source that is intended
     *                        to be unique across multiple cases (e.g., a UUID).
     * @param archivePath     Path to the archive file.
     * @param progressMonitor Progress monitor to report progress during
     *                        processing.
     * @param callback        Callback to call when processing is done.
     */
    AddArchiveTask(String deviceId, String archivePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        this.deviceId = deviceId;
        this.archivePath = archivePath;
        this.callback = callback;
        this.progressMonitor = progressMonitor;
        this.archiveDspLock = new Object();
    }

    /**
     * Adds the archive to the case database.
     */
    @Override
    public void run() {
        progressMonitor.setIndeterminate(true);
        List<String> errorMessages = new ArrayList<>();
        List<Content> newDataSources = new ArrayList<>();
        DataSourceProcessorCallback.DataSourceProcessorResult result;
        if (!ArchiveUtil.isArchive(Paths.get(archivePath))) {
            criticalErrorOccurred = true;
            logger.log(Level.SEVERE, String.format("Input data source is not a valid datasource: %s", archivePath)); //NON-NLS
            errorMessages.add("Input data source is not a valid datasource: " + archivePath);
            result = DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS;
            callback.done(result, errorMessages, newDataSources);
        }

        // extract the archive and pass the extracted folder as input
        UUID taskId = UUID.randomUUID();
        if (callback instanceof AddDataSourceCallback) {
            // if running as part of automated ingest - re-use the task ID
            taskId = ((AddDataSourceCallback) callback).getTaskId();
        }
        try {
            Case currentCase = Case.getCurrentCase();

            // create folder to extract archive to
            Path destinationFolder = createDirectoryForFile(archivePath, currentCase.getModuleDirectory());
            if (destinationFolder.toString().isEmpty()) {
                // unable to create directory
                criticalErrorOccurred = true;
                errorMessages.add(String.format("Unable to create directory {0} to extract archive {1} ", new Object[]{destinationFolder.toString(), archivePath}));
                logger.log(Level.SEVERE, String.format("Unable to create directory {0} to extract archive {1} ", new Object[]{destinationFolder.toString(), archivePath}));
                return;
            }

            // extract contents of ZIP archive into destination folder            
            List<String> extractedFiles = ArchiveUtil.unpackArchiveFile(archivePath, destinationFolder.toString());

            // do processing
            Map<AutoIngestDataSourceProcessor, Integer> validDataSourceProcessorsMap;
            for (String file : extractedFiles) {
                
                // identify DSP for this file
                try {
                    // lookup all AutomatedIngestDataSourceProcessors and poll which ones are able to process the current data source
                    validDataSourceProcessorsMap = DataSourceProcessorUtility.getDataSourceProcessor(Paths.get(file));
                    if (validDataSourceProcessorsMap.isEmpty()) {
                        continue;
                    }
                } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException ex) {
                    criticalErrorOccurred = true;
                    errorMessages.add(ex.getMessage());
                    logger.log(Level.SEVERE, String.format("Critical error occurred while extracting archive %s", archivePath), ex); //NON-NLS
                    // continue to next extracted file
                    continue;
                }

                // Get an ordered list of data source processors to try
                List<AutoIngestDataSourceProcessor> validDataSourceProcessors = DataSourceProcessorUtility.orderDataSourceProcessorsByConfidence(validDataSourceProcessorsMap);

                // Try each DSP in decreasing order of confidence
                for (AutoIngestDataSourceProcessor selectedProcessor : validDataSourceProcessors) {

                    // skip local files and local disk DSPs, only looking for "valid" data sources
                    if (selectedProcessor instanceof LocalDiskDSProcessor) {
                        continue;
                    }
                    if (selectedProcessor instanceof LocalFilesDSProcessor) {
                        continue;
                    }
                    // also skip nested archive files, those will be ingested as logical files and extracted during ingest
                    if (selectedProcessor instanceof ArchiveExtractorDSProcessor) {
                        continue;
                    }

                    // identified a "valid" data source within the archive
                    progressMonitor.setProgressText(String.format("Adding: %s", file));

                    /*
                     * NOTE: we have to move the valid data sources to a
                     * separate folder and then add the data source from that
                     * folder. This is necessary because after all valid data
                     * sources have been identified, we are going to add the
                     * remaining extracted contents of the archive as a single
                     * logacl file set. Hence, if we do not move the data
                     * sources out of the extracted contents folder, those data
                     * source files will get added twice and can potentially
                     * result in duplicate keyword hits.
                     */
                    Path newFolder = createDirectoryForFile(file, currentCase.getModuleDirectory());
                    if (newFolder.toString().isEmpty()) {
                        // unable to create directory
                        criticalErrorOccurred = true;
                        errorMessages.add(String.format("Unable to create directory {0} to extract content of archive {1} ", new Object[]{newFolder.toString(), archivePath}));
                        logger.log(Level.SEVERE, String.format("Unable to create directory {0} to extract content of archive {1} ", new Object[]{newFolder.toString(), archivePath}));
                        return;
                    }

                    // Move it to a different folder                     
                    FileUtils.moveFileToDirectory(new File(file), newFolder.toFile(), false);
                    Path newFilePath = Paths.get(newFolder.toString(), FilenameUtils.getName(file));

                    // ELTBD - do we want to log this in case log and/or system admin log?
                    synchronized (archiveDspLock) {
                        try {
                            DataSource internalDataSource = new DataSource(deviceId, newFilePath);
                            DataSourceProcessorCallback internalArchiveDspCallBack = new AddDataSourceCallback(currentCase, internalDataSource, taskId, archiveDspLock);
                            selectedProcessor.process(deviceId, newFilePath, progressMonitor, internalArchiveDspCallBack);
                            archiveDspLock.wait();

                            // at this point we got the content object(s) from the current DSP
                            newDataSources.addAll(internalDataSource.getContent());

                            // skip all other DSPs for this data source
                            break;
                        } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException ex) {
                            // Log that the current DSP failed and set the error flag. We consider it an error
                            // if a DSP fails even if a later one succeeds since we expected to be able to process
                            // the data source which each DSP on the list.
                            criticalErrorOccurred = true;
                            errorMessages.add(ex.getMessage());
                            logger.log(Level.SEVERE, "Exception while processing {0} with data source processor {1}", new Object[]{newFilePath.toString(), selectedProcessor.getDataSourceType()});
                        }
                    }
                }
            }

            // after all archive contents have been examined (and moved to separate folders if necessary), 
            // add remaining extracted contents as one logical file set
            progressMonitor.setProgressText(String.format("Adding: %s", destinationFolder.toString()));
            synchronized (archiveDspLock) {
                DataSource internalDataSource = new DataSource(deviceId, destinationFolder);
                DataSourceProcessorCallback internalArchiveDspCallBack = new AddDataSourceCallback(currentCase, internalDataSource, taskId, archiveDspLock);

                // folder where archive was extracted to
                List<String> pathsList = new ArrayList<>();
                pathsList.add(destinationFolder.toString());

                // use archive file name as the name of the logical file set
                String archiveFileName = FilenameUtils.getName(archivePath);

                LocalFilesDSProcessor localFilesDSP = new LocalFilesDSProcessor();
                localFilesDSP.run(deviceId, archiveFileName, pathsList, progressMonitor, internalArchiveDspCallBack);

                archiveDspLock.wait();

                // at this point we got the content object(s) from the current DSP
                newDataSources.addAll(internalDataSource.getContent());
            }
        } catch (Exception ex) {
            criticalErrorOccurred = true;
            errorMessages.add(ex.getMessage());
            logger.log(Level.SEVERE, String.format("Critical error occurred while extracting archive %s", archivePath), ex); //NON-NLS
        } finally {
            progressMonitor.setProgress(100);
            if (criticalErrorOccurred) {
                result = DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS;
            } else if (!errorMessages.isEmpty()) {
                result = DataSourceProcessorCallback.DataSourceProcessorResult.NONCRITICAL_ERRORS;
            } else {
                result = DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS;
            }
            callback.done(result, errorMessages, newDataSources);
        }
    }

    private Path createDirectoryForFile(String fileName, String baseDirectory) {
        // get file name without full path or extension
        String fileNameNoExt = FilenameUtils.getBaseName(fileName);

        // create folder to extract archive to
        Path newFolder = Paths.get(baseDirectory, ARCHIVE_EXTRACTOR_MODULE_OUTPUT_DIR, fileNameNoExt + "_" + TimeStampUtils.createTimeStamp());
        if (newFolder.toFile().mkdirs() == false) {
            // unable to create directory
            return Paths.get("");
        }
        return newFolder;
    }

    /*
     * Attempts to cancel adding the archive to the case database.
     */
    public void cancelTask() {
        // do a cancelation via future instead
    }
}
