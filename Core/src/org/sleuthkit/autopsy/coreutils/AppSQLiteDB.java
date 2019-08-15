/*
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

package org.sleuthkit.autopsy.coreutils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An abstraction around an SQLite app DB found in a data source.
 * This class makes a copy of it, opens a SQLite connection to it 
 * and runs queries on it.
 */
public final class AppSQLiteDB implements Closeable {
    private final Logger logger = Logger.getLogger(AppSQLiteDB.class.getName());
    
    private final AbstractFile dbAbstractFile;  // AbstractFile for the DB file
    
    private Connection connection = null;
    private Statement statement = null;
    
   private AppSQLiteDB(AbstractFile dbAbstractFile, File dbFileCopy) {
        this.dbAbstractFile = dbAbstractFile;
        
        try {
            Class.forName("org.sqlite.JDBC"); //NON-NLS //load JDBC driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFileCopy.getPath()); //NON-NLS
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, "Error opening database " + dbFileCopy.getPath(), e); //NON-NLS
            connection = null;
            statement = null;
        }
    }
    
    
    /**
     * Looks for the given SQLIte database filename, with matching path substring. 
     * It makes a copy of each matching file, and creates an instance of 
     * AppSQLiteDB to help query the DB. 
     * 
     * A list of AppSQLiteDB instances is returned, one for each 
     * match found., 
     * .
     * @param dataSource data source to search in 
     * @param dbNamePattern db file name pattern to search
     * @param parentPathSubstr path substring to match
     * 
     * @return AbstractFile for the DB if the database file is found.
     *         Returns NULL if no such database is found.
     */
    public static Collection<AppSQLiteDB> findAppDatabases(DataSource dataSource, String dbNamePattern, String parentPathSubstr) {
        
        List<AppSQLiteDB> appDbs = new ArrayList<> ();
        Case openCase;
        
        try {
            openCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            Logger.getLogger(AppSQLiteDB.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return appDbs;
        }
        
        List<AbstractFile> absFiles;
        long fileId = 0;
        String localDiskPath = "";
        try {
            SleuthkitCase skCase = openCase.getSleuthkitCase();
            String parentPath = parentPathSubstr.replace("\\", "/");
            parentPath = SleuthkitCase.escapeSingleQuotes(parentPath);
            final String whereClause = String.format("LOWER(name) LIKE LOWER(\'%%%1$s%%\') AND LOWER(parent_path) LIKE LOWER(\'%%%2$s%%\') AND data_source_obj_id = %s", dbNamePattern, parentPath, dataSource.getId());
            absFiles = skCase.findAllFilesWhere(whereClause); //NON-NLS //get exact file names
            if (absFiles.isEmpty()) {
                return appDbs;
            }
            
            for (AbstractFile absFile : absFiles) {
                try {
                    localDiskPath = openCase.getTempDirectory()
                                            + File.separator + absFile.getId() + absFile.getName();
                    File jFile = new java.io.File(localDiskPath);
                    fileId = absFile.getId();
                    ContentUtils.writeToFile(absFile, jFile);
                    
                    //Find and copy both WAL and SHM meta files 
                    findAndCopySQLiteMetaFile(absFile, absFile.getName() + "-wal");
                    findAndCopySQLiteMetaFile(absFile, absFile.getName() + "-shm");
                
                    appDbs.add(new AppSQLiteDB(absFile, jFile) );
                } catch (ReadContentInputStream.ReadContentInputStreamException ex) {
                    Logger.getLogger(AppSQLiteDB.class.getName()).log(Level.WARNING, String.format("Error reading content from file '%s' (id=%d).", absFile.getName(), fileId), ex); //NON-NLS
                } catch (IOException | NoCurrentCaseException | TskCoreException ex) {
                    Logger.getLogger(AppSQLiteDB.class.getName()).log(Level.SEVERE, String.format("Error writing content from file '%s' (id=%d) to '%s'.", absFile.getName(), fileId, localDiskPath), ex); //NON-NLS
                }
            }
        } catch (TskCoreException e) {
            Logger.getLogger(AppSQLiteDB.class.getName()).log(Level.SEVERE, "Error finding application DB file.", e); //NON-NLS
        }
        return appDbs;
    }
    
    public AbstractFile getDBFile() {
        return this.dbAbstractFile;
    }
    
    /**
     * Checks if the specified table exists in the given database file.
     * 
     * @param tableName table name to check
     * 
     * @return 
     */
    public boolean tableExists(String tableName) {
        // RAMAN TBD
        return false;
        
    }
    
    /**
     * Checks if the specified column exists.
     * 
     * @param tableName table name to check
     * @param columnName column name to check
     * @return 
     */
    public boolean columnExists(String tableName, String columnName) {
        // RAMAN TBD
        return false;
    }
    
    
    
    
    /**
     * Runs the given query on the database and returns result set.

     * @param queryStr SQL string for the query to run
     * 
     * @return ResultSet from running the query. 
     *         
     */
    public ResultSet runQuery(String queryStr) {
        // RAMAN TBD
        return null;
    }
    
    /**
     * Closes the DB connection
     * 
     * @throws IOException 
     */
    
    @Override
    public void close() throws IOException {
        
        // Close the DB connection
        try {
            statement.close();
            connection.close();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error closing the database", e); //NON-NLS
        } 
    }
    
    
    
    /**
     * Searches for a meta file associated with the give SQLite database. If
     * found, it copies this file into the temp directory of the current case.
     *
     * @param sqliteFile   file being processed
     * @param metaFileName name of meta file to look for
     *
     * @throws NoCurrentCaseException Case has been closed.
     * @throws TskCoreException       fileManager cannot find AbstractFile
     *                                files.
     * @throws IOException            Issue during writing to file.
     */
    private static void findAndCopySQLiteMetaFile(AbstractFile sqliteFile,
            String metaFileName) throws NoCurrentCaseException, TskCoreException, IOException {

        Case openCase = Case.getCurrentCaseThrows();
        SleuthkitCase sleuthkitCase = openCase.getSleuthkitCase();
        Services services = new Services(sleuthkitCase);
        FileManager fileManager = services.getFileManager();

        List<AbstractFile> metaFiles = fileManager.findFiles(
                sqliteFile.getDataSource(), metaFileName,
                sqliteFile.getParent().getName());

        if (metaFiles != null) {
            for (AbstractFile metaFile : metaFiles) {
                String localDiskPath = openCase.getTempDirectory()
                                            + File.separator + sqliteFile.getId() + metaFile.getName();
                File localMetaFile = new File(localDiskPath);
                if (!localMetaFile.exists()) {
                    ContentUtils.writeToFile(metaFile, localMetaFile);
                }  
            }
        }
    }
}
