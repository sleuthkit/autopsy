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

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.datamodel.CaseDbSchemaVersionNumber;

/**
 * Main interface for interacting with the database
 */
public interface EamDb {

    public static final int SCHEMA_VERSION = 2;
    public static final CaseDbSchemaVersionNumber CURRENT_DB_SCHEMA_VERSION
            = new CaseDbSchemaVersionNumber(1, 2);

    /**
     * Get the instance
     *
     * @return The EamDb instance or null if one is not configured.
     *
     * @throws EamDbException
     */
    static EamDb getInstance() throws EamDbException {

        EamDbPlatformEnum selectedPlatform = EamDbPlatformEnum.DISABLED;
        if (EamDbUtil.useCentralRepo()) {
            selectedPlatform = EamDbPlatformEnum.getSelectedPlatform();
        }
        switch (selectedPlatform) {
            case POSTGRESQL:
                return PostgresEamDb.getInstance();

            case SQLITE:
                return SqliteEamDb.getInstance();
            default:
                return null;
        }
    }

    /**
     * Shutdown the connection pool.
     *
     * This closes the connection pool including all idle database connections.
     * It will not close active/in-use connections. Thus, it is vital that there
     * are no in-use connections when you call this method.
     *
     * @throws EamDbException if there is a problem closing the connection pool.
     */
    void shutdownConnections() throws EamDbException;

    /**
     * Update settings
     *
     * When using updateSettings, if any database settings have changed, you
     * should call shutdownConnections() before using any API methods. That will
     * ensure that any old connections are closed and all new connections will
     * be made using the new settings.
     */
    void updateSettings();

    /**
     * Save settings
     */
    void saveSettings();

    /**
     * Reset the database (testing method)
     */
    void reset() throws EamDbException;

    /**
     * Is the database enabled?
     *
     * @return Is the database enabled
     */
    static boolean isEnabled() {
        return EamDbUtil.useCentralRepo()
                && EamDbPlatformEnum.getSelectedPlatform() != EamDbPlatformEnum.DISABLED;
    }

    /**
     * Add a new name/value pair in the db_info table.
     *
     * @param name  Key to set
     * @param value Value to set
     *
     * @throws EamDbException
     */
    public void newDbInfo(String name, String value) throws EamDbException;

    /**
     * Get the value for the given name from the name/value db_info table.
     *
     * @param name Name to search for
     *
     * @return value associated with name.
     *
     * @throws EamDbException
     */
    public String getDbInfo(String name) throws EamDbException;

    /**
     * Update the value for a name in the name/value db_info table.
     *
     * @param name  Name to find
     * @param value Value to assign to name.
     *
     * @throws EamDbException
     */
    public void updateDbInfo(String name, String value) throws EamDbException;

    /**
     * Creates new Case in the database
     *
     * Expects the Organization for this case to already exist in the database.
     *
     * @param eamCase The case to add
     */
    CorrelationCase newCase(CorrelationCase eamCase) throws EamDbException;

    /**
     * Creates new Case in the database from the given case
     *
     * @param autopsyCase The case to add
     */
    CorrelationCase newCase(Case autopsyCase) throws EamDbException;

    /**
     * Updates an existing Case in the database
     *
     * @param eamCase The case to update
     */
    void updateCase(CorrelationCase eamCase) throws EamDbException;

    /**
     * Retrieves Central Repo case based on an Autopsy Case
     *
     * @param autopsyCase Autopsy case to find corresponding CR case for
     *
     * @return CR Case
     *
     * @throws EamDbException
     */
    CorrelationCase getCase(Case autopsyCase) throws EamDbException;

    /**
     * Retrieves Case details based on Case UUID
     *
     * @param caseUUID unique identifier for a case
     *
     * @return The retrieved case
     */
    CorrelationCase getCaseByUUID(String caseUUID) throws EamDbException;

    /**
     * Retrieves Case details based on Case ID
     *
     * @param caseId unique identifier for a case
     *
     * @return The retrieved case
     */
    CorrelationCase getCaseById(int caseId) throws EamDbException;

    /**
     * Retrieves cases that are in DB.
     *
     * @return List of cases
     */
    List<CorrelationCase> getCases() throws EamDbException;

    /**
     * Creates new Data Source in the database
     *
     * @param eamDataSource the data source to add
     */
    void newDataSource(CorrelationDataSource eamDataSource) throws EamDbException;

    /**
     * Retrieves Data Source details based on data source device ID
     *
     * @param correlationCase    the current CorrelationCase used for ensuring
     *                           uniqueness of DataSource
     * @param dataSourceDeviceId the data source device ID number
     *
     * @return The data source
     */
    CorrelationDataSource getDataSource(CorrelationCase correlationCase, String dataSourceDeviceId) throws EamDbException;

    /**
     * Retrieves Data Source details based on data source ID
     *
     * @param correlationCase the current CorrelationCase used for ensuring
     *                        uniqueness of DataSource
     * @param dataSourceId    the data source ID number
     *
     * @return The data source
     */
    CorrelationDataSource getDataSourceById(CorrelationCase correlationCase, int dataSourceId) throws EamDbException;

    /**
     * Retrieves data sources that are in DB
     *
     * @return List of data sources
     */
    List<CorrelationDataSource> getDataSources() throws EamDbException;

    /**
     * Inserts new Artifact(s) into the database. Should add associated Case and
     * Data Source first.
     *
     * @param eamArtifact The artifact to add
     */
    void addArtifactInstance(CorrelationAttributeInstance eamArtifact) throws EamDbException;

    /**
     * Retrieves eamArtifact instances from the database that are associated
     * with the eamArtifactType and eamArtifactValue of the given eamArtifact.
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return List of artifact instances for a given type/value
     */
    List<CorrelationAttributeInstance> getArtifactInstancesByTypeValue(CorrelationAttributeInstance.Type aType, String value) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Retrieves eamArtifact instances from the database that are associated
     * with the aType and filePath
     *
     * @param aType    EamArtifact.Type to search for
     * @param filePath File path to search for
     *
     * @return List of 0 or more EamArtifactInstances
     *
     * @throws EamDbException
     */
    List<CorrelationAttributeInstance> getArtifactInstancesByPath(CorrelationAttributeInstance.Type aType, String filePath) throws EamDbException;

    /**
     * Retrieves number of artifact instances in the database that are
     * associated with the ArtifactType and artifactValue of the given artifact.
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Number of artifact instances having ArtifactType and
     *         ArtifactValue.
     */
    Long getCountArtifactInstancesByTypeValue(CorrelationAttributeInstance.Type aType, String value) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Calculate the percentage of data sources that have this attribute value.
     *
     * @param corAttr Attribute type and value to get data about
     *
     * @return Int between 0 and 100
     */
    int getFrequencyPercentage(CorrelationAttributeInstance corAttr) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Retrieves number of unique caseDisplayName / dataSource tuples in the
     * database that are associated with the artifactType and artifactValue of
     * the given artifact.
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Number of unique tuples
     */
    Long getCountUniqueCaseDataSourceTuplesHavingTypeValue(CorrelationAttributeInstance.Type aType, String value) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Retrieves number of data sources in the database.
     *
     * @return Number of unique data sources
     */
    Long getCountUniqueDataSources() throws EamDbException;

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
    Long getCountArtifactInstancesByCaseDataSource(String caseUUID, String dataSourceID) throws EamDbException;

    /**
     * Adds an eamArtifact to an internal list to be later added to DB. Artifact
     * can have 1 or more Artifact Instances. Insert will be triggered by a
     * threshold or a call to commitAttributeInstancesBulk().
     *
     * @param eamArtifact The artifact to add
     */
    void addAttributeInstanceBulk(CorrelationAttributeInstance eamArtifact) throws EamDbException;

    /**
     * Executes a bulk insert of the eamArtifacts added from the
     * addAttributeInstanceBulk() method
     */
    void commitAttributeInstancesBulk() throws EamDbException;

    /**
     * Executes a bulk insert of the cases
     */
    void bulkInsertCases(List<CorrelationCase> cases) throws EamDbException;

    /**
     * Update a correlation attribute instance comment in the database with that
     * in the associated CorrelationAttribute object.
     *
     * @param eamArtifact The correlation attribute whose database instance will
     *                    be updated.
     *
     * @throws EamDbException
     */
    void updateAttributeInstanceComment(CorrelationAttributeInstance eamArtifact) throws EamDbException;

    /**
     * Find a correlation attribute in the Central Repository database given the
     * instance type, case, data source, value, and file path.
     * 
     * Method exists to support instances added using Central Repository version 1,1 and
     * older
     *
     * @param type                  The type of instance.
     * @param correlationCase       The case tied to the instance.
     * @param correlationDataSource The data source tied to the instance.
     * @param value                 The value tied to the instance.
     * @param filePath              The file path tied to the instance.
     *
     * @return The correlation attribute if it exists; otherwise null.
     * 
     * @throws EamDbException
     */
    CorrelationAttributeInstance getCorrelationAttributeInstance(CorrelationAttributeInstance.Type type, CorrelationCase correlationCase,
            CorrelationDataSource correlationDataSource, String value, String filePath) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Find a correlation attribute in the Central Repository database given the
     * instance type, case, data source, object id.
     *
     * @param type                  The type of instance.
     * @param correlationCase       The case tied to the instance.
     * @param correlationDataSource The data source tied to the instance.
     * @param objectID              The object id of the file tied to the instance.
     *
     * @return The correlation attribute if it exists; otherwise null.
     *
     * @throws EamDbException
     */
    CorrelationAttributeInstance getCorrelationAttributeInstance(CorrelationAttributeInstance.Type type, CorrelationCase correlationCase,
            CorrelationDataSource correlationDataSource, long objectID) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Sets an eamArtifact instance to the given known status. If eamArtifact
     * exists, it is updated. If eamArtifact does not exist nothing happens
     *
     * @param eamArtifact Artifact containing exactly one (1) ArtifactInstance.
     * @param knownStatus The status to change the artifact to
     */
    void setAttributeInstanceKnownStatus(CorrelationAttributeInstance eamArtifact, TskData.FileKnown knownStatus) throws EamDbException;

    /**
     * Gets list of matching eamArtifact instances that have knownStatus =
     * "Bad".
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return List with 0 or more matching eamArtifact instances.
     */
    List<CorrelationAttributeInstance> getArtifactInstancesKnownBad(CorrelationAttributeInstance.Type aType, String value) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Gets list of matching eamArtifact instances that have knownStatus =
     * "Bad".
     *
     * @param aType EamArtifact.Type to search for
     *
     * @return List with 0 or more matching eamArtifact instances.
     *
     * @throws EamDbException
     */
    List<CorrelationAttributeInstance> getArtifactInstancesKnownBad(CorrelationAttributeInstance.Type aType) throws EamDbException;

    /**
     * Count matching eamArtifacts instances that have knownStatus = "Bad".
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Number of matching eamArtifacts
     */
    Long getCountArtifactInstancesKnownBad(CorrelationAttributeInstance.Type aType, String value) throws EamDbException, CorrelationAttributeNormalizationException;

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
     * @throws EamDbException
     */
    List<String> getListCasesHavingArtifactInstancesKnownBad(CorrelationAttributeInstance.Type aType, String value) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Remove a reference set and all values contained in it.
     *
     * @param referenceSetID
     *
     * @throws EamDbException
     */
    public void deleteReferenceSet(int referenceSetID) throws EamDbException;

    /**
     * Check whether a reference set with the given parameters exists in the
     * central repository. Used to check whether reference sets saved in the
     * settings are still present.
     *
     * @param referenceSetID
     * @param referenceSetName
     * @param version
     *
     * @return true if a matching entry exists in the central repository
     *
     * @throws EamDbException
     */
    public boolean referenceSetIsValid(int referenceSetID, String referenceSetName, String version) throws EamDbException;

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
     * @throws EamDbException
     */
    public boolean referenceSetExists(String referenceSetName, String version) throws EamDbException;

    /**
     * Check if the given file hash is in this reference set. Only searches the
     * reference_files table.
     *
     * @param hash
     * @param referenceSetID
     *
     * @return true if the hash is found in the reference set
     *
     * @throws EamDbException
     */
    public boolean isFileHashInReferenceSet(String hash, int referenceSetID) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Check if the given value is in a specific reference set
     *
     * @param value
     * @param referenceSetID
     * @param correlationTypeID
     *
     * @return true if the hash is found in the reference set
     */
    public boolean isValueInReferenceSet(String value, int referenceSetID, int correlationTypeID) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Is the artifact known as bad according to the reference entries?
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Global known status of the artifact
     */
    boolean isArtifactKnownBadByReference(CorrelationAttributeInstance.Type aType, String value) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Add a new organization
     *
     * @param eamOrg The organization to add
     *
     * @return The organization with the org ID set.
     *
     * @throws EamDbException
     */
    EamOrganization newOrganization(EamOrganization eamOrg) throws EamDbException;

    /**
     * Get all organizations
     *
     * @return A list of all organizations
     *
     * @throws EamDbException
     */
    List<EamOrganization> getOrganizations() throws EamDbException;

    /**
     * Get an organization having the given ID
     *
     * @param orgID The id to look up
     *
     * @return The organization with the given ID
     *
     * @throws EamDbException
     */
    EamOrganization getOrganizationByID(int orgID) throws EamDbException;

    /**
     * Get the organization associated with the given reference set.
     *
     * @param referenceSetID ID of the reference set
     *
     * @return The organization object
     *
     * @throws EamDbException
     */
    EamOrganization getReferenceSetOrganization(int referenceSetID) throws EamDbException;

    /**
     * Update an existing organization.
     *
     * @param updatedOrganization the values the Organization with the same ID
     *                            will be updated to in the database.
     *
     * @throws EamDbException
     */
    void updateOrganization(EamOrganization updatedOrganization) throws EamDbException;

    /**
     * Delete an organization if it is not being used by any case.
     *
     * @param organizationToDelete the organization to be deleted
     *
     * @throws EamDbException
     */
    void deleteOrganization(EamOrganization organizationToDelete) throws EamDbException;

    /**
     * Add a new Global Set
     *
     * @param eamGlobalSet The global set to add
     *
     * @return The ID of the new global set
     *
     * @throws EamDbException
     */
    int newReferenceSet(EamGlobalSet eamGlobalSet) throws EamDbException;

    /**
     * Get a global set by ID
     *
     * @param globalSetID The ID to look up
     *
     * @return The global set associated with the ID
     *
     * @throws EamDbException
     */
    EamGlobalSet getReferenceSetByID(int globalSetID) throws EamDbException;

    /**
     * Get all reference sets
     *
     * @param correlationType Type of sets to return
     *
     * @return List of all reference sets in the central repository
     *
     * @throws EamDbException
     */
    List<EamGlobalSet> getAllReferenceSets(CorrelationAttributeInstance.Type correlationType) throws EamDbException;

    /**
     * Add a new reference instance
     *
     * @param eamGlobalFileInstance The reference instance to add
     * @param correlationType       Correlation Type that this Reference
     *                              Instance is
     *
     * @throws EamDbException
     */
    void addReferenceInstance(EamGlobalFileInstance eamGlobalFileInstance, CorrelationAttributeInstance.Type correlationType) throws EamDbException;

    /**
     * Insert the bulk collection of Global File Instances
     *
     * @param globalInstances a Set of EamGlobalFileInstances to insert into the
     *                        db.
     * @param contentType     the Type of the global instances
     *
     * @throws EamDbException
     */
    void bulkInsertReferenceTypeEntries(Set<EamGlobalFileInstance> globalInstances, CorrelationAttributeInstance.Type contentType) throws EamDbException;

    /**
     * Get all reference entries having a given correlation type and value
     *
     * @param aType  Type to use for matching
     * @param aValue Value to use for matching
     *
     * @return List of all global file instances with a type and value
     *
     * @throws EamDbException
     */
    List<EamGlobalFileInstance> getReferenceInstancesByTypeValue(CorrelationAttributeInstance.Type aType, String aValue) throws EamDbException, CorrelationAttributeNormalizationException;

    /**
     * Add a new EamArtifact.Type to the db.
     *
     * @param newType New type to add.
     *
     * @return Type.ID for newType
     *
     * @throws EamDbException
     */
    int newCorrelationType(CorrelationAttributeInstance.Type newType) throws EamDbException;

    /**
     * Get the list of EamArtifact.Type's that are defined in the DB and can be
     * used to correlate artifacts.
     *
     * @return List of EamArtifact.Type's. If none are defined in the database,
     *         the default list will be returned.
     *
     * @throws EamDbException
     */
    List<CorrelationAttributeInstance.Type> getDefinedCorrelationTypes() throws EamDbException;

    /**
     * Get the list of enabled EamArtifact.Type's that will be used to correlate
     * artifacts.
     *
     * @return List of enabled EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     *
     * @throws EamDbException
     */
    List<CorrelationAttributeInstance.Type> getEnabledCorrelationTypes() throws EamDbException;

    /**
     * Get the list of supported EamArtifact.Type's that can be used to
     * correlate artifacts.
     *
     * @return List of supported EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     *
     * @throws EamDbException
     */
    List<CorrelationAttributeInstance.Type> getSupportedCorrelationTypes() throws EamDbException;

    /**
     * Update a EamArtifact.Type.
     *
     * @param aType EamArtifact.Type to update.
     *
     * @throws EamDbException
     */
    void updateCorrelationType(CorrelationAttributeInstance.Type aType) throws EamDbException;

    /**
     * Get the EamArtifact.Type that has the given Type.Id.
     *
     * @param typeId Type.Id of Correlation Type to get
     *
     * @return EamArtifact.Type or null if it doesn't exist.
     *
     * @throws EamDbException
     */
    CorrelationAttributeInstance.Type getCorrelationTypeById(int typeId) throws EamDbException;

    /**
     * Upgrade the schema of the database (if needed)
     *
     * @throws EamDbException
     */
    public void upgradeSchema() throws EamDbException, SQLException;

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
    public CoordinationService.Lock getExclusiveMultiUserDbLock() throws EamDbException;

    /**
     * Process the Artifact instance in the EamDb
     *
     * @param type                  EamArtifact.Type to search for
     * @param instanceTableCallback callback to process the instance
     *
     * @throws EamDbException
     */
    void processInstanceTable(CorrelationAttributeInstance.Type type, InstanceTableCallback instanceTableCallback) throws EamDbException;

    /**
     * Process the Artifact instance in the EamDb
     *
     * @param type                  EamArtifact.Type to search for
     * @param instanceTableCallback callback to process the instance
     * @param whereClause           query string to execute
     *
     * @throws EamDbException
     */
    void processInstanceTableWhere(CorrelationAttributeInstance.Type type, String whereClause, InstanceTableCallback instanceTableCallback) throws EamDbException;

}
