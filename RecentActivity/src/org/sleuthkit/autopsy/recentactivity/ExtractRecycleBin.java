/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 *
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_ACCOUNT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_DELETED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_ID;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * This module is based on the RecycleBin python module from Mark McKinnon.
 *
 * @see
 * <a href="https://github.com/markmckinnon/Autopsy-Plugins/blob/master/Recycle_Bin/Recycle_Bin.py">Recycle_Bin.py</a>
 *
 */
final class ExtractRecycleBin extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractRecycleBin.class.getName());

    private static final String RECYCLE_BIN_ARTIFACT_NAME = "TSK_RECYCLE_BIN"; //NON-NLS

    private static final int V1_FILE_NAME_OFFSET = 24;
    private static final int V2_FILE_NAME_OFFSET = 28;

    @Messages({
        "ExtractRecycleBin_module_name=Recycle Bin"
    })
    ExtractRecycleBin() {
        this.moduleName = Bundle.ExtractRecycleBin_module_name();
    }

    @Override
    void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) {
        // At this time it was decided that we would not include TSK_RECYCLE_BIN
        // in the default list of BlackboardArtifact types.
        try {
           createRecycleBinArtifactType();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("%s may not have been created.", RECYCLE_BIN_ARTIFACT_NAME), ex);
        }

        BlackboardArtifact.Type recycleBinArtifactType;

        try {
            recycleBinArtifactType = tskCase.getArtifactType(RECYCLE_BIN_ARTIFACT_NAME);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Unable to retrive custom artifact type %s", RECYCLE_BIN_ARTIFACT_NAME), ex); // NON-NLS
            // If this doesn't work bail.
            return;
        }
        Map<String, String> userNameMap;
        
        try {
            userNameMap = makeUserNameMap(dataSource);
        } catch (TskCoreException ex) {
           logger.log(Level.WARNING, "Unable to create OS Account user name map", ex);
           // This is not the end of the world we will just continue without 
           // user names
           userNameMap = new HashMap<>();
        }
        
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
        List<AbstractFile> iFiles;

        try {
            iFiles = fileManager.findFiles(dataSource, "$I%"); //NON-NLS            
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to find recycle bin I files.", ex); //NON-NLS
            return;  // No need to continue
        }
        
        String tempRARecycleBinPath = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), "recyclebin"); //NON-NLS

        for (AbstractFile iFile : iFiles) {

            if (context.dataSourceIngestIsCancelled()) {
                return;
            }
            
            processIFiles(dataSource, context, recycleBinArtifactType, iFile, userNameMap, tempRARecycleBinPath);
        }

        (new File(tempRARecycleBinPath)).delete();
    }
    
    /**
     * Process each individual iFile.
     * 
     * @param dataSource
     * @param context
     * @param recycleBinArtifactType Module created artifact type
     * @param iFile The AbstractFile to process
     * @param userNameMap Map of user ids to names
     * @param tempRARecycleBinPath Temp directory path
     */
    private void processIFiles(Content dataSource,  IngestJobContext context,  BlackboardArtifact.Type recycleBinArtifactType, AbstractFile iFile,  Map<String, String> userNameMap, String tempRARecycleBinPath) {
        String tempFilePath = tempRARecycleBinPath + File.separator + iFile.getName();
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
        try {
            try {
                ContentUtils.writeToFile(iFile, new File(tempFilePath));
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", iFile.getName(), tempFilePath), ex); //NON-NLS
                // if we cannot make a copy of the $I file for later processing
                // move onto the next file
                return;
            }

            RecycledFileMetaData metaData;
            try {
                metaData = parseIFile(tempFilePath);
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Unable to parse iFile %s", iFile.getName()), ex); //NON-NLS
                // Unable to parse the $I file move onto the next file
                return;
            }

            String userID = getUserIDFromPath(iFile.getParentPath());
            String userName = "";
            if (!userID.isEmpty()) {
                userName = userNameMap.get(userID);
            } else {
                // If the iFile doesn't have a user ID in its parent 
                // directory structure then it is not from the recyle bin
                return;
            }

            List<AbstractFile> rFiles;
            
            String rFileName = iFile.getName().replace("$I", "$R"); //NON-NLS

            try {
                rFiles = fileManager.findFiles(dataSource, rFileName, iFile.getParentPath());
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Unable to find R file (%s) for I file (%s)", rFileName, iFile.getName()), ex); //NON-NLS
                // If there are no $R files go on to the next $I file
                return;
            }

            for (AbstractFile rFile : rFiles) {
                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }

                if (iFile.getParentPath().equals(rFile.getParentPath())) {
                    try {
                        postArtifact(createArtifact(rFile, recycleBinArtifactType, metaData.getFileName(), userName, metaData.getDeletedTimeStamp()));
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, String.format("Unable to add attributes to artifact %s", rFile.getName()), ex); //NON-NLS
                    }
                }
            }
        } finally {
            (new File(tempFilePath)).delete();
        }
    }

    /**
     * Parse the $I file.
     *
     * File format prior to Windows 10:
     * <table>
     * <tr><th>Offset</th><th>Size</th><th>Description</th></tr>
     * <tr><td>0</td><td>8</td><td>Header</td></tr>
     * <tr><td>8</td><td>8</td><td>File Size</td></tr>
     * <tr><td>16</td><td>8</td><td>Deleted Timestamp</td></tr>
     * <tr><td>24</td><td>520</td><td>File Name</td></tr>
     * </table>
     *
     * File format Windows 10+
     * <table>
     * <tr><th>Offset</th><th>Size</th><th>Description</th></tr>
     * <tr><td>0</td><td>8</td><td>Header</td></tr>
     * <tr><td>8</td><td>8</td><td>File Size</td></tr>
     * <tr><td>16</td><td>8</td><td>Deleted TimeStamp</td></tr>
     * <tr><td>24</td><td>4</td><td>File Name Length</td></tr>
     * <tr><td>28</td><td>var</td><td>File Name</td></tr>
     * </table>
     *
     * For versions of Windows prior to 10, header = 0x01. Windows 10+ header ==
     * 0x02
     *
     * @param iFilePath
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    private RecycledFileMetaData parseIFile(String iFilePath) throws FileNotFoundException, IOException {
        byte[] allBytes = Files.readAllBytes(Paths.get(iFilePath));

        ByteBuffer byteBuffer = ByteBuffer.wrap(allBytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        long version = byteBuffer.getLong();
        long fileSize = byteBuffer.getLong();
        long timestamp = byteBuffer.getLong();
  
        // Convert from windows FILETIME to Unix Epoch seconds
        timestamp = Util.filetimeToMillis(timestamp) / 1000;

        byte[] stringBytes;

        if (version == 1) {
            stringBytes = Arrays.copyOfRange(allBytes, V1_FILE_NAME_OFFSET, allBytes.length);
        } else {
            int fileNameLength = byteBuffer.getInt() * 2; //Twice the bytes for unicode
            stringBytes = Arrays.copyOfRange(allBytes, V2_FILE_NAME_OFFSET, V2_FILE_NAME_OFFSET + fileNameLength);
        }

        String fileName = new String(stringBytes, "UTF-16LE"); //NON-NLS

        return new RecycledFileMetaData(fileSize, timestamp, fileName);
    }
    
    /**
     * Create a map of userids to usernames from the OS Accounts.
     * 
     * @param dataSource
     * 
     * @return A Map of userIDs and userNames
     * 
     * @throws TskCoreException 
     */
    private Map<String, String> makeUserNameMap(Content dataSource) throws TskCoreException{
        Map<String, String> userNameMap = new HashMap<>();
      
        List<BlackboardArtifact> accounts = blackboard.getArtifacts(TSK_OS_ACCOUNT.getTypeID(), dataSource.getId());
        
        for (BlackboardArtifact account: accounts) {
            BlackboardAttribute nameAttribute = getAttributeForArtifact(account, TSK_USER_NAME);
            BlackboardAttribute idAttribute = getAttributeForArtifact(account, TSK_USER_ID);
            
            String userName = nameAttribute != null ? nameAttribute.getDisplayString() : "";
            String userID = idAttribute != null ? idAttribute.getDisplayString() : "";
            
            if (!userID.isEmpty()) {
                userNameMap.put(userID, userName);
            }
        }
        
        return userNameMap;
    }
    
    /**
     * Helper functions to get the user ID from the iFile parent path. User ids
     * will be of the form S-<more characters>.
     * 
     * @param iFileParentPath String parent path of the iFile
     * 
     * @return String user id 
     */
    private String getUserIDFromPath(String iFileParentPath) {
        int index = iFileParentPath.indexOf('-') - 1;
        if (index >= 0) {
            return (iFileParentPath.substring(index)).replace("/", "");
        } else {
            return "";
        }
    }
    
    /**
     * Gets the attribute for the given type from the given artifact.
     * 
     * @param artifact BlackboardArtifact to get the attribute from
     * @param type The BlackboardAttribute Type to get
     * 
     * @return BlackboardAttribute for given artifact and type
     * 
     * @throws TskCoreException 
     */
    private BlackboardAttribute getAttributeForArtifact(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) throws TskCoreException{
       return artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.fromID(type.getTypeID())));
    }
    
    private void createRecycleBinArtifactType() throws TskCoreException {
         try {
            tskCase.addBlackboardArtifactType(RECYCLE_BIN_ARTIFACT_NAME, "Recycle Bin"); //NON-NLS
        } catch (TskDataException ex) {
            logger.log(Level.INFO, String.format("%s may have already been defined for this case", RECYCLE_BIN_ARTIFACT_NAME));
        }

    }
    
    /**
     * Create the new artifact for the give rFile
     * 
     * @param rFile AbstractFile to create the artifact for
     * @param type Type of artifact to create
     * @param fileName The original path of the deleted file
     * @param userName The name of the user that deleted the file
     * @param dateTime The time in epoch seconds that the file was deleted
     * 
     * @return Newly created artifact
     * 
     * @throws TskCoreException 
     */
    private BlackboardArtifact createArtifact(AbstractFile rFile, BlackboardArtifact.Type type, String fileName, String userName, long dateTime) throws TskCoreException{
        BlackboardArtifact bba = rFile.newArtifact(type.getTypeID());
        bba.addAttribute(new BlackboardAttribute(TSK_PATH, getName(), fileName));
        bba.addAttribute(new BlackboardAttribute(TSK_DATETIME_DELETED, getName(), dateTime));
        bba.addAttribute(new BlackboardAttribute(TSK_USER_NAME, getName(), userName == null || userName.isEmpty() ? "" : userName));
        
        return bba;
    }

    /**
     * Stores the data from the $I files.
     */
    final class RecycledFileMetaData {

        private final long fileSize;
        private final long deletedTimeStamp;
        private final String fileName;

        /**
         * Constructs a new instance.
         *
         * @param fileSize         Size of the deleted file.
         * @param deletedTimeStamp Time the file was deleted.
         * @param fileName         Name of the deleted file.
         */
        RecycledFileMetaData(Long fileSize, long deletedTimeStamp, String fileName) {
            this.fileSize = fileSize;
            this.deletedTimeStamp = deletedTimeStamp;
            this.fileName = fileName;
        }

        /**
         * Returns the size of the recycled file.
         * 
         * @return Size of deleted file
         */
        long getFileSize() {
            return fileSize;
        }

        /**
         * Returns the time the file was deleted.
         * 
         * @return deleted time in epoch seconds.
         */
        long getDeletedTimeStamp() {
            return deletedTimeStamp;
        }

        /**
         * Returns the name of the deleted file.
         * 
         * @return String name of the deleted file
         */
        String getFileName() {
            return fileName;
        }
    }
}
