/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2019 Basis Technology Corp.
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
 * An abstraction that loads a given SQLite driver, establishes a connection to
 * a given database, and creates a statement for the connection to support basic
 * SQL operations on the database.
 */
public class SQLiteDBConnect implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(SQLiteDBConnect.class.getName());
    private static final int STMT_EXEC_TIMEOUT_SECS = 30;

    /**
     * Constructs an abstraction that loads a given SQLite driver, establishes a
     * connection to a given database, and creates a statement for the
     * connection to support basic SQL operations on the database.
     *
     * @param driver The SQLite driver class name.
     * @param url    The SQLite database URL to which to connect.
     *
     * @throws SQLException If there is an error loading the driver,
     *                      establishing the connection, or creating a statement
     *                      for the connection.
     */
    public SQLiteDBConnect(String driver, String url) throws SQLException {
        sDriver = driver;
        sUrl = url;
        try {
            Class.forName(sDriver);
        } catch (ClassNotFoundException ex) {
            throw new SQLException(ex);
        }
        conn = DriverManager.getConnection(sUrl);
        statement = conn.createStatement();
        statement.setQueryTimeout(STMT_EXEC_TIMEOUT_SECS);
    }

    /**
     * Executes an SQL statement. For use with statements that do not return
     * result sets.
     *
     * @param sqlStatement The SQL statement to execute.
     *
     * @throws SQLException If there is an error executing the statement.
     */
    public void executeStmt(String sqlStatement) throws SQLException {
        statement.executeUpdate(sqlStatement);
    }

    /**
     * Executes one or more SQL statements in sequence. For use with statements
     * that do not return result sets.
     *
     * @param sqlStatements The SQL statements to execute.
     *
     * @throws SQLException If there is an error executing the statements.
     */
    public void executeStmt(String[] sqlStatements) throws SQLException {
        for (String stmt : sqlStatements) {
            executeStmt(stmt);
        }
    }

    /**
     * Executes an SQL query and returns a result set. The caller should close
     * the result set when finished with it, and should not make any other calls
     * on this object until finished with the result set.
     *
     * @param sqlStatement The SQL query to execute.
     *
     * @return The result set.
     *
     * @throws SQLException If there is an error executing the query.
     */
    public ResultSet executeQry(String sqlStatement) throws SQLException {
        return statement.executeQuery(sqlStatement);
    }

    /**
     * Closes the connection to the database. Should be called when the use of
     * this object is completed, unless the object was constructed in a try with
     * resources statement, in which case the closing is automatic when the
     * object goes out of scope.
     */
    public void closeConnection() {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Unable to close connection to SQLite DB at " + sUrl, ex);
        }
    }

    @Override
    public void close() {
        closeConnection();
    }

    /*
     * Partially constructs a utility object for doing basic operations on a
     * SQLite database. The object is not in a usable state. Further
     * initialization is required. See methods below.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public SQLiteDBConnect() {
    }

    /**
     * Loads a given SQLite driver, establishes a connection to a given
     * database, and creates a statement for the connection.
     *
     * @param driver The SQLite driver class name.
     * @param url    The SQLite database URL to which to connect.
     *
     * @throws SQLException If there is an error establishing the connection or
     *                      creating a statement for the connection.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public final void init(String driver, String url) throws SQLException {
        sDriver = driver;
        sUrl = url;
        closeConnection();
        setConnection();
        setStatement();
    }

    /**
     * Sets or resets the connection to the SQLite database, if the SQLite
     * driver and the database URL have been set.
     *
     * @throws SQLException If there is an error loading the driver or
     *                      establishing the connection.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public void setConnection() throws SQLException {
        if (sDriver == null || sDriver.isEmpty() || sUrl == null || sUrl.isEmpty()) {
            throw new SQLException("Driver and or databse URl not initialized");
        }
        closeConnection();
        try {
            Class.forName(sDriver);
        } catch (ClassNotFoundException ex) {
            throw new SQLException(ex);
        }
        conn = DriverManager.getConnection(sUrl);
    }

    /**
     * Gets the connection, if any, to the database.
     *
     * @return The connection to the database, may be null.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public Connection getConnection() {
        return conn;
    }

    /**
     * Creates a connection to the database if there is none, and creates a
     * statement using the connection.
     *
     * @throws SQLException If there is an error creating the connection or the
     *                      staement.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public void setStatement() throws SQLException {
        if (conn == null) {
            setConnection();
        }
        statement = conn.createStatement();
        statement.setQueryTimeout(iTimeout);
    }

    /**
     * Gets the statement, if any, associated with the connection to the
     * database, if any.
     *
     * @return The statement, may be null.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public Statement getStatement() {
        return statement;
    }

    /*
     * The lack of encapsulation of these fields is an error. Access to them
     * outside of instances of this class is deprecated.
     *
     * @deprecated Do not access.
     */
    public String sDriver = "";
    public String sUrl = null;
    public int iTimeout = STMT_EXEC_TIMEOUT_SECS;
    public Connection conn = null;
    public Statement statement = null;

}
