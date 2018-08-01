/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.textextraction;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle;

/**
 * Abstracts the need to open (and close) statements and result sets from the
 * JDBC. Classes using the SQLiteExtractor must maintain a reference to the 
 * Connection object generated in getDatabaseConnection.
 */
public final class SQLiteExtractor {
    
    private SQLiteExtractor(){}

    public static Connection getDatabaseConnection(String dbPath) 
            throws ClassNotFoundException, SQLException {
        
        // Load the SQLite JDBC driver, if necessary.
        Class.forName("org.sqlite.JDBC"); //NON-NLS  
        return DriverManager.getConnection(
                "jdbc:sqlite:" + dbPath); //NON-NLS
    }
    
     /**
     * Gets the table names and schemas from the SQLite database file.
     *
     * @return A mapping of table names to SQL CREATE TABLE statements.
     */
    public static Map<String, String> getTableNameAndSchemaPairs(Connection connection)
            throws SQLException {
        
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
        }
        
        return dbTablesMap;
    }
    
    public static Integer getTableRowCount(Connection connection, 
            String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT count (*) as count FROM " + tableName)){
            return resultSet.getInt("count");
        }
    }
    
    public static List<Map<String, Object>> getRowsFromTable(Connection connection, 
            String tableName) throws SQLException {
        try(Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT * FROM " + tableName)) {
            return resultSetToList(resultSet);
        }
    }
    
    @NbBundle.Messages({"# {0} - tableName",
        "SQLiteExtractor.readTable.errorText=Error getting rows for table: {0}"})
    public static List<Map<String, Object>> getRowsFromTable(Connection connection, String tableName, 
            int startRow, int numRowsToRead) throws SQLException{
        
        try(Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT * FROM " + tableName
                        + " LIMIT " + Integer.toString(numRowsToRead)
                        + " OFFSET " + Integer.toString(startRow - 1))) {
            return resultSetToList(resultSet);
        }
    }
    
    @NbBundle.Messages({"SQLiteExtractor.exportTableToCsv.write.errText=Failed to export table content to csv file.",
                        "SQLiteExtractor.exportTableToCsv.FileName=File name: ",
                        "SQLiteExtractor.exportTableToCsv.TableName=Table name: "
    })
    public static void exportTableToCSV(File file, String tableName, 
            List<Map<String, Object>> keyValuePairsInRows) throws FileNotFoundException, IOException{
        
        File csvFile;
        String fileName = file.getName();
        if (FilenameUtils.getExtension(fileName).equalsIgnoreCase("csv")) {
            csvFile = file;
        } else {
            csvFile = new File(file.toString() + ".csv");
        }

        try (FileOutputStream out = new FileOutputStream(csvFile, false)) {

            out.write((Bundle.SQLiteExtractor_exportTableToCsv_FileName() + csvFile.getName() + "\n").getBytes());
            out.write((Bundle.SQLiteExtractor_exportTableToCsv_TableName() + tableName + "\n").getBytes());
            
            String header = createColumnHeader(keyValuePairsInRows.get(0)).concat("\n");
            out.write(header.getBytes());

            for (Map<String, Object> maps : keyValuePairsInRows) {
                String row = maps.values()
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","))
                        .concat("\n");
                out.write(row.getBytes());
            }
        }
    }
    
    public static void exportTableToCSV(File file, String tableName, 
            ResultSet resultSet) throws SQLException, IOException {
        exportTableToCSV(file, tableName, resultSetToList(resultSet));
    }
    
    private static String createColumnHeader(Map<String, Object> row) {
        
        return row.entrySet()
                .stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
    }
    
    /**
     * The map holds the column name to value pairs for a particular row. For example, (id, 123)
     * is a valid key-value pair in the map. 
     */
    @NbBundle.Messages("SQLiteExtractor.BlobNotShown.message=BLOB Data not shown")
    private static List<Map<String, Object>> resultSetToList(ResultSet resultSet) throws SQLException {
        
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columns = metaData.getColumnCount();
        List<Map<String, Object>> keyValuePairsInRows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>(columns);
            for (int i = 1; i <= columns; ++i) {
                if (resultSet.getObject(i) == null) {
                    row.put(metaData.getColumnName(i), "");
                } else {
                    if (metaData.getColumnTypeName(i).compareToIgnoreCase("blob") == 0) {
                        row.put(metaData.getColumnName(i), Bundle.SQLiteExtractor_BlobNotShown_message());
                    } else {
                        row.put(metaData.getColumnName(i), resultSet.getObject(i));
                    }
                }
            }
            keyValuePairsInRows.add(row);
        }

        return keyValuePairsInRows;
    }
}
