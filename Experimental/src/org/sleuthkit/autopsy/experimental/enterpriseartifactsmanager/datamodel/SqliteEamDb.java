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

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.dbcp2.BasicDataSource;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Sqlite implementation of the enterprise artifacts manager database
 */
public class SqliteEamDb extends AbstractSqlEamDb {

    private final static Logger LOGGER = Logger.getLogger(SqliteEamDb.class.getName());

    private static SqliteEamDb instance;

    private BasicDataSource connectionPool = null;

    private final SqliteEamDbSettings dbSettings;

    public synchronized static SqliteEamDb getInstance() {
        if (instance == null) {
            instance = new SqliteEamDb();
        }

        return instance;
    }

    private SqliteEamDb() {
        dbSettings = new SqliteEamDbSettings();
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
            dropContent.executeUpdate("DELETE FROM organizations");
            dropContent.executeUpdate("DELETE FROM cases");
            dropContent.executeUpdate("DELETE FROM data_sources");
            dropContent.executeUpdate("DELETE FROM global_reference_sets");
            dropContent.executeUpdate("DELETE FROM global_files");
            dropContent.executeUpdate("DELETE FROM artifact_types");
            dropContent.executeUpdate("DELETE FROM db_info");

            String instancesTemplate = "DELETE FROM %s_instances";
            for (EamArtifact.Type type : DEFAULT_ARTIFACT_TYPES) {
                dropContent.executeUpdate(String.format(instancesTemplate, type.getName().toLowerCase()));
            }

            dropContent.executeUpdate("VACUUM");
            dbSettings.insertDefaultDatabaseContent();

        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to reset database.", ex);
        } finally {
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Setup a connection pool for db connections.
     *
     */
    private void setupConnectionPool() throws EamDbException {
        connectionPool = new BasicDataSource();
        connectionPool.setDriverClassName(dbSettings.getDriver());

        StringBuilder connectionURL = new StringBuilder();
        connectionURL.append(dbSettings.getJDBCBaseURI());
        connectionURL.append(dbSettings.getDbDirectory());
        connectionURL.append(File.separator);
        connectionURL.append(dbSettings.getDbName());

        connectionPool.setUrl(connectionURL.toString());

        // tweak pool configuration
        connectionPool.setInitialSize(50);
        connectionPool.setMaxTotal(-1);
        connectionPool.setMaxIdle(-1);
        connectionPool.setMaxWaitMillis(1000);
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

            try {
                return connectionPool.getConnection();
            } catch (SQLException ex) {
                throw new EamDbException("Error getting connection from connection pool.", ex); // NON-NLS
            }
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
