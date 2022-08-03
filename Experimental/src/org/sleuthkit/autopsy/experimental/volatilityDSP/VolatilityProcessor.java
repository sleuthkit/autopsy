/*
 * Autopsy
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.volatilityDSP;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.EncodingType;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

/**
 * Runs Volatility on a given memory image file and parses the output to create
 * artifacts.
 */
class VolatilityProcessor {
    
    private static final Logger logger = Logger.getLogger(VolatilityProcessor.class.getName());
    private static final String VOLATILITY = "Volatility"; //NON-NLS
    private static final String VOLATILITY_EXECUTABLE = "volatility_2.6_win64_standalone.exe"; //NON-NLS
    private final List<String> errorMsgs = new ArrayList<>();
    private final String memoryImagePath;
    private final Image dataSource;
    private final List<String> pluginsToRun;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private File executableFile;
    private String moduleOutputPath;
    private FileManager fileManager;
    private volatile boolean isCancelled;
    private Content outputVirtDir;
    private String profile;
    private Blackboard blackboard;
    private String caseDirectory;

    /**
     * Constructs a processor that runs Volatility on a given memory image file
     * and parses the output to create artifacts.
     *
     * @param memoryImagePath Path to memory image file.
     * @param dataSource      The memory image data source.
     * @param profile         Volatility profile to run or empty string to
     *                        autodetect
     * @param pluginsToRun    Volatility plugins to run.
     * @param progressMonitor Progress monitor for reporting progress during
     *                        processing.
     */
    VolatilityProcessor(String memoryImagePath, Image dataSource, String profile, List<String> pluginsToRun, DataSourceProcessorProgressMonitor progressMonitor) {
        this.profile = profile;
        this.memoryImagePath = memoryImagePath;
        this.pluginsToRun = pluginsToRun;
        this.dataSource = dataSource;
        this.progressMonitor = progressMonitor;
    }

    /**
     * Runs Volatility on a given memory image file and parses the output to
     * create artifacts.
     *
     * @throws VolatilityProcessorException If there is a critical error during
     *                                      processing.
     */
    @NbBundle.Messages({
        "VolatilityProcessor_progressMessage_noCurrentCase=Failed to get current case",
        "VolatilityProcessor_exceptionMessage_volatilityExeNotFound=Volatility executable not found",
        "# {0} - plugin name",
        "VolatilityProcessor_progressMessage_runningImageInfo=Running {0} plugin"
    })
    void run() throws VolatilityProcessorException {
        this.errorMsgs.clear();
        Case currentCase;
        try {

            currentCase = Case.getCurrentCaseThrows();

        } catch (NoCurrentCaseException ex) {
            throw new VolatilityProcessorException(Bundle.VolatilityProcessor_progressMessage_noCurrentCase(), ex);
        }
        blackboard = currentCase.getSleuthkitCase().getBlackboard();
        caseDirectory = currentCase.getCaseDirectory();
        executableFile = locateVolatilityExecutable();
        if (executableFile == null) {
            throw new VolatilityProcessorException(Bundle.VolatilityProcessor_exceptionMessage_volatilityExeNotFound());
        }

        fileManager = currentCase.getServices().getFileManager();

        try {
            // make a virtual directory to store the reports
            outputVirtDir = currentCase.getSleuthkitCase().addVirtualDirectory(dataSource.getId(), "ModuleOutput");
        } catch (TskCoreException ex) {
            throw new VolatilityProcessorException("Error creating virtual directory", ex);
        }

        /*
         * Make an output folder unique to this data source.
         */
        Long dataSourceId = dataSource.getId();
        moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), VOLATILITY, dataSourceId.toString()).toString();
        File directory = new File(String.valueOf(moduleOutputPath));
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // if they did not specify a profile, then run imageinfo to get one
        if (profile.isEmpty()) {
            progressMonitor.setProgressText(Bundle.VolatilityProcessor_progressMessage_runningImageInfo("imageinfo")); //NON-NLS
            runVolatilityPlugin("imageinfo"); //NON-NLS
            profile = getProfileFromImageInfoOutput();
        }

        progressMonitor.setIndeterminate(false);
        progressMonitor.setProgressMax(pluginsToRun.size());
        for (int i = 0; i < pluginsToRun.size(); i++) {
            if (isCancelled) {
                break;
            }
            String pluginToRun = pluginsToRun.get(i);
            runVolatilityPlugin(pluginToRun);
            progressMonitor.setProgress(i);
        }
    }

    /**
     * Gets a list of error messages that were generated during the processing.
     *
     * @return The list of error messages.
     */
    List<String> getErrorMessages() {
        return new ArrayList<>(errorMsgs);
    }

    /**
     * Runs a given Volatility plugin and parses its output to create artifacts.
     *
     * @param pluginToRun The name of the Volatility plugin to run.
     *
     * @throws VolatilityProcessorException If there is a critical error, add
     *                                      messages to the error messages list
     *                                      for non-critical errors.
     */
    @NbBundle.Messages({
        "VolatilityProcessor_exceptionMessage_failedToRunVolatilityExe=Could not run Volatility",
        "# {0} - plugin name",
        "VolatilityProcessor_exceptionMessage_errorRunningPlugin=Volatility error running {0} plugin",
        "# {0} - plugin name",
        "VolatilityProcessor_exceptionMessage_errorAddingOutput=Failed to add output for {0} to case",
        "# {0} - plugin name",
        "VolatilityProcessor_exceptionMessage_searchServiceNotFound=Keyword search service not found, output for {0} plugin not indexed",
        "# {0} - plugin name",
        "VolatilityProcessor_exceptionMessage_errorIndexingOutput=Error indexing output for {0} plugin"
    })
    private void runVolatilityPlugin(String pluginToRun) throws VolatilityProcessorException {
        progressMonitor.setProgressText("Running module " + pluginToRun);

        List<String> commandLine = new ArrayList<>();
        commandLine.add("\"" + executableFile + "\""); //NON-NLS
        File memoryImage = new File(memoryImagePath);
        commandLine.add("--filename=" + memoryImage.getName()); //NON-NLS
        if (!profile.isEmpty()) {
            commandLine.add("--profile=" + profile); //NON-NLS
        }
        commandLine.add(pluginToRun);

        switch (pluginToRun) {
            case "dlldump":
            case "moddump":
            case "procdump":
            case "dumpregistry":
            case "dumpfiles":
                String outputDir = moduleOutputPath + File.separator + pluginToRun;
                File directory = new File(outputDir);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                commandLine.add("--dump-dir=" + outputDir); //NON-NLS
                break;
            default:
                break;
        }

        String outputFileAsString = moduleOutputPath + File.separator + pluginToRun + ".txt"; //NON-NLS
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force Volatility to run with the same
         * permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        File outputFile = new File(outputFileAsString);
        processBuilder.redirectOutput(outputFile);
        processBuilder.redirectError(new File(moduleOutputPath + File.separator + "Volatility_err.txt"));  //NON-NLS
        processBuilder.directory(new File(memoryImage.getParent()));

        try {
            int exitVal = ExecUtil.execute(processBuilder);
            if (exitVal != 0) {
                errorMsgs.add(Bundle.VolatilityProcessor_exceptionMessage_errorRunningPlugin(pluginToRun));
                return;
            }
        } catch (IOException | SecurityException ex) {
            throw new VolatilityProcessorException(Bundle.VolatilityProcessor_exceptionMessage_failedToRunVolatilityExe(), ex);
        }

        if (isCancelled) {
            return;
        }

        try {
            String relativePath = new File(caseDirectory).toURI().relativize(new File(outputFileAsString).toURI()).getPath();
            fileManager.addDerivedFile(pluginToRun, relativePath, outputFile.length(), 0, 0, 0, 0, true, outputVirtDir, null, null, null, null, EncodingType.NONE);
        } catch (TskCoreException ex) {
            errorMsgs.add("Error adding " + pluginToRun + " volatility report as a file");
            logger.log(Level.WARNING, "Error adding report as derived file", ex);
        }

        createArtifactsFromPluginOutput(pluginToRun, new File(outputFileAsString));
    }

    /**
     * Finds and returns the path to the Volatility executable, if able.
     *
     * @return A File reference or null.
     */
    private static File locateVolatilityExecutable() {
        if (!PlatformUtil.isWindowsOS()) {
            return null;
        }

        String executableToFindName = Paths.get(VOLATILITY, VOLATILITY_EXECUTABLE).toString();
        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, VolatilityProcessor.class.getPackage().getName(), false);
        if (null == exeFile) {
            return null;
        }

        if (!exeFile.canExecute()) {
            return null;
        }

        return exeFile;
    }

    @NbBundle.Messages({
        "VolatilityProcessor_exceptionMessage_failedToParseImageInfo=Could not parse image info"
    })
    private String getProfileFromImageInfoOutput() throws VolatilityProcessorException {
        File imageOutputFile = new File(moduleOutputPath + File.separator + "imageinfo.txt"); //NON-NLS  
        try (BufferedReader br = new BufferedReader(new FileReader(imageOutputFile))) {
            String fileRead = br.readLine();
            if (fileRead != null) {
                String[] profileLine = fileRead.split(":");  //NON-NLS
                String[] memProfile = profileLine[1].split(",|\\("); //NON-NLS
                return memProfile[0].replaceAll("\\s+", ""); //NON-NLS
            } else {
                throw new VolatilityProcessorException(Bundle.VolatilityProcessor_exceptionMessage_failedToParseImageInfo());
            }
        } catch (IOException ex) {
            throw new VolatilityProcessorException(Bundle.VolatilityProcessor_exceptionMessage_failedToParseImageInfo(), ex);
        }
    }

    /**
     * Adds interesting file artifacts for files found by a Volatility plugin.
     *
     * @param fileSet    The paths of the files within the memeory image data
     *                   source.
     * @param pluginName The name of the source Volatility plugin.
     */
    @NbBundle.Messages({
        "# {0} - plugin name",
        "VolatilityProcessor_artifactAttribute_interestingFileSet=Volatility Plugin {0}",
        "# {0} - file path",
        "# {1} - file name",
        "# {2} - plugin name",
        "VolatilityProcessor_exceptionMessage_fileNotFound=File {0}/{1} not found for ouput of {2} plugin",
        "# {0} - plugin name",
        "VolatilityProcessor_exceptionMessage_errorCreatingArtifact=Error creating artifact for output of {0} plugin",
        "# {0} - plugin name",
        "VolatilityProcessor_errorMessage_errorFindingFiles=Error finding files parsed from output of {0} plugin",
        "# {0} - plugin name",
        "VolatilityProcessor_errorMessage_failedToIndexArtifact=Error indexing artifact from output of {0} plugin"
    })
    private void flagFiles(Set<String> fileSet, String pluginName) throws VolatilityProcessorException {
        for (String file : fileSet) {
            if (isCancelled) {
                return;
            }

            if (file.isEmpty()) {
                continue;
            }

            File volfile = new File(file);
            String fileName = volfile.getName().trim();
            if (fileName.length() < 1) {
                continue;
            }

            String filePath = volfile.getParent();

            logger.log(Level.INFO, "Looking up file {0} at path {1}", new Object[]{fileName, filePath});

            try {
                List<AbstractFile> resolvedFiles;
                if (filePath == null) {
                    resolvedFiles = fileManager.findFiles(fileName);
                } else {
                    // File changed the slashes back to \ on us...
                    filePath = filePath.replaceAll("\\\\", "/");  //NON-NLS
                    resolvedFiles = fileManager.findFiles(fileName, filePath);
                }

                // if we didn't get anything, then try adding a wildcard for extension
                if (resolvedFiles.isEmpty() && (fileName.contains(".") == false)) { //NON-NLS

                    // if there is already the same entry with ".exe" in the set, just use that one
                    if (fileSet.contains(file + ".exe")) { //NON-NLS
                        continue;
                    }

                    fileName += ".%"; //NON-NLS
                    logger.log(Level.INFO, "Looking up file (extension wildcard) {0} at path {1}", new Object[]{fileName, filePath});

                    resolvedFiles = filePath == null
                            ? fileManager.findFiles(fileName)
                            : fileManager.findFiles(fileName, filePath);
                }

                if (resolvedFiles.isEmpty()) {
                    errorMsgs.add(Bundle.VolatilityProcessor_exceptionMessage_fileNotFound(filePath, fileName, pluginName));
                    continue;
                }

                for (AbstractFile resolvedFile : resolvedFiles) {
                    if (resolvedFile.getType() == TSK_DB_FILES_TYPE_ENUM.SLACK) {
                        continue;
                    }
                    try {

                        String setName = Bundle.VolatilityProcessor_artifactAttribute_interestingFileSet(pluginName);
                        Collection<BlackboardAttribute> attributes = singleton(new BlackboardAttribute(TSK_SET_NAME, VOLATILITY, setName));

                        // Create artifact if it doesn't already exist.
                        if (!blackboard.artifactExists(resolvedFile, BlackboardArtifact.Type.TSK_INTERESTING_ITEM, attributes)) {
                            BlackboardArtifact volArtifact = resolvedFile.newAnalysisResult(
                                    BlackboardArtifact.Type.TSK_INTERESTING_ITEM, Score.SCORE_LIKELY_NOTABLE, 
                                    null, setName, null, 
                                    attributes)
                                    .getAnalysisResult();

                            try {
                                // index the artifact for keyword search
                                blackboard.postArtifact(volArtifact, VOLATILITY, null);
                            } catch (Blackboard.BlackboardException ex) {
                                errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_failedToIndexArtifact(pluginName));
                                /*
                                 * Log the exception as well as add it to the
                                 * error messages, to ensure that the stack
                                 * trace is not lost.
                                 */
                                logger.log(Level.SEVERE, String.format("Failed to index artifact (artifactId=%d) for for output of %s plugin", volArtifact.getArtifactID(), pluginName), ex);
                            }
                        }
                    } catch (TskCoreException ex) {
                        throw new VolatilityProcessorException(Bundle.VolatilityProcessor_exceptionMessage_errorCreatingArtifact(pluginName), ex);
                    }
                }
            } catch (TskCoreException ex) {
                throw new VolatilityProcessorException(Bundle.VolatilityProcessor_errorMessage_errorFindingFiles(pluginName), ex);
            }
        }
    }

    /**
     * Parses the output of a Volatility plugin and creates artifacts as needed.
     *
     * @param pluginName       Name of the Volatility plugin.
     * @param pluginOutputFile File that contains the output to parse.
     */
    private void createArtifactsFromPluginOutput(String pluginName, File pluginOutputFile) throws VolatilityProcessorException {
        progressMonitor.setProgressText("Parsing module " + pluginName);
        Set<String> fileSet = null;
        switch (pluginName) {
            case "dlllist": //NON-NLS
                fileSet = parseDllListOutput(pluginOutputFile);
                break;
            case "handles": //NON-NLS
                fileSet = parseHandlesOutput(pluginOutputFile);
                break;
            case "cmdline": //NON-NLS
                fileSet = parseCmdlineOutput(pluginOutputFile);
                break;
            case "psxview": //NON-NLS
                fileSet = parsePsxviewOutput(pluginOutputFile);
                break;
            case "pslist": //NON-NLS
                fileSet = parsePslistOutput(pluginOutputFile);
                break;
            case "psscan": //NON-NLS
                fileSet = parsePsscanOutput(pluginOutputFile);
                break;
            case "pstree": //NON-NLS
                fileSet = parsePstreeOutput(pluginOutputFile);
                break;
            case "svcscan": //NON-NLS
                fileSet = parseSvcscanOutput(pluginOutputFile);
                break;
            case "shimcache": //NON-NLS
                fileSet = parseShimcacheOutput(pluginOutputFile);
                break;
            default:
                break;
        }

        if (fileSet != null && !fileSet.isEmpty()) {
            progressMonitor.setProgressText("Flagging files from module " + pluginName);
            flagFiles(fileSet, pluginName);
        }
    }

    /**
     * Normalizes a file path from a Volatility plugin so it can be used to look
     * up the file in the case database.
     *
     * @param filePath Path to normalize.
     *
     * @return The normalized path or the empty string if the path cannot be
     *         normalized or should be ignored.
     */
    private String normalizePath(String filePath) {
        if (filePath == null) {
            return ""; //NON-NLS
        }
        String path = filePath.trim();

        // change slash direction
        path = path.replaceAll("\\\\", "/"); //NON-NLS
        path = path.toLowerCase();

        // \??\c:\windows ...
        if ((path.length() > 4) && (path.startsWith("/??/"))) { //NON-NLS
            path = path.substring(4);
        }

        // strip C: 
        if (path.contains(":")) { //NON-NLS
            int index = path.indexOf(":");
            if (index + 1 < path.length()) {
                path = path.substring(index + 1);
            }
        }

        path = path.replaceAll("/systemroot/", "/windows/");

        // catches 1 type of file in cmdline
        path = path.replaceAll("%systemroot%", "/windows/"); //NON-NLS
        path = path.replaceAll("/device/", ""); //NON-NLS
        // helps with finding files in handles plugin
        // example: \Device\clfs\Device\HarddiskVolume2\Users\joe\AppData\Local\Microsoft\Windows\UsrClass.dat{e15d4b01-1598-11e8-93e6-080027b5e733}.TM
        if (path.contains("/harddiskvolume")) { //NON-NLS
            // 16 advances beyond harddiskvolume and the number
            int index = path.indexOf("/harddiskvolume"); //NON-NLS
            if (index + 16 < path.length()) {
                path = path.substring(index + 16);
            }
        }

        // no point returning these. We won't map to them
        if (path.startsWith("/namedpipe/")) { //NON-NLS
            return ""; //NON-NLS
        }

        return path;
    }

    @NbBundle.Messages({
        "# {0} - plugin name",
        "VolatilityProcessor_errorMessage_outputParsingError=Error parsing output for {0} plugin"
    })
    private Set<String> parseHandlesOutput(File pluginOutputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(pluginOutputFile))) {
            // Ignore the first two header lines
            br.readLine();
            br.readLine();
            while ((line = br.readLine()) != null) {
                // 0x89ab7878      4      0x718  0x2000003 File             \Device\HarddiskVolume1\Documents and Settings\QA\Local Settings\Application 
                if (line.startsWith("0x") == false) { //NON-NLS
                    continue;
                }

                String TAG = " File "; //NON-NLS
                String file_path;
                if ((line.contains(TAG)) && (line.length() > 57)) {
                    file_path = line.substring(57);
                    if (file_path.contains("\"")) { //NON-NLS
                        file_path = file_path.substring(0, file_path.indexOf('\"')); //NON-NLS
                    }
                    // this file has a lot of device entries that are not files
                    if (file_path.startsWith("\\Device\\")) { //NON-NLS
                        if (file_path.contains("HardDiskVolume") == false) { //NON-NLS
                            continue;
                        }
                    }

                    fileSet.add(normalizePath(file_path));
                }
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("handles"));
            /*
             * Log the exception as well as add it to the error messages, to
             * ensure that the stack trace is not lost.
             */
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("handles"), ex);
        }
        return fileSet;
    }

    private Set<String> parseDllListOutput(File outputFile) {
        Set<String> fileSet = new HashSet<>();
        // read the first line from the text file
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                // we skip the Command Line entries because that data
                // is also in the 0x lines (and is more likely to have a full path there.

                // 0x4a680000     0x5000     0xffff \??\C:\WINDOWS\system32\csrss.exe
                // 0x7c900000    0xb2000     0xffff C:\WINDOWS\system32\ntdll.dll
                if (line.startsWith("0x") && line.length() > 33) {
                    // These lines do not have arguments
                    String file_path = line.substring(33);
                    fileSet.add(normalizePath(file_path));
                }
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("dlllist"));
            /*
             * Log the exception as well as add it to the error messages, to
             * ensure that the stack trace is not lost.
             */
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("dlllist"), ex);
        }
        return fileSet;
    }

    private Set<String> parseCmdlineOutput(File outputFile) {
        Set<String> fileSet = new HashSet<>();
        // read the first line from the text file
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.length() > 16) {
                    String TAG = "Command line : "; //NON-NLS
                    if ((line.startsWith(TAG)) && line.length() > TAG.length() + 1) {
                        String file_path;

                        // Command line : "C:\Program Files\VMware\VMware Tools\vmacthlp.exe"
                        // grab whats inbetween the quotes
                        if (line.charAt(TAG.length()) == '\"') { //NON-NLS
                            file_path = line.substring(TAG.length() + 1);
                            if (file_path.contains("\"")) { //NON-NLS
                                file_path = file_path.substring(0, file_path.indexOf('\"')); //NON-NLS
                            }
                        } // Command line : C:\WINDOWS\system32\csrss.exe ObjectDirectory=\Windows SharedSection=1024,3072,512
                        // grab everything before the next space - we don't want arguments
                        else {
                            file_path = line.substring(TAG.length());
                            if (file_path.contains(" ")) { //NON-NLS
                                file_path = file_path.substring(0, file_path.indexOf(' '));
                            }
                        }
                        fileSet.add(normalizePath(file_path));
                    }
                }
            }

        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("cmdline"));
            /*
             * Log the exception as well as add it to the error messages, to
             * ensure that the stack trace is not lost.
             */
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("cmdline"), ex);
        }
        return fileSet;
    }

    private Set<String> parseShimcacheOutput(File outputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            // ignore the first 2 header lines
            br.readLine();
            br.readLine();
            while ((line = br.readLine()) != null) {
                String file_path;
                //1970-01-01 00:00:00 UTC+0000   2017-10-25 13:07:30 UTC+0000   C:\WINDOWS\system32\msctfime.ime
                //2017-10-23 20:47:40 UTC+0000   2017-10-23 20:48:02 UTC+0000   \??\C:\WINDOWS\CT_dba9e71b-ad55-4132-a11b-faa946b197d6.exe
                if (line.length() > 62) {
                    file_path = line.substring(62);
                    if (file_path.contains("\"")) { //NON-NLS
                        file_path = file_path.substring(0, file_path.indexOf('\"')); //NON-NLS
                    }
                    fileSet.add(normalizePath(file_path));
                }
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("shimcache"));
            /*
             * Log the exception as well as add it to the error messages, to
             * ensure that the stack trace is not lost.
             */
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("shimcache"), ex);
        }
        return fileSet;
    }

    private Set<String> parsePsscanOutput(File outputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            // ignore the first two header lines
            br.readLine();
            br.readLine();
            while ((line = br.readLine()) != null) {
                // 0x000000000969a020 notepad.exe        3604   3300 0x16d40340 2018-01-12 14:41:16 UTC+0000  
                if (line.startsWith("0x") == false) { //NON-NLS
                    continue;
                } else if (line.length() < 37) {
                    continue;
                }

                String file_path = line.substring(19, 37);
                file_path = normalizePath(file_path);

                // ignore system, it's not really a path
                if (file_path.equals("system")) { //NON-NLS
                    continue;
                }
                fileSet.add(file_path);
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("psscan"));
            /*
             * Log the exception as well as add it to the error messages, to
             * ensure that the stack trace is not lost.
             */
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("psscan"), ex);
        }
        return fileSet;
    }

    private Set<String> parsePslistOutput(File outputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            // read the first line from the text file
            while ((line = br.readLine()) != null) {
                if (line.startsWith("0x") == false) { //NON-NLS
                    continue;
                }

                // 0x89cfb998 csrss.exe               704    640     14      532      0      0 2017-12-07 14:05:34 UTC+0000
                if (line.length() < 34) {
                    continue;
                }
                String file_path = line.substring(10, 34);
                file_path = normalizePath(file_path);

                // ignore system, it's not really a path
                if (file_path.equals("system")) { //NON-NLS
                    continue;
                }
                fileSet.add(file_path);
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("pslist"));
            /*
             * Log the exception as well as add it to the error messages, to
             * ensure that the stack trace is not lost.
             */
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("pslist"), ex);
        }
        return fileSet;
    }

    private Set<String> parsePsxviewOutput(File outputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            // ignore the first two header lines
            br.readLine();
            br.readLine();
            while ((line = br.readLine()) != null) {
                // 0x09adf980 svchost.exe            1368 True   True   False    True   True  True    True
                if (line.startsWith("0x") == false) { //NON-NLS
                    continue;
                }

                if (line.length() < 34) {
                    continue;
                }

                String file_path = line.substring(11, 34);
                file_path = normalizePath(file_path);

                // ignore system, it's not really a path
                if (file_path.equals("system")) { //NON-NLS
                    continue;
                }
                fileSet.add(file_path);
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("psxview"));
            /*
             * Log the exception as well as add it to the error messages, to
             * ensure that the stack trace is not lost.
             */
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("psxview"), ex);
        }
        return fileSet;
    }

    private Set<String> parsePstreeOutput(File outputFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            // read the first line from the text file
            while ((line = br.readLine()) != null) {
                //  ... 0x897e5020:services.exe                           772    728     15    287 2017-12-07 14:05:35 UTC+000
                String TAG = ":";
                if (line.contains(TAG)) {
                    int index = line.indexOf(TAG);
                    if (line.length() < 52 || index + 1 >= 52) {
                        continue;
                    }
                    String file_path = line.substring(line.indexOf(':') + 1, 52); //NON-NLS
                    file_path = normalizePath(file_path);

                    // ignore system, it's not really a path
                    if (file_path.equals("system")) { //NON-NLS
                        continue;
                    }
                    fileSet.add(file_path);
                }
            }
        } catch (IOException ex) {
            errorMsgs.add(Bundle.VolatilityProcessor_errorMessage_outputParsingError("pstree"));
            /*
             * Log the exception as well as add it to the error messages, to
             * ensure that the stack trace is not lost.
             */
            logger.log(Level.SEVERE, Bundle.VolatilityProcessor_errorMessage_outputParsingError("pstree"), ex);
        }
        return fileSet;
    }

    private Set<String> parseSvcscanOutput(File PluginFile) {
        String line;
        Set<String> fileSet = new HashSet<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(PluginFile));
            // read the first line from the text file
            while ((line = br.readLine()) != null) {
                String file_path;
                String TAG = "Binary Path: ";
                if (line.startsWith(TAG) && line.length() > TAG.length() + 1) {
                    if (line.charAt(TAG.length()) == '\"') {
                        file_path = line.substring(TAG.length() + 1);
                        if (file_path.contains("\"")) {
                            file_path = file_path.substring(0, file_path.indexOf('\"'));
                        }
                    } // Binary Path: -
                    else if (line.charAt(TAG.length()) == '-') {
                        continue;
                    } // Command line : C:\Windows\System32\svchost.exe -k LocalSystemNetworkRestricted
                    else {
                        file_path = line.substring(TAG.length());
                        if (file_path.contains(" ")) {
                            file_path = file_path.substring(0, file_path.indexOf(' '));
                        }
                        // We can't do anything with driver entries
                        if (file_path.startsWith("\\Driver\\")) {
                            continue;
                        } else if (file_path.startsWith("\\FileSystem\\")) {
                            continue;
                        }
                    }
                    fileSet.add(normalizePath(file_path));
                }
            }
            br.close();
        } catch (IOException ex) {
            String msg = "Error parsing svcscan output";
            logger.log(Level.SEVERE, msg, ex);
            errorMsgs.add(msg);
        }
        return fileSet;
    }

    /**
     * Requests cancellation of processing.
     */
    void cancel() {
        isCancelled = true;
    }

    /**
     * Exception type thrown when the processor experiences an error condition.
     */
    final class VolatilityProcessorException extends Exception {

        private static final long serialVersionUID = 1L;

        private VolatilityProcessorException(String message) {
            super(message);
        }

        private VolatilityProcessorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
