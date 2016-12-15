/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.experimental.configuration.AutoIngestUserPreferences;

/**
 * Write auto-ingest status updates to a database.
 */
public class StatusDatabaseLogger {
    /**
     * Log the current status to the database using the database
     * parameters saved in AutoIngestUserPreferences.
     * @param message  Current status message
     * @param isError  true if we're in an error state, false otherwise
     * @throws SQLException
     */
    public static void logToStatusDatabase(String message, boolean isError) throws SQLException, UserPreferencesException{
        
        try{
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex){
            java.util.logging.Logger SYS_LOGGER = AutoIngestSystemLogger.getLogger();
            SYS_LOGGER.log(Level.WARNING, "Error loading postgresql driver", ex);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" 
                + AutoIngestUserPreferences.getLoggingDatabaseHostnameOrIP()
                + ":" + AutoIngestUserPreferences.getLoggingPort()
                + "/" + AutoIngestUserPreferences.getLoggingDatabaseName(),
                AutoIngestUserPreferences.getLoggingUsername(), 
                AutoIngestUserPreferences.getLoggingPassword());
                Statement statement = connection.createStatement();) {

            logToStatusDatabase(statement, message, isError);
        }
    }
    
    /**
     * Log the current status to the database using an already 
     * configured Statement.
     * @param statement SQL statement (must have already been created)
     * @param message   Current status message
     * @param isError   true if we're in an error state, false otherwise
     * @throws SQLException 
     */
    public static void logToStatusDatabase(Statement statement, String message, boolean isError) throws SQLException{
        if((statement == null) || statement.isClosed()){
            throw new SQLException("SQL Statement is null/closed");
        }
        
        int status;
        if(isError){
            status = 1;
        } else {
            status = 0;
        }
        String timestamp = new java.text.SimpleDate‌​Format("yyyy-MM-dd HH:mm:ss").format(ne‌​w java.util.Date());

        String checkForPreviousEntry = "SELECT * FROM statusUpdates WHERE tool='" + UserPreferences.getAppName() + "' AND " +
                "node='" + NetworkUtils.getLocalHostName() + "'";

        ResultSet resultSet = statement.executeQuery(checkForPreviousEntry);
        String logMessage;
        if(resultSet.next()){
            logMessage = "UPDATE statusUpdates SET reportTime='" + timestamp + 
                    "', message='" + message + "', status=" + status 
                    + " WHERE tool='" + UserPreferences.getAppName() + "' AND node='" + NetworkUtils.getLocalHostName() + "'";
        } else {
            logMessage = "INSERT INTO statusUpdates (tool, node, reportTime, message, status) " + 
                "VALUES ('" + UserPreferences.getAppName()
                + "', '" + NetworkUtils.getLocalHostName() + 
                "', '" + 
                timestamp + "', '" + message + "', '" + status + "')";

        }
        statement.execute(logMessage);        
    }

}
