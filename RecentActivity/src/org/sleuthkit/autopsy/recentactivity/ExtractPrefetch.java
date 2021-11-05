/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
 *
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
package org.sleuthkit.autopsy.recentactivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import static org.sleuthkit.autopsy.coreutils.FileUtil.escapeFileName;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.SQLiteDBConnect;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extract the Prefetch Files and process them thru an External program. The
 * data will then be added to the TSK_PROG_RUN artifact. Associated artifacts
 * will be created if possible.
 */
final class ExtractPrefetch extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractPrefetch.class.getName());

    private final IngestJobContext context;    
    private static final String PREFETCH_TSK_COMMENT = "Prefetch File";
    private static final String PREFETCH_FILE_LOCATION = "/windows/prefetch";
    private static final String PREFETCH_TOOL_FOLDER = "markmckinnon"; //NON-NLS
    private static final String PREFETCH_TOOL_NAME_WINDOWS_64 = "parse_prefetch_x64.exe"; //NON-NLS
    private static final String PREFETCH_TOOL_NAME_WINDOWS_32 = "parse_prefetch_x32.exe"; //NON-NLS
    private static final String PREFETCH_TOOL_NAME_MACOS = "parse_prefetch_macos"; //NON-NLS
    private static final String PREFETCH_TOOL_NAME_LINUX = "parse_prefetch_linux"; //NON-NLS
    private static final String PREFETCH_OUTPUT_FILE_NAME = "Output.txt"; //NON-NLS
    private static final String PREFETCH_ERROR_FILE_NAME = "Error.txt"; //NON-NLS
    private static final String PREFETCH_PARSER_DB_FILE = "Autopsy_PF_DB.db3"; //NON-NLS
    private static final String PREFETCH_DIR_NAME = "prefetch"; //NON-NLS

    @Messages({
        "ExtractPrefetch_module_name=Windows Prefetch Analyzer",
        "# {0} - sub module name",
        "ExtractPrefetch_errMsg_prefetchParsingFailed={0}: Error analyzing prefetch files"
    })
    ExtractPrefetch(IngestJobContext context) {
        super(Bundle.ExtractPrefetch_module_name(), context);
        this.context = context;
    }

    /**
     * Get the temp folder name.
     *
     * @param dataSource Current data source
     *
     * @return The folder name
     */
    private String getPrefetchTempFolder(Content dataSource) {
        return dataSource.getId() + "-" + PREFETCH_PARSER_DB_FILE;
    }

    @Override
    void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {

        long ingestJobId = context.getJobId();

        String modOutPath = Case.getCurrentCase().getModuleDirectory() + File.separator + PREFETCH_DIR_NAME;
        File dir = new File(modOutPath);
        if (dir.exists() == false) {
            boolean dirMade = dir.mkdirs();
            if (!dirMade) {
                logger.log(Level.SEVERE, "Error creating directory to store prefetch output database"); //NON-NLS
                return; //If we cannot create the directory then we need to exit
            }
        }

        extractPrefetchFiles(dataSource, ingestJobId);

        final String prefetchDumper = getPathForPrefetchDumper();
        if (prefetchDumper == null) {
            logger.log(Level.SEVERE, "Error finding parse_prefetch program"); //NON-NLS
            return; //If we cannot find the parse_prefetch program we cannot proceed
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        String modOutFile = modOutPath + File.separator + getPrefetchTempFolder(dataSource);
        try {
            String tempDirPath = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), getPrefetchTempFolder(dataSource), ingestJobId);
            parsePrefetchFiles(prefetchDumper, tempDirPath, modOutFile, modOutPath);
            File prefetchDatabase = new File(modOutFile);
            if (prefetchDatabase.exists()) {
                createAppExecArtifacts(modOutFile, dataSource);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error parsing prefetch files", ex); //NON-NLS 
            addErrorMessage(Bundle.ExtractPrefetch_errMsg_prefetchParsingFailed(Bundle.ExtractPrefetch_module_name()));
        }
    }

    /**
     * Extract prefetch file to temp directory to process. Checks to make sure
     * that the prefetch files only come from the /Windows/Prefetch directory
     *
     * @param dataSource - datasource to search for prefetch files
     */
    void extractPrefetchFiles(Content dataSource, long ingestJobId) {
        List<AbstractFile> pFiles;

        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        try {
            pFiles = fileManager.findFiles(dataSource, "%.pf"); //NON-NLS            
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to find prefetch files.", ex); //NON-NLS
            return;  // No need to continue
        }

        for (AbstractFile pFile : pFiles) {

            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            if (pFile.getParentPath().toLowerCase().contains(PREFETCH_FILE_LOCATION.toLowerCase()) && pFile.getSize() > 0) {
                String origFileName = pFile.getName();
                String ext = FilenameUtils.getExtension(origFileName);
                String baseName = FilenameUtils.getBaseName(origFileName);
                String fileName = escapeFileName(String.format("%s_%d.%s", baseName, pFile.getId(), ext));
                String baseRaTempPath = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), getPrefetchTempFolder(dataSource), ingestJobId);
                String prefetchFile = Paths.get(baseRaTempPath, fileName).toString();
                try {
                    ContentUtils.writeToFile(pFile, new File(prefetchFile));
                } catch (IOException ex) {
                    logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", pFile.getName(), prefetchFile), ex); //NON-NLS
                }
            }
        }

    }

    /**
     * Run the export parse_prefetch program against the prefetch files
     *
     * @param prefetchExePath - Path to the Executable to run
     * @param prefetchDir     - Directory where the prefetch files reside to be
     *                        processed.
     * @param tempOutFile     - Output database file name and path.
     * @param tempOutPath     - Directory to store the output and error files.
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    void parsePrefetchFiles(String prefetchExePath, String prefetchDir, String tempOutFile, String tempOutPath) throws FileNotFoundException, IOException {
        final Path outputFilePath = Paths.get(tempOutPath, PREFETCH_OUTPUT_FILE_NAME);
        final Path errFilePath = Paths.get(tempOutPath, PREFETCH_ERROR_FILE_NAME);

        List<String> commandLine = new ArrayList<>();
        commandLine.add(prefetchExePath);
        commandLine.add(prefetchDir);  //NON-NLS
        commandLine.add(tempOutFile);

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.redirectOutput(outputFilePath.toFile());
        processBuilder.redirectError(errFilePath.toFile());

        ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context, true));
    }

    /**
     * Get the path and executable for the parse_prefetch program. Checks for
     * specific version of OS to get proper executable.
     *
     * @return - path and executable to run.
     *
     */
    private String getPathForPrefetchDumper() {
        Path path = null;
        if (PlatformUtil.isWindowsOS()) {
            if (PlatformUtil.is64BitOS()) {
                path = Paths.get(PREFETCH_TOOL_FOLDER, PREFETCH_TOOL_NAME_WINDOWS_64);
            } else {
                path = Paths.get(PREFETCH_TOOL_FOLDER, PREFETCH_TOOL_NAME_WINDOWS_32);
            }
        } else {
            if ("Linux".equals(PlatformUtil.getOSName())) {
                path = Paths.get(PREFETCH_TOOL_FOLDER, PREFETCH_TOOL_NAME_LINUX);
            } else {
                path = Paths.get(PREFETCH_TOOL_FOLDER, PREFETCH_TOOL_NAME_MACOS);
            }
        }
        File prefetchToolFile = InstalledFileLocator.getDefault().locate(path.toString(),
                ExtractPrefetch.class.getPackage().getName(), false);
        if (prefetchToolFile != null) {
            return prefetchToolFile.getAbsolutePath();
        }

        return null;

    }

    /**
     * Create the artifacts from external run of the parse_prefetch program
     *
     * @param prefetchDb - Database file to read from running the parse_prefetch
     *                   program.
     * @param dataSource - The datasource to search in
     *
     */
    private void createAppExecArtifacts(String prefetchDb, Content dataSource) {
        List<BlackboardArtifact> blkBrdArtList = new ArrayList<>();

        String sqlStatement = "SELECT prefetch_File_Name, actual_File_Name, file_path, Number_time_file_run, Embeded_date_Time_Unix_1, "
                + " Embeded_date_Time_Unix_2, Embeded_date_Time_Unix_3, Embeded_date_Time_Unix_4, Embeded_date_Time_Unix_5,"
                + " Embeded_date_Time_Unix_6, Embeded_date_Time_Unix_7, Embeded_date_Time_Unix_8 "
                + " FROM prefetch_file_info;"; //NON-NLS

        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + prefetchDb); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {

            while (resultSet.next()) {

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Cancelled Prefetch Artifact Creation."); //NON-NLS
                    return;
                }

                String prefetchFileName = resultSet.getString("prefetch_File_Name");
                String applicationName = resultSet.getString("actual_File_Name"); //NON-NLS
                List<Long> executionTimes = new ArrayList<>();
                executionTimes.add(Long.valueOf(resultSet.getInt("Embeded_date_Time_Unix_1")));
                executionTimes.add(Long.valueOf(resultSet.getInt("Embeded_date_Time_Unix_2")));
                executionTimes.add(Long.valueOf(resultSet.getInt("Embeded_date_Time_Unix_3")));
                executionTimes.add(Long.valueOf(resultSet.getInt("Embeded_date_Time_Unix_4")));
                executionTimes.add(Long.valueOf(resultSet.getInt("Embeded_date_Time_Unix_5")));
                executionTimes.add(Long.valueOf(resultSet.getInt("Embeded_date_Time_Unix_6")));
                executionTimes.add(Long.valueOf(resultSet.getInt("Embeded_date_Time_Unix_7")));
                executionTimes.add(Long.valueOf(resultSet.getInt("Embeded_date_Time_Unix_8")));
                String timesProgramRun = resultSet.getString("Number_time_file_run");
                String filePath = resultSet.getString("file_path");

                Set<Long> prefetchExecutionTimes = findNonZeroExecutionTimes(executionTimes);

                String baseName = FilenameUtils.getBaseName(prefetchFileName);
                Matcher match = Pattern.compile("_(?<objId>\\d*)\\s*$").matcher(baseName);
                if (!match.find()) {
                    logger.log(Level.WARNING, "Invalid format for PF file: " + prefetchFileName);//NON-NLS
                    continue;
                }

                /**
                 * A prefetch file is created when a program is run and the
                 * superfetch service collected data about the first 10 seconds
                 * of the run, the trace data is then written to a new prefetch
                 * file or merged with an existing prefetch file. If the
                 * prefetch file gets deleted for some reason then a new one
                 * will be created. See 7500 in JIRA for more information.
                 */
                AbstractFile pfAbstractFile = null;
                try {
                    Content c = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(Long.parseLong(match.group("objId")));
                    if (c instanceof AbstractFile) {
                        pfAbstractFile = (AbstractFile) c;
                    }
                } catch (NoCurrentCaseException | TskCoreException | NumberFormatException ex) {
                    logger.log(Level.SEVERE, "Unable to find content for: " + prefetchFileName, ex);
                }

                if (pfAbstractFile != null) {
                    for (Long executionTime : prefetchExecutionTimes) {

                        // only add prefetch file entries that have an actual date associated with them
                        Collection<BlackboardAttribute> blkBrdAttributes = Arrays.asList(
                                new BlackboardAttribute(
                                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, getDisplayName(),
                                        applicationName),//NON-NLS
                                new BlackboardAttribute(
                                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH, getDisplayName(), filePath),
                                new BlackboardAttribute(
                                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, getDisplayName(),
                                        executionTime),
                                new BlackboardAttribute(
                                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COUNT, getDisplayName(), Integer.valueOf(timesProgramRun)),
                                new BlackboardAttribute(
                                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, getDisplayName(), PREFETCH_TSK_COMMENT));

                        try {
                            BlackboardArtifact blkBrdArt = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_PROG_RUN, pfAbstractFile, blkBrdAttributes);
                            blkBrdArtList.add(blkBrdArt);
                            BlackboardArtifact associatedBbArtifact = createAssociatedArtifact(applicationName.toLowerCase(), filePath, blkBrdArt, dataSource);
                            if (associatedBbArtifact != null) {
                                blkBrdArtList.add(associatedBbArtifact);
                            }
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Exception Adding Artifact.", ex);//NON-NLS
                        }
                    }
                } else {
                    logger.log(Level.WARNING, "File has a null value " + prefetchFileName);//NON-NLS
                }

            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, String.format("Error while trying to read into a sqlite db %s.", prefetchDb));//NON-NLS
            logger.log(Level.WARNING, ex.getMessage());
        }

        if (!blkBrdArtList.isEmpty() && !context.dataSourceIngestIsCancelled()) {
            postArtifacts(blkBrdArtList);
        }
    }

    /**
     * Create associated artifacts using file path name and the artifact it
     * associates with
     *
     * @param fileName     the filename to search for
     * @param filePathName file and path of object being associated with
     * @param bba          blackboard artifact to associate with
     * @param dataSource   - The datasource to search in
     *
     * @returnv BlackboardArtifact or a null value
     */
    private BlackboardArtifact createAssociatedArtifact(String fileName, String filePathName, BlackboardArtifact bba, Content dataSource) throws TskCoreException {
        AbstractFile sourceFile = getAbstractFile(fileName, filePathName, dataSource);
        if (sourceFile != null) {
            return createAssociatedArtifact(sourceFile, bba);
        }
        return null;
    }

    /**
     * Get the abstract file for the prefetch file.
     *
     * @param fileName   - File name of the prefetch file to find.
     * @param filePath   - Path where the prefetch file is located.
     * @param dataSource - The datasource to search in
     *
     * @return Abstract file of the prefetch file.
     *
     */
    AbstractFile getAbstractFile(String fileName, String filePath, Content dataSource) {
        List<AbstractFile> files;

        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        try {
            files = fileManager.findFiles(dataSource, fileName); //NON-NLS

        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to find prefetch files.", ex); //NON-NLS
            return null;  // No need to continue
        }

        for (AbstractFile pFile : files) {
            if (pFile.getParentPath().toLowerCase().endsWith(filePath.toLowerCase() + '/')) {
                return pFile;
            }
        }

        return null;

    }

    /**
     * Cycle thru the execution times list and only return a new list of times
     * that are greater than zero.
     *
     * @param executionTimes - list of prefetch execution times 8 possible
     *                       timestamps
     *
     * @return List of timestamps that are greater than zero
     */
    private Set<Long> findNonZeroExecutionTimes(List<Long> executionTimes) {
        Set<Long> prefetchExecutionTimes = new HashSet<>();
        for (Long executionTime : executionTimes) {                        // only add prefetch file entries that have an actual date associated with them
            if (executionTime > 0) {
                prefetchExecutionTimes.add(executionTime);
            }
        }
        return prefetchExecutionTimes;
    }
}
