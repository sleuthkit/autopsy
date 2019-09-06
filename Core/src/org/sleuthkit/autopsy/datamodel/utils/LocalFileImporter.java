/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel.utils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SpecialDirectory;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Utility class for adding local files with specified paths in the data source.
 * It is currently assumed that the data source is empty to start or that at 
 * least the paths to the files being added do not exist; no checks will be done
 * to see if folders exist prior to creating them through addLocalFile().
 */
public class LocalFileImporter {
    private static final Logger logger = Logger.getLogger(LocalFileImporter.class.getName());

    SleuthkitCase.CaseDbTransaction globalTrans = null;
    boolean useSingleTransaction = true;
    SleuthkitCase sleuthkitCase;
    private final Map<String, SpecialDirectory> localFileDirMap = new HashMap<>();
    
    /**
     * Create a LocalFileImporter. 
     * 
     * @param sleuthkitCase The current SleuthkitCase
     */
    public LocalFileImporter(SleuthkitCase sleuthkitCase) {
        this.sleuthkitCase = sleuthkitCase;
        this.useSingleTransaction = false;
    }
    
    /**
     * Create a LocalFileImporter. The caller is responsible for committing 
     * or rolling back the transaction. 
     * 
     * @param sleuthkitCase The current SleuthkitCase
     * @param trans         The open CaseDbTransaction
     */
    public LocalFileImporter(SleuthkitCase sleuthkitCase, SleuthkitCase.CaseDbTransaction trans) {
        this.sleuthkitCase = sleuthkitCase;
        this.globalTrans = trans;
        this.useSingleTransaction = true;
    }
    
    /**
     * Add a local file to the database with the specified parameters. Will create
     * any necessary parent folders.
     * 
     * Will not fail if the fileOnDisk does not exist.
     * 
     * @param fileOnDisk  The local file on disk
     * @param name        The name to use in the data source
     * @param parentPath  The path to use in the data source
     * @param ctime       Change time
     * @param crtime      Created time
     * @param atime       Access time
     * @param mtime       Modified time
     * @param dataSource  The data source to add the file to
     * 
     * @return The AbstractFile that was just created
     * 
     * @throws TskCoreException 
     */
    public AbstractFile addLocalFile(File fileOnDisk, String name, String parentPath, 
            Long ctime, Long crtime, Long atime, Long mtime,
            DataSource dataSource) throws TskCoreException {
        
        // Get the parent folder, creating it and any of its parent folders if necessary
        SpecialDirectory parentDir = getOrMakeDirInDataSource(new File(parentPath), dataSource);
        
        SleuthkitCase.CaseDbTransaction trans = null;
        try {
            if (useSingleTransaction) {
                trans = globalTrans;
            } else {
                trans = sleuthkitCase.beginTransaction();
            }
            
            // Try to get the file size
            long size = 0;
            if (fileOnDisk.exists()) {
                size = fileOnDisk.length();
            }
             
            // Create the new file
            AbstractFile file = sleuthkitCase.addLocalFile(name, fileOnDisk.getAbsolutePath(), size,
                    ctime, crtime, atime, mtime,
                    true, TskData.EncodingType.NONE, parentDir, trans);

            if (! useSingleTransaction) {
                trans.commit();
            }
            return file;
        } catch (TskCoreException ex) {
            if ((!useSingleTransaction) && (null != trans)) {
                try {
                    trans.rollback();
                } catch (TskCoreException ex2) {
                    logger.log(Level.SEVERE, String.format("Failed to rollback transaction after exception: %s", ex.getMessage()), ex2);
                }
            }
            throw ex;
        }        
    }
    
    /**
     * Returns the SpecialDirectory object corresponding to the given directory, creating
     * it and its parents as needed.
     * 
     * @param directory       The file to get the SpecialDirectory for
     * @param dataSource The data source
     * 
     * @return The SpecialDirectory object corresponding to the given file
     * 
     * @throws TskCoreException 
     */
    private SpecialDirectory getOrMakeDirInDataSource(File directory, Content dataSource) throws TskCoreException {
        if ((directory == null) || directory.getPath().isEmpty()) {
            throw new TskCoreException("Can not create directory from null path");
        }

        // Check if we've already created it
        if (localFileDirMap.containsKey(directory.toString())) {
            return localFileDirMap.get(directory.toString());
        }

        File parent = directory.getParentFile();
        if (parent == null) {
            // This is the root of the path and it isn't in the map, so create it
            SpecialDirectory dir = createLocalFilesDir(dataSource.getId(), directory.getName());
            localFileDirMap.put(directory.getName(), dir);
            return dir;

        } else {
            // Create everything above this in the tree, and then add the parent folder
            SpecialDirectory parentDir = getOrMakeDirInDataSource(parent, dataSource);
            SpecialDirectory dir = createLocalFilesDir(parentDir.getId(), directory.getName());
            localFileDirMap.put(directory.getPath(), dir);
            return dir;
        }
    }
    
    /**
     * Create a new LocalDirectory
     * 
     * @param parentId The object ID for parent
     * @param name     The name of the new local directory
     * 
     * @return The new LocalDirectory
     * 
     * @throws TskCoreException 
     */
    private SpecialDirectory createLocalFilesDir(long parentId, String name) throws TskCoreException {
        SleuthkitCase.CaseDbTransaction trans = null;

        try {
            if (useSingleTransaction) {
                trans = globalTrans;
            } else {
                trans = sleuthkitCase.beginTransaction();
            }
            SpecialDirectory dir;

            dir = sleuthkitCase.addLocalDirectory(parentId, name, trans);

            if (! useSingleTransaction) {
                trans.commit();
            }
            return dir;
        } catch (TskCoreException ex) {
            if (( !useSingleTransaction) && (null != trans)) {
                try {
                    trans.rollback();
                } catch (TskCoreException ex2) {
                    logger.log(Level.SEVERE, String.format("Failed to rollback transaction after exception: %s", ex.getMessage()), ex2);
                }
            }
            throw ex;
        }
    }    
    
}