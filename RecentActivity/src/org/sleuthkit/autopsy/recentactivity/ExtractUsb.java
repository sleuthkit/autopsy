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
import java.util.List;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
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
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extract the USB Related hive files, envent logs and lnk files to a temp directory so it can be
 * parsed into a SQLite db and then brought into extracted content
 */
final class ExtractUsb extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractUsb.class.getName());

    private static final String USB_TOOL_FOLDER = "markmckinnon"; //NON-NLS
    private static final String USB_TOOL_NAME_WINDOWS = "usbparser.exe"; //NON-NLS
    private static final String USB_TOOL_NAME_LINUX = "usbparser"; //NON-NLS
    private static final String USB_OUTPUT_FILE_NAME = "Output.txt"; //NON-NLS
    private static final String USB_ERROR_FILE_NAME = "Error.txt"; //NON-NLS
    
    private static final String USB_ARTIFACT_NAME = "USB_REMOVABLE_DEVICE"; //NON-NLS
    private static final String USB_CONN_DISCONN_ARTIFACT_NAME = "USB_CONN_DISCONN"; //NON-NLS
    private static final String USB_ATTRIBUTE_CONNECT_DISCONNECT = "USB_CONNECT_DISCONNECT"; //NON-NLS
    private static final String USB_ATTRIBUTE_SERIAL_NUMBER = "USB_SERIAL_NUMBER"; //NON-NLS
    private static final String USB_ATTRIBUTE_FIRST_CONNECT_TIME = "USB_FIRST_CONNECT_TIME"; //NON-NLS 
    private static final String USB_ATTRIBUTE_LAST_CONNECT_TIME = "USB_LAST_CONNECT_TIME"; //NON-NLS
    private static final String USB_ATTRIBUTE_DISCONNECTED_TIME = "USB_DISCONNECTED_TIME"; //NON-NLS
    private static final String USB_ATTRIBUTE_VOLUME_NAME = "USB_VOLUME_NAME_TIME"; //NON-NLS
    private static final String USB_ATTRIBUTE_DISK_SIGNATURE = "USB_DISK_SIGNATURE"; //NON-NLS
    private static final String USB_ATTRIBUTE_DRIVE_LETTER = "USB_DRIVE_LETTER"; //NON-NLS
    private static final String USB_ATTRIBUTE_VSN = "USB_VSN"; //NON-NLS
    private static final String USB_ATTRIBUTE_FILE_SYSTEM = "USB_FILE_SYSTEM"; //NON-NLS
    

    private BlackboardArtifact.Type usbArtifactType = null;
    private BlackboardArtifact.Type usbConnDisconnArtifactType = null;
    private BlackboardAttribute.Type usbConnDisconnAttributeType = null;
    private BlackboardAttribute.Type usbSerialNumberAttributeType = null;
    private BlackboardAttribute.Type usbFirstConnectTimeAttributeType = null;
    private BlackboardAttribute.Type usbLastConnectTimeAttributeType = null;
    private BlackboardAttribute.Type usbDisconnectedTimeAttributeType = null;
    private BlackboardAttribute.Type usbVolumeNameAttributeType = null;
    private BlackboardAttribute.Type usbDiskSignatureAttributeType = null;
    private BlackboardAttribute.Type usbDriveLetterAttributeType = null;
    private BlackboardAttribute.Type usbVSNAttributeType = null;
    private BlackboardAttribute.Type usbFileSystemAttributeType = null;

//    private static final Map<String, AbstractFile> applicationFilesFound = new HashMap<>();
    private final IngestJobContext context;

    @Messages({
        "ExtractUsb_module_name=USB Analyzer"
    })
    ExtractUsb(IngestJobContext context) {
        super(Bundle.ExtractUsb_module_name(), context);
        this.context = context;
    }

    @Messages({
        "SoftwareHiveFile_Not_Found=SOFTWARE hive file not found",
        "SystemHiveFile_Not_Found=SYSTEM hive file not found",
        "EventPartitionLog_Not_Found=Event Log Partition information not found",
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
        AbstractFile softwareHiveFile = getHiveFile(dataSource, tempDirPath, "software", "/config/");
        if (softwareHiveFile == null) {
            this.addErrorMessage(Bundle.SoftwareHiveFile_Not_Found());
            logger.log(Level.SEVERE, "Error finding SOFTWARE Hive file"); //NON-NLS
            return; //If we cannot find the SOFTWARE hive we cannot proceed
            
        }
        AbstractFile systemHiveFile = getHiveFile(dataSource, tempDirPath, "system", "/config/");
        if (systemHiveFile == null) {
            this.addErrorMessage(Bundle.SystemHiveFile_Not_Found());
            logger.log(Level.SEVERE, "Error finding SOFTWARE Hive file"); //NON-NLS
            return; //If we cannot find the SYSTEM hive we cannot proceed
            
        }
        getNTUserHiveFiles(dataSource, tempDirPath);
        getEvtxFiles(dataSource, tempDirPath);
        getLnkFiles(dataSource, tempDirPath);
        
        final String usbDumper = getPathForUsbDumper();
        if (usbDumper == null) {
            this.addErrorMessage(Bundle.ExtractUsb_error_finding_usbparser_program());
            logger.log(Level.SEVERE, "Error finding usbparser program"); //NON-NLS
            return; //If we cannot find the usbParser program we cannot proceed
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }

        try {
            String modOutFile = modOutPath + File.separator + "parseusb.db3";
            String usbFileLocation = tempDirPath;

            extractUsbFiles(usbDumper, modOutFile, usbFileLocation);
            

            AbstractFile evtPartitionFile = getEvtFile(dataSource, tempDirPath, "Microsoft-Windows-Partition%4Diagnostic.evtx", "/Windows/System32/winevt/logs/");
            if (evtPartitionFile == null) {
            this.addErrorMessage(Bundle.EventPartitionLog_Not_Found());
            logger.log(Level.SEVERE, "Error finding Event Log file"); //NON-NLS
            return; //If we cannot find the event log we cannot proceed
            
        }

            createUSBArtifacts(modOutFile, systemHiveFile);
            createConnectDisconnectArtifacts(modOutFile, evtPartitionFile);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error processing SRUDB.dat file", ex); //NON-NLS=
            this.addErrorMessage(Bundle.ExtractSru_process_error_executing_export_srudb_program());
        }
    }

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
    AbstractFile getHiveFile(Content dataSource, String tempDirPath, String hiveName, String parentPath) {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        List<AbstractFile> hiveFiles;

        try {
            hiveFiles = fileManager.findFiles(dataSource, hiveName); //NON-NLS            
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractUsb_process_errormsg_find_hive());
            logger.log(Level.WARNING, String.format("Unable to find %s HIVE file.", hiveName), ex); //NON-NLS
            return null;  // No need to continue
        }
        AbstractFile hiveFileAbs = null;
        String hiveFileName = null;

        for (AbstractFile hiveFile : hiveFiles) {

            if (hiveFile.getParentPath().endsWith(parentPath)) {
                hiveFileName = tempDirPath + File.separator + hiveFile.getName();

                try {
                    ContentUtils.writeToFile(hiveFile, new File(hiveFileName));
                    hiveFileAbs = hiveFile;
                } catch (IOException ex) {
                    this.addErrorMessage(Bundle.ExtractUsb_process_errormsg_find_hive());
                    logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", hiveFile.getName(), hiveFile), ex); //NON-NLS
                    return null;
                }
            }
        }
        return hiveFileAbs;
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
                Path path = Paths.get(hiveFile.getParentPath());
                String username = path.getFileName().toString();
                hiveFileName = tempDirPath + File.separator + username + "_" + hiveFile.getName();
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

    /**
     * Get the AbstractFile of the file
     *
     * @param dataSource  datasource where software hive is
     * @param tempDirPath temp directory to write file to
     * @param evtFileName name of the hive file to extract
     *
     * @return abstractFile
     *
     */
    AbstractFile getEvtFile(Content dataSource, String tempDirPath, String evtFileName, String parentPath) {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        List<AbstractFile> evtxFiles;

        try {
            evtxFiles = fileManager.findFiles(dataSource, evtFileName, parentPath); //NON-NLS            
        } catch (TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractUsb_process_errormsg_find_evtx());
            logger.log(Level.WARNING, "Unable to find evtx log files.", ex); //NON-NLS
            return null;  // No need to continue
        }

        AbstractFile evtAbsFile = null;
        String evtxFileName = null;

        for (AbstractFile evtxFile : evtxFiles) {
            evtAbsFile = evtxFile;
        }
        
        return evtAbsFile;
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
        commandLine.add("-d"); //NON-NLS
        commandLine.add(tempOutFile);
        commandLine.add("-f"); //NON-NLS
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

    private void createUSBArtifacts(String usbDb, AbstractFile systemAbstractFile) {
        List<BlackboardArtifact> bba = new ArrayList<>();

        String sqlStatement = "SELECT serial_number, Description, first_connect_time, last_connect_time, " +
                              "last_disconnected_time, volume_name_label, drive_letter, vsn, " +
                              "disk_signature, user_name, file_system from usb_data;"; //NON-NLS

        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + usbDb); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {

            while (resultSet.next()) {

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Cancelled USB Artifact Creation."); //NON-NLS
                    return;
                }

                String serialNumber = resultSet.getString("serial_number"); //NON-NLS
                String description = resultSet.getString("Description");
                Long firstConnectTime = resultSet.getLong("first_connect_time"); //NON-NLS
                Long lastConnectTime = resultSet.getLong("last_connect_time"); //NON-NLS
                Long lastDisconnectedTime = resultSet.getLong("last_disconnected_time"); //NON-NLS
                String volumeLabelName = resultSet.getString("volume_name_label"); //NON-NLS
                String driveLetter = resultSet.getString("drive_letter"); //NON-NLS
                String vsn = resultSet.getString("vsn"); //NON-NLS
                String diskSignature = resultSet.getString("disk_signature"); //NON-NLS
                String userName = resultSet.getString("user_name"); //NON-NLS
                String fileSystem = resultSet.getString("file_system"); //NON-NLS

                try {
                    Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                            new BlackboardAttribute(
                                    getSerialNumberAttribute(), getDisplayName(),
                                    serialNumber),//NON-NLS
                            new BlackboardAttribute(
                                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION, getDisplayName(),
                                    description),
                            new BlackboardAttribute(
                                    getFirstConnectTimeAttribute(), getDisplayName(),
                                    firstConnectTime),
                            new BlackboardAttribute(
                                    getLastConnectTimeAttribute(), getDisplayName(), lastConnectTime),
                            new BlackboardAttribute(
                                    getDisconnectedTimeAttribute(), getDisplayName(), lastDisconnectedTime),
                            new BlackboardAttribute(
                                    getVolumeNameAttribute(), getDisplayName(), volumeLabelName),
                            new BlackboardAttribute(
                                    getDriveLetterAttribute(), getDisplayName(), driveLetter),
                            new BlackboardAttribute(
                                    getVSNAttribute(), getDisplayName(), vsn),
                            new BlackboardAttribute(
                                    getDiskSignatureAttribute(), getDisplayName(), diskSignature),
                            new BlackboardAttribute(
                                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME, getDisplayName(), userName),
                            new BlackboardAttribute(
                                    getFileSystemAttribute(), getDisplayName(), fileSystem));
               
                    BlackboardArtifact bbart = createArtifactWithAttributes(getUSBArtifact(), systemAbstractFile, bbattributes);
                    bba.add(bbart);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Exception Adding Artifact/Attribute.", ex);//NON-NLS
                }
            }

        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Error while trying to read into a sqlite db.", ex);//NON-NLS
        }

        if (!context.dataSourceIngestIsCancelled()) {
            postArtifacts(bba);
        }
    }

    private void createConnectDisconnectArtifacts(String usbDb, AbstractFile sruAbstractFile) {
        List<BlackboardArtifact> bba = new ArrayList<>();

        String sqlStatement = "SELECT SerialNum, TimeCreated_SystemTime, ConnectType, disksignature, vsn " +
                              "  FROM connect_disconnect ORDER BY TimeCreated_SystemTime"; //NON-NLS

        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + usbDb); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {

            while (resultSet.next()) {

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Cancelled SRU Net Usage Artifact Creation."); //NON-NLS
                    return;
                }

                String serialNumber = resultSet.getString("SerialNum");
                Long timeCreated = resultSet.getLong("TimeCreated_SystemTime"); //NON-NLS
                String connectType = resultSet.getString("connectType");
                String vsn = resultSet.getString("vsn"); //NON-NLS
                String diskSignature = resultSet.getString("disksignature"); //NON-NLS

                try {
                    Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                                new BlackboardAttribute(
                                        getSerialNumberAttribute(), getDisplayName(),
                                        serialNumber),//NON-NLS
                                new BlackboardAttribute(
                                    getConnDisconnAttribute(), getDisplayName(),
                                    connectType),
                                new BlackboardAttribute(
                                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, getDisplayName(), 
                                    timeCreated),
                                new BlackboardAttribute(
                                        getVSNAttribute(), getDisplayName(), vsn),
                                new BlackboardAttribute(
                                        getDiskSignatureAttribute(), getDisplayName(), diskSignature));

                    BlackboardArtifact bbart = createArtifactWithAttributes(getUSBConnectDisconnectArtifact(), sruAbstractFile, bbattributes);
                    bba.add(bbart);
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

    @NbBundle.Messages({
      "Usb_Artifact_Name=USB Removable Device",
      "Usb_serialNumber=Serial Number",
      "Usb_firstConnectTime=First Connect Time",
      "Usb_lastConnectTime=Last Connect Time",
      "Usb_disconnectedTime=Disconnected Time",
      "Usb_volumeName=Volume Label",
      "Usb_driveLetter=Drive Letter",
      "Usb_vsn=Volume Serial Number",
      "Usb_diskSignature=Disk Signature",
      "Usb_fileSystem=File System",  
      "Usb_Artifact_Connect_Disconnect=USB Connects/Disconnects",
      "Usb_connect_disconnect=Connection Type"
    })
    
     /**
     * Returns the custom USB artifact type or creates it if it does not
     * currently exist.
     *
     * @return BlackboardArtifact.Type for USB artifacts
     *
     * @throws TskCoreException
     */
    private BlackboardArtifact.Type getUSBArtifact() throws TskCoreException {
        if (usbArtifactType == null) {
            try {
                usbArtifactType = tskCase.getBlackboard().getOrAddArtifactType(USB_ARTIFACT_NAME, Bundle.Usb_Artifact_Name());
            } catch (Blackboard.BlackboardException ex) {
                throw new TskCoreException(String.format("Failed to get USB artifact type", USB_CONN_DISCONN_ARTIFACT_NAME), ex);
            }
        }

        return usbArtifactType;
    }

     /**
     * Returns the custom USB artifact type or creates it if it does not
     * currently exist.
     *
     * @return BlackboardArtifact.Type for Connect/Disconnect artifact
     *
     * @throws TskCoreException
     */
    private BlackboardArtifact.Type getUSBConnectDisconnectArtifact() throws TskCoreException {
        if (usbConnDisconnArtifactType == null) {
            try {
                usbConnDisconnArtifactType = tskCase.getBlackboard().getOrAddArtifactType(USB_CONN_DISCONN_ARTIFACT_NAME, Bundle.Usb_Artifact_Connect_Disconnect());
            } catch (Blackboard.BlackboardException ex) {
                throw new TskCoreException(String.format("Failed to get connect/disconnect artifact type", USB_ARTIFACT_NAME), ex);
            }
        }

        return usbConnDisconnArtifactType;
    }

   /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getConnDisconnAttribute() throws TskCoreException {
        if (usbConnDisconnAttributeType == null) {
            try {
                usbConnDisconnAttributeType = tskCase.getBlackboard().getOrAddAttributeType(USB_ATTRIBUTE_CONNECT_DISCONNECT,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                        Bundle.Usb_connect_disconnect());
            } catch (Blackboard.BlackboardException ex) {
                // Attribute already exists get it from the case
                throw new TskCoreException(String.format("Failed to get custom attribute %s", USB_ATTRIBUTE_SERIAL_NUMBER), ex);
            }
        }
        return usbConnDisconnAttributeType;
    }

   /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getSerialNumberAttribute() throws TskCoreException {
        if (usbSerialNumberAttributeType == null) {
            try {
                usbSerialNumberAttributeType = tskCase.getBlackboard().getOrAddAttributeType(USB_ATTRIBUTE_SERIAL_NUMBER,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                        Bundle.Usb_serialNumber());
            } catch (Blackboard.BlackboardException ex) {
                // Attribute already exists get it from the case
                throw new TskCoreException(String.format("Failed to get custom attribute %s", USB_ATTRIBUTE_SERIAL_NUMBER), ex);
            }
        }
        return usbSerialNumberAttributeType;
    }

    /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getFirstConnectTimeAttribute() throws TskCoreException {
        if (usbFirstConnectTimeAttributeType == null) {
            try {
                usbFirstConnectTimeAttributeType = tskCase.getBlackboard().getOrAddAttributeType(USB_ATTRIBUTE_FIRST_CONNECT_TIME,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME,
                        Bundle.Usb_firstConnectTime());
            } catch (Blackboard.BlackboardException ex) {
                // Attribute already exists get it from the case
                throw new TskCoreException(String.format("Failed to get custom attribute %s", USB_ATTRIBUTE_FIRST_CONNECT_TIME), ex);
            }
        }
        return usbFirstConnectTimeAttributeType;
    }

    /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getLastConnectTimeAttribute() throws TskCoreException {
        if (usbLastConnectTimeAttributeType == null) {
            try {
                usbLastConnectTimeAttributeType = tskCase.getBlackboard().getOrAddAttributeType(USB_ATTRIBUTE_LAST_CONNECT_TIME,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME,
                        Bundle.Usb_lastConnectTime());
            } catch (Blackboard.BlackboardException ex) {
                // Attribute already exists get it from the case
                throw new TskCoreException(String.format("Failed to get custom attribute %s", USB_ATTRIBUTE_LAST_CONNECT_TIME), ex);
            }
        }
        return usbLastConnectTimeAttributeType;
    }

    /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getDisconnectedTimeAttribute() throws TskCoreException {
        if (usbDisconnectedTimeAttributeType == null) {
            try {
                usbDisconnectedTimeAttributeType = tskCase.getBlackboard().getOrAddAttributeType(USB_ATTRIBUTE_DISCONNECTED_TIME,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME,
                        Bundle.Usb_disconnectedTime());
            } catch (Blackboard.BlackboardException ex) {
                // Attribute already exists get it from the case
                throw new TskCoreException(String.format("Failed to get custom attribute %s", USB_ATTRIBUTE_DISCONNECTED_TIME), ex);
            }
        }
        return usbDisconnectedTimeAttributeType;
    }

    /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getVolumeNameAttribute() throws TskCoreException {
        if (usbVolumeNameAttributeType == null) {
            try {
                usbVolumeNameAttributeType = tskCase.getBlackboard().getOrAddAttributeType(USB_ATTRIBUTE_VOLUME_NAME,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                        Bundle.Usb_volumeName());
            } catch (Blackboard.BlackboardException ex) {
                // Attribute already exists get it from the case
                throw new TskCoreException(String.format("Failed to get custom attribute %s", USB_ATTRIBUTE_VOLUME_NAME), ex);
            }
        }
        return usbVolumeNameAttributeType;
    }

    /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getDiskSignatureAttribute() throws TskCoreException {
        if (usbDiskSignatureAttributeType == null) {
            try {
                usbDiskSignatureAttributeType = tskCase.getBlackboard().getOrAddAttributeType(USB_ATTRIBUTE_DISK_SIGNATURE,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                        Bundle.Usb_diskSignature());
            } catch (Blackboard.BlackboardException ex) {
                // Attribute already exists get it from the case
                throw new TskCoreException(String.format("Failed to get custom attribute %s", USB_ATTRIBUTE_DISK_SIGNATURE), ex);
            }
        }
        return usbDiskSignatureAttributeType;
    }

    /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getDriveLetterAttribute() throws TskCoreException {
        if (usbDriveLetterAttributeType == null) {
            try {
                usbDriveLetterAttributeType = tskCase.getBlackboard().getOrAddAttributeType(USB_ATTRIBUTE_DRIVE_LETTER,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                        Bundle.Usb_driveLetter());
            } catch (Blackboard.BlackboardException ex) {
                // Attribute already exists get it from the case
                throw new TskCoreException(String.format("Failed to get custom attribute %s", USB_ATTRIBUTE_DRIVE_LETTER), ex);
            }
        }
        return usbDriveLetterAttributeType;
    }

    /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getVSNAttribute() throws TskCoreException {
        if (usbVSNAttributeType == null) {
            try {
                usbVSNAttributeType = tskCase.getBlackboard().getOrAddAttributeType(USB_ATTRIBUTE_VSN,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                        Bundle.Usb_vsn());
            } catch (Blackboard.BlackboardException ex) {
                // Attribute already exists get it from the case
                throw new TskCoreException(String.format("Failed to get custom attribute %s", USB_ATTRIBUTE_VSN), ex);
            }
        }
        return usbVSNAttributeType;
    }

    /**
     * Gets the custom BlackboardAttribute type. The attribute type is created
     * if it does not currently exist.
     *
     * @return The BlackboardAttribute type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute.Type getFileSystemAttribute() throws TskCoreException {
        if (usbFileSystemAttributeType == null) {
            try {
                usbFileSystemAttributeType = tskCase.getBlackboard().getOrAddAttributeType(USB_ATTRIBUTE_FILE_SYSTEM,
                        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING,
                        Bundle.Usb_fileSystem());
            } catch (Blackboard.BlackboardException ex) {
                // Attribute already exists get it from the case
                throw new TskCoreException(String.format("Failed to get custom attribute %s", USB_ATTRIBUTE_FILE_SYSTEM), ex);
            }
        }
        return usbFileSystemAttributeType;
    }
    
}
