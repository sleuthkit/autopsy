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
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;
import org.apache.commons.io.FilenameUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;

/**
 * A local/logical files/logical evidence file(.lo1)/or directories data source
 * processor that implements the DataSourceProcessor service provider interface
 * to allow integration with the add data source wizard. It also provides a run
 * method overload to allow it to be used independently of the wizard.
 */
@ServiceProviders(value = {
    @ServiceProvider(service = DataSourceProcessor.class)
    ,
    @ServiceProvider(service = AutoIngestDataSourceProcessor.class)}
)
@Messages({
    "LocalFilesDSProcessor.logicalEvidenceFilter.desc=Logical Evidence Files (L01)"
})
public class LocalFilesDSProcessor implements DataSourceProcessor, AutoIngestDataSourceProcessor {

    private static final String DATA_SOURCE_TYPE = NbBundle.getMessage(LocalFilesDSProcessor.class, "LocalFilesDSProcessor.dsType");
    private static final Logger logger = Logger.getLogger(LocalFilesDSProcessor.class.getName());
    private final LogicalFilesDspPanel configPanel;
    private static final String L01_EXTRACTION_DIR = "L01";
    private static final String UNIQUENESS_CONSTRAINT_SEPERATOR = "_";
    private static final String EWFEXPORT_DIR = "ewfexport_exec"; // NON-NLS
    private static final String EWFEXPORT_32_BIT_DIR = "32-bit"; // NON-NLS
    private static final String EWFEXPORT_64_BIT_DIR = "64-bit"; // NON-NLS
    private static final String EWFEXPORT_WINDOWS_EXE = "ewfexport.exe"; // NON-NLS
    private static final String LOG_FILE_EXTENSION = ".txt";
    private static final List<String> LOGICAL_EVIDENCE_EXTENSIONS = Arrays.asList(".l01");
    private static final String LOGICAL_EVIDENCE_DESC = Bundle.LocalFilesDSProcessor_logicalEvidenceFilter_desc();
    private static final GeneralFilter LOGICAL_EVIDENCE_FILTER = new GeneralFilter(LOGICAL_EVIDENCE_EXTENSIONS, LOGICAL_EVIDENCE_DESC);
    /*
     * TODO: Remove the setDataSourceOptionsCalled flag and the settings fields
     * when the deprecated method setDataSourceOptions is removed.
     */
    private List<String> localFilePaths;
    private boolean setDataSourceOptionsCalled;

    /**
     * Constructs a local/logical files and/or directories data source processor
     * that implements the DataSourceProcessor service provider interface to
     * allow integration with the add data source wizard. It also provides a run
     * method overload to allow it to be used independently of the wizard.
     */
    public LocalFilesDSProcessor() {
        configPanel = LogicalFilesDspPanel.getDefault();
    }

    /**
     * Gets a string that describes the type of data sources this processor is
     * able to add to the case database. The string is suitable for display in a
     * type selection UI component (e.g., a combo box).
     *
     * @return A data source type display string for this data source processor.
     */
    public static String getType() {
        return DATA_SOURCE_TYPE;
    }

    /**
     * Gets a string that describes the type of data sources this processor is
     * able to add to the case database. The string is suitable for display in a
     * type selection UI component (e.g., a combo box).
     *
     * @return A data source type display string for this data source processor.
     */
    @Override
    public String getDataSourceType() {
        return DATA_SOURCE_TYPE;
    }

    /**
     * Gets the panel that allows a user to select a data source and do any
     * configuration required by the data source. The panel is less than 544
     * pixels wide and less than 173 pixels high.
     *
     * @return A selection and configuration panel for this data source
     *         processor.
     */
    @Override
    public JPanel getPanel() {
        configPanel.select();
        return configPanel;
    }

    /**
     * Indicates whether the settings in the selection and configuration panel
     * are valid and complete.
     *
     * @return True if the settings are valid and complete and the processor is
     *         ready to have its run method called, false otherwise.
     */
    @Override
    public boolean isPanelValid() {
        return configPanel.validatePanel();
    }

    /**
     * Adds a data source to the case database using a background task in a
     * separate thread and the settings provided by the selection and
     * configuration panel. Returns as soon as the background task is started.
     * The background task uses a callback object to signal task completion and
     * return results.
     *
     * This method should not be called unless isPanelValid returns true.
     *
     * @param progressMonitor Progress monitor that will be used by the
     *                        background task to report progress.
     * @param callback        Callback that will be used by the background task
     *                        to return results.
     */
    @Override
    public void run(DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        if (!setDataSourceOptionsCalled) {
            localFilePaths = configPanel.getContentPaths();
            if (configPanel.subTypeIsLogicalEvidencePanel()) {
                try {
                    //if the L01 option was chosen
                    localFilePaths = extractLogicalEvidenceFileContents(localFilePaths);
                } catch (L01Exception ex) {
                    //contents of l01 could not be extracted don't add data source or run ingest
                    final List<String> errors = new ArrayList<>();
                    errors.add(ex.getMessage());
                    callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errors, new ArrayList<>());
                    return;
                } catch (NoCurrentCaseException ex) {
                    logger.log(Level.WARNING, "Exception while getting open case.", ex);
                    return;
                }
            }
        }
        run(UUID.randomUUID().toString(), configPanel.getFileSetName(), localFilePaths, progressMonitor, callback);
    }

    /**
     * Extract the contents of the logical evidence files and return the paths
     * to those extracted files.
     *
     * @param logicalEvidenceFilePaths
     *
     * @return extractedPaths - the paths to all the files extracted from the
     *         logical evidence files
     *
     * @throws
     * org.sleuthkit.autopsy.casemodule.LocalFilesDSProcessor.L01Exception
     */
    private List<String> extractLogicalEvidenceFileContents(final List<String> logicalEvidenceFilePaths) throws L01Exception, NoCurrentCaseException {
        final List<String> extractedPaths = new ArrayList<>();
        Path ewfexportPath;
        ewfexportPath = locateEwfexportExecutable();
        List<String> command = new ArrayList<>();
        for (final String l01Path : logicalEvidenceFilePaths) {
            command.clear();
            command.add(ewfexportPath.toAbsolutePath().toString());
            command.add("-f");
            command.add("files");
            command.add("-t");
            File l01Dir = new File(Case.getOpenCase().getModuleDirectory(), L01_EXTRACTION_DIR);  //WJS-TODO change to getOpenCase() when that method exists
            if (!l01Dir.exists()) {
                l01Dir.mkdirs();
            }
            Path dirPath = Paths.get(FilenameUtils.getBaseName(l01Path) + UNIQUENESS_CONSTRAINT_SEPERATOR + System.currentTimeMillis());

            command.add(dirPath.toString());
            command.add(l01Path);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(l01Dir);
            try {
                //redirect  ewfexport stdout and stderr to txt file
                Path logFileName = Paths.get(l01Dir.toString(), dirPath.toString() + LOG_FILE_EXTENSION);
                File logFile = new File(logFileName.toString());
                Path errFileName = Paths.get(l01Dir.toString(), dirPath.toString() + LOG_FILE_EXTENSION);
                File errFile = new File(errFileName.toString());
                processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
                // open the file with ewfexport to extract its contents
                ExecUtil.execute(processBuilder, new ExecUtil.TimedProcessTerminator());
                if (l01Dir.toPath().resolve(dirPath).toFile().exists()) {
                    extractedPaths.add(l01Dir.toPath().resolve(dirPath).toString());
                } else { //if we failed to extract anything let the user know the L01 file was unable to be processed
                    throw new L01Exception("Can not process the selected L01 file, ewfExport was unable to extract any files from it.");
                }

            } catch (SecurityException ex) {
                throw new L01Exception("Security exception occcured while trying to extract l01 contents", ex);
            } catch (IOException ex) {
                throw new L01Exception("IOException occcured while trying to extract l01 contents", ex);
            }
        }
        return extractedPaths;
    }

    /**
     * Get a file filter for logical evidence files.
     *
     * @return LOGICAL_EVIDENCE_FILTER
     */
    static FileFilter getLogicalEvidenceFilter() {
        return LOGICAL_EVIDENCE_FILTER;
    }

    /**
     * Gets the path for the ewfexport executable.
     *
     * @return the path to ewfexport.exe
     *
     * @throws
     * org.sleuthkit.autopsy.casemodule.LocalFilesDSProcessor.L01Exception
     */
    private Path locateEwfexportExecutable() throws L01Exception {
        // Must be running under a Windows operating system.
        if (!PlatformUtil.isWindowsOS()) {
            throw new L01Exception("L01 files are only supported on windows currently");
        }

        // Build the expected path to either the 32-bit or 64-bit version of the 
        // ewfexport executable.
        final File ewfRoot = InstalledFileLocator.getDefault().locate(EWFEXPORT_DIR, LocalFilesDSProcessor.class.getPackage().getName(), false);

        Path executablePath;
        if (PlatformUtil.is64BitOS()) {
            executablePath = Paths.get(
                    ewfRoot.getAbsolutePath(),
                    EWFEXPORT_64_BIT_DIR,
                    EWFEXPORT_WINDOWS_EXE);
        } else {
            executablePath = Paths.get(
                    ewfRoot.getAbsolutePath(),
                    EWFEXPORT_32_BIT_DIR,
                    EWFEXPORT_WINDOWS_EXE);
        }

        // Make sure the executable exists at the expected location and that it  
        // can be run.
        final File ewfexport = executablePath.toFile();
        if (null == ewfexport || !ewfexport.exists()) {
            throw new LocalFilesDSProcessor.L01Exception("EWF export executable was not found");
        }
        if (!ewfexport.canExecute()) {
            throw new LocalFilesDSProcessor.L01Exception("EWF export executable can not be executed");
        }

        return executablePath;
    }

    /**
     * Adds a data source to the case database using a background task in a
     * separate thread and the given settings instead of those provided by the
     * selection and configuration panel. Returns as soon as the background task
     * is started and uses the callback object to signal task completion and
     * return results.
     *
     * @param deviceId                 An ASCII-printable identifier for the
     *                                 device associated with the data source
     *                                 that is intended to be unique across
     *                                 multiple cases (e.g., a UUID).
     * @param rootVirtualDirectoryName The name to give to the virtual directory
     *                                 that will serve as the root for the
     *                                 local/logical files and/or directories
     *                                 that compose the data source. Pass the
     *                                 empty string to get a default name of the
     *                                 form: LogicalFileSet[N]
     * @param localFilePaths           A list of local/logical file and/or
     *                                 directory localFilePaths.
     * @param progressMonitor          Progress monitor for reporting progress
     *                                 during processing.
     * @param callback                 Callback to call when processing is done.
     */
    public void run(String deviceId, String rootVirtualDirectoryName, List<String> localFilePaths, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        new Thread(new AddLocalFilesTask(deviceId, rootVirtualDirectoryName, localFilePaths, progressMonitor, callback)).start();
    }

    /**
     * Requests cancellation of the background task that adds a data source to
     * the case database, after the task is started using the run method. This
     * is a "best effort" cancellation, with no guarantees that the case
     * database will be unchanged. If cancellation succeeded, the list of new
     * data sources returned by the background task will be empty.
     *
     * TODO (AUT-1907): Implement cancellation by deleting rows added to the
     * case database.
     */
    @Override
    public void cancel() {
    }

    /**
     * Resets the selection and configuration panel for this data source
     * processor.
     */
    @Override
    public void reset() {
        configPanel.select();
        localFilePaths = null;
        setDataSourceOptionsCalled = false;
    }

    @Override
    public int canProcess(Path dataSourcePath) throws AutoIngestDataSourceProcessorException {
        // Local files DSP can process any file by simply adding it as a logical file.
        // It should return lowest possible non-zero confidence level and be treated 
        // as the "option of last resort" for auto ingest purposes

        this.localFilePaths = Arrays.asList(new String[]{dataSourcePath.toString()});
        //If there is only 1 file check if it is an L01 file and if it is extract the 
        //contents and replace the paths, if the contents can't be extracted return 0
        if (localFilePaths.size() == 1) {
            for (final String path : localFilePaths) {
                if (LOGICAL_EVIDENCE_FILTER.accept(new File(path))) {
                    try {
                        //if the L01 option was chosen
                        localFilePaths = extractLogicalEvidenceFileContents(localFilePaths);
                    } catch (L01Exception ex) {
                        logger.log(Level.WARNING, "File extension was .l01 but contents of logical evidence file were unable to be extracted", ex);
                        //contents of l01 could not be extracted don't add data source or run ingest
                        return 0;
                    } catch (NoCurrentCaseException ex) {
                        logger.log(Level.WARNING, "Exception while getting open case.", ex);
                        return 0;
                    }
                }
            }
        }
        return 1;
    }

    @Override
    public void process(String deviceId, Path dataSourcePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callBack) throws AutoIngestDataSourceProcessorException {
        run(deviceId, deviceId, this.localFilePaths, progressMonitor, callBack);
    }

    /**
     * Sets the configuration of the data source processor without using the
     * configuration panel. The data source processor will assign a UUID to the
     * data source and will use the time zone of the machine executing this code
     * when when processing dates and times for the image.
     *
     * @param paths A list of local/logical file and/or directory
     *              localFilePaths.
     *
     * @deprecated Use the provided overload of the run method instead.
     */
    @Deprecated
    public void setDataSourceOptions(String paths) {
        // The LocalFilesPanel used to separate file paths with a comma and pass
        // them as a string, but because file names are allowed to contain
        // commas, this approach was buggy and replaced. We now pass a list of
        // String paths.
        this.localFilePaths = Arrays.asList(paths.split(","));
        setDataSourceOptionsCalled = true;
    }

    /**
     * A custom exception for the L01 processing.
     */
    private final class L01Exception extends Exception {

        private static final long serialVersionUID = 1L;

        L01Exception(final String message) {
            super(message);
        }

        L01Exception(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

}
