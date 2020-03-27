/*
 * Central Repository
 *
 * Copyright 2015-2020 Basis Technology Corp.
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

/**
 * This class is a common interface for settings pertaining to the database in central repository.
 */
public interface CentralRepoDbConnectivityManager {
    /**
     * This method loads the current settings for this connection.
     */
    void loadSettings();
    
    /**
     * This method saves the altered settings to disk.
     */
    void saveSettings();
        
    /**
     * This method will create a central repository database if necessary.
     * @return      Whether or not the operation was successful.
     */
    boolean createDatabase();

    /**
     * This method deletes a central repository database (used for deleting a corrupted database).
     * @return      Whether or not the operation was successful.
     */
    boolean deleteDatabase();

    /**
     * This method uses the current settings and the validation query to test the connection
     * to the database.
     *
     * @return True if successfull connection, else false.
     */
    boolean verifyConnection();

    /**
     * This method checks to see if the database exists.
     *
     * @return True if exists, else false.
     */
    boolean verifyDatabaseExists();

    /**
     * This method is uses the current settings and the schema version query to test the
     * database schema.
     *
     * @return True if successful connection, else false.
     */
    boolean verifyDatabaseSchema();
    
    /**
     * This method tests the connectivity status of this connection and returns the testing result.
     * @return      The result of testing the database connectivity status.
     */
    DatabaseTestResult testStatus();
    
}
