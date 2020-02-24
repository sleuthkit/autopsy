/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.datamodel;

/**
 *
 * @author gregd
 */
public interface CentralRepoSettings {

    boolean createDatabase();

    boolean deleteDatabase();

    /**
     * Use the current settings and the validation query to test the connection
     * to the database.
     *
     * @return true if successfull connection, else false.
     */
    boolean verifyConnection();

    /**
     * Check to see if the database exists.
     *
     * @return true if exists, else false
     */
    boolean verifyDatabaseExists();

    /**
     * Use the current settings and the schema version query to test the
     * database schema.
     *
     * @return true if successful connection, else false.
     */
    boolean verifyDatabaseSchema();
    
}
