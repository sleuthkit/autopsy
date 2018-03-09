/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.LocalDiskDSProcessor;
import org.sleuthkit.autopsy.casemodule.LocalFilesDSProcessor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import static org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
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

        logger.log(Level.INFO, "Using Archive Extractor DSP to process archive {0} ", archivePath);

        // extract the archive and pass the extracted folder as input
        try {
            Case currentCase = Case.getOpenCase();

            // create folder to extract archive to
            Path destinationFolder = createDirectoryForFile(archivePath, currentCase.getModuleDirectory());
            if (destinationFolder.toString().isEmpty()) {
                // unable to create directory
                criticalErrorOccurred = true;
                errorMessages.add(String.format("Unable to create directory {0} to extract archive {1} ", new Object[]{destinationFolder.toString(), archivePath}));
                logger.log(Level.SEVERE, "Unable to create directory {0} to extract archive {1} ", new Object[]{destinationFolder.toString(), archivePath});
                return;
            }

            // extract contents of ZIP archive into destination folder
            List<String> extractedFiles = new ArrayList<>();
            int numExtractedFilesRemaining = 0;
            try {
                progressMonitor.setProgressText(String.format("Extracting archive contents to: %s", destinationFolder.toString()));
                extractedFiles = ArchiveUtil.unpackArchiveFile(archivePath, destinationFolder.toString());
                numExtractedFilesRemaining = extractedFiles.size();
            } catch (ArchiveUtil.ArchiveExtractionException ex) {
                // delete extracted contents
                logger.log(Level.SEVERE,"Exception while extracting archive contents into {0}. Deleteing the directory", destinationFolder.toString());
                FileUtils.deleteDirectory(destinationFolder.toFile());
                throw ex;
            }

            // lookup all AutomatedIngestDataSourceProcessors so that we only do it once. 
            // LocalDisk, LocalFiles, and ArchiveDSP are removed from the list.
            List<AutoIngestDataSourceProcessor> processorCandidates = getListOfValidDataSourceProcessors();
            
            // do processing
            for (String file : extractedFiles) {

                // we only care about files, skip directories
                File fileObject = new File(file);
                if (fileObject.isDirectory()) {
                    numExtractedFilesRemaining--;
                    continue;
                }

                // identify all "valid" DSPs that can process this file
                List<AutoIngestDataSourceProcessor> validDataSourceProcessors = getDataSourceProcessorsForFile(Paths.get(file), errorMessages, processorCandidates);
                if (validDataSourceProcessors.isEmpty()) {
                    continue;
                }

                // identified a "valid" data source within the archive
                progressMonitor.setProgressText(String.format("Adding: %s", file));

                /*
                 * NOTE: we have to move the valid data sources to a separate
                 * folder and then add the data source from that folder. This is
                 * necessary because after all valid data sources have been
                 * identified, we are going to add the remaining extracted
                 * contents of the archive as a single logical file set. Hence,
                 * if we do not move the data sources out of the extracted
                 * contents folder, those data source files will get added twice
                 * and can potentially result in duplicate keyword hits.
                 */
                Path newFolder = createDirectoryForFile(file, currentCase.getModuleDirectory());
                if (newFolder.toString().isEmpty()) {
                    // unable to create directory
                    criticalErrorOccurred = true;
                    errorMessages.add(String.format("Unable to create directory {0} to extract content of archive {1} ", new Object[]{newFolder.toString(), archivePath}));
                    logger.log(Level.SEVERE, "Unable to create directory {0} to extract content of archive {1} ", new Object[]{newFolder.toString(), archivePath});
                    return;
                }

                // Copy it to a different folder                     
                FileUtils.copyFileToDirectory(fileObject, newFolder.toFile());
                Path newFilePath = Paths.get(newFolder.toString(), FilenameUtils.getName(file));

                // Try each DSP in decreasing order of confidence
                boolean success = false;
                for (AutoIngestDataSourceProcessor selectedProcessor : validDataSourceProcessors) {

                    logger.log(Level.INFO, "Using {0} to process extracted file {1} ", new Object[]{selectedProcessor.getDataSourceType(), file});
                    synchronized (archiveDspLock) {
                        try {
                            UUID taskId = UUID.randomUUID();
                            currentCase.notifyAddingDataSource(taskId);
                            AutoIngestDataSource internalDataSource = new AutoIngestDataSource(deviceId, newFilePath);
                            DataSourceProcessorCallback internalArchiveDspCallBack = new AddDataSourceCallback(currentCase, internalDataSource, taskId, archiveDspLock);
                            selectedProcessor.process(deviceId, newFilePath, progressMonitor, internalArchiveDspCallBack);
                            archiveDspLock.wait();

                            // at this point we got the content object(s) from the current DSP.
                            // check whether the data source was processed successfully
                            if ((internalDataSource.getResultDataSourceProcessorResultCode() == CRITICAL_ERRORS)
                                    || internalDataSource.getContent().isEmpty()) {
                                // move onto the the next DSP that can process this data source
                                continue;
                            }

                            // if we are here it means the data source was addedd successfully
                            success = true;
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

                if (success) {
                    // one of the DSPs successfully processed the data source. delete the 
                    // copy of the data source in the original extracted archive folder. 
                    // otherwise the data source is going to be added again as a logical file.
                    numExtractedFilesRemaining--;
                    FileUtils.deleteQuietly(fileObject);
                } else {
                    // none of the DSPs were able to process the data source. delete the 
                    // copy of the data source in the temporary folder. the data source is 
                    // going to be added as a logical file with the rest of the extracted contents.
                    FileUtils.deleteQuietly(newFolder.toFile());
                }
            }

            // after all archive contents have been examined (and moved to separate folders if necessary), 
            // add remaining extracted contents as one logical file set
            if (numExtractedFilesRemaining > 0) {
                progressMonitor.setProgressText(String.format("Adding: %s", destinationFolder.toString()));
                logger.log(Level.INFO, "Adding directory {0} as logical file set", destinationFolder.toString());
                synchronized (archiveDspLock) {
                    UUID taskId = UUID.randomUUID();
                    currentCase.notifyAddingDataSource(taskId);
                    AutoIngestDataSource internalDataSource = new AutoIngestDataSource(deviceId, destinationFolder);
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
            }
        } catch (Exception ex) {
            criticalErrorOccurred = true;
            errorMessages.add(ex.getMessage());
            logger.log(Level.SEVERE, String.format("Critical error occurred while extracting archive %s", archivePath), ex); //NON-NLS
        } finally {
            logger.log(Level.INFO, "Finished processing of archive {0}", archivePath);
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

    /**
     * Get a list of data source processors. LocalDisk, LocalFiles, and
     * ArchiveDSP are removed from the list.
     *
     * @return List of data source processors
     */
    private List<AutoIngestDataSourceProcessor> getListOfValidDataSourceProcessors() {

        Collection<? extends AutoIngestDataSourceProcessor> processorCandidates = Lookup.getDefault().lookupAll(AutoIngestDataSourceProcessor.class);

        List<AutoIngestDataSourceProcessor> validDataSourceProcessors = processorCandidates.stream().collect(Collectors.toList());

        for (Iterator<AutoIngestDataSourceProcessor> iterator = validDataSourceProcessors.iterator(); iterator.hasNext();) {
            AutoIngestDataSourceProcessor selectedProcessor = iterator.next();

            // skip local files and local disk DSPs, only looking for "valid" data sources.
            // also skip nested archive files, those will be ingested as logical files and extracted during ingest
            if ((selectedProcessor instanceof LocalDiskDSProcessor)
                    || (selectedProcessor instanceof LocalFilesDSProcessor)
                    || (selectedProcessor instanceof ArchiveExtractorDSProcessor)) {
                iterator.remove();
            }
        }

        return validDataSourceProcessors;
    }

    /**
     * Get a list of data source processors that can process the data source of
     * interest. The list is sorted by confidence in decreasing order.
     *
     * @param dataSourcePath Full path to the data source
     * @param errorMessages  List<String> for error messages
     * @param errorMessages  List of AutoIngestDataSourceProcessor to try
     *
     * @return Ordered list of applicable DSPs
     */
    private List<AutoIngestDataSourceProcessor> getDataSourceProcessorsForFile(Path dataSourcePath, List<String> errorMessages,
            List<AutoIngestDataSourceProcessor> processorCandidates) {

        // Get an ordered list of data source processors to try
        List<AutoIngestDataSourceProcessor> validDataSourceProcessorsForFile = Collections.emptyList();
        try {
            validDataSourceProcessorsForFile = DataSourceProcessorUtility.getOrderedListOfDataSourceProcessors(dataSourcePath, processorCandidates);
        } catch (AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException ex) {
            criticalErrorOccurred = true;
            errorMessages.add(ex.getMessage());
            logger.log(Level.SEVERE, String.format("Critical error occurred while extracting archive %s", archivePath), ex); //NON-NLS
            return Collections.emptyList();
        }
        return validDataSourceProcessorsForFile;
    }

    /**
     * Create a directory in ModuleOutput folder based on input file name. A
     * time stamp is appended to the directory name.
     *
     * @param fileName      File name
     * @param baseDirectory Base directory. Typically the case output directory.
     *
     * @return Full path to the new directory
     */
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
}
