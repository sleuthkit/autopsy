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
 * This class makes a copy of it, along with any meta files (WAL, SHM),
 * opens a SQLite connection to it, and runs queries on it.
 */
public final class AppSQLiteDB implements Closeable {
    private final Logger logger = Logger.getLogger(AppSQLiteDB.class.getName());
    
    private final AbstractFile dbAbstractFile;  // AbstractFile for the DB file
    
    private final Connection connection;
    private final Statement statement;
    
    
    /**
     * Class to abstract the abstract file for a DB file and its  on disk copy
     * 
     */
    private static final class AppSQLiteDBFileBundle {
        private final AbstractFile dbAbstractFile;
        private final File dbFileCopy;
        
        AppSQLiteDBFileBundle(AbstractFile dbAbstractFile, File dbFileCopy) {
            this.dbAbstractFile = dbAbstractFile;
            this.dbFileCopy = dbFileCopy;
        }
        
        AbstractFile getAbstractFile() {
            return dbAbstractFile;
        }
        
        File getFileCopy() {
            return dbFileCopy;
        }
        
    }
    
    private AppSQLiteDB(AppSQLiteDBFileBundle appSQLiteDBFileBundle) throws ClassNotFoundException, SQLException {
        this.dbAbstractFile = appSQLiteDBFileBundle.getAbstractFile();
        
        Class.forName("org.sqlite.JDBC"); //NON-NLS //load JDBC driver
        connection = DriverManager.getConnection("jdbc:sqlite:" + appSQLiteDBFileBundle.getFileCopy().getPath()); //NON-NLS
        statement = connection.createStatement();
    }
    
   
    /**
     * Looks for the given SQLIte database filename, with matching path substring. 
     * It looks for exact name or a pattern match based on a input parameter.
     * It makes a copy of each matching file, and creates an instance of 
     * AppSQLiteDB to help query the DB. 
     * 
     * A list of AppSQLiteDB instances is returned, one for each 
     * match found., 
     * .
     * @param dataSource data source to search in 
     * @param dbName db file name to search
     * @param matchExactName whether to look for exact file name or a pattern match
     * @param parentPathSubstr path substring to match
     * 
     * @return A list of abstract files matching the specified name and path.
     *         Returns an empty list if no matching database is found.
     */
    public static Collection<AppSQLiteDB> findAppDatabases(DataSource dataSource,
            String dbName, boolean matchExactName, String parentPathSubstr) {
        
        List<AppSQLiteDB> appDbs = new ArrayList<> ();
        try {
            Collection<AppSQLiteDBFileBundle> dbFileBundles = findAndCopySQLiteDB( dataSource,  dbName,  matchExactName, parentPathSubstr, false);
            dbFileBundles.forEach((dbFileBundle) -> {
                try {
                    AppSQLiteDB appSQLiteDB = new AppSQLiteDB(dbFileBundle);
                    appDbs.add(appSQLiteDB);
                } catch (ClassNotFoundException | SQLException ex) {
                    Logger.getLogger(AppSQLiteDB.class.getName()).log(Level.SEVERE, String.format("Failed to open a DB connection for file = '%s' and path = '%s'.", dbFileBundle.dbAbstractFile.getName(), dbFileBundle.getFileCopy().getPath()), ex); //NON-NLS
                }
            });
        } catch (TskCoreException ex) {
            Logger.getLogger(AppSQLiteDB.class.getName()).log(Level.SEVERE, String.format("Error finding App database files with name = '%s' and path = '%s'.", dbName, parentPathSubstr), ex); //NON-NLS
        }
        
        return appDbs;
    }
    
    public AbstractFile getDBFile() {
        return this.dbAbstractFile;
    }
    
    /**
     * Attaches a database to the current connection.
     * 
     * Finds the specified database file in the specified folder.  
     * If found, makes copy of the database in the case folder and
     * run ATTACH DATABASE sql.
     * 
     * @param dataSource data source in which to look file the db file
     * @param dbName name of db file to look for
     * @param matchExactName specified whether the name is an exact name or a pattern
     * @param dbPath path in which to look for the db file
     * @param dbAlias alias name to attach the database as
     * 
     * @return abstract file for the matching db file.
     *         null if no match is found.
     *
     * @throws SQLException in case of an SQL error
     */
    public AbstractFile attachDatabase(DataSource dataSource, String dbName, 
                    boolean matchExactName, String dbPath, String dbAlias) throws SQLException {
        try {
            Collection<AppSQLiteDBFileBundle> dbFileBundles = findAndCopySQLiteDB(dataSource,  dbName,  matchExactName, dbPath, true);
            for (AppSQLiteDBFileBundle dbFileBundle: dbFileBundles) {
                String attachDbSql = String.format("ATTACH DATABASE '%s' AS '%s'", dbFileBundle.getFileCopy().getPath(), dbAlias); //NON-NLS
                    statement.executeUpdate(attachDbSql); 
                    
                return dbFileBundle.getAbstractFile();
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(AppSQLiteDB.class.getName()).log(Level.SEVERE, String.format("Error attaching to App database files with name = '%s' and path = '%s'.", dbName, dbPath), ex); //NON-NLS
        }
        
        return null;
    }
    
    /**
     * Finds database file with the specified name, makes a copy of the file in the case directory, 
     * and returns the AbstractFile as well as the file copy.
     * 
     * @param dataSource data source to search in 
     * @param dbName db file name to search
     * @param matchExactName whether to look for exact file name or a pattern match
     * @param dbPath path to match
     * @param matchExactName whether to look for exact path name or a substring match
     * 
     * @return a collection of AppSQLiteDBFileBundle
     * 
     * @throws TskCoreException 
     */
    private static Collection<AppSQLiteDBFileBundle> findAndCopySQLiteDB(DataSource dataSource, String dbName, 
                    boolean matchExactName,  String dbPath, boolean matchExactPath) throws TskCoreException {
        
        List<AppSQLiteDBFileBundle> dbFileBundles = new ArrayList<> ();
        Case openCase;
        
        try {
            openCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            throw new TskCoreException("Failed to get current case.", ex);
        }
        
        List<AbstractFile> absFiles;
        long fileId = 0;
        String localDiskPath = "";
        
        SleuthkitCase skCase = openCase.getSleuthkitCase();
        String parentPath = dbPath.replace("\\", "/");
        parentPath = SleuthkitCase.escapeSingleQuotes(parentPath);
        
        String whereClause;
        if (matchExactName) {
            whereClause = String.format("LOWER(name) = LOWER('%s')", dbName);
        } else {
            whereClause = String.format("LOWER(name) LIKE LOWER('%%%s%%') AND LOWER(name) NOT LIKE LOWER('%%journal%%')", dbName );
        }
        if (matchExactPath) {
            whereClause += String.format(" AND LOWER(parent_path) = LOWER('%s')", parentPath );
        } else {
            whereClause += String.format(" AND LOWER(parent_path) LIKE LOWER('%%%s%%')", parentPath );
        }
        whereClause += String.format(" AND data_source_obj_id = %s", dataSource.getId());
        
        absFiles = skCase.findAllFilesWhere(whereClause);
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

                AppSQLiteDBFileBundle dbFileBundle = new AppSQLiteDBFileBundle(absFile, jFile);
                dbFileBundles.add(dbFileBundle);

            } catch (ReadContentInputStream.ReadContentInputStreamException ex) {
                Logger.getLogger(AppSQLiteDB.class.getName()).log(Level.WARNING, String.format("Error reading content from file '%s' (id=%d).", absFile.getName(), fileId), ex); //NON-NLS
            } catch (IOException | NoCurrentCaseException | TskCoreException ex) {
                Logger.getLogger(AppSQLiteDB.class.getName()).log(Level.SEVERE, String.format("Error creating AppSQLiteDB  for file '%s' (id=%d) to  copied to '%s'.", absFile.getName(), fileId, localDiskPath), ex); //NON-NLS
            } 
        }
        
        return dbFileBundles;
    }
    
    /**
     * Detaches the specified database from the connection
     * 
     * @param dbAlias alias for database to detach
     * 
     * @throws SQLException 
     */
    public void detachDatabase(String dbAlias) throws SQLException  {
        String detachDbSql = String.format("DETACH DATABASE '%s'", dbAlias);
        statement.executeUpdate(detachDbSql); //NON-NLS
    }
     
    
    /**
     * Runs the given query on the database and returns result set.

     * @param queryStr SQL string for the query to run
     * 
     * @return ResultSet from running the query. 
     * 
     * @throws SQLException in case of an error.
     *         
     */
    public ResultSet runQuery(String queryStr) throws SQLException {
        ResultSet resultSet = null;
       
        if (null != queryStr) {
            resultSet = statement.executeQuery(queryStr); //NON-NLS
        } 
        return resultSet;
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
