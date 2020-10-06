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
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Reads through SQLite tables row by row. Functions performed on the 
 * data must be declared up front to the Builder. For example:
 * 
 * tableReader = new SQLiteTableReader.Builder(file).forAllColumnNames(System.out::println);
 * tableReader.read("Sample Table X");
 * 
 * By declaring the functions up front, the SQLiteTableReader instance can stream the 
 * table contents in the most memory efficient manner. 
 */
public class SQLiteTableReader implements AutoCloseable {

    /**
     * Builder patten for configuring SQLiteTableReader instances.
     */
    public static class Builder {

        private final AbstractFile file;

        private Consumer<String> forAllColumnNamesConsumer;
        private Consumer<String> forAllStringValuesConsumer;
        private Consumer<Long> forAllLongValuesConsumer;
        private Consumer<Integer> forAllIntegerValuesConsumer;
        private Consumer<Double> forAllFloatValuesConsumer;
        private Consumer<byte[]> forAllBlobValuesConsumer;
        private Consumer<Object> forAllTableValuesConsumer;
        
        static <T> Consumer<T> doNothing() {
            return NOOP -> {};
        }

        /**
         * Creates a Builder for this abstract file.
         *
         * @param file
         */
        public Builder(AbstractFile file) {
            this.file = file;

            this.forAllColumnNamesConsumer = Builder.doNothing();
            this.forAllStringValuesConsumer = Builder.doNothing();
            this.forAllLongValuesConsumer = Builder.doNothing();
            this.forAllIntegerValuesConsumer = Builder.doNothing();
            this.forAllFloatValuesConsumer = Builder.doNothing();
            this.forAllBlobValuesConsumer = Builder.doNothing();
            this.forAllTableValuesConsumer = Builder.doNothing();
        }

        /**
         * Specify a function to do on column names. Column names will be read
         * from left to right.
         *
         * @param action Consumer of column name strings
         *
         * @return Builder reference
         */
        public Builder forAllColumnNames(Consumer<String> action) {
            this.forAllColumnNamesConsumer = action;
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
        public Builder forAllStringValues(Consumer<String> action) {
            this.forAllStringValuesConsumer = action;
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
        public Builder forAllIntegerValues(Consumer<Integer> action) {
            this.forAllIntegerValuesConsumer = action;
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
        public Builder forAllFloatValues(Consumer<Double> action) {
            this.forAllFloatValuesConsumer = action;
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
        public Builder forAllLongValues(Consumer<Long> action) {
            this.forAllLongValuesConsumer = action;
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
        public Builder forAllBlobValues(Consumer<byte[]> action) {
            this.forAllBlobValuesConsumer = action;
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
        public Builder forAllTableValues(Consumer<Object> action) {
            this.forAllTableValuesConsumer = action;
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
    private final Builder builder;
  
    private static final String SELECT_ALL_QUERY = "SELECT * FROM \"%s\"";
    private static final Logger logger = Logger.getLogger(SQLiteTableReader.class.getName());

    private Connection conn;
    private PreparedStatement statement;
    private ResultSet queryResults;
    private ResultSetMetaData currentMetadata;

    //Iteration state
    private int currRowColumnIndex;
    private int columnNameIndex;
    private int totalColumnCount;
    private boolean unfinishedRow;
    private boolean liveResultSet;
    private String prevTableName;

    /**
     * Holds reference to the builder instance so that we can use its actions
     * during iteration.
     */
    private SQLiteTableReader(Builder builder) {
        this.builder = builder;
        this.file = builder.file;
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
                .executeQuery(String.format(SELECT_ALL_QUERY, tableName))) {
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
        readHelper(String.format(SELECT_ALL_QUERY, tableName), () -> false);
    }

    /**
     * Reads column names and values from the table. Only actions that were
     * configured in the Builder will be invoked during iteration. Iteration
     * will stop when the table read has completed or an exception was
     * encountered.
     *
     * @param tableName Source table to perform a read
     * @param limit     Number of rows to read from the table
     * @param offset    Starting row to read from in the table
     *
     * @throws SQLiteTableReaderException
     *
     */
    public void read(String tableName, int limit, int offset) throws SQLiteTableReaderException {
        readHelper(String.format(SELECT_ALL_QUERY, tableName) + " LIMIT " + limit
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
        if (Objects.isNull(prevTableName) || !prevTableName.equals(tableName)) {
            prevTableName = tableName;
            closeTableResources();
        }
        readHelper(String.format(SELECT_ALL_QUERY, tableName), condition);
    }

    /**
     * Performs the result set iteration and is responsible for maintaining
     * state of the read over multiple invocations.
     *
     * @throws SQLiteTableReaderException
     */
    private void readHelper(String query, BooleanSupplier condition) throws SQLiteTableReaderException {
        try {
            if (!liveResultSet) {
                openTableResources(query);
                columnNameIndex = 0;
            }

            //Process column names before reading the database table values
            while (columnNameIndex < totalColumnCount) {
                if (condition.getAsBoolean()) {
                    return;
                }
                builder.forAllColumnNamesConsumer.accept(currentMetadata
                        .getColumnName(++columnNameIndex));
            }

            while (unfinishedRow || queryResults.next()) {
                while (currRowColumnIndex < totalColumnCount) {
                    if (condition.getAsBoolean()) {
                        unfinishedRow = true;
                        return;
                    }

                    Object item = queryResults.getObject(++currRowColumnIndex);
                    if (item instanceof String) {
                        builder.forAllStringValuesConsumer.accept((String) item);
                    } else if (item instanceof Integer) {
                        builder.forAllIntegerValuesConsumer.accept((Integer) item);
                    } else if (item instanceof Double) {
                        builder.forAllFloatValuesConsumer.accept((Double) item);
                    } else if (item instanceof Long) {
                        builder.forAllLongValuesConsumer.accept((Long) item);
                    } else if (item instanceof byte[]) {
                        builder.forAllBlobValuesConsumer.accept((byte[]) item);
                    }

                    builder.forAllTableValuesConsumer.accept(item);
                }
                unfinishedRow = false;
                //Wrap column index back around if we've reached the end of the row
                currRowColumnIndex = currRowColumnIndex % totalColumnCount;
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
                copyFileToTempDirectory(metaFile, sqliteFile.getId());
            }
        }
    }

    /**
     * Copies the file contents into a unique path in the current case temp
     * directory.
     *
     * @param file AbstractFile from the data source
     * @param fileId   The input files id value
     *
     * @return The path of the file on disk
     *
     * @throws IOException            Exception writing file contents
     * @throws NoCurrentCaseException Current case closed during file copying
     */
    private String copyFileToTempDirectory(AbstractFile file, long fileId)
            throws IOException, NoCurrentCaseException {

        String localDiskPath = Case.getCurrentCaseThrows().getTempDirectory()
                + File.separator + fileId + file.getName();
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
            totalColumnCount = currentMetadata.getColumnCount();
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
            logger.log(Level.SEVERE, "Failed to close table resources", ex);
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
    protected void finalize() throws Throwable {
        try {
            close();
        } catch (SQLiteTableReaderException ex) {
            logger.log(Level.SEVERE, "Failed to close reader in finalizer", ex);
        }
        super.finalize();
    }
}
