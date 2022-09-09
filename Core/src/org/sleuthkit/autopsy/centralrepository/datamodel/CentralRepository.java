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

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount.CentralRepoAccountType;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.datamodel.HashHitInfo;
import org.sleuthkit.datamodel.InvalidAccountIDException;

/**
 * Main interface for interacting with the database
 */
public interface CentralRepository {

    /**
     * Get the instance
     *
     * @return The EamDb instance or null if one is not configured.
     *
     * @throws CentralRepoException
     */
    static CentralRepository getInstance() throws CentralRepoException {

        CentralRepoPlatforms selectedPlatform = CentralRepoPlatforms.DISABLED;
        if (CentralRepoDbUtil.allowUseOfCentralRepository()) {
            selectedPlatform = CentralRepoDbManager.getSavedDbChoice().getDbPlatform();
        }
        switch (selectedPlatform) {
            case POSTGRESQL:
                return PostgresCentralRepo.getInstance();

            case SQLITE:
                return SqliteCentralRepo.getInstance();
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
     * @throws CentralRepoException if there is a problem closing the connection
     *                              pool.
     */
    void shutdownConnections() throws CentralRepoException;

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
    void reset() throws CentralRepoException;

    /**
     * Is the database enabled?
     *
     * @return Is the database enabled
     */
    static boolean isEnabled() {
        return CentralRepoDbUtil.allowUseOfCentralRepository()
                && CentralRepoDbManager.getSavedDbChoice() != CentralRepoDbChoice.DISABLED;
    }

    /**
     * Add a new name/value pair in the db_info table.
     *
     * @param name  Key to set
     * @param value Value to set
     *
     * @throws CentralRepoException
     */
    void newDbInfo(String name, String value) throws CentralRepoException;

    /**
     * Set the data source object id for a specific entry in the data_sources
     * table
     *
     * @param rowId              - the row id for the data_sources table entry
     * @param dataSourceObjectId - the object id for the data source from the
     *                           caseDb
     */
    void addDataSourceObjectId(int rowId, long dataSourceObjectId) throws CentralRepoException;

    /**
     * Get the value for the given name from the name/value db_info table.
     *
     * @param name Name to search for
     *
     * @return value associated with name.
     *
     * @throws CentralRepoException
     */
    String getDbInfo(String name) throws CentralRepoException;

    /**
     * Update the value for a name in the name/value db_info table.
     *
     * @param name  Name to find
     * @param value Value to assign to name.
     *
     * @throws CentralRepoException
     */
    void updateDbInfo(String name, String value) throws CentralRepoException;

    /**
     * Creates new Case in the database
     *
     * Expects the Organization for this case to already exist in the database.
     *
     * @param eamCase The case to add
     */
    CorrelationCase newCase(CorrelationCase eamCase) throws CentralRepoException;

    /**
     * Creates new Case in the database from the given case
     *
     * @param autopsyCase The case to add
     */
    CorrelationCase newCase(Case autopsyCase) throws CentralRepoException;

    /**
     * Updates an existing Case in the database
     *
     * @param eamCase The case to update
     */
    void updateCase(CorrelationCase eamCase) throws CentralRepoException;

    /**
     * Queries the examiner table for the given user name. Adds a row if the
     * user is not found in the examiner table.
     *
     * @param examinerLoginName user name to look for.
     *
     * @return CentralRepoExaminer for the given user name.
     *
     * @throws CentralRepoException If there is an error in looking up or
     *                              inserting the user in the examiners table.
     */
    CentralRepoExaminer getOrInsertExaminer(String examinerLoginName) throws CentralRepoException;

    /**
     * Retrieves Central Repo case based on an Autopsy Case
     *
     * @param autopsyCase Autopsy case to find corresponding CR case for
     *
     * @return CR Case
     *
     * @throws CentralRepoException
     */
    CorrelationCase getCase(Case autopsyCase) throws CentralRepoException;

    /**
     * Retrieves Case details based on Case UUID
     *
     * @param caseUUID unique identifier for a case
     *
     * @return The retrieved case
     */
    CorrelationCase getCaseByUUID(String caseUUID) throws CentralRepoException;

    /**
     * Retrieves Case details based on Case ID
     *
     * @param caseId unique identifier for a case
     *
     * @return The retrieved case
     */
    CorrelationCase getCaseById(int caseId) throws CentralRepoException;

    /**
     * Retrieves cases that are in DB.
     *
     * @return List of cases
     */
    List<CorrelationCase> getCases() throws CentralRepoException;

    /**
     * Creates new Data Source in the database
     *
     * @param eamDataSource the data source to add
     *
     * @return - A CorrelationDataSource object with data source's central
     *         repository id
     */
    CorrelationDataSource newDataSource(CorrelationDataSource eamDataSource) throws CentralRepoException;

    /**
     * Updates the MD5 hash value in an existing data source in the database.
     *
     * @param eamDataSource The data source to update
     */
    void updateDataSourceMd5Hash(CorrelationDataSource eamDataSource) throws CentralRepoException;

    /**
     * Updates the SHA-1 hash value in an existing data source in the database.
     *
     * @param eamDataSource The data source to update
     */
    void updateDataSourceSha1Hash(CorrelationDataSource eamDataSource) throws CentralRepoException;

    /**
     * Updates the SHA-256 hash value in an existing data source in the
     * database.
     *
     * @param eamDataSource The data source to update
     */
    void updateDataSourceSha256Hash(CorrelationDataSource eamDataSource) throws CentralRepoException;

    /**
     * Retrieves Data Source details based on data source device ID
     *
     * @param correlationCase    the current CorrelationCase used for ensuring
     *                           uniqueness of DataSource
     * @param caseDbDataSourceId the data source device ID number
     *
     * @return The data source
     */
    CorrelationDataSource getDataSource(CorrelationCase correlationCase, Long caseDbDataSourceId) throws CentralRepoException;

    /**
     * Retrieves Data Source details based on data source ID
     *
     * @param correlationCase the current CorrelationCase used for ensuring
     *                        uniqueness of DataSource
     * @param dataSourceId    the data source ID number
     *
     * @return The data source
     */
    CorrelationDataSource getDataSourceById(CorrelationCase correlationCase, int dataSourceId) throws CentralRepoException;

    /**
     * Retrieves data sources that are in DB
     *
     * @return List of data sources
     */
    List<CorrelationDataSource> getDataSources() throws CentralRepoException;

    /**
     * Changes the name of a data source in the DB
     *
     * @param eamDataSource The data source
     * @param newName       The new name
     *
     * @throws CentralRepoException
     */
    void updateDataSourceName(CorrelationDataSource eamDataSource, String newName) throws CentralRepoException;

    /**
     * Inserts new Artifact(s) into the database. Should add associated Case and
     * Data Source first.
     *
     * @param eamArtifact The artifact to add
     */
    void addArtifactInstance(CorrelationAttributeInstance eamArtifact) throws CentralRepoException;

    /**
     * Retrieves eamArtifact instances from the database that are associated
     * with the eamArtifactType and eamArtifactValues of the given eamArtifact.
     *
     * @param aType  EamArtifact.Type to search for
     * @param values The list of correlation values to get
     *               CorrelationAttributeInstances for
     *
     * @return List of artifact instances for a given type with the specified
     *         values
     *
     * @throws CorrelationAttributeNormalizationException
     * @throws CentralRepoException
     */
    List<CorrelationAttributeInstance> getArtifactInstancesByTypeValues(CorrelationAttributeInstance.Type aType, List<String> values) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Retrieves correlation attribute instances from the central repository
     * that match a given attribute type and value.
     *
     * @param type  The correlation attribute type.
     * @param value The correlation attribute value.
     *
     * @return The matching correlation attribute instances.
     *
     * @throws CorrelationAttributeNormalizationException The exception is
     *                                                    thrown if the supplied
     *                                                    correlation attribute
     *                                                    value cannot be
     *                                                    normlaized.
     * @throws CentralRepoException                       The exception is
     *                                                    thrown if there is an
     *                                                    error querying the
     *                                                    central repository.
     */
    List<CorrelationAttributeInstance> getArtifactInstancesByTypeValue(CorrelationAttributeInstance.Type type, String value) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Retrieves eamArtifact instances from the database that are associated
     * with the eamArtifactType and eamArtifactValues of the given eamArtifact
     * for the specified cases.
     *
     * @param aType   The type of the artifact
     * @param values  The list of correlation values to get
     *                CorrelationAttributeInstances for
     * @param caseIds The list of central repository case ids to get
     *                CorrelationAttributeInstances for
     *
     * @return List of artifact instances for a given type with the specified
     *         values for the specified cases
     *
     * @throws CorrelationAttributeNormalizationException
     * @throws CentralRepoException
     */
    List<CorrelationAttributeInstance> getArtifactInstancesByTypeValuesAndCases(CorrelationAttributeInstance.Type aType, List<String> values, List<Integer> caseIds) throws CentralRepoException, CorrelationAttributeNormalizationException;

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
    Long getCountArtifactInstancesByTypeValue(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Calculate the percentage of data sources that have this attribute value.
     *
     * @param corAttr Attribute type and value to get data about
     *
     * @return Int between 0 and 100
     */
    int getFrequencyPercentage(CorrelationAttributeInstance corAttr) throws CentralRepoException, CorrelationAttributeNormalizationException;

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
    Long getCountUniqueCaseDataSourceTuplesHavingTypeValue(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Gets the count of cases that have an instance of a given correlation
     * attribute.
     *
     * This count will ignore the specified instance of the correlation
     * attribute and any other instances of this correlation attribute existing
     * on the same object.
     *
     * @param instance The instance having its cases with other occurrences
     *                 counted.
     *
     * @return Number of cases with additional instances of this
     *         CorrelationAttributeInstance.
     */
    Long getCountCasesWithOtherInstances(CorrelationAttributeInstance instance) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Retrieves number of data sources in the database.
     *
     * @return Number of unique data sources
     */
    Long getCountUniqueDataSources() throws CentralRepoException;

    /**
     * Retrieves number of eamArtifact instances in the database that are
     * associated with the given data source.
     *
     * @param correlationDataSource Data source to search for
     *
     * @return Number of artifact instances having caseDisplayName and
     *         dataSource
     */
    Long getCountArtifactInstancesByCaseDataSource(CorrelationDataSource correlationDataSource) throws CentralRepoException;

    /**
     * Adds an eamArtifact to an internal list to be later added to DB. Artifact
     * can have 1 or more Artifact Instances. Insert will be triggered by a
     * threshold or a call to commitAttributeInstancesBulk().
     *
     * @param eamArtifact The artifact to add
     */
    void addAttributeInstanceBulk(CorrelationAttributeInstance eamArtifact) throws CentralRepoException;

    /**
     * Executes a bulk insert of the eamArtifacts added from the
     * addAttributeInstanceBulk() method
     */
    void commitAttributeInstancesBulk() throws CentralRepoException;

    /**
     * Executes a bulk insert of the cases
     */
    void bulkInsertCases(List<CorrelationCase> cases) throws CentralRepoException;

    /**
     * Update a correlation attribute instance comment in the database with that
     * in the associated CorrelationAttribute object.
     *
     * @param eamArtifact The correlation attribute whose database instance will
     *                    be updated.
     *
     * @throws CentralRepoException
     */
    void updateAttributeInstanceComment(CorrelationAttributeInstance eamArtifact) throws CentralRepoException;

    /**
     * Find a correlation attribute in the Central Repository database given the
     * instance type, case, data source, value, and file path.
     *
     * Method exists to support instances added using Central Repository version
     * 1,1 and older
     *
     * @param type                  The type of instance.
     * @param correlationCase       The case tied to the instance.
     * @param correlationDataSource The data source tied to the instance.
     * @param value                 The value tied to the instance.
     * @param filePath              The file path tied to the instance.
     *
     * @return The correlation attribute if it exists; otherwise null.
     *
     * @throws CentralRepoException
     */
    CorrelationAttributeInstance getCorrelationAttributeInstance(CorrelationAttributeInstance.Type type, CorrelationCase correlationCase,
            CorrelationDataSource correlationDataSource, String value, String filePath) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Find a correlation attribute in the Central Repository database given the
     * instance type, case, data source, object id.
     *
     * @param type                  The type of instance.
     * @param correlationCase       The case tied to the instance.
     * @param correlationDataSource The data source tied to the instance.
     * @param objectID              The object id of the file tied to the
     *                              instance.
     *
     * @return The correlation attribute if it exists; otherwise null.
     *
     * @throws CentralRepoException
     */
    CorrelationAttributeInstance getCorrelationAttributeInstance(CorrelationAttributeInstance.Type type, CorrelationCase correlationCase,
            CorrelationDataSource correlationDataSource, long objectID) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Sets an eamArtifact instance to the given known status. If eamArtifact
     * exists, it is updated. If eamArtifact does not exist nothing happens
     *
     * @param eamArtifact Artifact containing exactly one (1) ArtifactInstance.
     * @param knownStatus The status to change the artifact to
     */
    void setAttributeInstanceKnownStatus(CorrelationAttributeInstance eamArtifact, TskData.FileKnown knownStatus) throws CentralRepoException;

    /**
     * Count matching eamArtifacts instances that have knownStatus = "Bad".
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Number of matching eamArtifacts
     */
    Long getCountArtifactInstancesKnownBad(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException;

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
    List<String> getListCasesHavingArtifactInstancesKnownBad(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Gets list of distinct case display names, where each case has 1+ Artifact
     * Instance matching eamArtifact.
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return List of cases containing this artifact with instances marked as
     *         bad
     *
     * @throws CentralRepoException
     */
    List<String> getListCasesHavingArtifactInstances(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Remove a reference set and all values contained in it.
     *
     * @param referenceSetID
     *
     * @throws CentralRepoException
     */
    void deleteReferenceSet(int referenceSetID) throws CentralRepoException;

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
     * @throws CentralRepoException
     */
    boolean referenceSetIsValid(int referenceSetID, String referenceSetName, String version) throws CentralRepoException;

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
    boolean referenceSetExists(String referenceSetName, String version) throws CentralRepoException;

    /**
     * Check if the given file hash is in this reference set. Only searches the
     * reference_files table.
     *
     * @param hash
     * @param referenceSetID
     *
     * @return true if the hash is found in the reference set
     *
     * @throws CentralRepoException
     */
    boolean isFileHashInReferenceSet(String hash, int referenceSetID) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Retrieves the given file HashHitInfo if the given file hash is in this
     * reference set. Only searches the reference_files table.
     *
     * @param hash           The hash to find in a search.
     * @param referenceSetID The referenceSetID within which the file should
     *                       exist.
     *
     * @return The HashHitInfo if found or null if not found.
     *
     * @throws CentralRepoException
     * @throws CorrelationAttributeNormalizationException
     */
    HashHitInfo lookupHash(String hash, int referenceSetID) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Check if the given value is in a specific reference set
     *
     * @param value
     * @param referenceSetID
     * @param correlationTypeID
     *
     * @return true if the hash is found in the reference set
     */
    boolean isValueInReferenceSet(String value, int referenceSetID, int correlationTypeID) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Is the artifact known as bad according to the reference entries?
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Global known status of the artifact
     */
    boolean isArtifactKnownBadByReference(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Add a new organization
     *
     * @param eamOrg The organization to add
     *
     * @return The organization with the org ID set.
     *
     * @throws CentralRepoException
     */
    CentralRepoOrganization newOrganization(CentralRepoOrganization eamOrg) throws CentralRepoException;

    /**
     * Get all organizations
     *
     * @return A list of all organizations
     *
     * @throws CentralRepoException
     */
    List<CentralRepoOrganization> getOrganizations() throws CentralRepoException;

    /**
     * Get an organization having the given ID
     *
     * @param orgID The id to look up
     *
     * @return The organization with the given ID
     *
     * @throws CentralRepoException
     */
    CentralRepoOrganization getOrganizationByID(int orgID) throws CentralRepoException;

    /**
     * Get the organization associated with the given reference set.
     *
     * @param referenceSetID ID of the reference set
     *
     * @return The organization object
     *
     * @throws CentralRepoException
     */
    CentralRepoOrganization getReferenceSetOrganization(int referenceSetID) throws CentralRepoException;

    /**
     * Update an existing organization.
     *
     * @param updatedOrganization the values the Organization with the same ID
     *                            will be updated to in the database.
     *
     * @throws CentralRepoException
     */
    void updateOrganization(CentralRepoOrganization updatedOrganization) throws CentralRepoException;

    /**
     * Delete an organization if it is not being used by any case.
     *
     * @param organizationToDelete the organization to be deleted
     *
     * @throws CentralRepoException
     */
    void deleteOrganization(CentralRepoOrganization organizationToDelete) throws CentralRepoException;

    /**
     * Add a new Global Set
     *
     * @param eamGlobalSet The global set to add
     *
     * @return The ID of the new global set
     *
     * @throws CentralRepoException
     */
    int newReferenceSet(CentralRepoFileSet eamGlobalSet) throws CentralRepoException;

    /**
     * Get a global set by ID
     *
     * @param globalSetID The ID to look up
     *
     * @return The global set associated with the ID
     *
     * @throws CentralRepoException
     */
    CentralRepoFileSet getReferenceSetByID(int globalSetID) throws CentralRepoException;

    /**
     * Get all reference sets
     *
     * @param correlationType Type of sets to return
     *
     * @return List of all reference sets in the central repository
     *
     * @throws CentralRepoException
     */
    List<CentralRepoFileSet> getAllReferenceSets(CorrelationAttributeInstance.Type correlationType) throws CentralRepoException;

    /**
     * Add a new reference instance
     *
     * @param eamGlobalFileInstance The reference instance to add
     * @param correlationType       Correlation Type that this Reference
     *                              Instance is
     *
     * @throws CentralRepoException
     */
    void addReferenceInstance(CentralRepoFileInstance eamGlobalFileInstance, CorrelationAttributeInstance.Type correlationType) throws CentralRepoException;

    /**
     * Insert the bulk collection of Global File Instances
     *
     * @param globalInstances a Set of EamGlobalFileInstances to insert into the
     *                        db.
     * @param contentType     the Type of the global instances
     *
     * @throws CentralRepoException
     */
    void bulkInsertReferenceTypeEntries(Set<CentralRepoFileInstance> globalInstances, CorrelationAttributeInstance.Type contentType) throws CentralRepoException;

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
    List<CentralRepoFileInstance> getReferenceInstancesByTypeValue(CorrelationAttributeInstance.Type aType, String aValue) throws CentralRepoException, CorrelationAttributeNormalizationException;

    /**
     * Add a new EamArtifact.Type to the db.
     *
     * @param newType New type to add.
     *
     * @return Type.ID for newType
     *
     * @throws CentralRepoException
     */
    int newCorrelationType(CorrelationAttributeInstance.Type newType) throws CentralRepoException;

    /**
     * Get the list of EamArtifact.Type's that are defined in the DB and can be
     * used to correlate artifacts.
     *
     * @return List of EamArtifact.Type's. If none are defined in the database,
     *         the default list will be returned.
     *
     * @throws CentralRepoException
     */
    List<CorrelationAttributeInstance.Type> getDefinedCorrelationTypes() throws CentralRepoException;

    /**
     * Get the list of enabled EamArtifact.Type's that will be used to correlate
     * artifacts.
     *
     * @return List of enabled EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     *
     * @throws CentralRepoException
     */
    List<CorrelationAttributeInstance.Type> getEnabledCorrelationTypes() throws CentralRepoException;

    /**
     * Get the list of supported EamArtifact.Type's that can be used to
     * correlate artifacts.
     *
     * @return List of supported EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     *
     * @throws CentralRepoException
     */
    List<CorrelationAttributeInstance.Type> getSupportedCorrelationTypes() throws CentralRepoException;

    /**
     * Update a EamArtifact.Type.
     *
     * @param aType EamArtifact.Type to update.
     *
     * @throws CentralRepoException
     */
    void updateCorrelationType(CorrelationAttributeInstance.Type aType) throws CentralRepoException;

    /**
     * Get the EamArtifact.Type that has the given Type.Id.
     *
     * @param typeId Type.Id of Correlation Type to get
     *
     * @return EamArtifact.Type or null if it doesn't exist.
     *
     * @throws CentralRepoException
     */
    CorrelationAttributeInstance.Type getCorrelationTypeById(int typeId) throws CentralRepoException;

    /**
     * Upgrade the schema of the database (if needed)
     *
     * @throws CentralRepoException
     */
    void upgradeSchema() throws CentralRepoException, SQLException, IncompatibleCentralRepoException;

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
    CoordinationService.Lock getExclusiveMultiUserDbLock() throws CentralRepoException;

    /**
     * Process the Artifact instance in the EamDb
     *
     * @param type                  EamArtifact.Type to search for
     * @param instanceTableCallback callback to process the instance
     *
     * @throws CentralRepoException
     */
    void processInstanceTable(CorrelationAttributeInstance.Type type, InstanceTableCallback instanceTableCallback) throws CentralRepoException;

    /**
     * Process the Artifact instance in the EamDb
     *
     * @param type                  EamArtifact.Type to search for
     * @param instanceTableCallback callback to process the instance
     * @param whereClause           query string to execute
     *
     * @throws CentralRepoException
     */
    void processInstanceTableWhere(CorrelationAttributeInstance.Type type, String whereClause, InstanceTableCallback instanceTableCallback) throws CentralRepoException;

    /**
     * Process a SELECT query
     *
     * @param selectClause          query string to execute
     * @param instanceTableCallback callback to process the instance
     *
     * @throws CentralRepoException
     */
    void processSelectClause(String selectClause, InstanceTableCallback instanceTableCallback) throws CentralRepoException;

    /**
     * Executes an INSERT/UPDATE/DELETE sql as a prepared statement, on the
     * central repository database.
     *
     * @param sql    sql to execute.
     * @param params List of query params to use, may be empty.
     *
     * @throws CentralRepoException If there is an error.
     */
    void executeCommand(String sql, List<Object> params) throws CentralRepoException;

    /**
     * Executes a SELECT query sql as a prepared statement, on the central
     * repository database.
     *
     * @param sql           sql to execute.
     * @param params        List of query params to use, may be empty.
     * @param queryCallback Query callback to handle the result of the query.
     *
     * @throws CentralRepoException If there is an error.
     */
    void executeQuery(String sql, List<Object> params, CentralRepositoryDbQueryCallback queryCallback) throws CentralRepoException;

    /**
     * Get account type by type name.
     *
     * @param accountTypeName account type name to look for
     *
     * @return CR account type (if found)
     *
     * @throws CentralRepoException
     */
    Optional<CentralRepoAccountType> getAccountTypeByName(String accountTypeName) throws CentralRepoException;

    /**
     * Gets all account types.
     *
     * @return Collection of all CR account types in the database.
     *
     * @throws CentralRepoException
     */
    Collection<CentralRepoAccountType> getAllAccountTypes() throws CentralRepoException;

    /**
     * Get an account from the accounts table matching the given type/ID.
     * Inserts a row if one doesn't exists.
     *
     * @param crAccountType   CR account type to look for or create
     * @param accountUniqueID type specific unique account id
     *
     * @return CR account
     *
     * @throws CentralRepoException      If there is an error accessing Central
     *                                   Repository.
     * @throws InvalidAccountIDException If the account identifier is not valid.
     */
    CentralRepoAccount getOrCreateAccount(CentralRepoAccount.CentralRepoAccountType crAccountType, String accountUniqueID) throws InvalidAccountIDException, CentralRepoException;

    /**
     * Gets an account from the accounts table matching the given type/ID, if
     * one exists.
     *
     * @param crAccountType   CR account type to look for or create
     * @param accountUniqueID type specific unique account id
     *
     * @return CR account, if found, null otherwise.
     *
     * @throws CentralRepoException      If there is an error accessing Central
     *                                   Repository.
     * @throws InvalidAccountIDException If the account identifier is not valid.
     */
    CentralRepoAccount getAccount(CentralRepoAccount.CentralRepoAccountType crAccountType, String accountUniqueID) throws InvalidAccountIDException, CentralRepoException;

}
