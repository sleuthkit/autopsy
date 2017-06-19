/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import static org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamDb.SCHEMA_VERSION;

/**
 *
 */
public class EamDbUtil {
    /**
     * Close the prepared statement.
     *
     * @param preparedStatement
     *
     * @throws EamDbException
     */
    public static void closePreparedStatement(PreparedStatement preparedStatement) throws EamDbException {
        if (null != preparedStatement) {
            try {
                preparedStatement.close();
            } catch (SQLException ex) {
                throw new EamDbException("Error closing PreparedStatement.", ex);
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
    public static void closeResultSet(ResultSet resultSet) throws EamDbException {
        if (null != resultSet) {
            try {
                resultSet.close();
            } catch (SQLException ex) {
                throw new EamDbException("Error closing ResultSet.", ex);
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
    public static void closeConnection(Connection conn) throws EamDbException {
        if (null != conn) {
            try {
                conn.close();
            } catch (SQLException ex) {
                throw new EamDbException("Error closing Connection.", ex);
            }
        }
    }

    /**
     * Insert the default artifact types into the database.
     * 
     * @param conn An open database connection.
     * 
     * @throws EamDbException 
     */
    public static void insertDefaultArtifactTypes(Connection conn) throws EamDbException {
        PreparedStatement preparedStatement = null;
        List<EamArtifact.Type> DEFAULT_ARTIFACT_TYPES = EamArtifact.getDefaultArtifactTypes();
        String sql = "INSERT INTO artifact_types(name, supported, enabled) VALUES (?, ?, ?)";

        try {
            preparedStatement = conn.prepareStatement(sql);
            for (EamArtifact.Type newType : DEFAULT_ARTIFACT_TYPES) {
                preparedStatement.setString(1, newType.getName());
                preparedStatement.setInt(2, newType.isSupported() ? 1 : 0);
                preparedStatement.setInt(3, newType.isEnabled() ? 1 : 0);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting default correlation artifact types.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
        }
    }
 
    /**
     * Store the schema version into the db_info table.
     *
     * This should be called immediately following the database schema being
     * loaded.
     *
     * @throws EamDbException
     */
    public static void insertSchemaVersion(Connection conn) throws EamDbException {
        PreparedStatement preparedStatement = null;
        String sql = "INSERT INTO db_info (name, value) VALUES (?, ?)";
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, "SCHEMA_VERSION");
            preparedStatement.setString(2, String.valueOf(SCHEMA_VERSION));
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new EamDbException("Error adding schema version to db_info.", ex);
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
        }
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
            try {
                EamDbUtil.closeResultSet(resultSet);
            } catch (EamDbException ex) {
            }
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
            try {
                EamDbUtil.closeResultSet(resultSet);
            } catch (EamDbException ex) {
            }
        }

        return false;
    }
}
