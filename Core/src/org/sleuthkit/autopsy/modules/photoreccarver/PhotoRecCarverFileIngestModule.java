/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.photoreccarver;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.IngestServices;

/**
 * A file ingest module that runs the Unallocated Carver executable with unallocated space files as input.
 */
final class PhotoRecCarverFileIngestModule implements FileIngestModule {

    private static final String PHOTOREC_DIRECTORY = "photorec_exec"; //NON-NLS
    private static final String PHOTOREC_EXECUTABLE = "photorec_win.exe"; //NON-NLS
    private static final String PHOTOREC_RESULTS_BASE = "results"; //NON-NLS
    private static final String PHOTOREC_RESULTS_EXTENDED = "results.1"; //NON-NLS
    private static final String PHOTOREC_REPORT = "report.xml"; //NON-NLS
    private static final String LOG_FILE = "run_log.txt"; //NON-NLS
    private static final String TEMP_DIR_NAME = "temp"; // NON-NLS
    private static final Logger logger = Logger.getLogger(PhotoRecCarverFileIngestModule.class.getName());
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private static final Map<Long, WorkingPaths> pathsByJob = new ConcurrentHashMap<>();
    private IngestJobContext context;
    private Path rootOutputDirPath;
    private File executableFile;

    /**
     * @inheritDoc
     */
    @Override
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {
        this.context = context;

        // If the global unallocated space processing setting and the module
        // process unallocated space only setting are not in sych, throw an 
        // exception. Although the result would not be incorrect, it would be
        // unfortunate for the user to get an accidental no-op for this module. 
        if (!this.context.processingUnallocatedSpace()) {
            throw new IngestModule.IngestModuleException(NbBundle.getMessage(this.getClass(), "unallocatedSpaceProcessingSettingsError.message"));
        }

        this.context = context;
        this.rootOutputDirPath = PhotoRecCarverFileIngestModule.createModuleOutputDirectoryForCase();

        Path execName = Paths.get(PHOTOREC_DIRECTORY, PHOTOREC_EXECUTABLE);
        executableFile = locateExecutable(execName.toString());

        if (PhotoRecCarverFileIngestModule.refCounter.incrementAndGet(this.context.getJobId()) == 1) {
            try {
                // The first instance creates an output subdirectory with a date and time stamp
                DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss-SSSS");  // NON-NLS
                Date date = new Date();
                String folder = this.context.getDataSource().getId() + "_" + dateFormat.format(date);
                Path outputDirPath = Paths.get(this.rootOutputDirPath.toAbsolutePath().toString(), folder);
                Files.createDirectories(outputDirPath);

                // A temp subdirectory is also created as a location for writing unallocated space files to disk.
                Path tempDirPath = Paths.get(outputDirPath.toString(), PhotoRecCarverFileIngestModule.TEMP_DIR_NAME);
                Files.createDirectory(tempDirPath);

                // Save the directories for the current job.
                PhotoRecCarverFileIngestModule.pathsByJob.put(this.context.getJobId(), new WorkingPaths(outputDirPath, tempDirPath));
            }
            catch (SecurityException | IOException | UnsupportedOperationException ex) {
                throw new IngestModule.IngestModuleException(NbBundle.getMessage(this.getClass(), "Utilities.cannotCreateOutputDir.message", ex.getLocalizedMessage()));
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public IngestModule.ProcessResult process(AbstractFile file) {
        // Skip everything except unallocated space files.
        if (file.getType() != TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) {
            return IngestModule.ProcessResult.OK;
        }

        Path tempFilePath = null;
        try {
            long id = getRootId(file);
            // make sure we have a valid systemID
            if (id == -1) {
                return ProcessResult.ERROR;
            }

            // Verify initialization succeeded.
            if (null == this.executableFile) {
                logger.log(Level.SEVERE, "PhotoRec carver called after failed start up");  // NON-NLS
                return IngestModule.ProcessResult.ERROR;
            }

            // Check that we have roughly enough disk space left to complete the operation
            long freeDiskSpace = IngestServices.getInstance().getFreeDiskSpace();
            if ((file.getSize() * 2) > freeDiskSpace) {
                logger.log(Level.SEVERE, "PhotoRec error processing {0} with {1} Not enough space on primary disk to carve unallocated space.",
                        new Object[]{file.getName(), PhotoRecCarverIngestModuleFactory.getModuleName()}); // NON-NLS
                return IngestModule.ProcessResult.ERROR;
            }

            // Write the file to disk.
            WorkingPaths paths = PhotoRecCarverFileIngestModule.pathsByJob.get(this.context.getJobId());
            tempFilePath = Paths.get(paths.getTempDirPath().toString(), file.getName());
            ContentUtils.writeToFile(file, tempFilePath.toFile());

            // Create a subdirectory for this file.
            Path outputDirPath = Paths.get(paths.getOutputDirPath().toString(), file.getName());
            Files.createDirectory(outputDirPath);
            File log = new File(Paths.get(outputDirPath.toString(), LOG_FILE).toString()); //NON-NLS

            // Scan the file with Unallocated Carver.
            ProcessBuilder processAndSettings = new ProcessBuilder(
                    "\"" + executableFile + "\"",
                    "/d",
                    "\"" + outputDirPath.toAbsolutePath() + File.separator + PHOTOREC_RESULTS_BASE + "\"",
                    "/cmd",
                    "\"" + tempFilePath.toFile() + "\"",
                    "search");  // NON_NLS

            // Add environment variable to force PhotoRec to run with the same permissions Autopsy uses
            processAndSettings.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
            processAndSettings.redirectErrorStream(true);
            processAndSettings.redirectOutput(Redirect.appendTo(log));

            int exitValue = ExecUtil.execute(processAndSettings, new FileIngestModuleProcessTerminator(this.context));

            if (this.context.fileIngestIsCancelled() == true) {
                // if it was cancelled by the user, result is OK
                // cleanup the output path
                FileUtil.deleteDir(new File(outputDirPath.toString()));
                if (null != tempFilePath && Files.exists(tempFilePath)) {
                    tempFilePath.toFile().delete();
                }
                logger.log(Level.INFO, "PhotoRec cancelled by user"); // NON-NLS
                return IngestModule.ProcessResult.OK;
            }

            else if (0 != exitValue) {
                // if it failed or was cancelled by timeout, result is ERROR
                // cleanup the output path
                FileUtil.deleteDir(new File(outputDirPath.toString()));
                if (null != tempFilePath && Files.exists(tempFilePath)) {
                    tempFilePath.toFile().delete();
                }
                logger.log(Level.SEVERE, "PhotoRec carver returned error exit value = {0} when scanning {1}",
                        new Object[]{exitValue, file.getName()}); // NON-NLS
                return IngestModule.ProcessResult.ERROR;
            }

            // Move carver log file to avoid placement into Autopsy results. PhotoRec appends ".1" to the folder name.
            java.io.File oldAuditFile = new java.io.File(Paths.get(outputDirPath.toString(), PHOTOREC_RESULTS_EXTENDED, PHOTOREC_REPORT).toString()); //NON-NLS
            java.io.File newAuditFile = new java.io.File(Paths.get(outputDirPath.toString(), PHOTOREC_REPORT).toString()); //NON-NLS
            oldAuditFile.renameTo(newAuditFile);

            Path pathToRemove = Paths.get(outputDirPath.toAbsolutePath().toString());
            DirectoryStream<Path> stream = Files.newDirectoryStream(pathToRemove);
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    FileUtil.deleteDir(new File(entry.toString()));
                }
            }

            // Now that we've cleaned up the folders and data files, parse the xml output file to add carved items into the database
            PhotoRecCarverOutputParser parser = new PhotoRecCarverOutputParser(outputDirPath);
            List<LayoutFile> theList = parser.parse(newAuditFile, id, file);
            if (theList != null) { // if there were any results from carving, add the unallocated carving event to the reports list.
                context.addFilesToJob(new ArrayList<>(theList));
            }
        }
        catch (IOException ex) {
            logger.log(Level.SEVERE, "Error processing " + file.getName() + " with PhotoRec carver", ex); // NON-NLS
            return IngestModule.ProcessResult.ERROR;
        }

        finally {
            if (null != tempFilePath && Files.exists(tempFilePath)) {
                // Get rid of the unallocated space file.
                tempFilePath.toFile().delete();
            }
        }
        return IngestModule.ProcessResult.OK;

    }

    /**
     * @inheritDoc
     */
    @Override
    public void shutDown() {
        if (refCounter.decrementAndGet(this.context.getJobId()) == 0) {
            try {
                // The last instance of this module for an ingest job cleans out 
                // the working paths map entry for the job and deletes the temp dir.
                WorkingPaths paths = PhotoRecCarverFileIngestModule.pathsByJob.remove(this.context.getJobId());
                FileUtil.deleteDir(new File(paths.getTempDirPath().toString()));
            }
            catch (SecurityException ex) {
                logger.log(Level.SEVERE, "Error shutting down PhotoRec carver module", ex); // NON-NLS
            }
        }
    }

    private static final class WorkingPaths {

        private final Path outputDirPath;
        private final Path tempDirPath;

        WorkingPaths(Path outputDirPath, Path tempDirPath) {
            this.outputDirPath = outputDirPath;
            this.tempDirPath = tempDirPath;
        }

        Path getOutputDirPath() {
            return this.outputDirPath;
        }

        Path getTempDirPath() {
            return this.tempDirPath;
        }
    }

    /**
     * Creates the output directory for this module for the current case, if it does not already exist.
     *
     * @return The absolute path of the output directory.
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    synchronized static Path createModuleOutputDirectoryForCase() throws IngestModule.IngestModuleException {
        Path path = Paths.get(Case.getCurrentCase().getModulesOutputDirAbsPath(), PhotoRecCarverIngestModuleFactory.getModuleName());
        try {
            Files.createDirectory(path);
        }
        catch (FileAlreadyExistsException ex) {
            // No worries.
        }
        catch (IOException | SecurityException | UnsupportedOperationException ex) {
            throw new IngestModule.IngestModuleException(NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "cannotCreateOutputDir.message", ex.getLocalizedMessage()));
        }
        return path;
    }

    /**
     * Finds the root Volume or Image of the AbstractFile passed in.
     *
     * @param file The file we want to find the root parent for
     * @return The ID of the root parent Volume or Image
     */
    private static long getRootId(AbstractFile file) {
        long id = -1;
        Content parent = null;
        try {
            parent = file.getParent();
            while (parent != null) {
                if (parent instanceof Volume || parent instanceof Image) {
                    id = parent.getId();
                    break;
                }
                parent = parent.getParent();
            }
        }
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "PhotoRec carver exception while trying to get parent of AbstractFile.", ex); //NON-NLS
        }
        return id;
    }

    /**
     * Finds and returns the path to the executable, if able.
     *
     * @param executableToFindName The name of the executable to find
     * @return A File reference or throws an exception
     * @throws IngestModuleException
     */
    public static File locateExecutable(String executableToFindName) throws IngestModule.IngestModuleException {
        // Must be running under a Windows operating system.
        if (!PlatformUtil.isWindowsOS()) {
            throw new IngestModule.IngestModuleException(NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "unsupportedOS.message"));
        }

        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, PhotoRecCarverFileIngestModule.class.getPackage().getName(), false);
        if (null == exeFile) {
            throw new IngestModule.IngestModuleException(NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "missingExecutable.message"));
        }

        if (!exeFile.canExecute()) {
            throw new IngestModule.IngestModuleException(NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "cannotRunExecutable.message"));
        }

        return exeFile;
    }

}
