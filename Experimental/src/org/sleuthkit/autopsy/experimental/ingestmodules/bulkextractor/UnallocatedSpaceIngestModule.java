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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.ProcTerminationCode;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.FileIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * A file ingest module that runs the Bulk Extractor executable with unallocated
 * space files as input.
 */
@NbBundle.Messages({
    "SettingsWrong='Process unallocated space only' is checked for this module, but global 'Process Unallocated Space' is not checked. Unallocated space will not be processed.",
    "Utilities.cannotCreateOutputDir.message=Unable to create output directory."
})
final class UnallocatedSpaceIngestModule implements FileIngestModule {

    private static final String TEMP_DIR_NAME = "temp"; // NON-NLS
    private static final Logger logger = Logger.getLogger(UnallocatedSpaceIngestModule.class.getName());
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private static final Map<Long, Path> tempPathsByJob = new ConcurrentHashMap<>();
    private final BulkExtractorIngestJobSettings settings;
    private IngestJobContext context;
    private Path bulkExtractorPath;
    private Path rootOutputDirPath;

    /**
     * Constructs a file ingest module that runs the Bulk Extractor executable
     * with unallocated space files as input.
     *
     * @param settings The settings for the ingest module.
     */
    UnallocatedSpaceIngestModule(BulkExtractorIngestJobSettings settings) {
        this.settings = settings;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        // Only process unallocated space files when the user does not want the
        // data source to be scanned in its entirety. Scans of an entire disk
        // image are performed by a data source ingest module.
        if (!this.settings.shouldOnlyProcessUnallocatedSpace()) {
            return;
        }

        // If the global unallocated space processing setting and the module
        // process unallocated space only setting are not in sych, throw an 
        // exception. Although the result would not be incorrect, it would be
        // unfortunate for the user to get an accidental no-op for this module. 
        if (!this.context.processingUnallocatedSpace()) {
            throw new IngestModuleException(Bundle.SettingsWrong());
        }

        this.bulkExtractorPath = Utilities.locateBulkExtractorExecutable();
        this.rootOutputDirPath = Utilities.createOutputDirectoryForDataSource(this.context.getDataSource().getName() + "_"
                + this.context.getDataSource().getId());

        // The first instance of the module for an ingest job creates 
        // a temp subdirectory as a location for writing unallocated 
        // space files to disk.
        if (UnallocatedSpaceIngestModule.refCounter.incrementAndGet(this.context.getJobId()) == 1) {
            try {
                Path tempDirPath = Paths.get(this.rootOutputDirPath.toString(), UnallocatedSpaceIngestModule.TEMP_DIR_NAME + Long.toString(this.context.getJobId()));
                Files.createDirectory(tempDirPath);
                UnallocatedSpaceIngestModule.tempPathsByJob.put(this.context.getJobId(), tempDirPath);
            } catch (SecurityException | IOException | UnsupportedOperationException ex) {
                throw new IngestModule.IngestModuleException(Bundle.Utilities_cannotCreateOutputDir_message(), ex);
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public ProcessResult process(AbstractFile file) {
        // Only process unallocated space files when the user does not want the
        // data source to be scanned in its entirety. Scans of an entire disk
        // image are performed by a data source ingest module.
        if (!this.settings.shouldOnlyProcessUnallocatedSpace()) {
            return ProcessResult.OK;
        }

        // Skip everything except unallocated space files.
        if (file.getType() != TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) {
            return ProcessResult.OK;
        }

        Path tempFilePath = null;
        try {
            // Verify initialization succeeded.
            if (null == this.bulkExtractorPath) {
                UnallocatedSpaceIngestModule.logger.log(Level.SEVERE, "Bulk Extractor unallocated space ingest module called after failed start up");  // NON-NLS
                return ProcessResult.ERROR;
            }

            // Write the file to disk.
            Path tempDirPath = UnallocatedSpaceIngestModule.tempPathsByJob.get(this.context.getJobId());
            tempFilePath = Paths.get(tempDirPath.toString(), file.getName());
            ContentUtils.writeToFile(file, tempFilePath.toFile(), this.context::fileIngestIsCancelled);
            if (this.context.fileIngestIsCancelled()) {
                Files.delete(tempFilePath);
                return ProcessResult.OK;
            }

            // Make an output directory for Bulk Extractor. 
            Path outputDirPath = Utilities.getOutputSubdirectoryPath(this.rootOutputDirPath, file.getId());
            Files.createDirectories(outputDirPath);

            // Scan the file with Bulk Extractor.
            FileIngestModuleProcessTerminator terminator = new FileIngestModuleProcessTerminator(this.context, true);
            int exitValue = Utilities.runBulkExtractor(this.bulkExtractorPath, outputDirPath, tempFilePath, terminator);

            if (terminator.getTerminationCode() == ProcTerminationCode.TIME_OUT) {
                String msg = NbBundle.getMessage(this.getClass(), "BulkExtractorIngestModule.processTerminated") + file.getName(); // NON-NLS
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "BulkExtractorIngestModule.moduleError"), msg); // NON-NLS                
                UnallocatedSpaceIngestModule.logger.log(Level.SEVERE, msg);
                FileUtil.deleteDir(new File(outputDirPath.toAbsolutePath().toString()));
                return IngestModule.ProcessResult.ERROR;
            }

            if (0 != exitValue) {
                FileUtil.deleteDir(new File(outputDirPath.toAbsolutePath().toString()));
                if (this.context.dataSourceIngestIsCancelled()) {
                    return ProcessResult.OK;
                } else {
                    UnallocatedSpaceIngestModule.logger.log(Level.SEVERE, "Bulk Extractor returned error exit value = {0} when scanning unallocated space of {1}", new Object[]{exitValue, file.getName()}); // NON-NLS
                    return ProcessResult.ERROR;
                }
            }

            if (!Utilities.isDirectoryEmpty(outputDirPath)) {
                // Add the output directory to the case as an Autopsy report.
                Case.getCurrentCase().addReport(outputDirPath.toAbsolutePath().toString(), Utilities.getModuleName(), Utilities.getReportName(file.getName()));
            }

            return ProcessResult.OK;

        } catch (IOException | InterruptedException | TskCoreException ex) {
            UnallocatedSpaceIngestModule.logger.log(Level.SEVERE, "Error processing " + file.getName() + " with Bulk Extractor", ex); // NON-NLS
            return ProcessResult.ERROR;
        } finally {
            if (null != tempFilePath && Files.exists(tempFilePath)) {
                // Get rid of the unallocated space file.
                tempFilePath.toFile().delete();
            }
        }
    }

    @Override
    public void shutDown() {
        if (!this.settings.shouldOnlyProcessUnallocatedSpace()) {
            return;
        }

        if (refCounter.decrementAndGet(this.context.getJobId()) == 0) {
            try {
                // The last instance of this module for an ingest job deletes 
                // the temp dir.
                Path tempDirPath = UnallocatedSpaceIngestModule.tempPathsByJob.remove(this.context.getJobId());
                FileUtil.deleteDir(new File(tempDirPath.toAbsolutePath().toString()));
            } catch (SecurityException ex) {
                UnallocatedSpaceIngestModule.logger.log(Level.SEVERE, "Error shutting down Bulk Extractor unallocated space module", ex); // NON-NLS
            }
        }
    }

}
