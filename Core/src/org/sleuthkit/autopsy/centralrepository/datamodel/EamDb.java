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
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.casemodule.Case;

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
     * Placeholder version to use for non-read only databases
     * @return The version that will be stored in the database
     */
    static String getDefaultVersion() {
        return "";
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
    void newCase(CorrelationCase eamCase) throws EamDbException;

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
     * Retrieves Case details based on Case UUID
     *
     * @param caseUUID unique identifier for a case
     *
     * @return The retrieved case
     */
    CorrelationCase getCaseByUUID(String caseUUID) throws EamDbException;

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
    CorrelationDataSource getDataSourceDetails(CorrelationCase correlationCase, String dataSourceDeviceId) throws EamDbException;

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
    void addArtifact(CorrelationAttribute eamArtifact) throws EamDbException;

    /**
     * Retrieves eamArtifact instances from the database that are associated
     * with the eamArtifactType and eamArtifactValue of the given eamArtifact.
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return List of artifact instances for a given type/value
     */
    List<CorrelationAttributeInstance> getArtifactInstancesByTypeValue(CorrelationAttribute.Type aType, String value) throws EamDbException;

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
    List<CorrelationAttributeInstance> getArtifactInstancesByPath(CorrelationAttribute.Type aType, String filePath) throws EamDbException;

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
    Long getCountArtifactInstancesByTypeValue(CorrelationAttribute.Type aType, String value) throws EamDbException;

    /**
     * Calculate the percentage of data sources that have this attribute value.
     *
     * @param corAttr Attribute type and value to get data about
     *
     * @return Int between 0 and 100
     */
    int getFrequencyPercentage(CorrelationAttribute corAttr) throws EamDbException;

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
    Long getCountUniqueCaseDataSourceTuplesHavingTypeValue(CorrelationAttribute.Type aType, String value) throws EamDbException;

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
     * threshold or a call to bulkInsertArtifacts().
     *
     * @param eamArtifact The artifact to add
     */
    void prepareBulkArtifact(CorrelationAttribute eamArtifact) throws EamDbException;

    /**
     * Executes a bulk insert of the eamArtifacts added from the
     * prepareBulkArtifact() method
     */
    void bulkInsertArtifacts() throws EamDbException;

    /**
     * Executes a bulk insert of the cases
     */
    void bulkInsertCases(List<CorrelationCase> cases) throws EamDbException;

    /**
     * Sets an eamArtifact instance to the given known status. If eamArtifact
     * exists, it is updated. If eamArtifact does not exist nothing happens
     *
     * @param eamArtifact Artifact containing exactly one (1) ArtifactInstance.
     * @param knownStatus The status to change the artifact to
     */
    void setArtifactInstanceKnownStatus(CorrelationAttribute eamArtifact, TskData.FileKnown knownStatus) throws EamDbException;

    /**
     * Gets list of matching eamArtifact instances that have knownStatus =
     * "Bad".
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return List with 0 or more matching eamArtifact instances.
     */
    List<CorrelationAttributeInstance> getArtifactInstancesKnownBad(CorrelationAttribute.Type aType, String value) throws EamDbException;

    /**
     * Count matching eamArtifacts instances that have knownStatus = "Bad".
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Number of matching eamArtifacts
     */
    Long getCountArtifactInstancesKnownBad(CorrelationAttribute.Type aType, String value) throws EamDbException;

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
    List<String> getListCasesHavingArtifactInstancesKnownBad(CorrelationAttribute.Type aType, String value) throws EamDbException;

    /**
     * Remove a reference set and all hashes contained in it.
     * @param referenceSetID
     * @throws EamDbException 
     */
    public void deleteReferenceSet(int referenceSetID) throws EamDbException;
    
    /**
     * Check whether the given reference set exists in the central repository.
     * @param referenceSetID
     * @param hashSetName
     * @param version
     * @return true if a matching entry exists in the central repository
     * @throws EamDbException
     */
    public boolean referenceSetIsValid(int referenceSetID, String hashSetName, String version) throws EamDbException;
    
    /**
     * Check whether a reference set with the given name/version is in the central repo
     * @param hashSetName
     * @param version
     * @return true if a matching set is found
     * @throws EamDbException 
     */
    public boolean referenceSetExists(String hashSetName, String version) throws EamDbException;
    
    /**
     * Check if the given hash is in a specific reference set
     * @param hash
     * @param referenceSetID
     * @return true if the hash is found in the reference set
     */
    public boolean isHashInReferenceSet(String hash, int referenceSetID) throws EamDbException;
    
    /**
     * Is the artifact known as bad according to the reference entries?
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Global known status of the artifact
     */
    boolean isArtifactlKnownBadByReference(CorrelationAttribute.Type aType, String value) throws EamDbException;

    /**
     * Add a new organization
     *
     * @param eamOrg The organization to add
     *
     * @return the Organization ID of the newly created organization.
     * 
     * @throws EamDbException
     */
    long newOrganization(EamOrganization eamOrg) throws EamDbException;

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
     * @param referenceSetID ID of the reference set
     * @return The organization object
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
    int newReferencelSet(EamGlobalSet eamGlobalSet) throws EamDbException;

    /**
     * Add a new reference set
     * 
     * @param orgID
     * @param setName
     * @param version
     * @param importDate
     * @return the reference set ID of the newly created set
     * @throws EamDbException 
     */
    int newReferenceSet(int orgID, String setName, String version, TskData.FileKnown knownStatus,
            boolean isReadOnly) throws EamDbException;   

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
     * @return List of all reference sets in the central repository
     *
     * @throws EamDbException
     */
    List<EamGlobalSet> getAllReferenceSets() throws EamDbException;    

    /**
     * Add a new reference instance
     *
     * @param eamGlobalFileInstance The reference instance to add
     * @param correlationType       Correlation Type that this Reference
     *                              Instance is
     *
     * @throws EamDbException
     */
    void addReferenceInstance(EamGlobalFileInstance eamGlobalFileInstance, CorrelationAttribute.Type correlationType) throws EamDbException;
    
    /**
     * Insert the bulk collection of Global File Instances
     *
     * @param globalInstances a Set of EamGlobalFileInstances to insert into the
     *                        db.
     * @param contentType     the Type of the global instances
     *
     * @throws EamDbException
     */
    void bulkInsertReferenceTypeEntries(Set<EamGlobalFileInstance> globalInstances, CorrelationAttribute.Type contentType) throws EamDbException;

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
    List<EamGlobalFileInstance> getReferenceInstancesByTypeValue(CorrelationAttribute.Type aType, String aValue) throws EamDbException;

    /**
     * Add a new EamArtifact.Type to the db.
     *
     * @param newType New type to add.
     *
     * @return Type.ID for newType
     *
     * @throws EamDbException
     */
    public int newCorrelationType(CorrelationAttribute.Type newType) throws EamDbException;

    /**
     * Get the list of EamArtifact.Type's that are defined in the DB and can be
     * used to correlate artifacts.
     *
     * @return List of EamArtifact.Type's. If none are defined in the database,
     *         the default list will be returned.
     *
     * @throws EamDbException
     */
    public List<CorrelationAttribute.Type> getDefinedCorrelationTypes() throws EamDbException;

    /**
     * Get the list of enabled EamArtifact.Type's that will be used to correlate
     * artifacts.
     *
     * @return List of enabled EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     *
     * @throws EamDbException
     */
    public List<CorrelationAttribute.Type> getEnabledCorrelationTypes() throws EamDbException;

    /**
     * Get the list of supported EamArtifact.Type's that can be used to
     * correlate artifacts.
     *
     * @return List of supported EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     *
     * @throws EamDbException
     */
    public List<CorrelationAttribute.Type> getSupportedCorrelationTypes() throws EamDbException;

    /**
     * Update a EamArtifact.Type.
     *
     * @param aType EamArtifact.Type to update.
     *
     * @throws EamDbException
     */
    public void updateCorrelationType(CorrelationAttribute.Type aType) throws EamDbException;

    /**
     * Get the EamArtifact.Type that has the given Type.Id.
     *
     * @param typeId Type.Id of Correlation Type to get
     *
     * @return EamArtifact.Type or null if it doesn't exist.
     *
     * @throws EamDbException
     */
    public CorrelationAttribute.Type getCorrelationTypeById(int typeId) throws EamDbException;
}
