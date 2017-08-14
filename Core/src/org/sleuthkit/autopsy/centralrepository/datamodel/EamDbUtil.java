/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import static org.sleuthkit.autopsy.centralrepository.datamodel.EamDb.SCHEMA_VERSION;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 *
 */
public class EamDbUtil {
    private final static Logger LOGGER = Logger.getLogger(EamDbUtil.class.getName());
    private static final String CENTRAL_REPO_NAME= "CentralRepository";
    private static final String CENTRAL_REPO_USE_KEY="db.useCentralRepo";
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
     * @return true on success, else false
     */
    public static boolean insertDefaultCorrelationTypes(Connection conn) {
        PreparedStatement preparedStatement = null;
        String sql = "INSERT INTO correlation_types(id, display_name, db_table_name, supported, enabled) VALUES (?, ?, ?, ?, ?)";

        try {
            List<EamArtifact.Type> DEFAULT_CORRELATION_TYPES = EamArtifact.getDefaultCorrelationTypes();
            preparedStatement = conn.prepareStatement(sql);
            for (EamArtifact.Type newType : DEFAULT_CORRELATION_TYPES) {
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
     * @return true on success, else false
     */
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
    
    /**
     * If the Central Repos use has been enabled.
     * 
     * @return true if the Central Repo may be configured, false if it should not be able to be
     */
    public static boolean useCentralRepo(){
        return Boolean.parseBoolean(ModuleSettings.getConfigSetting(CENTRAL_REPO_NAME, CENTRAL_REPO_USE_KEY));
    }
    
    /**
     *  Saves the setting for whether the Central Repo should be able to be configured.
     * 
     * @param centralRepoCheckBoxIsSelected - true if the central repo can be used
     */
    public static void setUseCentralRepo(boolean centralRepoCheckBoxIsSelected){
        ModuleSettings.setConfigSetting(CENTRAL_REPO_NAME, CENTRAL_REPO_USE_KEY, Boolean.toString(centralRepoCheckBoxIsSelected));
    }
    
   /**
     * Use the current settings and the validation query 
     * to test the connection to the database.
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
     * @return  Instance table name for this Type.
     */
    public static String correlationTypeToInstanceTableName(EamArtifact.Type type) {
        return type.getDbTableName() + "_instances";
    }
    
    /**
     * Convert the Type's DbTableName string to the reference_* table name.
     * 
     * @param type Correlation Type
     * @return Reference table name for this Type.
     */
    public static String correlationTypeToReferenceTableName(EamArtifact.Type type) {
        return "reference_" + type.getDbTableName();
    }

}
