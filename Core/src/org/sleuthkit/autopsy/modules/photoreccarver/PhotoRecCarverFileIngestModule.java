package org.sleuthkit.autopsy.modules.photoreccarver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
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

/**
 * A file ingest module that runs the Unallocated Carver executable with unallocated space files as input.
 */
final class PhotoRecCarverFileIngestModule implements FileIngestModule {

    private static final String TEMP_DIR_NAME = "temp"; // NON-NLS
    private static final Logger logger = Logger.getLogger(PhotoRecCarverFileIngestModule.class.getName());
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private static final Map<Long, WorkingPaths> pathsByJob = new ConcurrentHashMap<>();
    private final PhotoRecCarverIngestJobSettings settings;
    private IngestJobContext context;
    private Path rootOutputDirPath;
    private File executableFile;

    /**
     * Constructs a file ingest module that runs the Unallocated Carver executable with unallocated space files as
     * input.
     *
     * @param settings The settings for the ingest module.
     */
    PhotoRecCarverFileIngestModule(PhotoRecCarverIngestJobSettings settings) {
        this.settings = settings;
    }

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

        String execName = PhotoRecCarverIngestJobSettings.PHOTOREC_DIRECTORY + PhotoRecCarverIngestJobSettings.PHOTOREC_EXECUTABLE;
        executableFile = settings.locateExecutable(execName);
        if (PhotoRecCarverFileIngestModule.refCounter.incrementAndGet(this.context.getJobId()) == 1) {
            try {
                // The first instance of the module for an ingest job creates 
                // a time-stamped output subdirectory of the unallocated space
                // scans subdirectory of the Unallocated Carver module output  
                // directory for the current case. 

                // Make output subdirectories for the current time and image within
                // the module output directory for the current case.
                DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss-SSSS");  // NON-NLS
                Date date = new Date();
                Path outputDirPath = Paths.get(this.rootOutputDirPath.toAbsolutePath().toString(), dateFormat.format(date));
                Files.createDirectories(outputDirPath);

                // A temp subdirectory is also created as a location for writing 
                // unallocated space files to disk.
                Path tempDirPath = Paths.get(outputDirPath.toString(), PhotoRecCarverFileIngestModule.TEMP_DIR_NAME);
                Files.createDirectory(tempDirPath);

                // Save the directories for the current job.
                PhotoRecCarverFileIngestModule.pathsByJob.put(this.context.getJobId(), new WorkingPaths(outputDirPath, tempDirPath));
            }
            catch (SecurityException | IOException | UnsupportedOperationException ex) {
                throw new IngestModule.IngestModuleException(NbBundle.getMessage(this.getClass(), "Utilities.cannotCreateOutputDir.message", ex.getLocalizedMessage()));
            }
        }
        else {
            int a;
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

            System.out.println("Root id of abstractfile (layoutfile) " + file.getId() + " is " + id);

            // Verify initialization succeeded.
            if (null == this.executableFile) {
                PhotoRecCarverFileIngestModule.logger.log(Level.SEVERE, "Unallocated Carver unallocated space ingest module called after failed start up");  // NON-NLS
                return IngestModule.ProcessResult.ERROR;
            }

            // Write the file to disk.
            WorkingPaths paths = PhotoRecCarverFileIngestModule.pathsByJob.get(this.context.getJobId());
            Path tempDirPath = paths.getTempDirPath();
            tempFilePath = Paths.get(tempDirPath.toString(), file.getName());
            ContentUtils.writeToFile(file, tempFilePath.toFile());

            // Create a subdirectory for this file.
            Path outputDirPath = Paths.get(paths.getOutputDirPath().toString(), file.getName());
            Files.createDirectory(outputDirPath);

            // Scan the file with Unallocated Carver.
            ExecUtil executor = new ExecUtil();
            Writer stdoutWriter = new FileWriter(outputDirPath + "\\" + PhotoRecCarverIngestJobSettings.LOG_FILE + PhotoRecCarverIngestJobSettings.LOG_FILE_EXT); //NON-NLS

            // Run the executable against the file. Note that each of the 
            // following arguments must be its own entry, or they get gummed 
            // up in the string array on the other side. It has to do with 
            // the whitespace when it gets passed back in.
            executor.setExtraEnvironmentVariables(new String[]{"__COMPAT_LAYER=RunAsInvoker"});
            executor.execute(stdoutWriter,
                    "\"" + settings.executableFile().toString() + "\"",
                    "/d",
                    "\"" + outputDirPath.toAbsolutePath() + "\\results\"",
                    "/cmd",
                    "\"" + tempFilePath.toFile() + "\"",
                    "search");  // NON_NLS
            int exitValue = executor.getExitValue();

            if (0 != exitValue) {
                PhotoRecCarverFileIngestModule.logger.log(Level.SEVERE, "Unallocated Carver returned error exit value = {0} when scanning {1}", new Object[]{
                    exitValue, file.getName()
                }); // NON-NLS
                Files.delete(outputDirPath);
                if (null != tempFilePath && Files.exists(tempFilePath)) {
                    tempFilePath.toFile().delete();
                }
                return IngestModule.ProcessResult.ERROR;
            }

            // Move carver log file to avoid placement into Autopsy results. PhotoRec appends ".1" to the folder name.
            java.io.File oldAuditFile = new java.io.File(outputDirPath.toAbsolutePath() + "\\results.1\\report.xml"); //NON-NLS
            java.io.File newAuditFile = new java.io.File(outputDirPath.toAbsolutePath() + "\\report.xml"); //NON-NLS
            oldAuditFile.renameTo(newAuditFile);

            PhotoRecCarverOutputParser parser = new PhotoRecCarverOutputParser();
            List<LayoutFile> theList = parser.parse(newAuditFile, id, file);
            if (theList != null) { // if there were any results from carving, add the unallocated carving event to the reports list.
                context.scheduleFiles(new ArrayList<AbstractFile>(theList));
            }

            if (!isDirectoryEmpty(outputDirPath)) {
                // Add the output directory to the case as an Autopsy report.
                Case.getCurrentCase().addReport(outputDirPath.toAbsolutePath().toString(),
                        NbBundle.getMessage(this.getClass(), "moduleDisplayName.text"), file.getName()
                        + " " + NbBundle.getMessage(this.getClass(), "moduleDisplayName.text") + " Scan"); // NON-NLS
            }
        }
        catch (IOException | InterruptedException | TskCoreException ex) {
            PhotoRecCarverFileIngestModule.logger.log(Level.SEVERE, "Error processing " + file.getName() + " with Unallocated Carver", ex); // NON-NLS
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

    @Override
    public void shutDown() {
        if (refCounter.decrementAndGet(this.context.getJobId()) == 0) {
            try {
                // The last instance of this module for an ingest job cleans out 
                // the working paths map entry for the job and deletes the temp dir.
                WorkingPaths paths = PhotoRecCarverFileIngestModule.pathsByJob.remove(this.context.getJobId());
                Files.delete(paths.getTempDirPath());
            }
            catch (IOException | SecurityException ex) {
                PhotoRecCarverFileIngestModule.logger.log(Level.SEVERE, "Error shutting down Unallocated Carver unallocated space module", ex); // NON-NLS

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

    private long getRootId(AbstractFile file) {
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
            logger.log(Level.SEVERE, "Exception while trying to get parent of AbstractFile.", ex); //NON-NLS
        }
        return id;
    }

    /**
     * Determines whether or not a directory is empty.
     *
     * @param directoryPath The path to the directory to inspect.
     * @return True if the directory is empty, false otherwise.
     * @throws IllegalArgumentException
     * @throws IOException
     */
    static boolean isDirectoryEmpty(final Path directoryPath) throws IllegalArgumentException, IOException {
        if (!Files.isDirectory(directoryPath)) {
            throw new IllegalArgumentException("The directoryPath argument must be a directory path"); // NON-NLS
        }
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directoryPath)) {
            return !dirStream.iterator().hasNext();
        }
    }
}
