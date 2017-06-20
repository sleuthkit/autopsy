/*
 * Enterprise Artifacts Manager
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.apache.commons.dbcp2.BasicDataSource;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Enterprise artifacts manager database implementation using Postgres as a
 * backend
 */
public class PostgresEamDb extends AbstractSqlEamDb {

    private final static Logger LOGGER = Logger.getLogger(PostgresEamDb.class.getName());

    private static PostgresEamDb instance;

    private static final int CONN_POOL_SIZE = 10;
    private BasicDataSource connectionPool = null;

    private final PostgresEamDbSettings dbSettings;

    public synchronized static PostgresEamDb getInstance() {
        if (instance == null) {
            instance = new PostgresEamDb();
        }

        return instance;
    }

    private PostgresEamDb() {
        dbSettings = new PostgresEamDbSettings();
        updateSettings();
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
            dropContent.executeUpdate("TRUNCATE TABLE global_reference_sets RESTART IDENTITY CASCADE");
            dropContent.executeUpdate("TRUNCATE TABLE global_files RESTART IDENTITY CASCADE");
            dropContent.executeUpdate("TRUNCATE TABLE artifact_types RESTART IDENTITY CASCADE");
            dropContent.executeUpdate("TRUNCATE TABLE db_info RESTART IDENTITY CASCADE");

            String instancesTemplate = "TRUNCATE TABLE %s_instances RESTART IDENTITY CASCADE";
            for (EamArtifact.Type type : DEFAULT_ARTIFACT_TYPES) {
                dropContent.executeUpdate(String.format(instancesTemplate, type.getName().toLowerCase()));
            }

        } catch (SQLException ex) {
            //LOGGER.log(Level.WARNING, "Failed to reset database.", ex);
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
        connectionURL.append("?user=");
        connectionURL.append(dbSettings.getUserName());
        connectionURL.append("&password=");
        connectionURL.append(dbSettings.getPassword());

        connectionPool.setUrl(connectionURL.toString());

        // tweak pool configuration
        connectionPool.setInitialSize(5); // start with 5 connections
        connectionPool.setMaxIdle(CONN_POOL_SIZE); // max of 10 idle connections
        connectionPool.setValidationQuery(dbSettings.getValidationQuery());
    }

    /**
     * Lazily setup Singleton connection on first request.
     *
     * @return A connection from the connection pool.
     *
     * @throws EamDbException
     */
    @Override
    protected Connection connect() throws EamDbException {
        synchronized (this) {
            if (!EamDb.isEnabled()) {
                throw new EamDbException("Enterprise artifacts manager is not enabled"); // NON-NLS
            }

            if (connectionPool == null) {
                setupConnectionPool();
            }
        }

        try {
            return connectionPool.getConnection();
        } catch (SQLException ex) {
            throw new EamDbException("Error getting connection from connection pool.", ex); // NON-NLS
        }
    }

    @Override
    public List<String> getBadTags() {
        return dbSettings.getBadTags();
    }

    @Override
    public void setBadTags(List<String> badTags) {
        dbSettings.setBadTags(badTags);
    }

}
