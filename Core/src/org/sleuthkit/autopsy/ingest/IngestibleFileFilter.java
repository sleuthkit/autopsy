/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Utility class for identifying files that should not be ingested: synthesized
 * unallocated space files, pseudo-files, and special NTFS or FAT file system
 * files.
 */
public class IngestibleFileFilter {

    private static final Logger logger = Logger.getLogger(IngestibleFileFilter.class.getName());
    private static final int FAT_NTFS_FLAGS = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT12.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue() | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_NTFS.getValue();

    /**
     *
     * @param candidateFile
     * @param excludeUnallocSpaceFiles
     *
     * @return
     */
    static boolean isIngestible(AbstractFile candidateFile, boolean excludeUnallocSpaceFiles) {
        /*
         * Filter out synthesized unallocated space files.
         */
        if (excludeUnallocSpaceFiles && candidateFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return false;
        }

        /*
         * Filter out pseudo-files.
         */
        String fileName = candidateFile.getName();
        if (fileName.equals(".") || fileName.equals("..")) {
            return false;
        }

        if (candidateFile instanceof org.sleuthkit.datamodel.File) {
            /*
             * Is the file in an NTFS or FAT file system?
             */
            org.sleuthkit.datamodel.File file = (org.sleuthkit.datamodel.File) candidateFile;
            TskData.TSK_FS_TYPE_ENUM fileSystemType = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_UNSUPP;
            try {
                FileSystem fileSystem = file.getFileSystem();
                if (null != fileSystem) {
                    fileSystemType = fileSystem.getFsType();
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error querying file system for " + file, ex); //NON-NLS
            }
            if ((fileSystemType.getValue() & FAT_NTFS_FLAGS) == 0) {
                return true;
            }

            /*
             * Is the NTFS or FAT file in the root directory?
             */
            boolean isInRootDir = false;
            try {
                AbstractFile parent = file.getParentDirectory();
                isInRootDir = parent.isRoot();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error querying parent directory for" + file.getName(), ex); //NON-NLS
            }

            /*
             * Check its meta-address and check its name for the '$' character
             * and a ':' character (not a default attribute).
             */
            if (isInRootDir && file.getMetaAddr() < 32) {
                String name = file.getName();
                if (name.length() > 0 && name.charAt(0) == '$' && name.contains(":")) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Prevents instantiation of this class.
     */
    private IngestibleFileFilter() {
    }

}
