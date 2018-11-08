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
 * Reads row by row through SQLite tables and performs user-defined actions on the row values.
 * Table values are processed by data type. Users configure these actions for certain data types 
 * in the Builder. Example usage:
 * 
 *  SQLiteTableReader reader = new SQLiteTableReader.Builder(file)
 *                   .onInteger((i) -> {
 *                          System.out.println(i);
 *                   }).build(); 
 *  reader.read(tableName);
 * 
 *  or
 * 
 *  SQLiteTableReader reader = new SQLiteTableReader.Builder(file)
 *                   .onInteger(new Consumer<Integer>() {
 *                      @Override
 *                      public void accept(Integer i) {
 *                          System.out.println(i);
 *                      }
 *                   }).build();
 *  reader.reader(tableName);
 * 
 * Invocation of read(String tableName) causes that table name to be processed row by row.
 * When an Integer is encountered, its value will be passed to the Consumer that 
 * was defined above.
 */
public class SQLiteTableReader implements AutoCloseable {

    /**
     * Builder patten for configuring SQLiteTableReader instances.
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
         * Creates a Builder for this abstract file.
         *
         * @param file
         */
        public Builder(AbstractFile file) {
            this.file = file;
        }

        /**
         * Specify a function to do on column names. Column names will be read
         * from left to right.
         *
         * @param action Consumer of column name strings
         *
         * @return Builder reference
         */
        public Builder onColumnNames(Consumer<String> action) {
            this.onColumnNameAction = action;
            return this;
        }

        /**
         * Specify a function to do when encountering a database value that is
         * of java type String.
         *
         * @param action Consumer of strings
         *
         * @return Builder reference
         */
        public Builder onString(Consumer<String> action) {
            this.onStringAction = action;
            return this;
        }

        /**
         * Specify a function to do when encountering a database value that is
         * of java type Integer.
         *
         * @param action Consumer of integer
         *
         * @return Builder reference
         */
        public Builder onInteger(Consumer<Integer> action) {
            this.onIntegerAction = action;
            return this;
        }

        /**
         * Specify a function to do when encountering a database value that is
         * of java type Double.
         *
         * @param action Consumer of doubles
         *
         * @return Builder reference
         */
        public Builder onFloat(Consumer<Double> action) {
            this.onFloatAction = action;
            return this;
        }

        /**
         * Specify a function to do when encountering a database value that is
         * of java type Long.
         *
         * @param action Consumer of longs
         *
         * @return Builder reference
         */
        public Builder onLong(Consumer<Long> action) {
            this.onLongAction = action;
            return this;
        }

        /**
         * Specify a function to do when encountering a database value that is
         * of java type byte[] aka blob.
         *
         * @param action Consumer of blobs
         *
         * @return Builder reference
         */
        public Builder onBlob(Consumer<byte[]> action) {
            this.onBlobAction = action;
            return this;
        }

        /**
         * Specify a function to do when encountering any database value,
         * regardless of type. This function only captures database values, not
         * column names.
         *
         * @param action Consumer of objects
         *
         * @return Builder reference
         */
        public Builder forAll(Consumer<Object> action) {
            this.forAllAction = action;
            return this;
        }

        /**
         * Creates a SQLiteTableReader instance given this Builder
         * configuration.
         *
         * @return SQLiteTableReader instance
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
    private Integer columnNameIndex;
    private Integer currentColumnCount;
    private ResultSetMetaData currentMetadata;

    private boolean liveResultSet;
    private String prevTableName;

    /**
     * Assigns references to each action based on the Builder configuration.
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

    /**
     * Ensures the action is null safe. If action is left null, then during
     * iteration null checks would be necessary. To mitigate against that, no-op
     * lambdas are substituted for null values.
     *
     * @param <T>    Generic type of consumer
     * @param action Consumer for generic type, supplied by Builder.
     *
     * @return If action is null, then a no-op lambda, if not then the action
     *         itself.
     */
    private <T> Consumer<T> nonNullValue(Consumer<T> action) {
        return (Objects.nonNull(action)) ? action : NO_OP -> {
        };
    }

    /**
     * Fetches all table names from the database.
     *
     * @return List of all table names found while querying the sqlite_master
     *         table
     *
     * @throws SQLiteTableReaderException
     */
    public List<String> getTableNames() throws SQLiteTableReaderException {
        ensureOpen();
        try (ResultSet tableNameResult = conn.createStatement()
                .executeQuery("SELECT name FROM sqlite_master "
                        + " WHERE type= 'table' ")) {
            List<String> tableNames = new ArrayList<>();
            while (tableNameResult.next()) {
                tableNames.add(tableNameResult.getString("name")); //NON-NLS
            }
            return tableNames;
        } catch (SQLException ex) {
            throw new SQLiteTableReaderException(ex);
        }
    }

    /**
     * Fetches the row count.
     *
     * @param tableName Source table to count
     *
     * @return Count as an integer
     *
     * @throws SQLiteTableReaderException
     */
    public int getRowCount(String tableName) throws SQLiteTableReaderException {
        ensureOpen();
        try (ResultSet countResult = conn.createStatement()
                .executeQuery("SELECT count (*) as count FROM "
                        + "\"" + tableName + "\"")) {
            return countResult.getInt("count");
        } catch (SQLException ex) {
            throw new SQLiteTableReaderException(ex);
        }
    }

    /**
     * Fetches the column count of the table.
     *
     * @param tableName Source table to count
     *
     * @return Count as an integer
     *
     * @throws SQLiteTableReaderException
     */
    public int getColumnCount(String tableName) throws SQLiteTableReaderException {
        ensureOpen();
        try (ResultSet columnCount = conn.createStatement()
                .executeQuery("SELECT * FROM "
                        + "\"" + tableName + "\"")) {
            return columnCount.getMetaData().getColumnCount();
        } catch (SQLException ex) {
            throw new SQLiteTableReaderException(ex);
        }
    }

    /**
     * Reads column names and values from the table. Only actions that were
     * configured in the Builder will be invoked during iteration. Iteration
     * will stop when the table read has completed or an exception was
     * encountered.
     *
     * @param tableName Source table to read
     *
     * @throws SQLiteTableReaderException
     */
    public void read(String tableName) throws SQLiteTableReaderException {
        readHelper("SELECT * FROM \"" + tableName + "\"", () -> false);
    }

    /**
     * Reads column names and values from the table. Only actions that were
     * configured in the Builder will be invoked during iteration. Column names
     * are only read during the first call to this function. Iteration will stop
     * when the table read has completed or an exception was encountered.
     *
     * @param tableName Source table to perform a read
     * @param limit Number of rows to read from the table
     * @param offset Starting row to read from in the table
     *
     * @throws SQLiteTableReaderException
     *
     */
    public void read(String tableName, int limit, int offset) throws SQLiteTableReaderException {
        readHelper("SELECT * FROM \"" + tableName + "\" LIMIT " + limit
                + " OFFSET " + offset, () -> false);
    }

    /**
     * Reads column names and values from the table. Iteration will stop when
     * the condition is true.
     *
     * @param tableName Source table to perform a read
     * @param condition Condition to stop iteration when true
     *
     * @throws SQLiteTableReaderException
     *
     */
    public void read(String tableName, BooleanSupplier condition) throws SQLiteTableReaderException {
        if (Objects.nonNull(prevTableName) && prevTableName.equals(tableName)) {
            readHelper("SELECT * FROM \"" + tableName + "\"", condition);
        } else {
            prevTableName = tableName;
            closeTableResources();
            readHelper("SELECT * FROM \"" + tableName + "\"", condition);
        }
    }

    /**
     * Performs the result set iteration and is responsible for maintaining state 
     * of the read over multiple invocations. 
     *
     * @throws SQLiteTableReaderException
     */
    private void readHelper(String query, BooleanSupplier condition) throws SQLiteTableReaderException {
        try {
            if (!liveResultSet) {
                openTableResources(query);
                columnNameIndex = 1;
            }

            //Process column names before reading the database table values
            for (; columnNameIndex <= currentColumnCount; columnNameIndex++) {
                if (condition.getAsBoolean()) {
                        return;
                }
                this.onColumnNameAction.accept(currentMetadata
                        .getColumnName(columnNameIndex));
            }

            //currRowColumnIndex > 0 means we are still reading the current result set row
            while (currRowColumnIndex > 0 || queryResults.next()) {
                while(currRowColumnIndex < currentColumnCount) {
                    if (condition.getAsBoolean()) {
                        return;
                    }
                    
                    Object item = queryResults.getObject(++currRowColumnIndex);
                    if (item instanceof String) {
                        this.onStringAction.accept((String) item);
                    } else if (item instanceof Integer) {
                        this.onIntegerAction.accept((Integer) item);
                    } else if (item instanceof Double) {
                        this.onFloatAction.accept((Double) item);
                    } else if (item instanceof Long) {
                        this.onLongAction.accept((Long) item);
                    } else if (item instanceof byte[]) {
                        this.onBlobAction.accept((byte[]) item);
                    }

                    this.forAllAction.accept(item);
                }
                //Wrap column index back around if we've reached the end of the row
                currRowColumnIndex = (currRowColumnIndex % currentColumnCount);
            }
            closeTableResources();
        } catch (SQLException ex) {
            closeTableResources();
            throw new SQLiteTableReaderException(ex);
        }
    }

    /**
     * Ensures that the underlying database connection is open. This entails
     * copying the abstract file contents to temp directory, copying over any
     * WAL or SHM files and getting the connection from the DriverManager.
     *
     * @throws SQLiteTableReaderException
     */
    private void ensureOpen() throws SQLiteTableReaderException {
        if (Objects.isNull(conn)) {
            try {
                Class.forName("org.sqlite.JDBC"); //NON-NLS  
                String localDiskPath = copyFileToTempDirectory(file, file.getId());

                //Find and copy both WAL and SHM meta files 
                findAndCopySQLiteMetaFile(file, file.getName() + "-wal");
                findAndCopySQLiteMetaFile(file, file.getName() + "-shm");
                conn = DriverManager.getConnection("jdbc:sqlite:" + localDiskPath);
            } catch (NoCurrentCaseException | TskCoreException | IOException
                    | ClassNotFoundException | SQLException ex) {
                throw new SQLiteTableReaderException(ex);
            }
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
                copyFileToTempDirectory(metaFile, sqliteFile.getId());
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
    private String copyFileToTempDirectory(AbstractFile file, long id)
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
     * Executes the query and assigns resource references to instance variables.
     *
     * @param query Input query to execute
     *
     * @throws SQLiteTableReaderException
     */
    private void openTableResources(String query) throws SQLiteTableReaderException {
        try {
            ensureOpen();
            statement = conn.prepareStatement(query);
            queryResults = statement.executeQuery();
            currentMetadata = queryResults.getMetaData();
            currentColumnCount = currentMetadata.getColumnCount();
            liveResultSet = true;
        } catch (SQLException ex) {
            throw new SQLiteTableReaderException(ex);
        }
    }

    /**
     * Ensures both the statement and the result set for a table are closed.
     */
    private void closeTableResources() {
        try {
            if (Objects.nonNull(statement)) {
                statement.close();
            }
            if (Objects.nonNull(queryResults)) {
                queryResults.close();
            }
            liveResultSet = false;
        } catch (SQLException ex) {
            //Do nothing, can't close.. tried our best.
        }
    }

    /**
     * Closes all resources attached to the database file.
     *
     * @throws SQLiteTableReaderException
     */
    @Override
    public void close() throws SQLiteTableReaderException {
        try {
            closeTableResources();
            if (Objects.nonNull(conn)) {
                conn.close();
            }
        } catch (SQLException ex) {
            throw new SQLiteTableReaderException(ex);
        }
    }

    /**
     * Provides status of the current read operation.
     *
     * @return
     */
    public boolean isFinished() {
        return !liveResultSet;
    }

    /**
     * Last ditch effort to close the connections during garbage collection.
     *
     * @throws Throwable
     */
    @Override
    public void finalize() throws Throwable {
        super.finalize();
        try {
            close();
        } catch (SQLiteTableReaderException ex) {
            //Do nothing, we tried out best to close the connection.
        }
    }
}
