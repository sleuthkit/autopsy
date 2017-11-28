/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import static org.sleuthkit.autopsy.centralrepository.datamodel.EamDb.CURRENT_DB_SCHEMA_VERSION;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.CaseDbSchemaVersionNumber;

/**
 *
 */
public class EamDbUtil {

    private final static Logger LOGGER = Logger.getLogger(EamDbUtil.class.getName());
    private static final String CENTRAL_REPO_NAME = "CentralRepository";
    private static final String CENTRAL_REPO_USE_KEY = "db.useCentralRepo";

    /**
     * Close the prepared statement.
     *
     * @param preparedStatement
     *
     * @throws EamDbException
     */
    public static void closePreparedStatement(PreparedStatement preparedStatement) {
        if (null != preparedStatement) {
            try {
                preparedStatement.close();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error closing PreparedStatement.", ex);
            }
        }
    }

    /**
     * Close the resultSet.
     *
     * @param resultSet
     *
     * @throws EamDbException
     */
    public static void closeResultSet(ResultSet resultSet) {
        if (null != resultSet) {
            try {
                resultSet.close();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error closing ResultSet.", ex);
            }
        }
    }

    /**
     * Close the in-use connection and return it to the pool.
     *
     * @param conn An open connection
     *
     * @throws EamDbException
     */
    public static void closeConnection(Connection conn) {
        if (null != conn) {
            try {
                conn.close();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error closing Connection.", ex);
            }
        }
    }

    /**
     * Insert the default correlation types into the database.
     *
     * @param conn Open connection to use.
     *
     * @return true on success, else false
     */
    public static boolean insertDefaultCorrelationTypes(Connection conn) {
        PreparedStatement preparedStatement = null;
        String sql = "INSERT INTO correlation_types(id, display_name, db_table_name, supported, enabled) VALUES (?, ?, ?, ?, ?)";

        try {
            List<CorrelationAttribute.Type> DEFAULT_CORRELATION_TYPES = CorrelationAttribute.getDefaultCorrelationTypes();
            preparedStatement = conn.prepareStatement(sql);
            for (CorrelationAttribute.Type newType : DEFAULT_CORRELATION_TYPES) {
                preparedStatement.setInt(1, newType.getId());
                preparedStatement.setString(2, newType.getDisplayName());
                preparedStatement.setString(3, newType.getDbTableName());
                preparedStatement.setInt(4, newType.isSupported() ? 1 : 0);
                preparedStatement.setInt(5, newType.isEnabled() ? 1 : 0);

                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        } catch (EamDbException | SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error inserting default correlation types.", ex); // NON-NLS
            return false;
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
        }
        return true;
    }

    /**
     * Store the schema version into the db_info table.
     *
     * This should be called immediately following the database schema being
     * loaded.
     *
     * @param conn Open connection to use.
     *
     * @return true on success, else false
     */
    /*
    public static boolean insertSchemaVersion(Connection conn) {
        PreparedStatement preparedStatement = null;
        String sql = "INSERT INTO db_info (name, value) VALUES (?, ?)";
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, "SCHEMA_VERSION");
            preparedStatement.setString(2, String.valueOf(SCHEMA_VERSION));
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error adding schema version to db_info.", ex);
            return false;
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
        }
        return true;
    }*/
    
    static boolean updateSchemaVersion(Connection conn){
     
        Statement statement;
        ResultSet resultSet;
        //PreparedStatement preparedStatement = null;
        String sql = "INSERT INTO db_info (name, value) VALUES (?, ?)";
        try {
            statement = conn.createStatement();
            resultSet = statement.executeQuery("SELECT id FROM db_info WHERE name='SCHEMA_VERSION'");
            if(resultSet.next()){
                int id = resultSet.getInt("id");
                statement.execute("UPDATE db_info SET value=" + CURRENT_DB_SCHEMA_VERSION.getMajor() + " WHERE id=" + id);
            } else {
                statement.execute("INSERT INTO db_info (name, value) VALUES (SCHEMA_VERSION, " + CURRENT_DB_SCHEMA_VERSION.getMajor() + ")");
            }
            
            resultSet = statement.executeQuery("SELECT id FROM db_info WHERE name='SCHEMA_MINOR_VERSION'");
            if(resultSet.next()){
                int id = resultSet.getInt("id");
                statement.execute("UPDATE db_info SET value=" + CURRENT_DB_SCHEMA_VERSION.getMinor() + " WHERE id=" + id);
            } else {
                statement.execute("INSERT INTO db_info (name, value) VALUES (SCHEMA_MINOR_VERSION, " + CURRENT_DB_SCHEMA_VERSION.getMinor() + ")");
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error adding schema version to db_info.", ex);
            return false;
        } finally {
        }
        
        return true;
    }

    /**
     * Query to see if the SCHEMA_VERSION is set in the db.
     *
     * @return true if set, else false.
     */
    public static boolean schemaVersionIsSet(Connection conn) {
        if (null == conn) {
            return false;
        }

        ResultSet resultSet = null;
        try {
            Statement tester = conn.createStatement();
            String sql = "SELECT value FROM db_info WHERE name='SCHEMA_VERSION'";
            resultSet = tester.executeQuery(sql);
            if (resultSet.next()) {
                String value = resultSet.getString("value");
            }
        } catch (SQLException ex) {
            return false;
        } finally {
            EamDbUtil.closeResultSet(resultSet);
        }
        return true;
    }
    
    static void updateSchema(Connection conn){
        if (null == conn) {
            // Add exception
            return;
        }

        ResultSet resultSet = null;
        Statement statement;
        try {
            
            statement = conn.createStatement();
            
            int minorVersion = 0;
            int majorVersion = 0;
            resultSet = statement.executeQuery("SELECT value FROM db_info WHERE name='SCHEMA_MINOR_VERSION'");
            if(resultSet.next()){
                String minorVersionStr = resultSet.getString("value");
                try{
                    minorVersion = Integer.parseInt(minorVersionStr);
                } catch (NumberFormatException ex){
                    ex.printStackTrace();
                }
            }
            
            resultSet = statement.executeQuery("SELECT value FROM db_info WHERE name='SCHEMA_VERSION'");
            if(resultSet.next()){
                String majorVersionStr = resultSet.getString("value");
                try{
                    majorVersion = Integer.parseInt(majorVersionStr);
                } catch (NumberFormatException ex){
                    ex.printStackTrace();
                }
            }
            
            System.out.println("Current schema version: " + majorVersion + "." + minorVersion);
            CaseDbSchemaVersionNumber dbSchemaVersion = new CaseDbSchemaVersionNumber(majorVersion, minorVersion);
            
            if(dbSchemaVersion.compareTo(new CaseDbSchemaVersionNumber(1, 1)) < 0){
                statement.execute("ALTER TABLE reference_sets ADD COLUMN known_status INTEGER;"); //NON-NLS
                statement.execute("ALTER TABLE reference_sets ADD COLUMN read_only BOOLEAN;"); //NON-NLS
                statement.execute("ALTER TABLE reference_sets ADD COLUMN type INTEGER;"); //NON-NLS
            }
            
            updateSchemaVersion(conn);            
        } catch (SQLException ex) {
            ex.printStackTrace();
        } finally {
            EamDbUtil.closeResultSet(resultSet);
        }
    }

    /**
     * If the Central Repos use has been enabled.
     *
     * @return true if the Central Repo may be configured, false if it should
     *         not be able to be
     */
    public static boolean useCentralRepo() {
        return Boolean.parseBoolean(ModuleSettings.getConfigSetting(CENTRAL_REPO_NAME, CENTRAL_REPO_USE_KEY));
    }

    /**
     * Saves the setting for whether the Central Repo should be able to be
     * configured.
     *
     * @param centralRepoCheckBoxIsSelected - true if the central repo can be
     *                                      used
     */
    public static void setUseCentralRepo(boolean centralRepoCheckBoxIsSelected) {
        ModuleSettings.setConfigSetting(CENTRAL_REPO_NAME, CENTRAL_REPO_USE_KEY, Boolean.toString(centralRepoCheckBoxIsSelected));
    }

    /**
     * Use the current settings and the validation query to test the connection
     * to the database.
     *
     * @return true if successfull query execution, else false.
     */
    public static boolean executeValidationQuery(Connection conn, String validationQuery) {
        if (null == conn) {
            return false;
        }

        ResultSet resultSet = null;
        try {
            Statement tester = conn.createStatement();
            resultSet = tester.executeQuery(validationQuery);
            if (resultSet.next()) {
                return true;
            }
        } catch (SQLException ex) {
            return false;
        } finally {
            EamDbUtil.closeResultSet(resultSet);
        }

        return false;
    }

    /**
     * Conver thte Type's DbTableName string to the *_instances table name.
     *
     * @param type Correlation Type
     *
     * @return Instance table name for this Type.
     */
    public static String correlationTypeToInstanceTableName(CorrelationAttribute.Type type) {
        return type.getDbTableName() + "_instances";
    }

    /**
     * Convert the Type's DbTableName string to the reference_* table name.
     *
     * @param type Correlation Type
     *
     * @return Reference table name for this Type.
     */
    public static String correlationTypeToReferenceTableName(CorrelationAttribute.Type type) {
        return "reference_" + type.getDbTableName();
    }

}
