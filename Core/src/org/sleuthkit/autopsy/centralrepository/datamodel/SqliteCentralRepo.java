/*
 * Central Repository
 *
 * Copyright 2015-2021 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import org.apache.commons.dbcp2.BasicDataSource;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;

/**
 * Sqlite implementation of the Central Repository database. All methods in
 * AbstractSqlEamDb that read or write to the database should be overriden here
 * and use appropriate locking.
 */
final class SqliteCentralRepo extends RdbmsCentralRepo {

    private final static Logger LOGGER = Logger.getLogger(SqliteCentralRepo.class.getName());

    private static SqliteCentralRepo instance;

    private BasicDataSource connectionPool = null;

    private final SqliteCentralRepoSettings dbSettings;

    // While the Sqlite database should only be used for single users, it is still
    // possible for multiple threads to attempt to write to the database simultaneously. 
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);

    /**
     * Get the singleton instance of SqliteEamDb
     *
     * @return the singleton instance of SqliteEamDb
     *
     * @throws CentralRepoException if one or more default correlation type(s)
     *                              have an invalid db table name.
     */
    public synchronized static SqliteCentralRepo getInstance() throws CentralRepoException {
        if (instance == null) {
            instance = new SqliteCentralRepo();
        }

        return instance;
    }

    /**
     *
     * @throws CentralRepoException if the AbstractSqlEamDb class has one or
     *                              more default correlation type(s) having an
     *                              invalid db table name.
     */
    private SqliteCentralRepo() throws CentralRepoException {
        dbSettings = new SqliteCentralRepoSettings();
        bulkArtifactsThreshold = dbSettings.getBulkThreshold();
    }

    @Override
    public void shutdownConnections() throws CentralRepoException {
        try {
            synchronized (this) {
                if (null != connectionPool) {
                    connectionPool.close();
                    connectionPool = null; // force it to be re-created on next connect()
                }
                clearCaches();
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Failed to close existing database connections.", ex); // NON-NLS
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
    public void reset() throws CentralRepoException {
        try {
            acquireExclusiveLock();

            Connection conn = connect();

            try {

                Statement dropContent = conn.createStatement();
                dropContent.executeUpdate("DELETE FROM organizations");
                dropContent.executeUpdate("DELETE FROM cases");
                dropContent.executeUpdate("DELETE FROM data_sources");
                dropContent.executeUpdate("DELETE FROM reference_sets");
                dropContent.executeUpdate("DELETE FROM artifact_types");
                dropContent.executeUpdate("DELETE FROM db_info");

                String instancesTemplate = "DELETE FROM %s_instances";
                String referencesTemplate = "DELETE FROM global_files";
                for (CorrelationAttributeInstance.Type type : defaultCorrelationTypes) {
                    dropContent.executeUpdate(String.format(instancesTemplate, type.getDbTableName()));
                    // FUTURE: support other reference types
                    if (type.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                        dropContent.executeUpdate(String.format(referencesTemplate, type.getDbTableName()));
                    }
                }

                dropContent.executeUpdate("VACUUM");
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Failed to reset database.", ex);
            } finally {
                CentralRepoDbUtil.closeConnection(conn);
            }

            RdbmsCentralRepoFactory centralRepoSchemaFactory = new RdbmsCentralRepoFactory(CentralRepoPlatforms.SQLITE, dbSettings);
            centralRepoSchemaFactory.insertDefaultDatabaseContent();
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Setup a connection pool for db connections.
     *
     */
    @Messages({"SqliteEamDb.databaseMissing.message=Central repository database missing"})
    private void setupConnectionPool(boolean foreignKeysEnabled) throws CentralRepoException {

        if (dbSettings.dbFileExists() == false) {
            throw new CentralRepoException("Central repository database missing", Bundle.SqliteEamDb_databaseMissing_message());
        }

        connectionPool = new BasicDataSource();
        connectionPool.setDriverClassName(dbSettings.getDriver());
        connectionPool.setUrl(dbSettings.getConnectionURL());

        // tweak pool configuration
        connectionPool.setInitialSize(50);
        connectionPool.setMaxTotal(-1);
        connectionPool.setMaxIdle(-1);
        connectionPool.setMaxWaitMillis(1000);
        connectionPool.setValidationQuery(dbSettings.getValidationQuery());
        if (foreignKeysEnabled) {
            connectionPool.setConnectionInitSqls(Arrays.asList("PRAGMA foreign_keys = ON"));
        } else {
            connectionPool.setConnectionInitSqls(Arrays.asList("PRAGMA foreign_keys = OFF"));
        }
    }

    /**
     * Lazily setup Singleton connection on first request.
     *
     * @param foreignKeys determines if foreign keys should be enforced during
     *                    this connection for SQLite
     *
     * @return A connection from the connection pool.
     *
     * @throws CentralRepoException
     */
    @Messages({"SqliteEamDb.connectionFailedMessage.message=Error getting connection to database.",
        "SqliteEamDb.centralRepositoryDisabled.message=Central Repository module is not enabled."})
    @Override
    protected Connection connect(boolean foreignKeys) throws CentralRepoException {
        synchronized (this) {
            if (!CentralRepository.isEnabled()) {
                throw new CentralRepoException("Central repository database missing", Bundle.SqliteEamDb_centralRepositoryDisabled_message()); // NON-NLS
            }
            if (connectionPool == null) {
                setupConnectionPool(foreignKeys);
            }
            try {
                return connectionPool.getConnection();
            } catch (SQLException ex) {
                throw new CentralRepoException("Error getting connection from connection pool.", Bundle.SqliteEamDb_connectionFailedMessage_message(), ex); // NON-NLS
            }
        }
    }

    /**
     * Lazily setup Singleton connection on first request with foreign keys
     * enforced.
     *
     * @return A connection from the connection pool.
     *
     * @throws CentralRepoException
     */
    @Override
    protected Connection connect() throws CentralRepoException {
        return connect(true);
    }

    @Override
    protected String getConflictClause() {
        // For sqlite, our conflict clause is part of the table schema
        return "";
    }

    @Override
    protected Connection getEphemeralConnection() {
        return this.dbSettings.getEphemeralConnection();
    }

    /**
     * Add a new name/value pair in the db_info table.
     *
     * @param name  Key to set
     * @param value Value to set
     *
     * @throws CentralRepoException
     */
    @Override
    public void newDbInfo(String name, String value) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.newDbInfo(name, value);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Get the value for the given name from the name/value db_info table.
     *
     * @param name Name to search for
     *
     * @return value associated with name.
     *
     * @throws CentralRepoException
     */
    @Override
    public String getDbInfo(String name) throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getDbInfo(name);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Update the value for a name in the name/value db_info table.
     *
     * @param name  Name to find
     * @param value Value to assign to name.
     *
     * @throws CentralRepoException
     */
    @Override
    public void updateDbInfo(String name, String value) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.updateDbInfo(name, value);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Creates new Case in the database from the given case
     *
     * @param autopsyCase The case to add
     */
    @Override
    public CorrelationCase newCase(Case autopsyCase) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            return super.newCase(autopsyCase);
        } finally {
            releaseExclusiveLock();
        }
    }

    @Override
    public void addDataSourceObjectId(int rowId, long dataSourceObjectId) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.addDataSourceObjectId(rowId, dataSourceObjectId);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Creates new Case in the database
     *
     * Expects the Organization for this case to already exist in the database.
     *
     * @param eamCase The case to add
     */
    @Override
    public CorrelationCase newCase(CorrelationCase eamCase) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            return super.newCase(eamCase);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Updates an existing Case in the database
     *
     * @param eamCase The case to update
     */
    @Override
    public void updateCase(CorrelationCase eamCase) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.updateCase(eamCase);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Retrieves Case details based on Case UUID
     *
     * @param caseUUID unique identifier for a case
     *
     * @return The retrieved case
     */
    @Override
    public CorrelationCase getCaseByUUID(String caseUUID) throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getCaseByUUID(caseUUID);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Retrieves Case details based on Case ID
     *
     * @param caseID unique identifier for a case
     *
     * @return The retrieved case
     */
    @Override
    public CorrelationCase getCaseById(int caseId) throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getCaseById(caseId);
        } finally {
            releaseSharedLock();
        }

    }

    /**
     * Retrieves cases that are in DB.
     *
     * @return List of cases
     */
    @Override
    public List<CorrelationCase> getCases() throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getCases();
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Creates new Data Source in the database
     *
     * @param eamDataSource the data source to add
     */
    @Override
    public CorrelationDataSource newDataSource(CorrelationDataSource eamDataSource) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            return super.newDataSource(eamDataSource);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Retrieves Data Source details based on data source device ID
     *
     * @param correlationCase    the current CorrelationCase used for ensuring
     *                           uniqueness of DataSource
     * @param dataSourceDeviceId the data source device ID number
     *
     * @return The data source
     */
    @Override
    public CorrelationDataSource getDataSource(CorrelationCase correlationCase, Long caseDbDataSourceId) throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getDataSource(correlationCase, caseDbDataSourceId);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Retrieves Data Source details based on data source ID
     *
     * @param correlationCase the current CorrelationCase used for ensuring
     *                        uniqueness of DataSource
     * @param dataSourceId    the data source ID number
     *
     * @return The data source
     */
    @Override
    public CorrelationDataSource getDataSourceById(CorrelationCase correlationCase, int dataSourceId) throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getDataSourceById(correlationCase, dataSourceId);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Return a list of data sources in the DB
     *
     * @return list of data sources in the DB
     */
    @Override
    public List<CorrelationDataSource> getDataSources() throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getDataSources();
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Changes the name of a data source in the DB
     *
     * @param eamDataSource The data source
     * @param newName       The new name
     *
     * @throws CentralRepoException
     */
    @Override
    public void updateDataSourceName(CorrelationDataSource eamDataSource, String newName) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.updateDataSourceName(eamDataSource, newName);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Updates the MD5 hash value in an existing data source in the database.
     *
     * @param eamDataSource The data source to update
     */
    @Override
    public void updateDataSourceMd5Hash(CorrelationDataSource eamDataSource) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.updateDataSourceMd5Hash(eamDataSource);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Updates the SHA-1 hash value in an existing data source in the database.
     *
     * @param eamDataSource The data source to update
     */
    @Override
    public void updateDataSourceSha1Hash(CorrelationDataSource eamDataSource) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.updateDataSourceSha1Hash(eamDataSource);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Updates the SHA-256 hash value in an existing data source in the
     * database.
     *
     * @param eamDataSource The data source to update
     */
    @Override
    public void updateDataSourceSha256Hash(CorrelationDataSource eamDataSource) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.updateDataSourceSha256Hash(eamDataSource);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Inserts new Artifact(s) into the database. Should add associated Case and
     * Data Source first.
     *
     * @param eamArtifact The artifact to add
     */
    @Override
    public void addArtifactInstance(CorrelationAttributeInstance eamArtifact) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.addArtifactInstance(eamArtifact);
        } finally {
            releaseExclusiveLock();
        }
    }

    @Override
    public List<CorrelationAttributeInstance> getArtifactInstancesByTypeValue(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.getArtifactInstancesByTypeValue(aType, value);
        } finally {
            releaseSharedLock();
        }
    }

    @Override
    public List<CorrelationAttributeInstance> getArtifactInstancesByTypeValues(CorrelationAttributeInstance.Type aType, List<String> values) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.getArtifactInstancesByTypeValues(aType, values);
        } finally {
            releaseSharedLock();
        }
    }

    @Override
    public List<CorrelationAttributeInstance> getArtifactInstancesByTypeValuesAndCases(CorrelationAttributeInstance.Type aType, List<String> values, List<Integer> caseIds) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.getArtifactInstancesByTypeValuesAndCases(aType, values, caseIds);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Retrieves number of artifact instances in the database that are
     * associated with the ArtifactType and artifactValue of the given artifact.
     *
     * @param aType The correlation type
     * @param value The value to search for
     *
     * @return Number of artifact instances having ArtifactType and
     *         ArtifactValue.
     *
     * @throws CentralRepoException
     */
    @Override
    public Long getCountArtifactInstancesByTypeValue(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.getCountArtifactInstancesByTypeValue(aType, value);
        } finally {
            releaseSharedLock();
        }
    }

    @Override
    public int getFrequencyPercentage(CorrelationAttributeInstance corAttr) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.getFrequencyPercentage(corAttr);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Retrieves number of unique caseDisplayName / dataSource tuples in the
     * database that are associated with the artifactType and artifactValue of
     * the given artifact.
     *
     * @param aType The correlation type
     * @param value The value to search for
     *
     * @return Number of unique tuples
     *
     * @throws CentralRepoException
     */
    @Override
    public Long getCountUniqueCaseDataSourceTuplesHavingTypeValue(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.getCountUniqueCaseDataSourceTuplesHavingTypeValue(aType, value);
        } finally {
            releaseSharedLock();
        }
    }

    @Override
    public Long getCountCasesWithOtherInstances(CorrelationAttributeInstance instance) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.getCountCasesWithOtherInstances(instance);
        } finally {
            releaseSharedLock();
        }
    }

    @Override
    public Long getCountUniqueDataSources() throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getCountUniqueDataSources();
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Retrieves number of eamArtifact instances in the database that are
     * associated with the caseDisplayName and dataSource of the given
     * eamArtifact instance.
     *
     * @param caseUUID     Case ID to search for
     * @param dataSourceID Data source ID to search for
     *
     * @return Number of artifact instances having caseDisplayName and
     *         dataSource
     */
    @Override
    public Long getCountArtifactInstancesByCaseDataSource(CorrelationDataSource correlationDataSource) throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getCountArtifactInstancesByCaseDataSource(correlationDataSource);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Executes a bulk insert of the eamArtifacts added from the
     * addAttributeInstanceBulk() method
     */
    @Override
    public void commitAttributeInstancesBulk() throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.commitAttributeInstancesBulk();
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Executes a bulk insert of the cases
     */
    @Override
    public void bulkInsertCases(List<CorrelationCase> cases) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.bulkInsertCases(cases);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Sets an eamArtifact instance to the given knownStatus. knownStatus should
     * be BAD if the file has been tagged with a notable tag and UNKNOWN
     * otherwise. If eamArtifact exists, it is updated. If eamArtifact does not
     * exist it is added with the given status.
     *
     * @param eamArtifact Artifact containing exactly one (1) ArtifactInstance.
     * @param knownStatus The status to change the artifact to. Should never be
     *                    KNOWN
     */
    @Override
    public void setAttributeInstanceKnownStatus(CorrelationAttributeInstance eamArtifact, TskData.FileKnown knownStatus) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.setAttributeInstanceKnownStatus(eamArtifact, knownStatus);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Count matching eamArtifacts instances that have knownStatus = "Bad".
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Number of matching eamArtifacts
     */
    @Override
    public Long getCountArtifactInstancesKnownBad(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.getCountArtifactInstancesKnownBad(aType, value);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Gets list of distinct case display names, where each case has 1+ Artifact
     * Instance matching eamArtifact with knownStatus = "Bad".
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return List of cases containing this artifact with instances marked as
     *         bad
     *
     * @throws CentralRepoException
     */
    @Override
    public List<String> getListCasesHavingArtifactInstancesKnownBad(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.getListCasesHavingArtifactInstancesKnownBad(aType, value);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Remove a reference set and all values contained in it.
     *
     * @param referenceSetID
     *
     * @throws CentralRepoException
     */
    @Override
    public void deleteReferenceSet(int referenceSetID) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.deleteReferenceSet(referenceSetID);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Check if the given hash is in a specific reference set
     *
     * @param value
     * @param referenceSetID
     * @param correlationTypeID
     *
     * @return true if the hash is found in the reference set
     */
    @Override
    public boolean isValueInReferenceSet(String value, int referenceSetID, int correlationTypeID) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.isValueInReferenceSet(value, referenceSetID, correlationTypeID);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Process the Artifact instance in the EamDb
     *
     * @param type                  EamArtifact.Type to search for
     * @param instanceTableCallback callback to process the instance
     *
     * @throws CentralRepoException
     */
    @Override
    public void processInstanceTable(CorrelationAttributeInstance.Type type, InstanceTableCallback instanceTableCallback) throws CentralRepoException {
        try {
            acquireSharedLock();
            super.processInstanceTable(type, instanceTableCallback);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Process the Artifact instance in the EamDb
     *
     * @param type                  EamArtifact.Type to search for
     * @param instanceTableCallback callback to process the instance
     *
     * @throws CentralRepoException
     */
    @Override
    public void processInstanceTableWhere(CorrelationAttributeInstance.Type type, String whereClause, InstanceTableCallback instanceTableCallback) throws CentralRepoException {
        try {
            acquireSharedLock();
            super.processInstanceTableWhere(type, whereClause, instanceTableCallback);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Process a SELECT query
     *
     * @param selectClause          query string to execute
     * @param instanceTableCallback callback to process the instance
     *
     * @throws CentralRepoException
     */
    @Override
    public void processSelectClause(String selectClause, InstanceTableCallback instanceTableCallback) throws CentralRepoException {
        try {
            acquireSharedLock();
            super.processSelectClause(selectClause, instanceTableCallback);
        } finally {
            releaseSharedLock();
        }
    }

    @Override
    public void executeCommand(String sql, List<Object> params) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.executeCommand(sql, params);
        } finally {
            releaseExclusiveLock();
        }
    }

    @Override
    public void executeQuery(String sql, List<Object> params, CentralRepositoryDbQueryCallback queryCallback) throws CentralRepoException {
        try {
            acquireSharedLock();
            super.executeQuery(sql, params, queryCallback);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Check whether a reference set with the given name/version is in the
     * central repo. Used to check for name collisions when creating reference
     * sets.
     *
     * @param referenceSetName
     * @param version
     *
     * @return true if a matching set is found
     *
     * @throws CentralRepoException
     */
    @Override
    public boolean referenceSetExists(String referenceSetName, String version) throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.referenceSetExists(referenceSetName, version);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Is the artifact known as bad according to the reference entries?
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Global known status of the artifact
     */
    @Override
    public boolean isArtifactKnownBadByReference(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.isArtifactKnownBadByReference(aType, value);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Add a new organization
     *
     * @return the Organization ID of the newly created organization.
     *
     * @param eamOrg The organization to add
     *
     * @throws CentralRepoException
     */
    @Override
    public CentralRepoOrganization newOrganization(CentralRepoOrganization eamOrg) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            return super.newOrganization(eamOrg);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Get all organizations
     *
     * @return A list of all organizations
     *
     * @throws CentralRepoException
     */
    @Override
    public List<CentralRepoOrganization> getOrganizations() throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getOrganizations();
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Get an organization having the given ID
     *
     * @param orgID The id to look up
     *
     * @return The organization with the given ID
     *
     * @throws CentralRepoException
     */
    @Override
    public CentralRepoOrganization getOrganizationByID(int orgID) throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getOrganizationByID(orgID);
        } finally {
            releaseSharedLock();
        }
    }

    @Override
    public void updateOrganization(CentralRepoOrganization updatedOrganization) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.updateOrganization(updatedOrganization);
        } finally {
            releaseExclusiveLock();
        }
    }

    @Override
    public void deleteOrganization(CentralRepoOrganization organizationToDelete) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.deleteOrganization(organizationToDelete);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Add a new Global Set
     *
     * @param eamGlobalSet The global set to add
     *
     * @return The ID of the new global set
     *
     * @throws CentralRepoException
     */
    @Override
    public int newReferenceSet(CentralRepoFileSet eamGlobalSet) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            return super.newReferenceSet(eamGlobalSet);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Get a reference set by ID
     *
     * @param referenceSetID The ID to look up
     *
     * @return The global set associated with the ID
     *
     * @throws CentralRepoException
     */
    @Override
    public CentralRepoFileSet getReferenceSetByID(int referenceSetID) throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getReferenceSetByID(referenceSetID);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Get all reference sets
     *
     * @param correlationType Type of sets to return
     *
     * @return List of all reference sets in the central repository
     *
     * @throws CentralRepoException
     */
    @Override
    public List<CentralRepoFileSet> getAllReferenceSets(CorrelationAttributeInstance.Type correlationType) throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getAllReferenceSets(correlationType);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Add a new reference instance
     *
     * @param eamGlobalFileInstance The reference instance to add
     * @param correlationType       Correlation Type that this Reference
     *                              Instance is
     *
     * @throws CentralRepoException
     */
    @Override
    public void addReferenceInstance(CentralRepoFileInstance eamGlobalFileInstance, CorrelationAttributeInstance.Type correlationType) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.addReferenceInstance(eamGlobalFileInstance, correlationType);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Insert the bulk collection of Reference Type Instances
     *
     * @throws CentralRepoException
     */
    @Override
    public void bulkInsertReferenceTypeEntries(Set<CentralRepoFileInstance> globalInstances, CorrelationAttributeInstance.Type contentType) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.bulkInsertReferenceTypeEntries(globalInstances, contentType);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Get all reference entries having a given correlation type and value
     *
     * @param aType  Type to use for matching
     * @param aValue Value to use for matching
     *
     * @return List of all global file instances with a type and value
     *
     * @throws CentralRepoException
     */
    @Override
    public List<CentralRepoFileInstance> getReferenceInstancesByTypeValue(CorrelationAttributeInstance.Type aType, String aValue) throws CentralRepoException, CorrelationAttributeNormalizationException {
        try {
            acquireSharedLock();
            return super.getReferenceInstancesByTypeValue(aType, aValue);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Add a new EamArtifact.Type to the db.
     *
     * @param newType New type to add.
     *
     * @return ID of this new Correlation Type
     *
     * @throws CentralRepoException
     */
    @Override
    public int newCorrelationType(CorrelationAttributeInstance.Type newType) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            return super.newCorrelationType(newType);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Get the list of EamArtifact.Type's that will be used to correlate
     * artifacts.
     *
     * @return List of EamArtifact.Type's. If none are defined in the database,
     *         the default list will be returned.
     *
     * @throws CentralRepoException
     */
    @Override
    public List<CorrelationAttributeInstance.Type> getDefinedCorrelationTypes() throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getDefinedCorrelationTypes();
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Get the list of enabled EamArtifact.Type's that will be used to correlate
     * artifacts.
     *
     * @return List of enabled EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     *
     * @throws CentralRepoException
     */
    @Override
    public List<CorrelationAttributeInstance.Type> getEnabledCorrelationTypes() throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getEnabledCorrelationTypes();
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Get the list of supported EamArtifact.Type's that can be used to
     * correlate artifacts.
     *
     * @return List of supported EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     *
     * @throws CentralRepoException
     */
    @Override
    public List<CorrelationAttributeInstance.Type> getSupportedCorrelationTypes() throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getSupportedCorrelationTypes();
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Update a EamArtifact.Type.
     *
     * @param aType EamArtifact.Type to update.
     *
     * @throws CentralRepoException
     */
    @Override
    public void updateCorrelationType(CorrelationAttributeInstance.Type aType) throws CentralRepoException {
        try {
            acquireExclusiveLock();
            super.updateCorrelationType(aType);
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Get the EamArtifact.Type that has the given Type.Id.
     *
     * @param typeId Type.Id of Correlation Type to get
     *
     * @return EamArtifact.Type or null if it doesn't exist.
     *
     * @throws CentralRepoException
     */
    @Override
    public CorrelationAttributeInstance.Type getCorrelationTypeById(int typeId) throws CentralRepoException {
        try {
            acquireSharedLock();
            return super.getCorrelationTypeById(typeId);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Upgrade the schema of the database (if needed)
     *
     * @throws CentralRepoException
     */
    @Override
    public void upgradeSchema() throws CentralRepoException, SQLException, IncompatibleCentralRepoException {
        try {
            acquireExclusiveLock();
            super.upgradeSchema();
        } finally {
            releaseExclusiveLock();
        }
    }

    /**
     * Gets an exclusive lock (if applicable). Will return the lock if
     * successful, null if unsuccessful because locking isn't supported, and
     * throw an exception if we should have been able to get the lock but failed
     * (meaning the database is in use).
     *
     * @return the lock, or null if locking is not supported
     *
     * @throws CentralRepoException if the coordination service is running but
     *                              we fail to get the lock
     */
    @Override
    public CoordinationService.Lock getExclusiveMultiUserDbLock() throws CentralRepoException {
        // Multiple users are not supported for SQLite
        return null;
    }

    /**
     * Acquire the lock that provides exclusive access to the case database.
     * Call this method in a try block with a call to the lock release method in
     * an associated finally block.
     */
    private void acquireExclusiveLock() {
        rwLock.writeLock().lock();
    }

    /**
     * Release the lock that provides exclusive access to the database. This
     * method should always be called in the finally block of a try block in
     * which the lock was acquired.
     */
    private void releaseExclusiveLock() {
        rwLock.writeLock().unlock();
    }

    /**
     * Acquire the lock that provides shared access to the case database. Call
     * this method in a try block with a call to the lock release method in an
     * associated finally block.
     */
    private void acquireSharedLock() {
        rwLock.readLock().lock();
    }

    /**
     * Release the lock that provides shared access to the database. This method
     * should always be called in the finally block of a try block in which the
     * lock was acquired.
     */
    private void releaseSharedLock() {
        rwLock.readLock().unlock();
    }

    @Override
    boolean doesColumnExist(Connection conn, String tableName, String columnName) throws SQLException {
        final String tableInfoQueryTemplate = "PRAGMA table_info(%s)";  //NON-NLS
        ResultSet resultSet = null;
        Statement statement = null;
        boolean columnExists = false;
        try {
            statement = conn.createStatement();
            resultSet = statement.executeQuery(String.format(tableInfoQueryTemplate, tableName));
            while (resultSet.next()) {
                // the second value ( 2 ) is the column name
                if (resultSet.getString(2).equals(columnName)) {
                    columnExists = true;
                    break;
                }
            }
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(statement);
        }
        return columnExists;
    }
}
