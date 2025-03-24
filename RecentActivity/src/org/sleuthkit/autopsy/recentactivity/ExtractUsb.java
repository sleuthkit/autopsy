/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2025 Sleuth Kit Labs.
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
final class ExtractUsb extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractUsb.class.getName());

    private static final String APPLICATION_USAGE_SOURCE_NAME = "System Resource Usage - Application Usage"; //NON-NLS
    private static final String NETWORK_USAGE_SOURCE_NAME = "System Resource Usage - Network Usage";
    private static final String USB_TOOL_FOLDER = "markmckinnon"; //NON-NLS
    private static final String USB_TOOL_NAME_WINDOWS = "usbparser.exe"; //NON-NLS
    private static final String USB_TOOL_NAME_LINUX = "usbparser"; //NON-NLS
    private static final String USB_OUTPUT_FILE_NAME = "Output.txt"; //NON-NLS
    private static final String USB_ERROR_FILE_NAME = "Error.txt"; //NON-NLS

    private static final Map<String, AbstractFile> applicationFilesFound = new HashMap<>();
    private final IngestJobContext context;

    @Messages({
        "ExtractUsb_module_name=USB Analyzer"
    })
    ExtractUsb(IngestJobContext context) {
        super(Bundle.ExtractUsb_module_name(), context);
        this.context = context;
    }

    @Messages({
        "ExtractUsb_error_finding_usbparser_program=Error finding usbparser program",
        "ExtractUsb_process_error_executing_export_srudb_program=Error running usbparser program"
    })

    @Override
    void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {

        String modOutPath = Case.getCurrentCase().getModuleDirectory() + File.separator + "usb";
        File dir = new File(modOutPath);
        if (dir.exists() == false) {
            dir.mkdirs();
        }

        String tempDirPath = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), "usb", context.getJobId()); //NON-NLS
        String softwareHiveFileName = getHiveFile(dataSource, tempDirPath, "software");
        String systemHiveFileName = getHiveFile(dataSource, tempDirPath, "system");
        getNTUserHiveFiles(dataSource, tempDirPath);
        getEvtxFiles(dataSource, tempDirPath);
        getLnkFiles(dataSource, tempDirPath);
        
/*        final String usbDumper = getPathForUsbDumper();
        if (usbDumper == null) {
            this.addErrorMessage(Bundle.ExtractSru_error_finding_usbparser_program());
            logger.log(Level.SEVERE, "Error finding usbparser program"); //NON-NLS
            return; //If we cannot find the export_srudb program we cannot proceed
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        try {
            String modOutFile = modOutPath + File.separator + "parseUsb.db3";
            String usbFileLocation = tempDirPath;

            extractUsbFiles(usbDumper, modOutFile, usbFileLocation);
            

            findSruExecutedFiles(modOutFile, dataSource);

            createNetUsageArtifacts(modOutFile, sruAbstractFile);
            createAppUsageArtifacts(modOutFile, sruAbstractFile);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error processing SRUDB.dat file", ex); //NON-NLS=
            this.addErrorMessage(Bundle.ExtractSru_process_error_executing_export_srudb_program());
        }
*/    }

    @Messages({
        "ExtractUsb_process_errormsg_find_hive=Unable to find HIVE file",
        "ExtractUsb_process_errormsg_write_hive=Unable to write HIVE file"
    })

    /**
     * Extract the hive file to the temp directory
     *
     * @param dataSource  datasource where software hive is
     * @param tempDirPath temp directory to write file to
     * @param hiveFileName name of the hive file to extract
     *
     * @return hive file location
     */
    String getHiveFile(Content dataSource, String tempDirPath, String hiveName) {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        List<AbstractFile> hiveFiles;

        try {
            hiveFiles = fileManager.findFiles(dataSource, hiveName); //NON-NLS            
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractUsb_process_errormsg_find_hive());
            logger.log(Level.WARNING, String.format("Unable to find %s HIVE file.", hiveName), ex); //NON-NLS
            return null;  // No need to continue
        }

        String hiveFileName = null;

        for (AbstractFile hiveFile : hiveFiles) {

            if (hiveFile.getParentPath().endsWith("/config/")) {
                hiveFileName = tempDirPath + File.separator + hiveFile.getName();

                try {
                    ContentUtils.writeToFile(hiveFile, new File(hiveFileName));
                } catch (IOException ex) {
                    this.addErrorMessage(Bundle.ExtractUsb_process_errormsg_find_hive());
                    logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", hiveFile.getName(), hiveFile), ex); //NON-NLS
                    return null;
                }
            }
        }
        return hiveFileName;
    }

    /**
     * Extract the NTuser.dat hive file to the temp directory
     *
     * @param dataSource  datasource where software hive is
     * @param tempDirPath temp directory to write file to
     *
     */
    void getNTUserHiveFiles(Content dataSource, String tempDirPath) {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        List<AbstractFile> hiveFiles;

        try {
            hiveFiles = fileManager.findFiles(dataSource, "NTuser.dat"); //NON-NLS            
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractUsb_process_errormsg_find_hive());
            logger.log(Level.WARNING, "Unable to find NTuser.dat HIVE file.", ex); //NON-NLS
            return;  // No need to continue
        }

        String hiveFileName = null;

        for (AbstractFile hiveFile : hiveFiles) {

            if (hiveFile.getParentPath().startsWith("/Users/")) {
                hiveFileName = tempDirPath + File.separator + hiveFile.getId() + "_" + hiveFile.getName();

                try {
                    ContentUtils.writeToFile(hiveFile, new File(hiveFileName));
                } catch (IOException ex) {
                    this.addErrorMessage(Bundle.ExtractUsb_process_errormsg_find_hive());
                    logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", hiveFile.getName(), hiveFile), ex); //NON-NLS
                    return;
                }
            }
        }
    }

    @Messages({
        "ExtractUsb_process_errormsg_find_evtx=Unable to find evtx file",
        "ExtractUsb_process_errormsg_write_evtx=Unable to write evtx file"
    })

    /**
     * Extract the Evtx files to the temp directory
     *
     * @param dataSource  datasource where software hive is
     * @param tempDirPath temp directory to write file to
     *
     */
    void getEvtxFiles(Content dataSource, String tempDirPath) {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        List<AbstractFile> evtxFiles;

        try {
            evtxFiles = fileManager.findFiles(dataSource, "%.evtx", "/Windows/System32/winevt/logs/"); //NON-NLS            
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractUsb_process_errormsg_find_evtx());
            logger.log(Level.WARNING, "Unable to find evtx log files.", ex); //NON-NLS
            return;  // No need to continue
        }

        String evtxFileName = null;

        for (AbstractFile evtxFile : evtxFiles) {

            evtxFileName = tempDirPath + File.separator + evtxFile.getName();

            try {
                ContentUtils.writeToFile(evtxFile, new File(evtxFileName));
            } catch (IOException ex) {
                this.addErrorMessage(Bundle.ExtractUsb_process_errormsg_find_evtx());
                logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", evtxFile.getName(), evtxFile), ex); //NON-NLS
                return;
            }
        }
    }

    @Messages({
        "ExtractUsb_process_errormsg_find_lnk=Unable to find lnk file",
        "ExtractUsb_process_errormsg_write_lnk=Unable to write lnk file"
    })

    /**
     * Extract the Lnk files to the temp directory
     *
     * @param dataSource  datasource where software hive is
     * @param tempDirPath temp directory to write file to
     *
     */
    void getLnkFiles(Content dataSource, String tempDirPath) {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        List<AbstractFile> lnkFiles;

        try {
            lnkFiles = fileManager.findFiles(dataSource, "%.lnk"); //NON-NLS            
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractUsb_process_errormsg_find_lnk());
            logger.log(Level.WARNING, "Unable to find lnk files.", ex); //NON-NLS
            return;  // No need to continue
        }

        String lnkFileName = null;

        for (AbstractFile lnkFile : lnkFiles) {

            lnkFileName = tempDirPath + File.separator + lnkFile.getId() + "_" + lnkFile.getName();
            
            try {
                ContentUtils.writeToFile(lnkFile, new File(lnkFileName));
            } catch (IOException ex) {
                this.addErrorMessage(Bundle.ExtractUsb_process_errormsg_find_lnk());
                logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", lnkFile.getName(), lnkFile), ex); //NON-NLS
                return;
            }
        }
    }

   /**
     * Run the usbparser program 
     *
     * @param usbExePath
     * @param tempDirPath
     * @param tempOutPath
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    void extractUsbFiles(String usbExePath, String tempOutFile, String tempOutPath) throws IOException {
        final Path outputFilePath = Paths.get(tempOutPath, USB_OUTPUT_FILE_NAME);
        final Path errFilePath = Paths.get(tempOutPath, USB_ERROR_FILE_NAME);

        List<String> commandLine = new ArrayList<>();
        commandLine.add(usbExePath);
        commandLine.add("-dbLocation");
        commandLine.add(tempOutFile);  //NON-NLS
        commandLine.add("-fileLocations");
        commandLine.add(tempOutPath);

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.redirectOutput(outputFilePath.toFile());
        processBuilder.redirectError(errFilePath.toFile());

        ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context, true));
    }

    private String getPathForUsbDumper() {
        Path path = null;
        if (PlatformUtil.isWindowsOS()) {
            path = Paths.get(USB_TOOL_FOLDER, USB_TOOL_NAME_WINDOWS);
        } else {
            if ("Linux".equals(PlatformUtil.getOSName())) {
                path = Paths.get(USB_TOOL_FOLDER, USB_TOOL_NAME_LINUX);
            }
        }
        File usbToolFile = InstalledFileLocator.getDefault().locate(path.toString(),
                ExtractUsb.class.getPackage().getName(), false);
        if (usbToolFile != null) {
            return usbToolFile.getAbsolutePath();
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

        String sqlStatement = "SELECT STRFTIME('%s', timestamp) ExecutionTime, b.application_name, b.Application_Name formatted_application_name, username User_Name, \n" +
                              "       bytesSent, BytesRecvd \n" +
                              "  FROM network_Usage a, SruDbIdMapTable s, exe_to_app b, userNames u\n" +
                              " WHERE s.idType = 0 and s.idIndex = appId and idblob = b.source_name and u.idindex = userid \n" +
                              " order by ExecutionTime;"; //NON-NLS

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

        String sqlStatement = "SELECT STRFTIME('%s', timestamp) ExecutionTime, b.Application_Name \n" +
                              "       formatted_application_name, username User_Name \n" +
                              "  FROM Application_Resource_Usage a, SruDbIdMapTable s, exe_to_app b, userNames u \n" +
                              " WHERE s.idType = 0 and s.idIndex = appId and idblob = b.source_name and u.idindex = userid \n" +
                              " order by ExecutionTime;"; //NON-NLS

        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + sruDb); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {

            while (resultSet.next()) {

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Cancelled SRU Net Usage Artifact Creation."); //NON-NLS
                    return;
                }

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
                    BlackboardArtifact associateBbArtifact = createAssociatedArtifact(formattedApplicationName.toLowerCase(), bbart);
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
