/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.util.List;
import java.util.Set;

/**
 * Main interface for interacting with the database
 */
public interface EamDb {

    public static final int SCHEMA_VERSION = 1;

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
     * Get the list of tags recognized as "Bad"
     *
     * @return The list of bad tags
     */
    List<String> getBadTags();

    /**
     * Set the tags recognized as "Bad"
     *
     * @param tags The tags to consider bad
     */
    void setBadTags(List<String> tags);

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
    void newCase(EamCase eamCase) throws EamDbException;

    /**
     * Updates an existing Case in the database
     *
     * @param eamCase The case to update
     */
    void updateCase(EamCase eamCase) throws EamDbException;

    /**
     * Retrieves Case details based on Case UUID
     *
     * @param caseUUID unique identifier for a case
     *
     * @return The retrieved case
     */
    EamCase getCaseDetails(String caseUUID) throws EamDbException;

    /**
     * Retrieves cases that are in DB.
     *
     * @return List of cases
     */
    List<EamCase> getCases() throws EamDbException;

    /**
     * Creates new Data Source in the database
     *
     * @param eamDataSource the data source to add
     */
    void newDataSource(EamDataSource eamDataSource) throws EamDbException;

    /**
     * Updates a Data Source in the database
     *
     * @param eamDataSource the data source to update
     */
    void updateDataSource(EamDataSource eamDataSource) throws EamDbException;

    /**
     * Retrieves Data Source details based on data source device ID
     *
     * @param dataSourceDeviceId the data source device ID number
     *
     * @return The data source
     */
    EamDataSource getDataSourceDetails(String dataSourceDeviceId) throws EamDbException;

    /**
     * Retrieves data sources that are in DB
     *
     * @return List of data sources
     */
    List<EamDataSource> getDataSources() throws EamDbException;

    /**
     * Inserts new Artifact(s) into the database. Should add associated Case and
     * Data Source first.
     *
     * @param eamArtifact The artifact to add
     */
    void addArtifact(EamArtifact eamArtifact) throws EamDbException;

    /**
     * Retrieves eamArtifact instances from the database that are associated
     * with the eamArtifactType and eamArtifactValue of the given eamArtifact.
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return List of artifact instances for a given type/value
     */
    List<EamArtifactInstance> getArtifactInstancesByTypeValue(EamArtifact.Type aType, String value) throws EamDbException;

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
    List<EamArtifactInstance> getArtifactInstancesByPath(EamArtifact.Type aType, String filePath) throws EamDbException;

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
    Long getCountArtifactInstancesByTypeValue(EamArtifact.Type aType, String value) throws EamDbException;

    /**
     * Using the ArtifactType and ArtifactValue from the given eamArtfact,
     * compute the ratio of: (The number of unique case_id/datasource_id tuples
     * where Type/Value is found) divided by (The total number of unique
     * case_id/datasource_id tuples in the database) expressed as a percentage.
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Int between 0 and 100
     */
    int getCommonalityPercentageForTypeValue(EamArtifact.Type aType, String value) throws EamDbException;

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
    Long getCountUniqueCaseDataSourceTuplesHavingTypeValue(EamArtifact.Type aType, String value) throws EamDbException;

    /**
     * Retrieves number of unique caseDisplayName/dataSource tuples in the
     * database.
     *
     * @return Number of unique tuples
     */
    Long getCountUniqueCaseDataSourceTuples() throws EamDbException;

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
     * threshold or a call to bulkInsertArtifacts().
     *
     * @param eamArtifact The artifact to add
     */
    void prepareBulkArtifact(EamArtifact eamArtifact) throws EamDbException;

    /**
     * Executes a bulk insert of the eamArtifacts added from the
     * prepareBulkArtifact() method
     */
    void bulkInsertArtifacts() throws EamDbException;

    /**
     * Executes a bulk insert of the cases
     */
    void bulkInsertCases(List<EamCase> cases) throws EamDbException;

    /**
     * Sets an eamArtifact instance as knownStatus = "Bad". If eamArtifact
     * exists, it is updated. If eamArtifact does not exist nothing happens
     *
     * @param eamArtifact Artifact containing exactly one (1) ArtifactInstance.
     */
    void setArtifactInstanceKnownBad(EamArtifact eamArtifact) throws EamDbException;

    /**
     * Gets list of matching eamArtifact instances that have knownStatus =
     * "Bad".
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return List with 0 or more matching eamArtifact instances.
     */
    List<EamArtifactInstance> getArtifactInstancesKnownBad(EamArtifact.Type aType, String value) throws EamDbException;

    /**
     * Count matching eamArtifacts instances that have knownStatus = "Bad".
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Number of matching eamArtifacts
     */
    Long getCountArtifactInstancesKnownBad(EamArtifact.Type aType, String value) throws EamDbException;

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
    List<String> getListCasesHavingArtifactInstancesKnownBad(EamArtifact.Type aType, String value) throws EamDbException;

    /**
     * Is the artifact known as bad according to the reference entries?
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Global known status of the artifact
     */
    boolean isArtifactlKnownBadByReference(EamArtifact.Type aType, String value) throws EamDbException;

    /**
     * Add a new organization
     *
     * @param eamOrg The organization to add
     *
     * @throws EamDbException
     */
    void newOrganization(EamOrganization eamOrg) throws EamDbException;

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
     * Add a new Global Set
     *
     * @param eamGlobalSet The global set to add
     *
     * @return The ID of the new global set
     *
     * @throws EamDbException
     */
    int newReferencelSet(EamGlobalSet eamGlobalSet) throws EamDbException;

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
     * Add a new reference instance
     *
     * @param eamGlobalFileInstance The reference instance to add
     * @param correlationType       Correlation Type that this Reference
     *                              Instance is
     *
     * @throws EamDbException
     */
    void addReferenceInstance(EamGlobalFileInstance eamGlobalFileInstance, EamArtifact.Type correlationType) throws EamDbException;

    /**
     * Add a new global file instance to the bulk collection
     *
     * @param eamGlobalFileInstance The global file instance to add
     *
     * @throws EamDbException
     */
//    void prepareGlobalFileInstance(EamGlobalFileInstance eamGlobalFileInstance) throws EamDbException;
    /**
     * Insert the bulk collection of Global File Instances
     *
     * @param globalInstances a Set of EamGlobalFileInstances to insert into the
     *                        db.
     * @param contentType     the Type of the global instances
     *
     * @throws EamDbException
     */
    void bulkInsertReferenceTypeEntries(Set<EamGlobalFileInstance> globalInstances, EamArtifact.Type contentType) throws EamDbException;

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
    List<EamGlobalFileInstance> getReferenceInstancesByTypeValue(EamArtifact.Type aType, String aValue) throws EamDbException;

    /**
     * Add a new EamArtifact.Type to the db.
     *
     * @param newType New type to add.
     *
     * @return Type.ID for newType
     *
     * @throws EamDbException
     */
    public int newCorrelationType(EamArtifact.Type newType) throws EamDbException;

    /**
     * Get the list of EamArtifact.Type's that will be used to correlate
     * artifacts.
     *
     * @return List of EamArtifact.Type's. If none are defined in the database,
     *         the default list will be returned.
     *
     * @throws EamDbException
     */
    public List<EamArtifact.Type> getCorrelationTypes() throws EamDbException;

    /**
     * Get the list of enabled EamArtifact.Type's that will be used to correlate
     * artifacts.
     *
     * @return List of enabled EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     *
     * @throws EamDbException
     */
    public List<EamArtifact.Type> getEnabledCorrelationTypes() throws EamDbException;

    /**
     * Get the list of supported EamArtifact.Type's that can be used to
     * correlate artifacts.
     *
     * @return List of supported EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     *
     * @throws EamDbException
     */
    public List<EamArtifact.Type> getSupportedCorrelationTypes() throws EamDbException;

    /**
     * Update a EamArtifact.Type.
     *
     * @param aType EamArtifact.Type to update.
     *
     * @throws EamDbException
     */
    public void updateCorrelationType(EamArtifact.Type aType) throws EamDbException;

    /**
     * Get the EamArtifact.Type that has the given Type.Id.
     *
     * @param typeId Type.Id of Correlation Type to get
     *
     * @return EamArtifact.Type or null if it doesn't exist.
     *
     * @throws EamDbException
     */
    public EamArtifact.Type getCorrelationTypeById(int typeId) throws EamDbException;
}
