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

package org.sleuthkit.autopsy.coreutils;

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
        private Consumer<String> onColumnNameAction;
        
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
        public Builder onColumnNames(Consumer<String> action) {
            this.onColumnNameAction = action;
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
            return new SQLiteTableReader(this);
        }
    }

    private final AbstractFile file;

    private Connection conn;
    private PreparedStatement statement;
    private ResultSet queryResults;

    private final Consumer<String> onColumnNameAction;
    private final Consumer<String> onStringAction;
    private final Consumer<Long> onLongAction;
    private final Consumer<Integer> onIntegerAction;
    private final Consumer<Double> onFloatAction;
    private final Consumer<byte[]> onBlobAction;
    private final Consumer<Object> forAllAction;

    //Iteration state variables
    private Integer currRowColumnIndex;
    private boolean unfinishedRowState;
    private Integer columnNameIndex;
    private Integer currentColumnCount;
    private ResultSetMetaData currentMetadata;

    private boolean isFinished;
    private boolean hasOpened;
    private String prevTableName;
    
    private final BooleanSupplier alwaysFalseCondition = () -> {return false;};

    /**
     * Initialize a new table stream given the parameters passed in from the
     * StreamBuilder above.
     */
    private SQLiteTableReader(Builder builder) {

        this.onColumnNameAction = nonNullValue(builder.onColumnNameAction);
        this.onStringAction = nonNullValue(builder.onStringAction);
        this.onIntegerAction = nonNullValue(builder.onIntegerAction);
        this.onLongAction = nonNullValue(builder.onLongAction);
        this.onFloatAction = nonNullValue(builder.onFloatAction);
        this.onBlobAction = nonNullValue(builder.onBlobAction);
        this.forAllAction = nonNullValue(builder.forAllAction);

        this.file = builder.file;
    }
    
    private <T> Consumer<T> nonNullValue(Consumer<T> action) {
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
     * @throws org.sleuthkit.autopsy.coreutils.SQLiteTableReaderException
     *
     */
    public List<String> getTableNames() throws SQLiteTableReaderException {
        ensureOpen();
        
        List<String> tableNames = new ArrayList<>();
        
        try (ResultSet tableNameResult = conn.createStatement()
                .executeQuery("SELECT name FROM sqlite_master "
                + " WHERE type= 'table' ")) {
            while (tableNameResult.next()) {
                tableNames.add(tableNameResult.getString("name")); //NON-NLS
            }
        } catch (SQLException ex) {
            throw new SQLiteTableReaderException(ex);
        }

        return tableNames;
    }
    
    /**
     * 
     * @param tableName
     * @return
     * @throws org.sleuthkit.autopsy.coreutils.SQLiteTableReaderException
     */
    public int getRowCount(String tableName) throws SQLiteTableReaderException {
        ensureOpen();
        
        try (ResultSet countResult = conn.createStatement()
                .executeQuery("SELECT count (*) as count FROM " + 
                        "\"" + tableName + "\"")) {
            return countResult.getInt("count");
        } catch (SQLException ex) {
            throw new SQLiteTableReaderException(ex);
        }
    }
    
    public int getColumnCount(String tableName) throws SQLiteTableReaderException {
        ensureOpen();
        
        try (ResultSet columnCount = conn.createStatement()
                .executeQuery("SELECT * FROM " + 
                        "\"" + tableName + "\"")) {
            return columnCount.getMetaData().getColumnCount();
        } catch (SQLException ex) {
            throw new SQLiteTableReaderException(ex);
        }
    }

    /**
     * 
     * @param tableName
     * @throws org.sleuthkit.autopsy.coreutils.SQLiteTableReaderException
     */
    public void read(String tableName) throws SQLiteTableReaderException {
        readHelper("SELECT * FROM \"" + tableName +"\"", alwaysFalseCondition);
    }

    /**
     * Read x number of rows (limit), starting from row number y (offset) in
     * table z (tableName).
     *
     * @param tableName
     * @param limit
     * @param offset
     * @throws org.sleuthkit.autopsy.coreutils.SQLiteTableReaderException
     *
     */
    public void read(String tableName, int limit, int offset) throws SQLiteTableReaderException {
        readHelper("SELECT * FROM \"" + tableName +"\" LIMIT " + limit
                + " OFFSET " + offset, alwaysFalseCondition);
    }

    /**
     * Iterate through the table stopping if we are done, an exception is
     * thrown, or the condition is false!
     *
     * @param tableName
     * @param condition
     * @throws org.sleuthkit.autopsy.coreutils.SQLiteTableReaderException
     *
     */
    public void read(String tableName, BooleanSupplier condition) throws SQLiteTableReaderException {
        if(Objects.nonNull(prevTableName) && prevTableName.equals(tableName)) {
            readHelper("SELECT * FROM \"" + tableName + "\"", condition);
        } else {
            prevTableName = tableName;
            closeResultSet();
            readHelper("SELECT * FROM \"" + tableName + "\"", condition);
        }
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
    private void readHelper(String query, BooleanSupplier condition) throws SQLiteTableReaderException {
        try {
            if(!hasOpened) {
                openResultSet(query);
                currentMetadata = queryResults.getMetaData();
                currentColumnCount = currentMetadata.getColumnCount();
                columnNameIndex = 1;
            }
            
            isFinished = false;
            
            for(; columnNameIndex <= currentColumnCount; columnNameIndex++) {
                this.onColumnNameAction.accept(currentMetadata.getColumnName(columnNameIndex));
            }
            
            while (unfinishedRowState || queryResults.next()) {
                if (!unfinishedRowState) {
                    currRowColumnIndex = 1;
                }
                
                for (; currRowColumnIndex <= currentColumnCount; currRowColumnIndex++) {
                    
                    if (condition.getAsBoolean()) {
                        unfinishedRowState = true;
                        return;
                    }
                    
                    //getObject automatically instiantiates the correct java data type
                    Object item = queryResults.getObject(currRowColumnIndex);
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
            closeResultSet();
            isFinished = true;
            throw new SQLiteTableReaderException(ex);
        }
    }

    /**
     * 
     * @throws org.sleuthkit.autopsy.core.AutopsySQLiteException
     */
    private void ensureOpen() throws SQLiteTableReaderException {
        if (Objects.isNull(conn)) {
            try {
                Class.forName("org.sqlite.JDBC"); //NON-NLS  
                String localDiskPath = writeAbstractFileToLocalDisk(file, file.getId());
                findAndCopySQLiteMetaFile(file);
                conn = DriverManager.getConnection("jdbc:sqlite:" + localDiskPath);
            } catch (NoCurrentCaseException | TskCoreException | IOException | 
                    ClassNotFoundException | SQLException ex) {
                throw new SQLiteTableReaderException(ex);
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
    private void openResultSet(String query) throws SQLiteTableReaderException {
        ensureOpen();
        
        try {    
            statement = conn.prepareStatement(query);
            
            queryResults = statement.executeQuery();
            hasOpened = true;
        } catch (SQLException ex) {
            throw new SQLiteTableReaderException(ex);
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