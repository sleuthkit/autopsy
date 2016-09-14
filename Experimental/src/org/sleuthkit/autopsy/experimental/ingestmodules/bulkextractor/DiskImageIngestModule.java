/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.ingestmodules.bulkextractor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.ProcTerminationCode;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A data source ingest module that runs the Bulk Extractor executable with an
 * entire disk image as input.
 */
public class DiskImageIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(DiskImageIngestModule.class.getName());
    private final BulkExtractorIngestJobSettings settings;
    private IngestJobContext context;
    private Path bulkExtractorPath;
    private Path rootOutputDirPath;

    /**
     * Constructs a Bulk Extractor disk image ingest module.
     *
     * @param settings The settings for the ingest module.
     */
    DiskImageIngestModule(BulkExtractorIngestJobSettings settings) {
        this.settings = settings;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        // Only process data sources that are disk images the user wants scanned 
        // in their entirety. Scans of unallocated space only are performed by 
        // a file ingest module.
        if (!(context.getDataSource() instanceof Image) || this.settings.shouldOnlyProcessUnallocatedSpace()) {
            return;
        }

        this.bulkExtractorPath = Utilities.locateBulkExtractorExecutable();
        this.rootOutputDirPath = Utilities.createOutputDirectoryForDataSource(this.context.getDataSource().getName() + "_"
                + this.context.getDataSource().getId());
    }

    /**
     * @inheritDoc
     */
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        // Only process data sources that are disk images the user wants scanned 
        // in their entirety. Scans of unallocated space only are performed by 
        // a file ingest module.
        if (!(dataSource instanceof Image) || this.settings.shouldOnlyProcessUnallocatedSpace()) {
            return ProcessResult.OK;
        }

        /**
         * Not sure how long it will take Bulk Extractor to complete.
         */
        progressBar.switchToIndeterminate();

        try {
            // Verify initialization succeeded.
            if ((null == this.bulkExtractorPath) || (null == this.rootOutputDirPath)) {
                DiskImageIngestModule.logger.log(Level.SEVERE, "Bulk Extractor disk image ingest module called after failed start up"); // NON-NLS
                return ProcessResult.ERROR;
            }

            // Make an output directory for Bulk Extractor. 
            Path outputDirPath = Utilities.getOutputSubdirectoryPath(this.rootOutputDirPath, dataSource.getId());
            Files.createDirectories(outputDirPath);

            // Scan the disk image file with Bulk Extractor.
            Image image = (Image) dataSource;
            String imageFilePath = image.getPaths()[0];
            DataSourceIngestModuleProcessTerminator terminator = new DataSourceIngestModuleProcessTerminator(this.context, true);
            int exitValue = Utilities.runBulkExtractor(this.bulkExtractorPath, outputDirPath, Paths.get(imageFilePath), terminator);

            if (terminator.getTerminationCode() == ProcTerminationCode.TIME_OUT) {
                String msg = NbBundle.getMessage(this.getClass(), "BulkExtractorIngestModule.processTerminated") + image.getName(); // NON-NLS
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "BulkExtractorIngestModule.moduleError"), msg); // NON-NLS
                DiskImageIngestModule.logger.log(Level.SEVERE, msg);
                FileUtil.deleteDir(new File(outputDirPath.toAbsolutePath().toString()));
                return IngestModule.ProcessResult.ERROR;
            }

            if (0 != exitValue) {
                FileUtil.deleteDir(new File(outputDirPath.toAbsolutePath().toString()));
                if (this.context.dataSourceIngestIsCancelled()) {
                    return ProcessResult.OK;
                } else {
                    DiskImageIngestModule.logger.log(Level.SEVERE, "Bulk Extractor returned error exit value = {0} when scanning {1}", new Object[]{exitValue, image.getName()}); // NON-NLS
                    return ProcessResult.ERROR;
                }
            }

            if (!Utilities.isDirectoryEmpty(outputDirPath)) {
                // Add the output directory to the case as an Autopsy report.            
                Case.getCurrentCase().addReport(outputDirPath.toAbsolutePath().toString(), Utilities.getModuleName(), Utilities.getReportName(image.getName()));
            }

            return ProcessResult.OK;

        } catch (InterruptedException | IOException | SecurityException | UnsupportedOperationException | TskCoreException ex) {
            DiskImageIngestModule.logger.log(Level.SEVERE, "Error processing " + dataSource.getName() + " with Bulk Extractor", ex); // NON-NLS
            return ProcessResult.ERROR;
        }
    }

}
