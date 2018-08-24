/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
 *
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * Database connection class & utilities.
 */
public class SQLiteDBConnect implements AutoCloseable {

    public String sDriver = "";
    public String sUrl = null;
    public int iTimeout = 30;
    public Connection conn = null;
    public Statement statement = null;
    private static final Logger logger = Logger.getLogger(SQLiteDBConnect.class.getName());

    /*
     * Stub constructor for quick instantiation o/t fly for using some of the
     * ancillary stuff
     */
    public SQLiteDBConnect() {
    }

    /*
     * quick and dirty constructor to test the database passing the
     * DriverManager name and the fully loaded url to handle
     */
 /*
     * NB this will typically be available if you make this class concrete and
     * not abstract
     */
    public SQLiteDBConnect(String sDriverToLoad, String sUrlToLoad) throws SQLException {
        init(sDriverToLoad, sUrlToLoad);
    }

    public final void init(String sDriverVar, String sUrlVar) throws SQLException {
        setDriver(sDriverVar);
        setUrl(sUrlVar);
        setConnection();
        setStatement();
    }

    private void setDriver(String sDriverVar) {
        sDriver = sDriverVar;
    }

    private void setUrl(String sUrlVar) {
        sUrl = sUrlVar;
    }

    public void setConnection() throws SQLException {
        try {
            Class.forName(sDriver);
        } catch (ClassNotFoundException e) {

        }
        conn = DriverManager.getConnection(sUrl);
    }

    public Connection getConnection() {
        return conn;
    }

    public void setStatement() throws SQLException {
        if (conn == null) {
            setConnection();
        }
        statement = conn.createStatement();
        statement.setQueryTimeout(iTimeout);  // set timeout to 30 sec.
    }

    public Statement getStatement() {
        return statement;
    }

    public void executeStmt(String instruction) throws SQLException {
        statement.executeUpdate(instruction);
    }

    /** processes an array of instructions e.g. a set of SQL command strings
     * passed from a file
     *
     * NB you should ensure you either handle empty lines in files by either
     * removing them or parsing them out since they will generate spurious
     * SQLExceptions when they are encountered during the iteration....
     */
    public void executeStmt(String[] instructionSet) throws SQLException {
        for (int i = 0; i < instructionSet.length; i++) {
            executeStmt(instructionSet[i]);
        }
    }

    public ResultSet executeQry(String instruction) throws SQLException {
        return statement.executeQuery(instruction);
    }

    public void closeConnection() {
        try {
            conn.close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Unable to close connection to SQLite DB at " + sUrl, ex);
        }
        //Implementing Autoclosable.close() allows this class to be used in try-with-resources.
    }

    @Override
    public void close() {
        closeConnection();
    }
}
