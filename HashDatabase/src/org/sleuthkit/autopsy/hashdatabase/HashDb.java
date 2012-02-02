/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.hashdatabase;

import java.io.File;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Log;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskException;

/**
 * HashDb is based on the path to a database, and has methods to check the 
 * status of database and index files, and create indexes.  One of these 
 * is created for every open hash database. 
 */
class HashDb {

    // Suffix added to the end of a database name to get its index file
    private static final String INDEX_SUFFIX = "-md5.idx";
    /**
     * Path to database (database and/or index may not actually exist)
     */
    String databasePath;

    /**
     * New {@link HashDb} for database at given path
     * @param databasePath Path of database this instance represents (database
     * and/or index may not actually exist)
     */
    HashDb(String databasePath) {
        this.databasePath = databasePath;
    }

    /**
     * Checks if the database exists.
     * @return true if a file exists at the database path, else false
     */
    boolean databaseExists() {
        return databaseFile().exists();
    }

    /**
     * Checks if Sleuth Kit can open the index for the database path.
     * @return true if the index was found and opened successfully, else false
     */
    boolean indexExists() {
        try {
            return hasIndex(databasePath);
        } catch (TskException ex) {
            Log.get(this.getClass()).log(Level.WARNING, "Error checking if index exists.", ex);
            return false;
        }
    }

    /**
     * Gets the database file.
     * @return a File initialized with the database path
     */
    File databaseFile() {
        return new File(databasePath);
    }

    /**
     * Gets the index file
     * @return a File initialized with an index path derived from the database
     * path
     */
    File indexFile() {
        return new File(toIndexPath(databasePath));
    }

    /**
     * Checks if the index file is older than the database file
     * @return true if there is are files at the index path and the database
     * path, and the index file has an older modified-time than the database
     * file, else false
     */
    boolean isOutdated() {
        File i = indexFile();
        File db = databaseFile();

        return i.exists() && db.exists() && isOlderThan(i, db);
    }

    /**
     * Returns the status of the HashDb as determined from indexExists(),
     * databaseExists(), and isOutdated()
     * @return IndexStatus enum according to their definitions
     */
    IndexStatus status() {
        boolean i = this.indexExists();
        boolean db = this.databaseExists();

        if (i) {
            if (db) {
                return this.isOutdated() ? IndexStatus.INDEX_OUTDATED : IndexStatus.INDEX_CURRENT;
            } else {
                return IndexStatus.NO_DB;
            }
        } else {
            return db ? IndexStatus.NO_INDEX : IndexStatus.NONE;
        }
    }

    /**
     * Tries to index the database (overwrites any existing index)
     * @throws TskException if an error occurs in the SleuthKit bindings 
     */
    void createIndex() throws TskException {
        SleuthkitJNI.createLookupIndex(databasePath);
        //TODO: error checking
    }

    /**
     * Checks if one file is older than an other
     * @param a first file
     * @param b second file
     * @return true if the first file's last modified data is before the second
     * file's last modified date
     */
    private static boolean isOlderThan(File a, File b) {
        return a.lastModified() < b.lastModified();
    }

    /**
     * Determines if a path points to an index by checking the suffix
     * @param path
     * @return true if index
     */
    static boolean isIndexPath(String path) {
        return path.endsWith(INDEX_SUFFIX);
    }

    /**
     * Derives database path from an image path by removing the suffix.
     * @param indexPath
     * @return 
     */
    static String toDatabasePath(String indexPath) {
        return indexPath.substring(0, indexPath.lastIndexOf(INDEX_SUFFIX));
    }

    /**
     * Derives image path from an database path by appending the suffix.
     * @param databasePath
     * @return 
     */
    static String toIndexPath(String databasePath) {
        return databasePath.concat(INDEX_SUFFIX);
    }

    /**
     * Calls Sleuth Kit method via JNI to determine whether there is an
     * index for the given path
     * @param databasePath path Path for the database the index is of
     * (database doesn't have to actually exist)'
     * @return true if index exists
     * @throws TskException if  there is an error in the JNI call 
     */
    static boolean hasIndex(String databasePath) throws TskException {
        return SleuthkitJNI.lookupIndexExists(databasePath);
    }
}
