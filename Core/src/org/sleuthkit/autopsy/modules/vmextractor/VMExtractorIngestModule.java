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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.ImageDSProcessor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.RunIngestModulesDialog;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An ingest module that extracts virtual machine files and adds them to a case
 * as data sources.
 */
@Immutable
final class VMExtractorIngestModule extends DataSourceIngestModuleAdapter {

    private static final Logger logger = Logger.getLogger(VMExtractorIngestModule.class.getName());
    private final List<Content> vmImages = new ArrayList<>();

    /**
     * @inheritDoc
     */
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        try {
            List<AbstractFile> vmFiles = Case.getCurrentCase().getServices().getFileManager().findFiles(dataSource, "%.img");
            // RJCTODO: Progress bar set up
            for (AbstractFile file : vmFiles) {
                try {
                    ingestVirtualMachineImage(file);
                    // RJCTODO: Progress bar update
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, "Interrupted while adding image", ex); // RJCTODO: Improve logging
                } catch (IOException ex) {
                    logger.log(Level.INFO, "Unable to save VM file to disk", ex); // RJCTODO: Improve logging
                }
            }
            return ProcessResult.OK;
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error querying case database", ex); // RJCTODO: Improve logging
            return ProcessResult.ERROR;
        }
    }

    /**
     * RJCTODO
     *
     * @param vmFile
     */
    private void ingestVirtualMachineImage(AbstractFile vmFile) throws InterruptedException, IOException {
        /*
         * Write the virtual machine file to disk.
         */
        String imageFileName = vmFile.getName() + "_" + vmFile.getId();
        Path imageFilePath = Paths.get(Case.getCurrentCase().getModuleDirectory(), imageFileName);
        File imageFile = imageFilePath.toFile();
        ContentUtils.writeToFile(vmFile, imageFile);

        /*
         * Try to add the virtual machine file to the case as an image.
         */
        vmImages.clear();
        UUID taskId = UUID.randomUUID();
        Case.getCurrentCase().notifyAddingDataSource(taskId);
        ImageDSProcessor dataSourceProcessor = new ImageDSProcessor();
        dataSourceProcessor.setDataSourceOptions(imageFile.getAbsolutePath(), "", false); // RJCTODO: Setting for FAT orphans?
        synchronized (this) {
            dataSourceProcessor.run(new AddDataSourceProgressMonitor(), new AddDataSourceCallback());
            this.wait();
        }

        /*
         * If the image was added, analyze it with the ingest modules for this
         * ingest context.
         */
        if (!vmImages.isEmpty()) {
            Case.getCurrentCase().notifyDataSourceAdded(vmImages.get(0), taskId);
            List<Content> images = new ArrayList<>(vmImages);
            IngestJobSettings ingestJobSettings = new IngestJobSettings(RunIngestModulesDialog.class.getCanonicalName()); // RJCTODO: Problem to solve, context string sharing!
            for (String warning : ingestJobSettings.getWarnings()) {
                logger.log(Level.WARNING, warning);
            }
            IngestManager.getInstance().queueIngestJob(images, ingestJobSettings);
        } else {
            Case.getCurrentCase().notifyFailedAddingDataSource(taskId);
            // RJCTODO: Some logging here
        }
    }

    /**
     * RJCTODO
     */
    // RJCTODO: Consider implementing in terms of the ingest progress monitor
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
     * A callback for the data source processor.
     */
    private final class AddDataSourceCallback extends DataSourceProcessorCallback {

        /**
         * @inheritDoc
         */
        @Override
        public void done(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errList, List<Content> content) {
            /*
             * Save a reference to the content object so it can be used to
             * create a new ingest job.
             */
            if (!content.isEmpty()) {
                vmImages.add(content.get(0));
            }

            // RJCTODO: Log errors if any
            
            /*
             * Unblock the processing thread.
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
