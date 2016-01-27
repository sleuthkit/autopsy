/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.vmextractor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.GeneralFilter;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An ingest module that extracts virtual machine files and adds them to a case
 * as data sources.
 */
final class VMExtractorIngestModule extends DataSourceIngestModuleAdapter {

    private static final Logger logger = Logger.getLogger(VMExtractorIngestModule.class.getName());
    private IngestJobContext context;
    private Path ingestJobOutputDir;

    private final HashMap<String, String> imageFolderToOutputFolder = new HashMap<>();
    private int folderId = 0;

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String timeStamp = dateFormat.format(Calendar.getInstance().getTime());
        String ingestJobOutputDirName = context.getDataSource().getName() + "_" + context.getDataSource().getId() + "_" + timeStamp;
        ingestJobOutputDir = Paths.get(Case.getCurrentCase().getModuleDirectory(), VMExtractorIngestModuleFactory.getModuleName(), ingestJobOutputDirName);
        try {
            Files.createDirectories(ingestJobOutputDir);
        } catch (IOException | SecurityException | UnsupportedOperationException ex) {
            throw new IngestModule.IngestModuleException(NbBundle.getMessage(this.getClass(), "VMExtractorIngestModule.cannotCreateOutputDir.message", ex.getLocalizedMessage()));
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {

        String outputFolderForThisVM;
        List<AbstractFile> vmFiles;

        // Configure and start progress bar - looking for VM files
        progressBar.progress(NbBundle.getMessage(this.getClass(), "VMExtractorIngestModule.searchingImage.message"));
        // Not sure how long it will take for search to complete.
        progressBar.switchToIndeterminate();

        logger.log(Level.INFO, "Looking for virtual machine files in data source {0}", dataSource.getName());
        
        try {
            // look for all VM files
            vmFiles = findVirtualMachineFiles(dataSource);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error querying case database", ex);
            return ProcessResult.ERROR;
        }

        if (vmFiles.isEmpty()) {
            // no VM files found
            logger.log(Level.INFO, "No virtual machine files found in data source {0}", dataSource.getName());
            return ProcessResult.OK;
        }
        // display progress for saving each VM file to disk
        progressBar.switchToDeterminate(vmFiles.size());
        progressBar.progress(NbBundle.getMessage(this.getClass(), "VMExtractorIngestModule.exportingToDisk.message"));
        
        int numFilesSaved = 0;
        for (AbstractFile vmFile : vmFiles) {
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }
            
            logger.log(Level.INFO, "Saving virtual machine file {0} to disk", vmFile.getName());

            // get vmFolderPathInsideTheImage to the folder where VM is located 
            String vmFolderPathInsideTheImage = vmFile.getParentPath();

            // check if the vmFolderPathInsideTheImage is already in hashmap
            if (imageFolderToOutputFolder.containsKey(vmFolderPathInsideTheImage)) {
                // if it is then we have already created output folder to write out all VM files in this parent folder
                outputFolderForThisVM = imageFolderToOutputFolder.get(vmFolderPathInsideTheImage);
            } else {
                // if not - create output folder to write out VM files (can use any unique ID or number for folder name)
                folderId++;
                outputFolderForThisVM = Paths.get(ingestJobOutputDir.toString(), Integer.toString(folderId)).toString();

                // add vmFolderPathInsideTheImage to hashmap
                imageFolderToOutputFolder.put(vmFolderPathInsideTheImage, outputFolderForThisVM);
            }

            // write the vm file to output folder
            try {
                writeVirtualMachineToDisk(vmFile, outputFolderForThisVM);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to write virtual machine file "+vmFile.getName()+" to folder "+outputFolderForThisVM, ex);
                MessageNotifyUtil.Notify.error("Failed to extract virtual machine file", String.format("Failed to write virtual machine file %s to disk", vmFile.getName()));
            }

            // Update progress bar
            numFilesSaved++;
            progressBar.progress(NbBundle.getMessage(this.getClass(), "VMExtractorIngestModule.exportingToDisk.message"), numFilesSaved);
        }
        logger.log(Level.INFO, "Finished saving virtual machine files to disk");

        // update progress bar
        progressBar.switchToDeterminate(imageFolderToOutputFolder.size());
        progressBar.progress(NbBundle.getMessage(this.getClass(), "VMExtractorIngestModule.queuingIngestJobs.message"));
        int numJobsQueued = 0;
        // start processing output folders after we are done writing out all vm files
        for (String folder : imageFolderToOutputFolder.values()) {
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }
            List<String> vmFilesToIngest = VirtualMachineFinderUtility.identifyVirtualMachines(Paths.get(folder));
            for (String file : vmFilesToIngest) {
                try {
                    logger.log(Level.INFO, "Ingesting virtual machine file {0} in folder {1}", new Object[]{file, folder});
                
                    // ingest the data sources                
                    ingestVirtualMachineImage(Paths.get(folder, file));
                    logger.log(Level.INFO, "Ingest complete for virtual machine file {0} in folder {1}", new Object[]{file, folder});
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, "Interrupted while ingesting virtual machine file "+file+" in folder "+folder, ex);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Failed to ingest virtual machine file "+file+" in folder "+folder, ex);
                    MessageNotifyUtil.Notify.error("Failed to ingest virtual machine", String.format("Failed to ingest virtual machine file %s", file));
                }
            }
            // Update progress bar
            numJobsQueued++;
            progressBar.progress(NbBundle.getMessage(this.getClass(), "VMExtractorIngestModule.queuingIngestJobs.message"), numJobsQueued);
        }
        logger.log(Level.INFO, "VMExtractorIngestModule completed processing of data source {0}", dataSource.getName());
        return ProcessResult.OK;
    }

    /**
     * Locate all supported virtual machine files, if any, contained in a data
     * source.
     *
     * @param dataSource The data source.
     *
     * @return A list of virtual machine files, possibly empty.
     *
     * @throws TskCoreException if there is a problem querying the case
     *                          database.
     */
    private static List<AbstractFile> findVirtualMachineFiles(Content dataSource) throws TskCoreException {
        List<AbstractFile> vmFiles = new ArrayList<>();
        for (String vmExtension : GeneralFilter.VIRTUAL_MACHINE_EXTS) {
            String searchString = "%" + vmExtension;    // want a search string that looks like this "%.vmdk"
            vmFiles.addAll(Case.getCurrentCase().getServices().getFileManager().findFiles(dataSource, searchString));
        }
        return vmFiles;
    }

    /**
     * Writes out an abstract file to a specified output folder.
     *
     * @param vmFile                Abstract file to write to disk.
     * @param outputFolderForThisVM Absolute path to output folder.
     *
     * @throws IOException
     */
    private void writeVirtualMachineToDisk(AbstractFile vmFile, String outputFolderForThisVM) throws IOException {

        // TODO: check available disk space first? See IngestMonitor.getFreeSpace()
        // check if output folder exists
        File destinationFolder = Paths.get(outputFolderForThisVM).toFile();
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs();
        }
        /*
         * Write the virtual machine file to disk.
         */
        File localFile = Paths.get(outputFolderForThisVM, vmFile.getName()).toFile();
        ContentUtils.writeToFile(vmFile, localFile);
    }

    /**
     * Add a virtual machine file to the case as a data source and analyze it
     * with the ingest modules.
     *
     * @param vmFile A virtual machine file.
     */
    private void ingestVirtualMachineImage(Path vmFile) throws InterruptedException, IOException {

        /*
         * Try to add the virtual machine file to the case as a data source.
         */
        UUID taskId = UUID.randomUUID();
        Case.getCurrentCase().notifyAddingDataSource(taskId);
        ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
        AddDataSourceCallback dspCallback = new AddDataSourceCallback(vmFile);
        synchronized (this) {
            // for extracted virtual machines we use UUID as data source ID because there is no manifest XML file
            dataSourceProcessor.run(taskId.toString(), vmFile.toString(), "", false, new AddDataSourceProgressMonitor(), dspCallback);
            /*
             * Block the ingest thread until the data source processor finishes.
             */
            this.wait();
        }

        /*
         * If the image was added, analyze it with the ingest modules for this
         * ingest context.
         */
        if (!dspCallback.vmDataSources.isEmpty()) {
            Case.getCurrentCase().notifyDataSourceAdded(dspCallback.vmDataSources.get(0), taskId);
            List<Content> dataSourceContent = new ArrayList<>(dspCallback.vmDataSources);
            IngestJobSettings ingestJobSettings = new IngestJobSettings(context.getExecutionContext());
            for (String warning : ingestJobSettings.getWarnings()) {
                logger.log(Level.WARNING, String.format("Ingest job settings warning for virtual machine file %s : %s", vmFile.toString(), warning));
            }
            IngestServices.getInstance().postMessage(IngestMessage.createMessage(IngestMessage.MessageType.INFO,
                    VMExtractorIngestModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(), "VMExtractorIngestModule.addedVirtualMachineImage.message", vmFile.toString())));
            IngestManager.getInstance().queueIngestJob(dataSourceContent, ingestJobSettings);
        } else {
            Case.getCurrentCase().notifyFailedAddingDataSource(taskId);
        }
    }

    /**
     * A do nothing data source processor progress monitor.
     */
    private static final class AddDataSourceProgressMonitor implements DataSourceProcessorProgressMonitor {

        @Override
        public void setIndeterminate(final boolean indeterminate) {
        }

        @Override
        public void setProgress(final int progress) {
        }

        @Override
        public void setProgressText(final String text) {
        }

    }

    /**
     * A callback for the data source processor that captures the content
     * objects for the data source and unblocks the ingest thread.
     */
    private final class AddDataSourceCallback extends DataSourceProcessorCallback {

        private final Path vmFile;
        private final List<Content> vmDataSources;

        /**
         * Constructs a callback for the data source processor.
         *
         * @param vmFile The virtual machine file to be added as a data source.
         */
        private AddDataSourceCallback(Path vmFile) {
            this.vmFile = vmFile;
            vmDataSources = new ArrayList<>();
        }

        /**
         * @inheritDoc
         */
        @Override
        public void done(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errList, List<Content> content) {
            for (String error : errList) {
                String logMessage = String.format("Data source processor error for virtual machine file %s: %s", vmFile.toString(), error);
                if (DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS == result) {
                    logger.log(Level.SEVERE, logMessage);
                } else {
                    logger.log(Level.WARNING, logMessage);
                }
            }

            /*
             * Save a reference to the content object so it can be used to
             * create a new ingest job.
             */
            if (!content.isEmpty()) {
                vmDataSources.add(content.get(0));
            }

            /*
             * Unblock the ingest thread.
             */
            synchronized (VMExtractorIngestModule.this) {
                VMExtractorIngestModule.this.notify();
            }
        }

        /**
         * @inheritDoc
         */
        @Override
        public void doneEDT(DataSourceProcessorResult result, List<String> errList, List<Content> newContents) {
            done(result, errList, newContents);
        }

    }

}
