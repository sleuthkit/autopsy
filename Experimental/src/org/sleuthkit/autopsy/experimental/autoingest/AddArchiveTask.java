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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
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
 * A runnable that adds an archive data source as well as data sources
 * contained in the archive to the case database.
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
     * @param deviceId An ASCII-printable identifier for the device associated
     * with the data source that is intended to be unique across multiple cases
     * (e.g., a UUID).
     * @param archivePath Path to the archive file.
     * @param progressMonitor Progress monitor to report progress during
     * processing.
     * @param callback Callback to call when processing is done.
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
        UUID taskId = UUID.randomUUID();    // ELTODO: do we want to come with a way to re-use task id?
        if (callback instanceof AddDataSourceCallback) {
            // if running as part of automated ingest - re-use the task ID
            taskId = ((AddDataSourceCallback) callback).getTaskId();
        }
        try {
            Case currentCase = Case.getCurrentCase();

            // get file name without full path or extension
            String dataSourceFileNameNoExt = FilenameUtils.getBaseName(archivePath);

            // create folder to extract archive to
            Path destinationFolder = Paths.get(currentCase.getModuleDirectory(), ARCHIVE_EXTRACTOR_MODULE_OUTPUT_DIR, dataSourceFileNameNoExt + "_" + TimeStampUtils.createTimeStamp());
            if (destinationFolder.toFile().mkdirs() == false) {
                // unable to create directory
                criticalErrorOccurred = true;
                errorMessages.add("Unable to create directory for archive extraction " + destinationFolder.toString());
                logger.log(Level.SEVERE, "Unable to create directory for archive extraction {0}", destinationFolder.toString());
                return;
            }

            // extract contents of ZIP archive into destination folder            
            List<String> extractedFiles = ArchiveUtil.unpackArchiveFile(archivePath, destinationFolder.toString());

            // do processing
            Map<AutoIngestDataSourceProcessor, Integer> validDataSourceProcessorsMap;
            for (String file : extractedFiles) {
                progressMonitor.setProgressText(String.format("Adding: %s", file));
                Path filePath = Paths.get(file);
                // identify DSP for this file
                // lookup all AutomatedIngestDataSourceProcessors and poll which ones are able to process the current data source
                validDataSourceProcessorsMap = DataSourceProcessorUtility.getDataSourceProcessor(filePath);
                if (validDataSourceProcessorsMap.isEmpty()) {
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
                    
                    //jobLogger.logDataSourceProcessorSelected(selectedProcessor.getDataSourceType());
                    //SYS_LOGGER.log(Level.INFO, "Identified data source type for {0} as {1}", new Object[]{manifestPath, selectedProcessor.getDataSourceType()});
                    synchronized (archiveDspLock) {
                        try {
                            DataSource internalDataSource = new DataSource(deviceId, filePath);
                            DataSourceProcessorCallback internalArchiveDspCallBack = new AddDataSourceCallback(currentCase, internalDataSource, taskId, archiveDspLock);
                            selectedProcessor.process(deviceId, filePath, progressMonitor, internalArchiveDspCallBack);
                            archiveDspLock.wait();

                            // at this point we got the content object(s) from the current DSP
                            newDataSources.addAll(internalDataSource.getContent());

                            break; // skip all other DSPs for this file
                        } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException ex) {
                            // Log that the current DSP failed and set the error flag. We consider it an error
                            // if a DSP fails even if a later one succeeds since we expected to be able to process
                            // the data source which each DSP on the list.
                            //AutoIngestAlertFile.create(caseDirectoryPath);
                            //currentJob.setErrorsOccurred(true);
                            //jobLogger.logDataSourceProcessorError(selectedProcessor.getDataSourceType());
                            criticalErrorOccurred = true;
                            errorMessages.add(ex.getMessage());
                            logger.log(Level.SEVERE, "Exception while processing {0} with data source processor {1}", new Object[]{file, selectedProcessor.getDataSourceType()});
                        }
                    }
                }
            }
            
            // after all archive contents have been ingested - all the archive itself as a logical file
            progressMonitor.setProgressText(String.format("Adding: %s", archivePath));
            LocalFilesDSProcessor localFilesDSP = new LocalFilesDSProcessor();
            synchronized (archiveDspLock) {
                try {
                    Path filePath = Paths.get(archivePath);
                    DataSource internalDataSource = new DataSource(deviceId, filePath);
                    DataSourceProcessorCallback internalArchiveDspCallBack = new AddDataSourceCallback(currentCase, internalDataSource, taskId, archiveDspLock);
                    localFilesDSP.process(deviceId, filePath, progressMonitor, internalArchiveDspCallBack);
                    archiveDspLock.wait();

                    // at this point we got the content object(s) from the current DSP
                    newDataSources.addAll(internalDataSource.getContent());
                } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException ex) {
                    // Log that the current DSP failed and set the error flag. We consider it an error
                    // if a DSP fails even if a later one succeeds since we expected to be able to process
                    // the data source which each DSP on the list.
                    //AutoIngestAlertFile.create(caseDirectoryPath);
                    //currentJob.setErrorsOccurred(true);
                    //jobLogger.logDataSourceProcessorError(selectedProcessor.getDataSourceType());
                    criticalErrorOccurred = true;
                    errorMessages.add(ex.getMessage());
                    logger.log(Level.SEVERE, "Exception while processing {0} with data source processor {1}", new Object[]{archivePath, localFilesDSP.getDataSourceType()});
                }
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

    /*
     * Attempts to cancel adding the archive to the case database.
     */
    public void cancelTask() {
        // do a cancelation via future instead
    }
}
