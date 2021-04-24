/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
 * An abstraction around an SQLite app DB found in a data source. This class
 * makes a copy of it, along with any meta files (WAL, SHM), opens a SQLite
 * connection to it, and runs queries on it.
 */
public final class AppSQLiteDB {

    private final Logger logger = Logger.getLogger(AppSQLiteDB.class.getName());

    private final AbstractFile dbAbstractFile;  // AbstractFile for the DB file

    private final Connection connection;
    private final Statement statement;

    /**
     * Class to abstract the abstract file for a DB file and its on disk copy
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
     * Looks for application SQLite database files with a given name or name
     * substring and a given parent path or parent path substring. For each
     * database file found, a temporary copy is made and an open connection to
     * the database in the form of an AppSQLiteDB object is created and
     * returned.
     *
     * @param dataSource       The data source to be searched for the database
     *                         files.
     * @param dbFileName       The database file name or file name substring for
     *                         which to search.
     * @param matchExactName   Whether or not the database file name argument is
     *                         the full database file name or a substring.
     * @param parentPathSubstr The parent path substring, may pass the empty
     *                         string to match any parent path.
     *
     * @return A list, possibly empty, of AppSQLiteDB objects for the files that
     *         were found, copied, and connected to.
     */
    public static Collection<AppSQLiteDB> findAppDatabases(DataSource dataSource,
            String dbFileName, boolean matchExactName, String parentPathSubstr) {

        List<AppSQLiteDB> appDbs = new ArrayList<>();
        try {
            Collection<AppSQLiteDBFileBundle> dbFileBundles = findAndCopySQLiteDB(dataSource, dbFileName, matchExactName, parentPathSubstr, false);
            dbFileBundles.forEach((dbFileBundle) -> {
                try {
                    AppSQLiteDB appSQLiteDB = new AppSQLiteDB(dbFileBundle);
                    appDbs.add(appSQLiteDB);
                } catch (ClassNotFoundException | SQLException ex) {
                    Logger.getLogger(AppSQLiteDB.class.getName()).log(Level.SEVERE, String.format("Failed to open a DB connection for file = '%s' and path = '%s'.", dbFileBundle.dbAbstractFile.getName(), dbFileBundle.getFileCopy().getPath()), ex); //NON-NLS
                }
            });
        } catch (TskCoreException ex) {
            Logger.getLogger(AppSQLiteDB.class.getName()).log(Level.SEVERE, String.format("Error finding App database files with name = '%s' and path = '%s'.", dbFileName, parentPathSubstr), ex); //NON-NLS
        }

        return appDbs;
    }

    public AbstractFile getDBFile() {
        return this.dbAbstractFile;
    }

    /**
     * Attaches a database to the current connection.
     *
     * Finds the specified database file in the specified folder. If found,
     * makes copy of the database in the case folder and run ATTACH DATABASE
     * sql.
     *
     * @param dataSource data source in which to look file the db file
     * @param dbName     name of db file to look for
     * @param dbPath     path in which to look for the db file
     * @param dbAlias    alias name to attach the database as
     *
     * @return abstract file for the matching db file. null if no match is
     *         found.
     *
     * @throws SQLException in case of an SQL error
     */
    public AbstractFile attachDatabase(DataSource dataSource, String dbName,
            String dbPath, String dbAlias) throws SQLException {
        try {
            // find and copy DB files with exact name and path.
            Collection<AppSQLiteDBFileBundle> dbFileBundles = findAndCopySQLiteDB(dataSource, dbName, true, dbPath, true);
            if (!dbFileBundles.isEmpty()) {
                AppSQLiteDBFileBundle dbFileBundle = dbFileBundles.iterator().next();
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
     * Finds database file with the specified name, makes a copy of the file in
     * the case directory, and returns the AbstractFile as well as the file
     * copy.
     *
     * @param dataSource     data source to search in
     * @param dbName         db file name to search
     * @param matchExactName whether to look for exact file name or a pattern
     *                       match
     * @param dbPath         path to match
     * @param matchExactPath whether to look for exact path name or a substring
     *                       match
     *
     * @return a collection of AppSQLiteDBFileBundle
     *
     * @throws TskCoreException
     */
    private static Collection<AppSQLiteDBFileBundle> findAndCopySQLiteDB(DataSource dataSource, String dbName,
            boolean matchExactName, String dbPath, boolean matchExactPath) throws TskCoreException {

        Case openCase;
        try {
            openCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            throw new TskCoreException("Failed to get current case.", ex);
        }

        List<AppSQLiteDBFileBundle> dbFileBundles = new ArrayList<>();
        long fileId = 0;
        String localDiskPath = "";

        SleuthkitCase skCase = openCase.getSleuthkitCase();
        String parentPath = dbPath.replace("\\", "/");
        parentPath = SleuthkitCase.escapeSingleQuotes(parentPath);

        String whereClause;
        if (matchExactName) {
            whereClause = String.format("LOWER(name) = LOWER('%s')", dbName);
        } else {
            whereClause = String.format("LOWER(name) LIKE LOWER('%%%s%%') AND LOWER(name) NOT LIKE LOWER('%%journal%%')", dbName);
        }
        if (matchExactPath) {
            whereClause += String.format(" AND LOWER(parent_path) = LOWER('%s')", parentPath);
        } else {
            whereClause += String.format(" AND LOWER(parent_path) LIKE LOWER('%%%s%%')", parentPath);
        }
        whereClause += String.format(" AND data_source_obj_id = %s", dataSource.getId());

        List<AbstractFile> absFiles = skCase.findAllFilesWhere(whereClause);
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
    public void detachDatabase(String dbAlias) throws SQLException {
        String detachDbSql = String.format("DETACH DATABASE '%s'", dbAlias);
        statement.executeUpdate(detachDbSql); //NON-NLS
    }

    /**
     * Runs the given query on the database and returns result set.
     *
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
     * Closes the DB connection.
     *
     */
    public void close() {

        // Close the DB connection
        try {
            statement.close();
            connection.close();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error closing the database", e); //NON-NLS
        }
    }

    /**
     * Checks if a column exists in a table.
     *
     * @param tableName name of the table
     * @param columnName column name to check
     *
     * @return true if the column exists, false otherwise
     * @throws TskCoreException
     */
    public boolean columnExists(String tableName, String columnName) throws TskCoreException {

        boolean columnExists = false;
        Statement colExistsStatement = null;
        ResultSet resultSet = null;
        try {
            colExistsStatement = connection.createStatement();
            String tableInfoQuery = "PRAGMA table_info(%s)";  //NON-NLS
            resultSet = colExistsStatement.executeQuery(String.format(tableInfoQuery, tableName));
            while (resultSet.next()) {
                if (resultSet.getString("name").equalsIgnoreCase(columnName)) {
                    columnExists = true;
                    break;
                }
            }
        } catch (SQLException ex) {
            throw new TskCoreException("Error checking if column  " + columnName + "exists ", ex);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException ex2) {
                    logger.log(Level.WARNING, "Failed to close resultset after checking column", ex2);
                }
            }
            if (colExistsStatement != null) {
                try {
                    colExistsStatement.close();
                } catch (SQLException ex2) {
                    logger.log(Level.SEVERE, "Error closing Statement", ex2); //NON-NLS
                }
            }
        }
        return columnExists;
    }
    
    /**
     * Checks if a table exists in the case database.
     *
     * @param tableName name of the table to check
     *
     * @return true if the table exists, false otherwise
     * @throws TskCoreException
     */
    public boolean tableExists(String tableName) throws TskCoreException {

        boolean tableExists = false;
        Statement tableExistsStatement = null;
        ResultSet resultSet = null;
        try {

            tableExistsStatement = connection.createStatement();
            resultSet = tableExistsStatement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'");  //NON-NLS
            while (resultSet.next()) {
                if (resultSet.getString("name").equalsIgnoreCase(tableName)) { //NON-NLS
                    tableExists = true;
                    break;
                }
            }
        } catch (SQLException ex) {
            throw new TskCoreException("Error checking if table  " + tableName + "exists ", ex);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException ex2) {
                    logger.log(Level.WARNING, "Failed to close resultset after checking table", ex2);
                }
            }
            if (tableExistsStatement != null) {
                try {
                    tableExistsStatement.close();
                } catch (SQLException ex2) {
                    logger.log(Level.SEVERE, "Error closing Statement", ex2); //NON-NLS
                }
            }
        }
        return tableExists;
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

        // Do not look for metaFile if this is a carved directory
        if(sqliteFile.getParentPath().equalsIgnoreCase("/$carvedfiles/")) {
            return;
        }
        
        Case openCase = Case.getCurrentCaseThrows();
        SleuthkitCase sleuthkitCase = openCase.getSleuthkitCase();
        Services services = new Services(sleuthkitCase);
        FileManager fileManager = services.getFileManager();
        
        List<AbstractFile> metaFiles = fileManager.findFilesExactName(sqliteFile.getParent().getId(), metaFileName);

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
