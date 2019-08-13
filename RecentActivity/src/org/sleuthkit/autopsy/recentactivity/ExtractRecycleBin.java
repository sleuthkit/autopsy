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
import java.util.List;
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
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_DELETED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
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
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
        String tempDirPath = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), "recyclebin"); //NON-NLS
        SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();

        // At this time it was decided tjat we would not include TSK_RECYCLE_BIN
        // in the default list of BlackboardArtifact types.
        try {
            skCase.addBlackboardArtifactType(RECYCLE_BIN_ARTIFACT_NAME, "Recycle Bin"); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("%s may not have been created.", RECYCLE_BIN_ARTIFACT_NAME), ex);
        } catch (TskDataException ex) {
            logger.log(Level.WARNING, String.format("%s may have already been defined for this case", RECYCLE_BIN_ARTIFACT_NAME), ex);
        }

        BlackboardArtifact.Type recycleBinArtifactType;

        try {
            recycleBinArtifactType = skCase.getArtifactType(RECYCLE_BIN_ARTIFACT_NAME);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Unable to retrive custom artifact type %s", RECYCLE_BIN_ARTIFACT_NAME), ex); // NON-NLS
            // If this doesn't work bail.
            return;
        }

        List<AbstractFile> iFiles;

        try {
            iFiles = fileManager.findFiles(dataSource, "$I%"); //NON-NLS            
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to find recycle bin I files.", ex); //NON-NLS
            return;  // No need to continue
        }

        for (AbstractFile iFile : iFiles) {

            if (context.dataSourceIngestIsCancelled()) {
                return;
            }

            String tempFilePath = tempDirPath + File.separator + iFile.getName();

            try {
                try {
                    ContentUtils.writeToFile(iFile, new File(tempFilePath));
                } catch (IOException ex) {
                    logger.log(Level.WARNING, String.format("Unable to write %s to temp directory. File name: %s", iFile.getName(), tempFilePath), ex); //NON-NLS
                    // if we cannot make a copy of the $I file for later processing
                    // move onto the next file
                    continue;
                }

                RecycledFileMetaData metaData;
                try {
                    metaData = parseIFile(tempFilePath);
                } catch (IOException ex) {
                    logger.log(Level.WARNING, String.format("Unable to parse iFile %s", iFile.getName()), ex); //NON-NLS
                    // Unable to parse the $I file move onto the next file
                    continue;
                }

                String rFileName = iFile.getName().replace("$I", "$R"); //NON-NLS
                List<AbstractFile> rFiles;

                try {
                    rFiles = fileManager.findFiles(dataSource, rFileName, iFile.getParentPath());
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, String.format("Unable to find R file (%s) for I file (%s)", rFileName, iFile.getName()), ex); //NON-NLS
                    // If there are no $R files go on to the next $I file
                    continue;
                }

                for (AbstractFile rFile : rFiles) {
                    if (context.dataSourceIngestIsCancelled()) {
                        return;
                    }

                    if (iFile.getParentPath().equals(rFile.getParentPath())) {
                        try {
                            BlackboardArtifact bba = rFile.newArtifact(recycleBinArtifactType.getTypeID());
                            bba.addAttribute(new BlackboardAttribute(TSK_PATH, getName(), metaData.getFileName()));
                            bba.addAttribute(new BlackboardAttribute(TSK_DATETIME_DELETED, getName(), metaData.getDeletedTimeStamp()));

                            postArtifact(bba);
                        } catch (TskCoreException ex) {
                            logger.log(Level.WARNING, String.format("Unable to add attributes to artifact %s", rFile.getName()), ex); //NON-NLS
                        }
                    }
                }
            } finally {
                (new File(tempFilePath)).delete();
            }
        }

        (new File(tempDirPath)).delete();
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
    RecycledFileMetaData parseIFile(String iFilePath) throws FileNotFoundException, IOException {
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
            int fileNameLength = byteBuffer.getInt();
            stringBytes = Arrays.copyOfRange(allBytes, V2_FILE_NAME_OFFSET, fileNameLength);
        }

        String fileName = new String(stringBytes, "UTF-16LE"); //NON-NLS

        return new RecycledFileMetaData(fileSize, timestamp, fileName);
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
