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
package org.sleuthkit.autopsy.tabulardatareader;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Reads sqlite databases and returns results in a list collection.
 */
@NbBundle.Messages({
    "SQLiteReader.ReadSQLiteFiles.moduleName=SQLiteReader"
})
public class SQLiteReader extends AbstractReader {
    
    private final Connection connection;
    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(Bundle.SQLiteReader_ReadSQLiteFiles_moduleName());
    
    /**
     * Writes data source file contents to local disk and opens a sqlite JDBC
     * connection. 
     * 
     * @param sqliteDbFile Data source abstract file
     * @param localDiskPath Location for database contents to be copied to
     * @throws org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderInitException
     */
    public SQLiteReader(AbstractFile sqliteDbFile, String localDiskPath) throws FileReaderInitException {
        super(sqliteDbFile, localDiskPath);
        try {
            // Look for any meta files associated with this DB - WAL, SHM, etc. 
            findAndCopySQLiteMetaFile(sqliteDbFile, sqliteDbFile.getName() + "-wal");
            findAndCopySQLiteMetaFile(sqliteDbFile, sqliteDbFile.getName() + "-shm");

            connection = getDatabaseConnection(localDiskPath);
        } catch (ClassNotFoundException | SQLException |IOException | 
                NoCurrentCaseException | TskCoreException ex) {
            throw new FileReaderInitException(ex);
        }
    }
    
    /**
     * Searches for a meta file associated with the give SQLite database. If found,
     * copies the file to the local disk folder
     * 
     * @param file file being processed
     * @param metaFileName name of meta file to look for
     * @throws NoCurrentCaseException Case has been closed.
     * @throws TskCoreException fileManager cannot find AbstractFile files.
     * @throws IOException Issue during writing to file.
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
                String tmpMetafilePathName = openCase.getTempDirectory() + 
                        File.separator + metaFile.getName();
                File tmpMetafile = new File(tmpMetafilePathName);
                ContentUtils.writeToFile(metaFile, tmpMetafile);
            }
        }
    }
    
    /**
     * Opens a JDBC connection to the sqlite database specified by the path
     * parameter.
     * 
     * @param databasePath Local path of sqlite database
     * @return Connection JDBC connection, to be maintained and closed by the reader
     * @throws ClassNotFoundException missing SQLite JDBC class
     * @throws SQLException Exception during opening database connection
     */
    private Connection getDatabaseConnection(String databasePath) 
            throws ClassNotFoundException, SQLException {
        
        // Load the SQLite JDBC driver, if necessary.
        Class.forName("org.sqlite.JDBC"); //NON-NLS  
        return DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath); //NON-NLS
    }
    
    
    /**
     * Retrieves a map view of table names to table schemas (in the form of
     * CREATE TABLE statments).
     * 
     * @return A map of table names to table schemas
     * @throws org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderException
     */
    @Override
    public Map<String, String> getTableSchemas() throws FileReaderException {
        
        Map<String, String> dbTablesMap = new TreeMap<>();
        
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                    "SELECT name, sql FROM sqlite_master " //NON-NLS
                    + " WHERE type= 'table' " //NON-NLS
                    + " ORDER BY name;")){ //NON-NLS
            
                while (resultSet.next()) {
                    String tableName = resultSet.getString("name"); //NON-NLS
                    String tableSQL = resultSet.getString("sql"); //NON-NLS
                    dbTablesMap.put(tableName, tableSQL);
                }
                
        } catch (SQLException ex) {
            throw new FileReaderException(ex);
        }
        
        return dbTablesMap;
    }
    
    /**
     * Retrieves the total number of rows from a table in the SQLite database.
     * 
     * @param tableName
     * @return Row count from tableName
     * @throws org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderException
     */
    @Override
    public Integer getRowCountFromTable(String tableName) 
            throws FileReaderException {
        tableName = wrapTableNameStringWithQuotes(tableName);
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT count (*) as count FROM " + tableName)){ //NON-NLS
            return resultSet.getInt("count"); //NON-NLS
        } catch (SQLException ex) {
            throw new FileReaderException(ex);
        }
    }
    
    /**
     * Retrieves all rows from a given table in the SQLite database. If only a 
     * subset of rows are desired, see the overloaded function below.
     * 
     * @param tableName
     * @return List of rows, where each row is 
     * represented as a column-value map.
     * @throws org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderException
     */
    @Override
    public List<Map<String, Object>> getRowsFromTable(String tableName) 
            throws FileReaderException {
        //This method does not directly call its overloaded counterpart 
        //since the second parameter would need to be retreived from a call to
        //getTableRowCount().
        tableName = wrapTableNameStringWithQuotes(tableName);
        try(Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT * FROM " + tableName)) { //NON-NLS
            return resultSetToList(resultSet);
        } catch (SQLException ex) {
            throw new FileReaderException(ex);
        }
    }
    
    /**
     * Retrieves a subset of the rows from a given table in the SQLite database.
     * 
     * @param tableName
     * @param startRow Desired start index (rows begin at 1)
     * @param numRowsToRead Number of rows past the start index
     * @return List of rows, where each row is 
     * represented as a column-value map.
     * @throws org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderException
     */
    public List<Map<String, Object>> getRowsFromTable(String tableName, 
            int startRow, int numRowsToRead) throws FileReaderException{
        tableName = wrapTableNameStringWithQuotes(tableName);
        try(Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT * FROM " + tableName    //NON-NLS
                        + " LIMIT " + Integer.toString(numRowsToRead) //NON-NLS
                        + " OFFSET " + Integer.toString(startRow - 1))) { //NON-NLS
            return resultSetToList(resultSet);
        } catch (SQLException ex) {
            throw new FileReaderException(ex);
        }
    }
    
    /**
     * Wraps table name with quotation marks in case table name contains spaces. 
     * sqliteJDBC cannot read table names with spaces in them unless surrounded 
     * by quotation marks.
     * 
     * @param tableName
     * @return Input name: Result Table -> "Result Table"
     */
    private String wrapTableNameStringWithQuotes(String tableName) {
        return "\"" + tableName +"\"";
    }
    
    /**
     * Converts a ResultSet (row results from a table read) into a list. 
     * 
     * @param resultSet row results from a table read
     * @return List of rows, where each row is 
     * represented as a column-value map.
     * @throws SQLException occurs if ResultSet is closed while attempting to 
     * access it's data.
     */
    @NbBundle.Messages("SQLiteReader.BlobNotShown.message=BLOB Data not shown")
    private List<Map<String, Object>> resultSetToList(ResultSet resultSet) throws SQLException {
        
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columns = metaData.getColumnCount();
        List<Map<String, Object>> rowMap = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>(columns);
            for (int i = 1; i <= columns; ++i) {
                if (resultSet.getObject(i) == null) {
                    row.put(metaData.getColumnName(i), "");
                } else {
                    if (metaData.getColumnTypeName(i).compareToIgnoreCase("blob") == 0) {
                        row.put(metaData.getColumnName(i), Bundle.SQLiteReader_BlobNotShown_message());
                    } else {
                        row.put(metaData.getColumnName(i), resultSet.getObject(i));
                    }
                }
            }
            rowMap.add(row);
        }

        return rowMap;
    }

    
    /**
     * Closes underlying JDBC connection. 
     */
    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ex) {
            //Non-essential exception, user has no need for the connection 
            //object at this stage so closing details are not important
            logger.log(Level.WARNING, "Could not close JDBC connection", ex);
        }
    }
}
