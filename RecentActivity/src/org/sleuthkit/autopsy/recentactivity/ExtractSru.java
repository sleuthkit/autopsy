/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
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
 * Extract the System Resource Usage database to a temp directory so it can be
 * parsed into a SQLite db and then brought into extracted content
 */
final class ExtractSru extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractSru.class.getName());

    private static final String APPLICATION_USAGE_SOURCE_NAME = "System Resource Usage - Application Usage"; //NON-NLS
    private static final String NETWORK_USAGE_SOURCE_NAME = "System Resource Usage - Network Usage";
    private static final String SRU_TOOL_FOLDER = "markmckinnon"; //NON-NLS
    private static final String SRU_TOOL_NAME_WINDOWS_32 = "Export_Srudb_32.exe"; //NON-NLS
    private static final String SRU_TOOL_NAME_WINDOWS_64 = "Export_Srudb_64.exe"; //NON-NLS
    private static final String SRU_TOOL_NAME_LINUX = "Export_Srudb_Linux.exe"; //NON-NLS
    private static final String SRU_TOOL_NAME_MAC = "Export_srudb_macos"; //NON-NLS
    private static final String SRU_OUTPUT_FILE_NAME = "Output.txt"; //NON-NLS
    private static final String SRU_ERROR_FILE_NAME = "Error.txt"; //NON-NLS

    private static final Map<String, AbstractFile> applicationFilesFound = new HashMap<>();
    private final IngestJobContext context;

    @Messages({
        "ExtractSru_module_name=System Resource Usage Analyzer"
    })
    ExtractSru(IngestJobContext context) {
        super(Bundle.ExtractSru_module_name(), context);
        this.context = context;
    }

    @Messages({
        "ExtractSru_error_finding_export_srudb_program=Error finding export_srudb program",
        "ExtractSru_process_error_executing_export_srudb_program=Error running export_srudb program"
    })

    @Override
    void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {

        String modOutPath = Case.getCurrentCase().getModuleDirectory() + File.separator + "sru";
        File dir = new File(modOutPath);
        if (dir.exists() == false) {
            dir.mkdirs();
        }

        String tempDirPath = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), "sru", context.getJobId()); //NON-NLS
        String softwareHiveFileName = getSoftwareHiveFile(dataSource, tempDirPath);

        if (softwareHiveFileName == null) {
            return;
        }

        AbstractFile sruAbstractFile = getSruFile(dataSource, tempDirPath);

        if (sruAbstractFile == null) {
            return; //If we cannot find the srudb.dat file we cannot proceed which is ok 
        }

        final String sruDumper = getPathForSruDumper();
        if (sruDumper == null) {
            this.addErrorMessage(Bundle.ExtractSru_error_finding_export_srudb_program());
            logger.log(Level.SEVERE, "Error finding export_srudb program"); //NON-NLS
            return; //If we cannot find the export_srudb program we cannot proceed
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        try {
            String modOutFile = modOutPath + File.separator + sruAbstractFile.getId() + "_srudb.db3";
            String sruFileName = tempDirPath + File.separator + sruAbstractFile.getId() + "_" + sruAbstractFile.getName();

            extractSruFiles(sruDumper, sruFileName, modOutFile, tempDirPath, softwareHiveFileName);

            findSruExecutedFiles(modOutFile, dataSource);

            createNetUsageArtifacts(modOutFile, sruAbstractFile);
            createAppUsageArtifacts(modOutFile, sruAbstractFile);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error processing SRUDB.dat file", ex); //NON-NLS=
            this.addErrorMessage(Bundle.ExtractSru_process_error_executing_export_srudb_program());
        }
    }

    @Messages({
        "ExtractSru_process_errormsg_find_software_hive=Unable to find SOFTWARE HIVE file",
        "ExtractSru_process_errormsg_write_software_hive=Unable to write SOFTWARE HIVE file"
    })

    /**
     * Extract the SOFTWARE hive file to the temp directory
     *
     * @param dataSource  datasource where software hiive is
     * @param tempDirPath temp directory to write file to
     *
     * @return Software hive file location
     */
    String getSoftwareHiveFile(Content dataSource, String tempDirPath) {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        List<AbstractFile> softwareHiveFiles;

        try {
            softwareHiveFiles = fileManager.findFiles(dataSource, "SOFTWARE"); //NON-NLS            
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractSru_process_errormsg_find_software_hive());
            logger.log(Level.WARNING, "Unable to find SOFTWARE HIVE file.", ex); //NON-NLS
            return null;  // No need to continue
        }

        String softwareHiveFileName = null;

        for (AbstractFile softwareFile : softwareHiveFiles) {

            if (softwareFile.getParentPath().endsWith("/config/")) {
                softwareHiveFileName = tempDirPath + File.separator + softwareFile.getId() + "_" + softwareFile.getName();

                try {
                    ContentUtils.writeToFile(softwareFile, new File(softwareHiveFileName));
                } catch (IOException ex) {
                    this.addErrorMessage(Bundle.ExtractSru_process_errormsg_find_software_hive());
                    logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", softwareFile.getName(), softwareFile), ex); //NON-NLS
                    return null;
                }
            }
        }
        return softwareHiveFileName;
    }

    @Messages({
        "ExtractSru_process_errormsg_find_srudb_dat=Unable to find srudb.dat file",
        "ExtractSru_process_errormsg_write_srudb_dat=Unable to write srudb.dat file"
    })
    /**
     * Extract the SOFTWARE hive file to the temp directory
     *
     * @param dataSource  datasource where software hiive is
     * @param tempDirPath temp directory to write file to
     *
     * @return Software hive file location
     */
    AbstractFile getSruFile(Content dataSource, String tempDirPath) {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        List<AbstractFile> sruFiles;

        try {
            sruFiles = fileManager.findFiles(dataSource, "SRUDB.DAT"); //NON-NLS            
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractSru_process_errormsg_find_srudb_dat());
            logger.log(Level.WARNING, "Unable to find SRUDB.DAT file.", ex); //NON-NLS
            return null;  // No need to continue
        }

        AbstractFile sruAbstractFile = null;

        for (AbstractFile sruFile : sruFiles) {

            String sruFileName = tempDirPath + File.separator + sruFile.getId() + "_" + sruFile.getName();
            sruAbstractFile = sruFile;

            try {
                ContentUtils.writeToFile(sruFile, new File(sruFileName));
            } catch (IOException ex) {
                this.addErrorMessage(Bundle.ExtractSru_process_errormsg_write_srudb_dat());
                logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", sruFile.getName(), sruFile), ex); //NON-NLS
                return null;
            }

        }
        return sruAbstractFile;
    }

    /**
     * Run the export srudb program against the srudb.dat file
     *
     * @param sruExePath
     * @param tempDirPath
     * @param tempOutPath
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    void extractSruFiles(String sruExePath, String sruFile, String tempOutFile, String tempOutPath, String softwareHiveFile) throws IOException {
        final Path outputFilePath = Paths.get(tempOutPath, SRU_OUTPUT_FILE_NAME);
        final Path errFilePath = Paths.get(tempOutPath, SRU_ERROR_FILE_NAME);

        List<String> commandLine = new ArrayList<>();
        commandLine.add(sruExePath);
        commandLine.add(sruFile);  //NON-NLS
        commandLine.add(softwareHiveFile);
        commandLine.add(tempOutFile);

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.redirectOutput(outputFilePath.toFile());
        processBuilder.redirectError(errFilePath.toFile());

        ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context, true));
    }

    private String getPathForSruDumper() {
        Path path = null;
        if (PlatformUtil.isWindowsOS()) {
            if (PlatformUtil.is64BitOS()) {
                path = Paths.get(SRU_TOOL_FOLDER, SRU_TOOL_NAME_WINDOWS_64);
            } else {
                path = Paths.get(SRU_TOOL_FOLDER, SRU_TOOL_NAME_WINDOWS_32);
            }
        } else {
            if ("Linux".equals(PlatformUtil.getOSName())) {
                path = Paths.get(SRU_TOOL_FOLDER, SRU_TOOL_NAME_LINUX);
            } else {
                path = Paths.get(SRU_TOOL_FOLDER, SRU_TOOL_NAME_MAC);
            }
        }
        File sruToolFile = InstalledFileLocator.getDefault().locate(path.toString(),
                ExtractSru.class.getPackage().getName(), false);
        if (sruToolFile != null) {
            return sruToolFile.getAbsolutePath();
        }

        return null;
    }

    private void findSruExecutedFiles(String sruDb, Content dataSource) {

        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();

        String sqlStatement = "SELECT DISTINCT SUBSTR(LTRIM(IdBlob, '\\Device\\HarddiskVolume'), INSTR(LTRIM(IdBlob, '\\Device\\HarddiskVolume'), '\\'))  "
                + " application_name, idBlob source_name FROM SruDbIdMapTable WHERE idType = 0 AND idBlob NOT LIKE '!!%'"; //NON-NLS

        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + sruDb); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {

            while (resultSet.next()) {

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Cancelled SRU Artifact Creation."); //NON-NLS
                    return;
                }

                String applicationName = resultSet.getString("application_name"); //NON-NLS
                String sourceName = resultSet.getString("source_name"); //NON-NLS

                String normalizePathName = FilenameUtils.normalize(applicationName, true);
                String fileName = FilenameUtils.getName(normalizePathName);
                String filePath = FilenameUtils.getPath(normalizePathName);
                if (fileName.contains(" [")) {
                    fileName = fileName.substring(0, fileName.indexOf(" ["));
                }
                List<AbstractFile> sourceFiles;
                try {
                    sourceFiles = fileManager.findFiles(dataSource, fileName, filePath); //NON-NLS
                    for (AbstractFile sourceFile : sourceFiles) {
                        if (sourceFile.getParentPath().endsWith(filePath)) {
                            applicationFilesFound.put(sourceName.toLowerCase(), sourceFile);
                        }
                    }

                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, String.format("Error finding actual file %s. file may not exist", normalizePathName)); //NON-NLS
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Error while trying to read into a sqlite db.", ex);//NON-NLS
        }

    }

    private void createNetUsageArtifacts(String sruDb, AbstractFile sruAbstractFile) {
        List<BlackboardArtifact> bba = new ArrayList<>();

        String sqlStatement = "SELECT STRFTIME('%s', timestamp) ExecutionTime, a.application_name, b.Application_Name formatted_application_name, User_Name, "
                + " bytesSent, BytesRecvd FROM network_Usage a, SruDbIdMapTable, exe_to_app b "
                + " where appId = IdIndex and IdType = 0 and a.application_name = b.source_name order by ExecutionTime;"; //NON-NLS

        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + sruDb); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {

            while (resultSet.next()) {

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Cancelled SRU Net Usage Artifact Creation."); //NON-NLS
                    return;
                }

                String applicationName = resultSet.getString("Application_Name"); //NON-NLS
                String formattedApplicationName = resultSet.getString("formatted_Application_name");
                Long executionTime = Long.valueOf(resultSet.getInt("ExecutionTime")); //NON-NLS
                Long bytesSent = Long.valueOf(resultSet.getInt("bytesSent")); //NON-NLS
                Long bytesRecvd = Long.valueOf(resultSet.getInt("BytesRecvd")); //NON-NLS
                String userName = resultSet.getString("User_Name"); //NON-NLS

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, getDisplayName(),
                                formattedApplicationName),//NON-NLS
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME, getDisplayName(),
                                userName),
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, getDisplayName(),
                                executionTime),
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_BYTES_SENT, getDisplayName(), bytesSent),
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_BYTES_RECEIVED, getDisplayName(), bytesRecvd),
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, getDisplayName(), NETWORK_USAGE_SOURCE_NAME));

                try {
                    BlackboardArtifact bbart = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_PROG_RUN, sruAbstractFile, bbattributes);
                    bba.add(bbart);
                    BlackboardArtifact associateBbArtifact = createAssociatedArtifact(applicationName.toLowerCase(), bbart);
                    if (associateBbArtifact != null) {
                        bba.add(associateBbArtifact);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Exception Adding Artifact.", ex);//NON-NLS
                }
            }

        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Error while trying to read into a sqlite db.", ex);//NON-NLS
        }

        if (!context.dataSourceIngestIsCancelled()) {
            postArtifacts(bba);
        }
    }

    private void createAppUsageArtifacts(String sruDb, AbstractFile sruAbstractFile) {
        List<BlackboardArtifact> bba = new ArrayList<>();

        String sqlStatement = "SELECT STRFTIME('%s', timestamp) ExecutionTime, a.application_name, b.Application_Name formatted_application_name, User_Name "
                + " FROM Application_Resource_Usage a, SruDbIdMapTable, exe_to_app b WHERE "
                + " idType = 0 and idIndex = appId and a.application_name = b.source_name order by ExecutionTime;"; //NON-NLS

        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + sruDb); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {

            while (resultSet.next()) {

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Cancelled SRU Net Usage Artifact Creation."); //NON-NLS
                    return;
                }

                String applicationName = resultSet.getString("Application_Name"); //NON-NLS
                String formattedApplicationName = resultSet.getString("formatted_application_name");
                Long executionTime = Long.valueOf(resultSet.getInt("ExecutionTime")); //NON-NLS
                String userName = resultSet.getString("User_Name");

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, getDisplayName(),
                                formattedApplicationName),//NON-NLS
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME, getDisplayName(),
                                userName),
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, getDisplayName(),
                                executionTime),
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, getDisplayName(), APPLICATION_USAGE_SOURCE_NAME));

                try {
                    BlackboardArtifact bbart = createArtifactWithAttributes(BlackboardArtifact.Type.TSK_PROG_RUN, sruAbstractFile, bbattributes);
                    bba.add(bbart);
                    BlackboardArtifact associateBbArtifact = createAssociatedArtifact(applicationName.toLowerCase(), bbart);
                    if (associateBbArtifact != null) {
                        bba.add(associateBbArtifact);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Exception Adding Artifact.", ex);//NON-NLS
                }
            }

        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Error while trying to read into a sqlite db.", ex);//NON-NLS
        }

        if (!context.dataSourceIngestIsCancelled()) {
            postArtifacts(bba);
        }

    }

    /**
     * Create associated artifacts using file path name and the artifact it
     * associates with
     *
     * @param filePathName file and path of object being associated with
     *
     * @param bba          blackboard artifact to associate with
     *
     * @returnv BlackboardArtifact or a null value
     */
    private BlackboardArtifact createAssociatedArtifact(String filePathName, BlackboardArtifact bba) throws TskCoreException {
        if (applicationFilesFound.containsKey(filePathName)) {
            AbstractFile sourceFile = applicationFilesFound.get(filePathName);
            return createAssociatedArtifact(sourceFile, bba);
        }

        return null;
    }

}
