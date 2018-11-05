/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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

package org.sleuthkit.autopsy.core;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author dsmyda
 */
public class SQLiteTableReader implements AutoCloseable {

    /**
     * 
     */
    public static class Builder {

        private final AbstractFile file;
        private Consumer<ResultSetMetaData> onMetaDataAction;
        private Consumer<String> onStringAction;
        private Consumer<Long> onLongAction;
        private Consumer<Integer> onIntegerAction;
        private Consumer<Double> onFloatAction;
        private Consumer<byte[]> onBlobAction;
        private Consumer<Object> forAllAction;

        /**
         * Creates a SQLiteTableReaderBuilder for this abstract file.
         *
         * @param file
         */
        public Builder(AbstractFile file) {
            this.file = file;
        }

        /**
         * Specify a function to handle MetaData parsing. The MetaData object
         * will be parsed before any contents are read from the table.
         *
         * @param action
         *
         * @return
         */
        public Builder onMetadata(Consumer<ResultSetMetaData> action) {
            this.onMetaDataAction = action;
            return this;
        }

        /**
         * Specify a function to do on receiving a database entry that is type
         * String.
         *
         * @param action
         *
         * @return
         */
        public Builder onString(Consumer<String> action) {
            this.onStringAction = action;
            return this;
        }

        /**
         * Specify a function to do on receiving a database entry that is type
         * Integer.
         *
         * @param action
         *
         * @return
         */
        public Builder onInteger(Consumer<Integer> action) {
            this.onIntegerAction = action;
            return this;
        }

        /**
         * Specify a function to do on receiving a database entry that is type
         * Real.
         *
         * @param action
         *
         * @return
         */
        public Builder onFloat(Consumer<Double> action) {
            this.onFloatAction = action;
            return this;
        }
        
        /**
         * 
         * @param action
         * @return 
         */
        public Builder onLong(Consumer<Long> action) {
            this.onLongAction = action;
            return this;
        }
        
        /**
         * 
         * @param action
         * @return 
         */
        public Builder onBlob(Consumer<byte[]> action) {
            this.onBlobAction = action;
            return this;
        }

        /**
         * Specify a function to do for any database entry, regardless of type.
         *
         * @param action
         *
         * @return
         */
        public Builder forAll(Consumer<Object> action) {
            this.forAllAction = action;
            return this;
        }

        /**
         * Pass all params to the SQLTableStream so that it can iterate through
         * the table
         *
         * @return
         */
        public SQLiteTableReader build() {
            return new SQLiteTableReader(
                    file,
                    onMetaDataAction,
                    onStringAction,
                    onIntegerAction,
                    onLongAction,
                    onFloatAction,
                    onBlobAction,
                    forAllAction
            );
        }
    }

    private final AbstractFile file;

    private Connection conn;
    private PreparedStatement statement;
    private ResultSet queryResults;

    private final Consumer<ResultSetMetaData> onMetaDataAction;
    private final Consumer<String> onStringAction;
    private final Consumer<Integer> onIntegerAction;
    private final Consumer<Long> onLongAction;
    private final Consumer<Double> onFloatAction;
    private final Consumer<byte[]> onBlobAction;
    private final Consumer<Object> forAllAction;

    //Iteration state variables
    private Integer currColumnCount;
    private boolean unfinishedRowState = false;

    private boolean isFinished;
    private boolean hasOpened;
    
    private final BooleanSupplier alwaysFalseCondition = () -> {return false;};

    /**
     * Initialize a new table stream given the parameters passed in from the
     * StreamBuilder above.
     */
    private SQLiteTableReader(AbstractFile file,
            Consumer<ResultSetMetaData> metaDataAction,
            Consumer<String> stringAction,
            Consumer<Integer> integerAction,
            Consumer<Long> longAction,
            Consumer<Double> floatAction,
            Consumer<byte[]> blobAction,
            Consumer<Object> forAllAction) {

        this.onMetaDataAction = checkNonNull(metaDataAction);
        this.onStringAction = checkNonNull(stringAction);
        this.onIntegerAction = checkNonNull(integerAction);
        this.onLongAction = checkNonNull(longAction);
        this.onFloatAction = checkNonNull(floatAction);
        this.onBlobAction = checkNonNull(blobAction);
        this.forAllAction = checkNonNull(forAllAction);

        this.file = file;
    }

    /**
     * 
     * @param <T>
     * @param action
     * @return 
     */
    private <T> Consumer<T> checkNonNull(Consumer<T> action) {
        if (Objects.nonNull(action)) {
            return action;
        }

        //No-op lambda, keep from NPE or having to check during iteration 
        //if action == null.
        return (NO_OP) -> {};
    }

    /**
     * Get table names from database
     *
     * @return
     * @throws org.sleuthkit.autopsy.core.AutopsySQLiteException
     *
     */
    public List<String> getTableNames() throws AutopsySQLiteException {
        ensureOpen();
        
        List<String> tableNames = new ArrayList<>();
        
        try (ResultSet tableNameResult = conn.createStatement()
                .executeQuery("SELECT name FROM sqlite_master "
                + " WHERE type= 'table' ")) {
            while (tableNameResult.next()) {
                tableNames.add(tableNameResult.getString("name")); //NON-NLS
            }
        } catch (SQLException ex) {
            throw new AutopsySQLiteException(ex);
        }

        return tableNames;
    }
    
    /**
     * 
     * @param tableName
     * @return
     * @throws org.sleuthkit.autopsy.core.AutopsySQLiteException 
     */
    public int getRowCount(String tableName) throws AutopsySQLiteException {
        ensureOpen();
        
        try (ResultSet countResult = conn.createStatement()
                .executeQuery("SELECT count (*) as count FROM " + 
                        "\"" + tableName + "\"")) {
            return countResult.getInt("count");
        } catch (SQLException ex) {
            throw new AutopsySQLiteException(ex);
        }
    }

    /**
     * 
     * @param tableName
     * @throws org.sleuthkit.autopsy.core.AutopsySQLiteException
     */
    public void read(String tableName) throws AutopsySQLiteException {
        readHelper("SELECT * FROM \"" + tableName +"\"", alwaysFalseCondition);
    }

    /**
     * Read x number of rows (limit), starting from row number y (offset) in
     * table z (tableName).
     *
     * @param tableName
     * @param limit
     * @param offset
     * @throws org.sleuthkit.autopsy.core.AutopsySQLiteException
     *
     */
    public void read(String tableName, int limit, int offset) throws AutopsySQLiteException {
        readHelper("SELECT * FROM \"" + tableName +"\" LIMIT " + limit
                + " OFFSET " + offset, alwaysFalseCondition);
    }

    /**
     * Iterate through the table stopping if we are done, an exception is
     * thrown, or the condition is false!
     *
     * @param tableName
     * @param condition
     * @throws org.sleuthkit.autopsy.core.AutopsySQLiteException
     *
     */
    public void read(String tableName, BooleanSupplier condition) throws AutopsySQLiteException {
        readHelper("SELECT * FROM \"" + tableName + "\"", condition);
    }

    /**
     * Iterate through the entire table calling the correct function given the
     * datatype. Only stop when there is nothing left to read or a SQLException
     * is thrown.
     *
     * @param tableName
     *
     * @throws org.sleuthkit.autopsy.core.AutopsySQLiteException
     */
    private void readHelper(String query, BooleanSupplier condition) throws AutopsySQLiteException {
        try {
            if(!hasOpened) {
                openResultSet(query);
            }
            
            isFinished = false;
            
            ResultSetMetaData metaData = queryResults.getMetaData();
            this.onMetaDataAction.accept(metaData);
            
            int columnCount = metaData.getColumnCount();
            
            while (unfinishedRowState || queryResults.next()) {
                if (!unfinishedRowState) {
                    currColumnCount = 1;
                }
                
                for (; currColumnCount <= columnCount; currColumnCount++) {
                    
                    if (condition.getAsBoolean()) {
                        unfinishedRowState = true;
                        return;
                    }
                    
                    Object item = queryResults.getObject(currColumnCount);
                    if(item instanceof String) {
                        this.onStringAction.accept((String) item);
                    } else if(item instanceof Integer) {
                        this.onIntegerAction.accept((Integer) item);
                    } else if(item instanceof Double) {
                        this.onFloatAction.accept((Double) item);
                    } else if(item instanceof Long) {
                        this.onLongAction.accept((Long) item);
                    } else if(item instanceof byte[]) {
                        this.onBlobAction.accept((byte[]) item);
                    }
                    
                    this.forAllAction.accept(item);
                }
                
                unfinishedRowState = false;
            }
            
            isFinished = true;
            closeResultSet();
        } catch (SQLException ex) {
            throw new AutopsySQLiteException(ex);
        }
    }

    /**
     * 
     * @throws org.sleuthkit.autopsy.core.AutopsySQLiteException
     */
    private void ensureOpen() throws AutopsySQLiteException {
        if (Objects.isNull(conn)) {
            try {
                String localDiskPath = writeAbstractFileToLocalDisk(file, file.getId());
                findAndCopySQLiteMetaFile(file);
                conn = DriverManager.getConnection("jdbc:sqlite:" + localDiskPath);
            } catch (NoCurrentCaseException | TskCoreException | IOException | SQLException ex) {
                throw new AutopsySQLiteException(ex);
            }
        }
    }
    
    /**
     * Overloaded implementation of
     * {@link #findAndCopySQLiteMetaFile(AbstractFile, String) findAndCopySQLiteMetaFile}
     * , automatically tries to copy -wal and -shm files without needing to know
     * their existence.
     *
     * @param sqliteFile file which has -wal and -shm meta files
     *
     * @throws NoCurrentCaseException Case has been closed.
     * @throws TskCoreException       fileManager cannot find AbstractFile
     *                                files.
     * @throws IOException            Issue during writing to file.
     */
    private void findAndCopySQLiteMetaFile(AbstractFile sqliteFile)
            throws NoCurrentCaseException, TskCoreException, IOException {

        findAndCopySQLiteMetaFile(sqliteFile, sqliteFile.getName() + "-wal");
        findAndCopySQLiteMetaFile(sqliteFile, sqliteFile.getName() + "-shm");
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
    private void findAndCopySQLiteMetaFile(AbstractFile sqliteFile,
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
                writeAbstractFileToLocalDisk(metaFile, sqliteFile.getId());
            }
        }
    }
    
    /**
     * Copies the file contents into a unique path in the current case temp
     * directory.
     *
     * @param file AbstractFile from the data source
     *
     * @return The path of the file on disk
     *
     * @throws IOException            Exception writing file contents
     * @throws NoCurrentCaseException Current case closed during file copying
     */
    private String writeAbstractFileToLocalDisk(AbstractFile file, long id)
            throws IOException, NoCurrentCaseException {

        String localDiskPath = Case.getCurrentCaseThrows().getTempDirectory()
                + File.separator + id + file.getName();
        File localDatabaseFile = new File(localDiskPath);
        if (!localDatabaseFile.exists()) {
            ContentUtils.writeToFile(file, localDatabaseFile);
        }
        return localDiskPath;
    }
    
    /**
     * 
     * @param query
     * @throws SQLException 
     */
    private void openResultSet(String query) throws AutopsySQLiteException {
        ensureOpen();
        
        try {    
            statement = conn.prepareStatement(query);
            // statement.setFetchSize(300);
            
            queryResults = statement.executeQuery();
            hasOpened = true;
        } catch (SQLException ex) {
            throw new AutopsySQLiteException(ex);
        }
    }

    /**
     * 
     */
    private void closeResultSet() {
        try {
            if(Objects.nonNull(statement)) {
                statement.close();
            }   
            if(Objects.nonNull(queryResults)) {
                queryResults.close();
            }
            hasOpened = false;
        } catch (SQLException ex) {
            //Do nothing, can't close.. tried our best.
        }
    }

    /**
     * Closes all connections with the database.
     */
    @Override
    public void close() {
        try {
            closeResultSet();
            if(Objects.nonNull(conn)) {
                conn.close();
            }
        } catch (SQLException ex) {
            //Do nothing, can't close.. tried our best.
        }
    }

    /**
     * Checks if there is still work to do on the result set.
     *
     * @return boolean
     */
    public boolean isFinished() {
        return isFinished;
    }

    /**
     * Last ditch effort to close the connections.
     * 
     * @throws Throwable 
     */
    @Override
    public void finalize() throws Throwable {
        super.finalize();
        close();
    }
}