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

/**
 *
 */
public class EamDbUtil {
    private final static Logger LOGGER = Logger.getLogger(EamDbUtil.class.getName());
   
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
     * Insert the default artifact types into the database.
     * 
     * @param conn Open connection to use.
     * @return true on success, else false
     */
    public static boolean insertDefaultArtifactTypes(Connection conn) {
        PreparedStatement preparedStatement = null;
        List<EamArtifact.Type> DEFAULT_ARTIFACT_TYPES = EamArtifact.getCorrelationTypes();
        String sql = "INSERT INTO artifact_types(name, supported, enabled) VALUES (?, ?, ?)";

        try {
            preparedStatement = conn.prepareStatement(sql);
            for (EamArtifact.Type newType : DEFAULT_ARTIFACT_TYPES) {
                preparedStatement.setString(1, newType.getTypeName());
                preparedStatement.setInt(2, newType.isSupported() ? 1 : 0);
                preparedStatement.setInt(3, newType.isEnabled() ? 1 : 0);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error inserting default correlation artifact types.", ex); // NON-NLS
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
}
