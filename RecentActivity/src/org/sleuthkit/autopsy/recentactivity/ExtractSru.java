 /*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;


/**
 * Extract the System Resource Usage database to a temp directory so it can be parsed into a SQLite db
 * and then brought into extracted content
 */
final class ExtractSru extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractSru.class.getName());

    private IngestJobContext context;

    private static final String NETWORK_USAGE_ARTIFACT_NAME = "RA_SRU_NETWORK_USAGE"; //NON-NLS
    private static final String APPLICATION_RESOURCE_ARTIFACT_NAME = "RA_SRU_APPLICATION_RESOURCE"; //NON-NLS
    private static final String NETWORK_PROFILE_NAME_ATTRIBUTE_NAME = "RA_SRU_NETWORK_PROFILE_NAME"; //NON-NLS

    private static final String ARTIFACT_ATTRIBUTE_NAME = "TSK_ARTIFACT_NAME"; //NON-NLS
    private static final String BACKGROUND_CYCLE_TIME_ART_NAME = "RA_BACKGROUND_CYCLE_TIME"; //NON-NLS
    private static final String FOREGROUND_CYCLE_TIME_ART_NAME = "RA_FOREGROUND_CYCLE_TIME"; //NON-NLS
    private static final String BYTES_SENT_ART_NAME = "RA_BYTES_SENT"; //NON-NLS
    private static final String BYTES_RECEIVED_ART_NAME = "RA_BYTES_RECEIVED"; //NON-NLS

    private static final String MODULE_NAME = "extractSRU"; //NON-NLS

    private static final String SRU_TOOL_FOLDER = "markmckinnon"; //NON-NLS
    private static final String SRU_TOOL_NAME_WINDOWS_32 = "export_srudb_32.exe"; //NON-NLS
    private static final String SRU_TOOL_NAME_WINDOWS_64 = "export_srudb_64.exe"; //NON-NLS
    private static final String SRU_OUTPUT_FILE_NAME = "Output.txt"; //NON-NLS
    private static final String SRU_ERROR_FILE_NAME = "Error.txt"; //NON-NLS
    
    private static final HashMap<String, AbstractFile> applicationFilesFound = new HashMap<>();

    @Messages({
        "ExtractSru_module_name=System Resource Usage Extractor"
    })
    ExtractSru() {
        this.moduleName = Bundle.ExtractSru_module_name();
    }

    @Override
    void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) {

        this.context = context;

        try {
            createSruArtifactType();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("%s or $s may not have been created.", NETWORK_USAGE_ARTIFACT_NAME, APPLICATION_RESOURCE_ARTIFACT_NAME), ex);
        }

        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
        String tempDirPath = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), "sru"); //NON-NLS
        String tempOutPath = Case.getCurrentCase().getModuleDirectory() + File.separator + "sru"; //NON-NLS

        File dir = new File(tempOutPath);
        if (dir.exists() == false) {
            dir.mkdirs();
        }

        SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();

        List<AbstractFile> softwareHiveFiles;

        try {
            softwareHiveFiles = fileManager.findFiles(dataSource, "SOFTWARE"); //NON-NLS            
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to find SOFTWARE HIVE file.", ex); //NON-NLS
            return;  // No need to continue
        }

        String softwareHiveFileName = null;
        AbstractFile softwareHiveAbstractFile = null;

        for (AbstractFile softwareFile : softwareHiveFiles) {

            if (context.dataSourceIngestIsCancelled()) {
                return;
            }
  
            if (softwareFile.getParentPath().endsWith("/config/")) {
                softwareHiveFileName = tempDirPath + File.separator + softwareFile.getId() + "_" + softwareFile.getName();
                softwareHiveAbstractFile = softwareFile;

                try {
                    ContentUtils.writeToFile(softwareFile, new File(softwareHiveFileName));
                } catch (IOException ex) {
                    logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", softwareFile.getName(), softwareFile), ex); //NON-NLS
                }
            }
        }

        List<AbstractFile> sruFiles;

        try {
            sruFiles = fileManager.findFiles(dataSource, "SRUDB.DAT"); //NON-NLS            
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to find SRUDB.DAT file.", ex); //NON-NLS
            return;  // No need to continue
        }

        String sruFileName = null;
        String tempOutFile = null;
        AbstractFile sruAbstractFile = null;

        for (AbstractFile sruFile : sruFiles) {

            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            sruFileName = tempDirPath + File.separator + sruFile.getId() + "_" + sruFile.getName();
            tempOutFile = tempDirPath + File.separator + sruFile.getId() + "_srudb.db3";
            sruAbstractFile = sruFile;

            try {
                ContentUtils.writeToFile(sruFile, new File(sruFileName));
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", sruFile.getName(), sruFile), ex); //NON-NLS
            }

        }

        final String sruDumper = getPathForSruDumper();
        if (sruDumper == null) {
            logger.log(Level.SEVERE, "Error finding export_srudb program"); //NON-NLS
            return; //If we cannot find the ESEDatabaseView we cannot proceed
        }

        if (context.dataSourceIngestIsCancelled()) {
            return;
        }
        if (sruFileName == null) {
            logger.log(Level.SEVERE, "SRUDB.dat file not found"); //NON-NLS
            return; //If we cannot find the ESEDatabaseView we cannot proceed
        }

        try {
            extractSruFiles(sruDumper, sruFileName, tempOutFile, tempDirPath, softwareHiveFileName);
            createSruAttributeType();
            createSruArtifactType();
            findSruExecutedFiles(tempOutFile, dataSource);
            createNetUsageArtifacts(tempOutFile, sruAbstractFile);
            createAppUsageArtifacts(tempOutFile, sruAbstractFile);
        } finally {
            return;
        }
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
    void extractSruFiles(String sruExePath, String sruFile, String tempOutFile, String tempOutPath, String softwareHiveFile) throws FileNotFoundException, IOException {
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

        ExecUtil.execute(processBuilder, new DataSourceIngestModuleProcessTerminator(context));
    }

    private String getPathForSruDumper() {
        Path path = null;
        if (PlatformUtil.is64BitOS()) {
            path = Paths.get(SRU_TOOL_FOLDER, SRU_TOOL_NAME_WINDOWS_64);
        } else {
            path = Paths.get(SRU_TOOL_FOLDER, SRU_TOOL_NAME_WINDOWS_32);            
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
                fileName.trim();
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
            logger.log(Level.SEVERE, "Error while trying to read into a sqlite db.", ex);//NON-NLS
        }

        logger.log(Level.WARNING, "Error finding actual file %s. file may not exist"); //NON-NLS

    }
 
    private void createNetUsageArtifacts(String sruDb, AbstractFile sruAbstractFile) {
        Blackboard blackboard = currentCase.getSleuthkitCase().getBlackboard();
        BlackboardAttribute.Type bytesSentAttributeType;
        BlackboardAttribute.Type bytesRecvAttributeType;
        BlackboardAttribute.Type networkProfileName;
        BlackboardArtifact.Type artifactType;
        List<BlackboardArtifact> bba = new ArrayList<>();

        try {
            bytesSentAttributeType = currentCase.getSleuthkitCase().getAttributeType(BYTES_SENT_ART_NAME);
            bytesRecvAttributeType = currentCase.getSleuthkitCase().getAttributeType(BYTES_RECEIVED_ART_NAME);
            artifactType = currentCase.getSleuthkitCase().getArtifactType(NETWORK_USAGE_ARTIFACT_NAME);
            networkProfileName = currentCase.getSleuthkitCase().getAttributeType(NETWORK_PROFILE_NAME_ATTRIBUTE_NAME);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting Net Usage Attribute's and Artifact.", ex);//NON-NLS
            return;
        }

        String sqlStatement = "SELECT STRFTIME('%s', timestamp) ExecutionTime, Application_Name, User_Name, Profile_Name,"
                + " bytesSent, BytesRecvd FROM network_Usage , SruDbIdMapTable " 
                + " where appId = IdIndex and IdType = 0 order by ExecutionTime;"; //NON-NLS

        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + sruDb); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {

            while (resultSet.next()) {

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Cancelled SRU Net Usage Artifact Creation."); //NON-NLS
                    return;
                }

                String applicationName = resultSet.getString("Application_Name"); //NON-NLS
                Long executionTime = new Long(resultSet.getInt("ExecutionTime")); //NON-NLS
                Long bytesSent = new Long(resultSet.getInt("bytesSent")); //NON-NLS
                Long bytesRecvd = new Long(resultSet.getInt("BytesRecvd")); //NON-NLS
                String userName = resultSet.getString("User_Name"); //NON-NLS
                String profileName = resultSet.getString("Profile_Name"); //NON-NLS

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, getName(),
                                applicationName),//NON-NLS
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME, getName(),
                                userName), 
                        new BlackboardAttribute(
                                networkProfileName, getName(), profileName),
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, getName(),
                                executionTime),
                        new BlackboardAttribute(
                                bytesSentAttributeType, getName(), bytesSent),
                        new BlackboardAttribute(
                                bytesRecvAttributeType, getName(), bytesRecvd));

                try {
                    BlackboardArtifact bbart = sruAbstractFile.newArtifact(artifactType.getTypeID());
                    bbart.addAttributes(bbattributes);
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

        try {
            blackboard.postArtifacts(bba, MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error Posting Artifact.", ex);//NON-NLS
        }
    }

    private void createAppUsageArtifacts(String sruDb, AbstractFile sruAbstractFile) {
        Blackboard blackboard = currentCase.getSleuthkitCase().getBlackboard();
        BlackboardAttribute.Type fgCycleTimeAttributeType;
        BlackboardAttribute.Type bgCycleTimeAttributeType;
        BlackboardArtifact.Type artifactType;
        List<BlackboardArtifact> bba = new ArrayList<>();

        try {
            fgCycleTimeAttributeType = currentCase.getSleuthkitCase().getAttributeType(FOREGROUND_CYCLE_TIME_ART_NAME);
            bgCycleTimeAttributeType = currentCase.getSleuthkitCase().getAttributeType(BACKGROUND_CYCLE_TIME_ART_NAME);
            artifactType = currentCase.getSleuthkitCase().getArtifactType(APPLICATION_RESOURCE_ARTIFACT_NAME);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting APP Usage Attribute's and Artifact.", ex);//NON-NLS
            return;
        }

        String sqlStatement = "SELECT STRFTIME('%s', timestamp) ExecutionTime, Application_Name, User_Name,"
                + " foregroundCycleTime, backgroundCycleTime FROM Application_Resource_Usage, SruDbIdMapTable WHERE "
                + " idType = 0 and idIndex = appId order by ExecutionTime;"; //NON-NLS

        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + sruDb); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {

            while (resultSet.next()) {

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Cancelled SRU Net Usage Artifact Creation."); //NON-NLS
                    return;
                }

                String applicationName = resultSet.getString("Application_Name"); //NON-NLS
                Long executionTime = new Long(resultSet.getInt("ExecutionTime")); //NON-NLS
                Long fgCycleTime = new Long(resultSet.getInt("foregroundCycleTime")); //NON-NLS
                Long bgCycleTime = new Long(resultSet.getInt("backgroundCycleTime")); //NON-NLS
                String userName = resultSet.getString("User_Name");

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME, getName(),
                                applicationName),//NON-NLS
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME, getName(),
                                userName),
                        new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME, getName(),
                                executionTime),
                        new BlackboardAttribute(
                                fgCycleTimeAttributeType, getName(),
                                fgCycleTime),
                        new BlackboardAttribute(
                                bgCycleTimeAttributeType, getName(),
                                bgCycleTime));

                try {
                    BlackboardArtifact bbart = sruAbstractFile.newArtifact(artifactType.getTypeID());
                    bbart.addAttributes(bbattributes);
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
        
        try {
            blackboard.postArtifacts(bba, MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error Posting Artifact.", ex);//NON-NLS
        }
    }
    
    /**
     * Create associated artifacts using file path name and the artifact it associates with
     * 
     * @param filePathName file and path of object being associated with
     * 
     * @param bba blackboard artifact to associate with
     * 
     * @returnv BlackboardArtifact or a null value 
     */  
    private BlackboardArtifact createAssociatedArtifact(String filePathName, BlackboardArtifact bba) {
        if (applicationFilesFound.containsKey(filePathName)) {
            AbstractFile sourceFile = applicationFilesFound.get(filePathName);
            Collection<BlackboardAttribute> bbattributes2 = new ArrayList<>();
            bbattributes2.addAll(Arrays.asList(
                 new BlackboardAttribute(TSK_ASSOCIATED_ARTIFACT, this.getName(),
                 bba.getArtifactID())));

            BlackboardArtifact associatedObjectBba = createArtifactWithAttributes(TSK_ASSOCIATED_OBJECT, sourceFile, bbattributes2);
            if (associatedObjectBba != null) {
                return associatedObjectBba;
            }
        }
       
        return null;
    }

    /**
     * Create artifact type's for System Resource Usage.
     *
     * @throws TskCoreException
     */
    private void createSruArtifactType() throws TskCoreException {

        try {
            tskCase.addBlackboardArtifactType(NETWORK_USAGE_ARTIFACT_NAME, "SRU Network Usage"); //NON-NLS
        } catch (TskDataException ex) {
            logger.log(Level.INFO, String.format("%s may have already been defined for this case", NETWORK_USAGE_ARTIFACT_NAME));
        }
        try {
            tskCase.addBlackboardArtifactType(APPLICATION_RESOURCE_ARTIFACT_NAME, "SRU Application Resource Usage"); //NON-NLS
        } catch (TskDataException ex) {
            logger.log(Level.INFO, String.format("%s may have already been defined for this case", APPLICATION_RESOURCE_ARTIFACT_NAME));
        }

    }

    /**
     * Create System Resource Usage Attribute type's.
     *
     * @throws TskCoreException
     */
    private void createSruAttributeType() throws TskCoreException {

        try {
            tskCase.addArtifactAttributeType(ARTIFACT_ATTRIBUTE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING, "Artifact Name"); //NON-NLS
        } catch (TskDataException ex) {
            logger.log(Level.INFO, String.format("%s may have already been defined for this case", ARTIFACT_ATTRIBUTE_NAME));
        }
        try {
            tskCase.addArtifactAttributeType(BACKGROUND_CYCLE_TIME_ART_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG, "Background Cycle Time"); //NON-NLS
        } catch (TskDataException ex) {
            logger.log(Level.INFO, String.format("%s may have already been defined for this case", BACKGROUND_CYCLE_TIME_ART_NAME));
        }
        try {
            tskCase.addArtifactAttributeType(FOREGROUND_CYCLE_TIME_ART_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG, "Foreground Cycle Time"); //NON-NLS
        } catch (TskDataException ex) {
            logger.log(Level.INFO, String.format("%s may have already been defined for this case", FOREGROUND_CYCLE_TIME_ART_NAME));
        }
        try {
            tskCase.addArtifactAttributeType(BYTES_SENT_ART_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG, "Bytes Sent"); //NON-NLS
        } catch (TskDataException ex) {
            logger.log(Level.INFO, String.format("%s may have already been defined for this case", BYTES_SENT_ART_NAME));
        }
        try {
            tskCase.addArtifactAttributeType(BYTES_RECEIVED_ART_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG, "Bytes Received"); //NON-NLS
        } catch (TskDataException ex) {
            logger.log(Level.INFO, String.format("%s may have already been defined for this case", BYTES_RECEIVED_ART_NAME));
        }

        try {
            tskCase.addArtifactAttributeType(NETWORK_PROFILE_NAME_ATTRIBUTE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING, "Network Profile Name");
        } catch (TskDataException ex) {
            logger.log(Level.INFO, String.format("%s may have already been defined for this case", BYTES_RECEIVED_ART_NAME));
        }

    }

}
