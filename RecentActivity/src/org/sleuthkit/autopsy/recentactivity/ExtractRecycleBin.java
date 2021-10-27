/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.BufferUnderflowException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.joda.time.Instant;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_DELETED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

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

    private static final String RECYCLE_BIN_DIR_NAME = "$RECYCLE.BIN"; //NON-NLS

    private static final int V1_FILE_NAME_OFFSET = 24;
    private static final int V2_FILE_NAME_OFFSET = 28;
    private final IngestJobContext context;

    @Messages({
        "ExtractRecycleBin_module_name=Recycle Bin Analyzer"
    })
    ExtractRecycleBin(IngestJobContext context) {
        super(Bundle.ExtractRecycleBin_module_name(), context);
        this.context = context;
    }

    @Override
    void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        // At this time it was decided that we would not include TSK_RECYCLE_BIN
        // in the default list of BlackboardArtifact types.
        try {
            createRecycleBinArtifactType();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("%s may not have been created.", RECYCLE_BIN_ARTIFACT_NAME), ex);
        }

        BlackboardArtifact.Type recycleBinArtifactType;

        try {
            recycleBinArtifactType = tskCase.getBlackboard().getArtifactType(RECYCLE_BIN_ARTIFACT_NAME);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Unable to retrive custom artifact type %s", RECYCLE_BIN_ARTIFACT_NAME), ex); // NON-NLS
            // If this doesn't work bail.
            return;
        }

        // map SIDs to user names so that we can include that in the artifact
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

        // Collect all of the $R files so that we can later easily map them to corresponding $I file
        Map<String, List<AbstractFile>> rFileMap;
        try {
            rFileMap = makeRFileMap(dataSource);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Unable to create $R file map for dataSource: %s", dataSource.getName()), ex);
            return; // No $R files, no need to continue;
        }

        // Get the $I files
        List<AbstractFile> iFiles;
        try {
            iFiles = fileManager.findFiles(dataSource, "$I%", RECYCLE_BIN_DIR_NAME); //NON-NLS            
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to find recycle bin I files.", ex); //NON-NLS
            return;  // No need to continue
        }

        String tempRARecycleBinPath = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), "recyclebin", context.getJobId()); //NON-NLS

        // cycle through the $I files and process each. 
        for (AbstractFile iFile : iFiles) {

            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            processIFile(context, recycleBinArtifactType, iFile, userNameMap, rFileMap, tempRARecycleBinPath);
        }

        (new File(tempRARecycleBinPath)).delete();
    }

    /**
     * Process each individual iFile. Each iFile ($I) contains metadata about
     * files that have been deleted. Each $I file should have a corresponding $R
     * file which is the actuall deleted file.
     *
     * @param context
     * @param recycleBinArtifactType Module created artifact type
     * @param iFile                  The AbstractFile to process
     * @param userNameMap            Map of user ids to names
     * @param tempRARecycleBinPath   Temp directory path
     */
    private void processIFile(IngestJobContext context, BlackboardArtifact.Type recycleBinArtifactType, AbstractFile iFile, Map<String, String> userNameMap, Map<String, List<AbstractFile>> rFileMap, String tempRARecycleBinPath) {
        String tempFilePath = tempRARecycleBinPath + File.separator + Instant.now().getMillis() + iFile.getName();
        try {
            try {
                ContentUtils.writeToFile(iFile, new File(tempFilePath));
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", iFile.getName(), tempFilePath), ex); //NON-NLS
                // if we cannot make a copy of the $I file for later processing
                // move onto the next file
                return;
            }

            // get the original name, dates, etc. from the $I file
            RecycledFileMetaData metaData;
            try {
                metaData = parseIFile(tempFilePath);
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Unable to parse iFile %s", iFile.getParentPath() + iFile.getName()), ex); //NON-NLS
                // Unable to parse the $I file move onto the next file
                return;
            }

            // each user has its own Recyle Bin folder.  Figure out the user name based on its name .
            String userID = getUserIDFromPath(iFile.getParentPath());
            String userName = "";
            if (!userID.isEmpty()) {
                userName = userNameMap.get(userID);
            } else {
                // If the iFile doesn't have a user ID in its parent 
                // directory structure then it is not from the recyle bin
                return;
            }

            // get the corresponding $R file, which is in the same folder and has the file content
            String rFileName = iFile.getName().replace("$I", "$R"); //NON-NLS
            List<AbstractFile> rFiles = rFileMap.get(rFileName);
            if (rFiles == null) {
                return;
            }
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            for (AbstractFile rFile : rFiles) {
                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }

                if (iFile.getParentPath().equals(rFile.getParentPath())
                        && iFile.getMetaFlagsAsString().equals(rFile.getMetaFlagsAsString())) {
                    try {
                        postArtifact(createArtifact(rFile, recycleBinArtifactType, metaData.getFullWindowsPath(), userName, metaData.getDeletedTimeStamp()));

                        // If we are processing a disk image, we will also make a deleted file entry so that the user
                        // sees the deleted file in its original folder.  We re-use the metadata address so that the user 
                        // can see the content. 
                        if (rFile instanceof FsContent) {
                            // if the user deleted a folder, then we need to recusively go into it.  Note the contents of the $R folder
                            // do not have corresponding $I files anymore.  Only the $R folder does.
                            if (rFile.isDir()) {
                                AbstractFile directory = getOrMakeFolder(Case.getCurrentCase().getSleuthkitCase(), (FsContent) rFile, metaData.getFullWindowsPath());
                                popuplateDeletedDirectory(Case.getCurrentCase().getSleuthkitCase(), directory, rFile.getChildren(), metaData.getFullWindowsPath(), metaData.getDeletedTimeStamp());

                            } else {
                                AbstractFile folder = getOrMakeFolder(Case.getCurrentCase().getSleuthkitCase(), (FsContent) rFile.getParent(), Paths.get(metaData.getFullWindowsPath()).getParent().toString());
                                addFileSystemFile(skCase, (FsContent) rFile, folder, Paths.get(metaData.getFullWindowsPath()).getFileName().toString(), metaData.getDeletedTimeStamp());
                            }
                        }
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
     * Add the children of recycled $R folder to the folder.
     *
     * @param skCase           The current Sleuthkit case
     * @param parentFolder     The folder to folder the deleted files are to be
     *                         added.
     * @param children         The recycled children of the $R folder
     * @param parentPath       String path to the directory the children were
     *                         deleted from
     * @param deletedTimeStamp The time at which the files were deleted,
     *                         inherited from the $R file.
     *
     * @throws TskCoreException
     */
    private void popuplateDeletedDirectory(SleuthkitCase skCase, AbstractFile parentFolder, List<Content> recycledChildren, String parentPath, long deletedTimeStamp) throws TskCoreException {
        if (recycledChildren == null) {
            return;
        }

        for (Content child : recycledChildren) {
            if (child instanceof FsContent) {
                FsContent fsContent = (FsContent) child;
                if (fsContent.isFile()) {
                    addFileSystemFile(skCase, fsContent, parentFolder, fsContent.getName(), deletedTimeStamp);
                } else if (fsContent.isDir()) {
                    String newPath = parentPath + "\\" + fsContent.getName();
                    AbstractFile childFolder = getOrMakeFolder(skCase, fsContent, parentPath);
                    popuplateDeletedDirectory(skCase, childFolder, fsContent.getChildren(), newPath, deletedTimeStamp);
                }
            }
        }
    }

    /**
     * Parse the $I file. This file contains metadata information about deleted
     * files.
     *
     * File format prior to Windows 10:
     *
     * Offset Size Description
     *
     * 0 8 Header
     *
     * 8 8 File Size
     *
     * 16 8 Deleted Timestamp
     *
     * 24 520 File Name
     *
     * File format Windows 10+
     *
     * Offset Size Description
     *
     * 0 8 Header
     *
     * 8 8 File Size
     *
     * 16 8 Deleted TimeStamp
     *
     * 24 4 File Name Length
     *
     * 28 var File Name
     *
     * @param iFilePath Path to local copy of file in temp folder
     *
     * @throws IOException
     */
    private RecycledFileMetaData parseIFile(String iFilePath) throws IOException {
        try {
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
        } catch (IOException | BufferUnderflowException | IllegalArgumentException | ArrayIndexOutOfBoundsException ex) {
            throw new IOException("Error parsing $I File, file is corrupt or not a valid I$ file", ex);
        }
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
    private Map<String, String> makeUserNameMap(Content dataSource) throws TskCoreException {
        Map<String, String> userNameMap = new HashMap<>();

        for (OsAccount account : tskCase.getOsAccountManager().getOsAccounts(((DataSource) dataSource).getHost())) {
            Optional<String> userName = account.getLoginName();
            userNameMap.put(account.getName(), userName.isPresent() ? userName.get() : "");
        }
        return userNameMap;
    }

    /**
     * Get a list of files that start with $R and create a map of the file to
     * their name.
     *
     * @param dataSource
     *
     * @return File map
     *
     * @throws TskCoreException
     */
    private Map<String, List<AbstractFile>> makeRFileMap(Content dataSource) throws TskCoreException {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
        List<AbstractFile> rFiles = fileManager.findFiles(dataSource, "$R%");
        Map<String, List<AbstractFile>> fileMap = new HashMap<>();

        for (AbstractFile rFile : rFiles) {
            String fileName = rFile.getName();
            List<AbstractFile> fileList = fileMap.get(fileName);

            if (fileList == null) {
                fileList = new ArrayList<>();
                fileMap.put(fileName, fileList);
            }

            fileList.add(rFile);
        }

        return fileMap;
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
     * @param type     The BlackboardAttribute Type to get
     *
     * @return BlackboardAttribute for given artifact and type
     *
     * @throws TskCoreException
     */
    private BlackboardAttribute getAttributeForArtifact(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) throws TskCoreException {
        return artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.fromID(type.getTypeID())));
    }

    @Messages({
        "ExtractRecycleBin_Recyle_Bin_Display_Name=Recycle Bin"
    })
    /**
     * Create TSK_RECYCLE_BIN artifact type.
     *
     * @throws TskCoreException
     */
    private void createRecycleBinArtifactType() throws TskCoreException {
        try {
            tskCase.getBlackboard().getOrAddArtifactType(RECYCLE_BIN_ARTIFACT_NAME, Bundle.ExtractRecycleBin_Recyle_Bin_Display_Name()); //NON-NLS
        } catch (BlackboardException ex) {
            throw new TskCoreException(String.format("An exception was thrown while defining artifact type %s", RECYCLE_BIN_ARTIFACT_NAME), ex);
        }

    }

    /**
     * Create the new artifact for the give rFile
     *
     * @param rFile    AbstractFile to create the artifact for
     * @param type     Type of artifact to create
     * @param fileName The original path of the deleted file
     * @param userName The name of the user that deleted the file
     * @param dateTime The time in epoch seconds that the file was deleted
     *
     * @return Newly created artifact
     *
     * @throws TskCoreException
     */
    private BlackboardArtifact createArtifact(AbstractFile rFile, BlackboardArtifact.Type type, String fileName, String userName, long dateTime) throws TskCoreException {
        List<BlackboardAttribute> attributes = new ArrayList<>();
        attributes.add(new BlackboardAttribute(TSK_PATH, getDisplayName(), fileName));
        attributes.add(new BlackboardAttribute(TSK_DATETIME_DELETED, getDisplayName(), dateTime));
        attributes.add(new BlackboardAttribute(TSK_USER_NAME, getDisplayName(), userName == null || userName.isEmpty() ? "" : userName));
        return createArtifactWithAttributes(type, rFile, attributes);
    }

    /**
     * Returns a folder for the given path. If the path does not exist the the
     * folder is created. Recursively makes as many parent folders as needed.
     *
     * @param skCase
     * @param dataSource
     * @param path
     *
     * @return AbstractFile for the given path.
     *
     * @throws TskCoreException
     */
    private AbstractFile getOrMakeFolder(SleuthkitCase skCase, FsContent dataSource, String path) throws TskCoreException {

        String parentPath = getParentPath(path);
        String folderName = getFileName(path);

        List<AbstractFile> files = null;
        if (parentPath != null) {
            if (!parentPath.equals("/")) {
                parentPath = parentPath + "/";
            }

            files = skCase.findAllFilesWhere(String.format("fs_obj_id=%s AND parent_path='%s' AND name='%s'",
                    dataSource.getFileSystemId(), SleuthkitCase.escapeSingleQuotes(parentPath), folderName != null ? SleuthkitCase.escapeSingleQuotes(folderName) : ""));
        } else {
            files = skCase.findAllFilesWhere(String.format("fs_obj_id=%s AND parent_path='/' AND name=''", dataSource.getFileSystemId()));
        }

        if (files == null || files.isEmpty()) {
            AbstractFile parent = getOrMakeFolder(skCase, dataSource, parentPath);
            return skCase.addVirtualDirectory(parent.getId(), folderName);
        } else {
            return files.get(0);
        }
    }

    /**
     * Adds a new file system file that is unallocated and maps to the original
     * file in recycle bin directory.
     *
     * @param skCase         The current case.
     * @param recycleBinFile The file from the recycle bin.
     * @param parentDir      The directory that the recycled file was deleted.
     * @param fileName       The name of the file.
     * @param deletedTime    The time the file was deleted.
     *
     * @throws TskCoreException
     */
    private void addFileSystemFile(SleuthkitCase skCase, FsContent recycleBinFile, Content parentDir, String fileName, long deletedTime) throws TskCoreException {
        skCase.addFileSystemFile(
                recycleBinFile.getDataSourceObjectId(),
                recycleBinFile.getFileSystemId(),
                fileName,
                recycleBinFile.getMetaAddr(),
                (int) recycleBinFile.getMetaSeq(),
                recycleBinFile.getAttrType(),
                recycleBinFile.getAttributeId(),
                TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC,
                (short) (TskData.TSK_FS_META_FLAG_ENUM.UNALLOC.getValue() | TskData.TSK_FS_META_FLAG_ENUM.USED.getValue()),
                recycleBinFile.getSize(),
                recycleBinFile.getCtime(), recycleBinFile.getCrtime(), recycleBinFile.getAtime(), deletedTime,
                true, parentDir);
    }

    /**
     * Clean up the windows path string to match what the autopsy db uses.
     *
     * @param path The file\folder path to normalize
     *
     * @return New path string with the root removed (ie X:) and the slashes
     *         changed from windows to unix.
     */
    String normalizeFilePath(String pathString) {
        if (pathString == null || pathString.isEmpty()) {
            return null;
        }

        Path path = Paths.get(pathString);
        int nameCount = path.getNameCount();
        if (nameCount > 0) {
            String rootless = "/" + path.subpath(0, nameCount);
            return rootless.replace("\\", "/");
        } else {
            return "/";
        }
    }

    /**
     * Helper function get from the given path either the file name or the last
     * directory in the path.
     *
     * @param filePath The file\directory path
     *
     * @return If file path, returns the file name. If directory path the The
     *         last directory in the path is returned.
     */
    String getFileName(String filePath) {
        Path fileNamePath = Paths.get(filePath).getFileName();
        if (fileNamePath != null) {
            return fileNamePath.toString();
        }
        return filePath;
    }

    /**
     * Returns the parent path for the given path.
     *
     * @param path Path string
     *
     * @return The parent path for the given path.
     */
    String getParentPath(String path) {
        Path parentPath = Paths.get(path).getParent();
        if (parentPath != null) {
            return normalizeFilePath(parentPath.toString());
        }
        return null;
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
         * Returns the full path to the deleted file or folder. This path will
         * include the drive letter, ie C:\
         *
         * @return String name of the deleted file
         */
        String getFullWindowsPath() {
            return fileName.trim();
        }
    }
}
