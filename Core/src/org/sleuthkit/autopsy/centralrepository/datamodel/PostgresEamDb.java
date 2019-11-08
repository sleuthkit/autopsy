/*
 * Central Repository
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.commons.dbcp2.BasicDataSource;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Central Repository database implementation using Postgres as a backend
 */
final class PostgresEamDb extends AbstractSqlEamDb {

    private final static Logger LOGGER = Logger.getLogger(PostgresEamDb.class.getName());

    private final static String CONFLICT_CLAUSE = "ON CONFLICT DO NOTHING";

    private static PostgresEamDb instance;

    private static final int CONN_POOL_SIZE = 10;
    private BasicDataSource connectionPool = null;

    private final PostgresEamDbSettings dbSettings;

    /**
     * Get the singleton instance of PostgresEamDb
     *
     * @return the singleton instance of PostgresEamDb
     *
     * @throws EamDbException if one or more default correlation type(s) have an
     *                        invalid db table name.
     */
    public synchronized static PostgresEamDb getInstance() throws EamDbException {
        if (instance == null) {
            instance = new PostgresEamDb();
        }

        return instance;
    }

    /**
     *
     * @throws EamDbException if the AbstractSqlEamDb class has one or more
     *                        default correlation type(s) having an invalid db
     *                        table name.
     */
    private PostgresEamDb() throws EamDbException {
        dbSettings = new PostgresEamDbSettings();
        bulkArtifactsThreshold = dbSettings.getBulkThreshold();
    }

    @Override
    public void shutdownConnections() throws EamDbException {
        try {
            synchronized (this) {
                if (connectionPool != null) {
                    connectionPool.close();
                    connectionPool = null; // force it to be re-created on next connect()
                }
                clearCaches();
            }
        } catch (SQLException ex) {
            throw new EamDbException("Failed to close existing database connections.", ex); // NON-NLS
        }
    }

    @Override
    public void updateSettings() {
        synchronized (this) {
            dbSettings.loadSettings();
            bulkArtifactsThreshold = dbSettings.getBulkThreshold();
        }
    }

    @Override
    public void saveSettings() {
        synchronized (this) {
            dbSettings.saveSettings();
        }
    }

    @Override
    public void reset() throws EamDbException {
        Connection conn = connect();

        try {
            Statement dropContent = conn.createStatement();
            dropContent.executeUpdate("TRUNCATE TABLE organizations RESTART IDENTITY CASCADE");
            dropContent.executeUpdate("TRUNCATE TABLE cases RESTART IDENTITY CASCADE");
            dropContent.executeUpdate("TRUNCATE TABLE data_sources RESTART IDENTITY CASCADE");
            dropContent.executeUpdate("TRUNCATE TABLE reference_sets RESTART IDENTITY CASCADE");
            dropContent.executeUpdate("TRUNCATE TABLE correlation_types RESTART IDENTITY CASCADE");
            dropContent.executeUpdate("TRUNCATE TABLE db_info RESTART IDENTITY CASCADE");

            String instancesTemplate = "TRUNCATE TABLE %s_instances RESTART IDENTITY CASCADE";
            String referencesTemplate = "TRUNCATE TABLE reference_%s RESTART IDENTITY CASCADE";
            for (CorrelationAttributeInstance.Type type : defaultCorrelationTypes) {
                dropContent.executeUpdate(String.format(instancesTemplate, type.getDbTableName()));
                // FUTURE: support other reference types
                if (type.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                    dropContent.executeUpdate(String.format(referencesTemplate, type.getDbTableName()));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to reset database.", ex);
        } finally {
            EamDbUtil.closeConnection(conn);
        }

        dbSettings.insertDefaultDatabaseContent();
    }

    /**
     * Setup a connection pool for db connections.
     *
     */
    private void setupConnectionPool() throws EamDbException {
        connectionPool = new BasicDataSource();
        connectionPool.setUsername(dbSettings.getUserName());
        connectionPool.setPassword(dbSettings.getPassword());
        connectionPool.setDriverClassName(dbSettings.getDriver());

        StringBuilder connectionURL = new StringBuilder();
        connectionURL.append(dbSettings.getJDBCBaseURI());
        connectionURL.append(dbSettings.getHost());
        connectionURL.append(":");
        connectionURL.append(dbSettings.getPort());
        connectionURL.append("/");
        connectionURL.append(dbSettings.getDbName());

        connectionPool.setUrl(connectionURL.toString());
        connectionPool.setUsername(dbSettings.getUserName());
        connectionPool.setPassword(dbSettings.getPassword());

        // tweak pool configuration
        connectionPool.setInitialSize(5); // start with 5 connections
        connectionPool.setMaxIdle(CONN_POOL_SIZE); // max of 10 idle connections
        connectionPool.setValidationQuery(dbSettings.getValidationQuery());
    }

    /**
     * Lazily setup Singleton connection on first request.
     *
     * @param foreignKeys -ignored arguement with postgres databases
     *
     * @return A connection from the connection pool.
     *
     * @throws EamDbException
     */
    @Override
    protected Connection connect(boolean foreignKeys) throws EamDbException {
        //foreignKeys boolean is ignored for postgres
        return connect();
    }

    /**
     * Lazily setup Singleton connection on first request.
     *
     * @return A connection from the connection pool.
     *
     * @throws EamDbException
     */
    @Messages({"PostgresEamDb.centralRepoDisabled.message=Central Repository module is not enabled.",
        "PostgresEamDb.connectionFailed.message=Error getting connection to database."})
    @Override
    protected Connection connect() throws EamDbException {
        synchronized (this) {
            if (!EamDb.isEnabled()) {
                throw new EamDbException("Central Repository module is not enabled", Bundle.PostgresEamDb_centralRepoDisabled_message()); // NON-NLS
            }

            if (connectionPool == null) {
                setupConnectionPool();
            }
        }
        try {
            return connectionPool.getConnection();
        } catch (SQLException ex) {
            throw new EamDbException("Error getting connection from connection pool.", Bundle.PostgresEamDb_connectionFailed_message(), ex); // NON-NLS
        }
    }

    @Override
    protected String getConflictClause() {
        return CONFLICT_CLAUSE;
    }

    /**
     * Gets an exclusive lock (if applicable). Will return the lock if
     * successful, null if unsuccessful because locking isn't supported, and
     * throw an exception if we should have been able to get the lock but failed
     * (meaning the database is in use).
     *
     * @return the lock, or null if locking is not supported
     *
     * @throws EamDbException if the coordination service is running but we fail
     *                        to get the lock
     */
    @Override
    @Messages({"PostgresEamDb.multiUserLockError.message=Error acquiring database lock"})
    public CoordinationService.Lock getExclusiveMultiUserDbLock() throws EamDbException {
        try {
            // First check if multi user mode is enabled - if not there's no point trying to get a lock
            if (!UserPreferences.getIsMultiUserModeEnabled()) {
                return null;
            }

            String databaseNodeName = dbSettings.getHost() + "_" + dbSettings.getDbName();
            CoordinationService.Lock lock = CoordinationService.getInstance().tryGetExclusiveLock(CoordinationService.CategoryNode.CENTRAL_REPO, databaseNodeName, 5, TimeUnit.MINUTES);

            if (lock != null) {
                return lock;
            }
            throw new EamDbException("Error acquiring database lock", Bundle.PostgresEamDb_multiUserLockError_message());
        } catch (InterruptedException ex) {
            throw new EamDbException("Error acquiring database lock", Bundle.PostgresEamDb_multiUserLockError_message(), ex);
        } catch (CoordinationService.CoordinationServiceException ex) {
            // This likely just means the coordination service isn't running, which is ok
            return null;
        }
    }

    @Override
    boolean doesColumnExist(Connection conn, String tableName, String columnName) throws SQLException {
        final String objectIdColumnExistsTemplate = "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='%s' AND column_name='%s')";  //NON-NLS
        ResultSet resultSet = null;
        Statement statement = null;
        boolean columnExists = false;
        try {
            statement = conn.createStatement();
            resultSet = statement.executeQuery(String.format(objectIdColumnExistsTemplate, tableName, columnName));
            if (resultSet.next()) {
                columnExists = resultSet.getBoolean(1);
            }
        } finally {
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeStatement(statement);
        }
        return columnExists;
    }

}
