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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount.CentralRepoAccountType;
import static org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil.updateSchemaVersion;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.healthmonitor.HealthMonitor;
import org.sleuthkit.autopsy.healthmonitor.TimingMetric;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.CaseDbSchemaVersionNumber;
import org.sleuthkit.datamodel.HashHitInfo;
import org.sleuthkit.datamodel.InvalidAccountIDException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;

/**
 *
 * Generic JDBC methods
 *
 */
abstract class RdbmsCentralRepo implements CentralRepository {

    private final static Logger logger = Logger.getLogger(RdbmsCentralRepo.class.getName());
    static final String SCHEMA_MAJOR_VERSION_KEY = "SCHEMA_VERSION";
    static final String SCHEMA_MINOR_VERSION_KEY = "SCHEMA_MINOR_VERSION";
    static final String CREATION_SCHEMA_MAJOR_VERSION_KEY = "CREATION_SCHEMA_MAJOR_VERSION";
    static final String CREATION_SCHEMA_MINOR_VERSION_KEY = "CREATION_SCHEMA_MINOR_VERSION";
    static final CaseDbSchemaVersionNumber SOFTWARE_CR_DB_SCHEMA_VERSION = new CaseDbSchemaVersionNumber(1, 6);

    protected final List<CorrelationAttributeInstance.Type> defaultCorrelationTypes;

    private int bulkArtifactsCount;
    protected int bulkArtifactsThreshold;
    private final Map<String, Collection<CorrelationAttributeInstance>> bulkArtifacts;
    private static final int CASE_CACHE_TIMEOUT = 5;
    private static final int DATA_SOURCE_CACHE_TIMEOUT = 5;
    private static final int ACCOUNTS_CACHE_TIMEOUT = 5;
    private static final Cache<String, Optional<CentralRepoAccountType>> accountTypesCache = CacheBuilder.newBuilder().build();
    private static final Cache<Pair<CentralRepoAccountType, String>, CentralRepoAccount> accountsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(ACCOUNTS_CACHE_TIMEOUT, TimeUnit.MINUTES).
            build();

    private boolean isCRTypeCacheInitialized;
    private static final Cache<Integer, CorrelationAttributeInstance.Type> typeCache = CacheBuilder.newBuilder().build();
    private static final Cache<String, CorrelationCase> caseCacheByUUID = CacheBuilder.newBuilder()
            .expireAfterWrite(CASE_CACHE_TIMEOUT, TimeUnit.MINUTES).
            build();
    private static final Cache<Integer, CorrelationCase> caseCacheById = CacheBuilder.newBuilder()
            .expireAfterWrite(CASE_CACHE_TIMEOUT, TimeUnit.MINUTES).
            build();
    private static final Cache<String, CorrelationDataSource> dataSourceCacheByDsObjectId = CacheBuilder.newBuilder()
            .expireAfterWrite(DATA_SOURCE_CACHE_TIMEOUT, TimeUnit.MINUTES).
            build();
    private static final Cache<String, CorrelationDataSource> dataSourceCacheById = CacheBuilder.newBuilder()
            .expireAfterWrite(DATA_SOURCE_CACHE_TIMEOUT, TimeUnit.MINUTES).
            build();
    // Maximum length for the value column in the instance tables
    static final int MAX_VALUE_LENGTH = 256;

    // number of instances to keep in bulk queue before doing an insert.
    // Update Test code if this changes.  It's hard coded there.
    static final int DEFAULT_BULK_THRESHHOLD = 1000;

    private static final int QUERY_STR_MAX_LEN = 1000;

    /**
     * Connect to the DB and initialize it.
     *
     * @throws UnknownHostException, EamDbException
     */
    protected RdbmsCentralRepo() throws CentralRepoException {
        isCRTypeCacheInitialized = false;
        bulkArtifactsCount = 0;
        bulkArtifacts = new HashMap<>();

        defaultCorrelationTypes = CorrelationAttributeInstance.getDefaultCorrelationTypes();
        defaultCorrelationTypes.forEach((type) -> {
            bulkArtifacts.put(CentralRepoDbUtil.correlationTypeToInstanceTableName(type), new ArrayList<>());
        });
    }

    /**
     * Setup and create a connection to the selected database implementation
     */
    protected abstract Connection connect(boolean foreignKeys) throws CentralRepoException;

    /**
     * Setup and create a connection to the selected database implementation
     */
    protected abstract Connection connect() throws CentralRepoException;

    /**
     * Get an ephemeral connection.
     */
    protected abstract Connection getEphemeralConnection();

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
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "INSERT INTO db_info (name, value) VALUES (?, ?) "
                + getConflictClause();
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, name);
            preparedStatement.setString(2, value);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new CentralRepoException("Error adding new name/value pair to db_info.", ex);
        } finally {
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

    }

    @Override
    public void addDataSourceObjectId(int rowId, long dataSourceObjectId) throws CentralRepoException {
        Connection conn = connect();
        PreparedStatement preparedStatement = null;
        String sql = "UPDATE data_sources SET datasource_obj_id=? WHERE id=?";
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setLong(1, dataSourceObjectId);
            preparedStatement.setInt(2, rowId);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new CentralRepoException("Error updating data source object id for data_sources row " + rowId, ex);
        } finally {
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
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
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String value = null;
        String sql = "SELECT value FROM db_info WHERE name=?";
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, name);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                value = resultSet.getString("value");
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting value for name.", ex);
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return value;
    }

    /**
     * Reset the contents of the caches associated with EamDb results.
     */
    public final void clearCaches() {
        synchronized (typeCache) {
            typeCache.invalidateAll();
            isCRTypeCacheInitialized = false;
        }
        caseCacheByUUID.invalidateAll();
        caseCacheById.invalidateAll();
        dataSourceCacheByDsObjectId.invalidateAll();
        dataSourceCacheById.invalidateAll();
        accountsCache.invalidateAll();
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
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "UPDATE db_info SET value=? WHERE name=?";
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, value);
            preparedStatement.setString(2, name);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new CentralRepoException("Error updating value for name.", ex);
        } finally {
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Creates new Case in the database
     *
     * Expects the Organization for this case to already exist in the database.
     *
     * @param eamCase The case to add
     *
     * @returns New Case class with populated database ID
     */
    @Override
    public synchronized CorrelationCase newCase(CorrelationCase eamCase) throws CentralRepoException {

        if (eamCase.getCaseUUID() == null) {
            throw new CentralRepoException("Case UUID is null");
        }

        // check if there is already an existing CorrelationCase for this Case
        CorrelationCase cRCase = getCaseByUUID(eamCase.getCaseUUID());
        if (cRCase != null) {
            return cRCase;
        }

        Connection conn = connect();
        PreparedStatement preparedStatement = null;

        String sql = "INSERT INTO cases(case_uid, org_id, case_name, creation_date, case_number, "
                + "examiner_name, examiner_email, examiner_phone, notes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + getConflictClause();
        ResultSet resultSet = null;
        try {
            preparedStatement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            preparedStatement.setString(1, eamCase.getCaseUUID());
            if (null == eamCase.getOrg()) {
                preparedStatement.setNull(2, Types.INTEGER);
            } else {
                preparedStatement.setInt(2, eamCase.getOrg().getOrgID());
            }
            preparedStatement.setString(3, eamCase.getDisplayName());
            preparedStatement.setString(4, eamCase.getCreationDate());
            if ("".equals(eamCase.getCaseNumber())) {
                preparedStatement.setNull(5, Types.INTEGER);
            } else {
                preparedStatement.setString(5, eamCase.getCaseNumber());
            }
            if ("".equals(eamCase.getExaminerName())) {
                preparedStatement.setNull(6, Types.INTEGER);
            } else {
                preparedStatement.setString(6, eamCase.getExaminerName());
            }
            if ("".equals(eamCase.getExaminerEmail())) {
                preparedStatement.setNull(7, Types.INTEGER);
            } else {
                preparedStatement.setString(7, eamCase.getExaminerEmail());
            }
            if ("".equals(eamCase.getExaminerPhone())) {
                preparedStatement.setNull(8, Types.INTEGER);
            } else {
                preparedStatement.setString(8, eamCase.getExaminerPhone());
            }
            if ("".equals(eamCase.getNotes())) {
                preparedStatement.setNull(9, Types.INTEGER);
            } else {
                preparedStatement.setString(9, eamCase.getNotes());
            }

            preparedStatement.executeUpdate();
            //update the case in the caches
            resultSet = preparedStatement.getGeneratedKeys();
            if (!resultSet.next()) {
                throw new CentralRepoException(String.format("Failed to INSERT case %s in central repo", eamCase.getCaseUUID()));
            }
            int caseID = resultSet.getInt(1); //last_insert_rowid()    
            CorrelationCase correlationCase = new CorrelationCase(caseID, eamCase.getCaseUUID(), eamCase.getOrg(),
                    eamCase.getDisplayName(), eamCase.getCreationDate(), eamCase.getCaseNumber(), eamCase.getExaminerName(),
                    eamCase.getExaminerEmail(), eamCase.getExaminerPhone(), eamCase.getNotes());
            caseCacheByUUID.put(eamCase.getCaseUUID(), correlationCase);
            caseCacheById.put(caseID, correlationCase);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error inserting new case.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        // get a new version with the updated ID
        return getCaseByUUID(eamCase.getCaseUUID());
    }

    /**
     * Creates new Case in the database from the given case
     *
     * @param autopsyCase The case to add
     */
    @Override
    public CorrelationCase newCase(Case autopsyCase) throws CentralRepoException {
        if (autopsyCase == null) {
            throw new CentralRepoException("Case is null");
        }

        CorrelationCase curCeCase = new CorrelationCase(
                -1,
                autopsyCase.getName(), // unique case ID
                CentralRepoOrganization.getDefault(),
                autopsyCase.getDisplayName(),
                autopsyCase.getCreatedDate(),
                autopsyCase.getNumber(),
                autopsyCase.getExaminer(),
                autopsyCase.getExaminerEmail(),
                autopsyCase.getExaminerPhone(),
                autopsyCase.getCaseNotes());
        return newCase(curCeCase);
    }

    @Override
    public CorrelationCase getCase(Case autopsyCase) throws CentralRepoException {
        return getCaseByUUID(autopsyCase.getName());
    }

    /**
     * Updates an existing Case in the database
     *
     * @param eamCase The case to update
     */
    @Override
    public void updateCase(CorrelationCase eamCase) throws CentralRepoException {
        if (eamCase == null) {
            throw new CentralRepoException("Correlation case is null");
        }

        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "UPDATE cases "
                + "SET org_id=?, case_name=?, creation_date=?, case_number=?, examiner_name=?, examiner_email=?, examiner_phone=?, notes=? "
                + "WHERE case_uid=?";

        try {
            preparedStatement = conn.prepareStatement(sql);

            if (null == eamCase.getOrg()) {
                preparedStatement.setNull(1, Types.INTEGER);
            } else {
                preparedStatement.setInt(1, eamCase.getOrg().getOrgID());
            }
            preparedStatement.setString(2, eamCase.getDisplayName());
            preparedStatement.setString(3, eamCase.getCreationDate());

            if ("".equals(eamCase.getCaseNumber())) {
                preparedStatement.setNull(4, Types.INTEGER);
            } else {
                preparedStatement.setString(4, eamCase.getCaseNumber());
            }
            if ("".equals(eamCase.getExaminerName())) {
                preparedStatement.setNull(5, Types.INTEGER);
            } else {
                preparedStatement.setString(5, eamCase.getExaminerName());
            }
            if ("".equals(eamCase.getExaminerEmail())) {
                preparedStatement.setNull(6, Types.INTEGER);
            } else {
                preparedStatement.setString(6, eamCase.getExaminerEmail());
            }
            if ("".equals(eamCase.getExaminerPhone())) {
                preparedStatement.setNull(7, Types.INTEGER);
            } else {
                preparedStatement.setString(7, eamCase.getExaminerPhone());
            }
            if ("".equals(eamCase.getNotes())) {
                preparedStatement.setNull(8, Types.INTEGER);
            } else {
                preparedStatement.setString(8, eamCase.getNotes());
            }

            preparedStatement.setString(9, eamCase.getCaseUUID());

            preparedStatement.executeUpdate();
            //update the case in the cache
            caseCacheById.put(eamCase.getID(), eamCase);
            caseCacheByUUID.put(eamCase.getCaseUUID(), eamCase);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error updating case.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Retrieves Case details based on Case UUID from the central repo
     *
     * @param caseUUID unique identifier for a case
     *
     * @return The retrieved case
     */
    @Override
    public CorrelationCase getCaseByUUID(String caseUUID) throws CentralRepoException {
        try {
            return caseCacheByUUID.get(caseUUID, () -> getCaseByUUIDFromCr(caseUUID));
        } catch (CacheLoader.InvalidCacheLoadException ignored) {
            //lambda valueloader returned a null value and cache can not store null values this is normal if the case does not exist in the central repo yet
            return null;
        } catch (ExecutionException ex) {
            throw new CentralRepoException("Error getting autopsy case from Central repo", ex);
        }
    }

    /**
     * Retrieves Case details based on Case UUID
     *
     * @param caseUUID unique identifier for a case
     *
     * @return The retrieved case
     */
    private CorrelationCase getCaseByUUIDFromCr(String caseUUID) throws CentralRepoException {
        Connection conn = connect();

        CorrelationCase eamCaseResult = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String sql = "SELECT cases.id as case_id, case_uid, case_name, creation_date, case_number, examiner_name, "
                + "examiner_email, examiner_phone, notes, organizations.id as org_id, org_name, poc_name, poc_email, poc_phone "
                + "FROM cases "
                + "LEFT JOIN organizations ON cases.org_id=organizations.id "
                + "WHERE case_uid=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, caseUUID);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                eamCaseResult = getEamCaseFromResultSet(resultSet);
            }
            if (eamCaseResult != null) {
                //Update the version in the other cache
                caseCacheById.put(eamCaseResult.getID(), eamCaseResult);
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting case details.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return eamCaseResult;
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
            return caseCacheById.get(caseId, () -> getCaseByIdFromCr(caseId));
        } catch (CacheLoader.InvalidCacheLoadException ignored) {
            //lambda valueloader returned a null value and cache can not store null values this is normal if the case does not exist in the central repo yet
            return null;
        } catch (ExecutionException ex) {
            throw new CentralRepoException("Error getting autopsy case from Central repo", ex);
        }
    }

    /**
     * Retrieves Case details based on Case ID
     *
     * @param caseID unique identifier for a case
     *
     * @return The retrieved case
     */
    private CorrelationCase getCaseByIdFromCr(int caseId) throws CentralRepoException {
        Connection conn = connect();

        CorrelationCase eamCaseResult = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String sql = "SELECT cases.id as case_id, case_uid, case_name, creation_date, case_number, examiner_name, "
                + "examiner_email, examiner_phone, notes, organizations.id as org_id, org_name, poc_name, poc_email, poc_phone "
                + "FROM cases "
                + "LEFT JOIN organizations ON cases.org_id=organizations.id "
                + "WHERE cases.id=?";
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, caseId);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                eamCaseResult = getEamCaseFromResultSet(resultSet);
            }
            if (eamCaseResult != null) {
                //Update the version in the other cache
                caseCacheByUUID.put(eamCaseResult.getCaseUUID(), eamCaseResult);
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting case details.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return eamCaseResult;
    }

    /**
     * Retrieves cases that are in DB.
     *
     * @return List of cases
     */
    @Override
    public List<CorrelationCase> getCases() throws CentralRepoException {
        Connection conn = connect();

        List<CorrelationCase> cases = new ArrayList<>();
        CorrelationCase eamCaseResult;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String sql = "SELECT cases.id as case_id, case_uid, case_name, creation_date, case_number, examiner_name, "
                + "examiner_email, examiner_phone, notes, organizations.id as org_id, org_name, poc_name, poc_email, poc_phone "
                + "FROM cases "
                + "LEFT JOIN organizations ON cases.org_id=organizations.id";

        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                eamCaseResult = getEamCaseFromResultSet(resultSet);
                cases.add(eamCaseResult);
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting all cases.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return cases;
    }

    /**
     * Create a key to the dataSourceCacheByDsObjectId
     *
     * @param caseId             - the id of the CorrelationCase in the Central
     *                           Repository
     * @param dataSourceObjectId - the object id if of the data source in the
     *                           case db
     *
     * @return a String to be used as a key for the dataSourceCacheByDsObjectId
     */
    private static String getDataSourceByDSObjectIdCacheKey(int caseId, Long dataSourceObjectId) {
        return "Case" + caseId + "DsObjectId" + dataSourceObjectId; //NON-NLS
    }

    /**
     * Create a key to the DataSourceCacheById
     *
     * @param caseId       - the id of the CorrelationCase in the Central
     *                     Repository
     * @param dataSourceId - the id of the datasource in the central repository
     *
     * @return a String to be used as a key for the dataSourceCacheById
     */
    private static String getDataSourceByIdCacheKey(int caseId, int dataSourceId) {
        return "Case" + caseId + "Id" + dataSourceId; //NON-NLS
    }

    /**
     * Creates new Data Source in the database
     *
     * @param eamDataSource the data source to add
     */
    @Override
    public CorrelationDataSource newDataSource(CorrelationDataSource eamDataSource) throws CentralRepoException {
        if (eamDataSource.getCaseID() == -1) {
            throw new CentralRepoException("Case ID is -1");
        }
        if (eamDataSource.getDeviceID() == null) {
            throw new CentralRepoException("Device ID is null");
        }
        if (eamDataSource.getName() == null) {
            throw new CentralRepoException("Name is null");
        }
        if (eamDataSource.getID() != -1) {
            // This data source is already in the central repo
            return eamDataSource;
        }

        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        //The conflict clause exists in case multiple nodes are trying to add the data source because it did not exist at the same time
        String sql = "INSERT INTO data_sources(device_id, case_id, name, datasource_obj_id, md5, sha1, sha256) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + getConflictClause();
        ResultSet resultSet = null;
        try {
            preparedStatement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            preparedStatement.setString(1, eamDataSource.getDeviceID());
            preparedStatement.setInt(2, eamDataSource.getCaseID());
            preparedStatement.setString(3, eamDataSource.getName());
            preparedStatement.setLong(4, eamDataSource.getDataSourceObjectID());
            preparedStatement.setString(5, eamDataSource.getMd5());
            preparedStatement.setString(6, eamDataSource.getSha1());
            preparedStatement.setString(7, eamDataSource.getSha256());

            preparedStatement.executeUpdate();
            resultSet = preparedStatement.getGeneratedKeys();
            if (!resultSet.next()) {
                /*
                 * If nothing was inserted, then return the data source that
                 * exists in the Central Repository.
                 *
                 * This is expected to occur with PostgreSQL Central Repository
                 * databases.
                 */
                try {
                    return dataSourceCacheByDsObjectId.get(getDataSourceByDSObjectIdCacheKey(
                            eamDataSource.getCaseID(), eamDataSource.getDataSourceObjectID()),
                            () -> getDataSourceFromCr(eamDataSource.getCaseID(), eamDataSource.getDataSourceObjectID()));
                } catch (CacheLoader.InvalidCacheLoadException | ExecutionException getException) {
                    throw new CentralRepoException(String.format("Unable to to INSERT or get data source %s in central repo:", eamDataSource.getName()), getException);
                }
            } else {
                //if a new data source was added to the central repository update the caches to include it and return it
                int dataSourceId = resultSet.getInt(1); //last_insert_rowid()
                CorrelationDataSource dataSource = new CorrelationDataSource(eamDataSource.getCaseID(), dataSourceId, eamDataSource.getDeviceID(), eamDataSource.getName(), eamDataSource.getDataSourceObjectID(), eamDataSource.getMd5(), eamDataSource.getSha1(), eamDataSource.getSha256());
                dataSourceCacheByDsObjectId.put(getDataSourceByDSObjectIdCacheKey(dataSource.getCaseID(), dataSource.getDataSourceObjectID()), dataSource);
                dataSourceCacheById.put(getDataSourceByIdCacheKey(dataSource.getCaseID(), dataSource.getID()), dataSource);
                return dataSource;
            }

        } catch (SQLException insertException) {
            /*
             * If an exception was thrown causing us to not return a new data
             * source, attempt to get an existing data source with the same case
             * ID and data source object ID.
             *
             * This exception block is expected to occur with SQLite Central
             * Repository databases.
             */
            try {
                return dataSourceCacheByDsObjectId.get(getDataSourceByDSObjectIdCacheKey(
                        eamDataSource.getCaseID(), eamDataSource.getDataSourceObjectID()),
                        () -> getDataSourceFromCr(eamDataSource.getCaseID(), eamDataSource.getDataSourceObjectID()));
            } catch (CacheLoader.InvalidCacheLoadException | ExecutionException getException) {
                throw new CentralRepoException(String.format("Unable to to INSERT or get data source %s in central repo, insert failed due to Exception: %s", eamDataSource.getName(), insertException.getMessage()), getException);
            }
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Retrieves Data Source details based on data source object ID
     *
     * @param correlationCase    the current CorrelationCase used for ensuring
     *                           uniqueness of DataSource
     * @param dataSourceObjectId the object id of the data source
     *
     * @return The data source
     *
     * @throws CentralRepoException
     */
    @Override
    public CorrelationDataSource getDataSource(CorrelationCase correlationCase, Long dataSourceObjectId) throws CentralRepoException {

        if (correlationCase == null) {
            throw new CentralRepoException("Correlation case is null");
        }
        try {
            return dataSourceCacheByDsObjectId.get(getDataSourceByDSObjectIdCacheKey(correlationCase.getID(), dataSourceObjectId), () -> getDataSourceFromCr(correlationCase.getID(), dataSourceObjectId));
        } catch (CacheLoader.InvalidCacheLoadException ignored) {
            //lambda valueloader returned a null value and cache can not store null values this is normal if the dataSource does not exist in the central repo yet
            return null;
        } catch (ExecutionException ex) {
            throw new CentralRepoException("Error getting data source from central repository", ex);
        }
    }

    /**
     * Gets the Data Source details based on data source device ID from the
     * central repository.
     *
     * @param correlationCaseId  the current CorrelationCase id used for
     *                           ensuring uniqueness of DataSource
     * @param dataSourceObjectId the object id of the data source
     *
     * @return The data source
     *
     * @throws CentralRepoException
     */
    private CorrelationDataSource getDataSourceFromCr(int correlationCaseId, Long dataSourceObjectId) throws CentralRepoException {
        Connection conn = connect();

        CorrelationDataSource eamDataSourceResult = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String sql = "SELECT * FROM data_sources WHERE datasource_obj_id=? AND case_id=?"; // NON-NLS

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setLong(1, dataSourceObjectId);
            preparedStatement.setInt(2, correlationCaseId);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                eamDataSourceResult = getEamDataSourceFromResultSet(resultSet);
            }
            if (eamDataSourceResult != null) {
                dataSourceCacheById.put(getDataSourceByIdCacheKey(correlationCaseId, eamDataSourceResult.getID()), eamDataSourceResult);
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting data source.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return eamDataSourceResult;
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
        if (correlationCase == null) {
            throw new CentralRepoException("Correlation case is null");
        }
        try {
            return dataSourceCacheById.get(getDataSourceByIdCacheKey(correlationCase.getID(), dataSourceId), () -> getDataSourceByIdFromCr(correlationCase, dataSourceId));
        } catch (CacheLoader.InvalidCacheLoadException ignored) {
            //lambda valueloader returned a null value and cache can not store null values this is normal if the dataSource does not exist in the central repo yet
            return null;
        } catch (ExecutionException ex) {
            throw new CentralRepoException("Error getting data source from central repository", ex);
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
    private CorrelationDataSource getDataSourceByIdFromCr(CorrelationCase correlationCase, int dataSourceId) throws CentralRepoException {
        Connection conn = connect();

        CorrelationDataSource eamDataSourceResult = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String sql = "SELECT * FROM data_sources WHERE id=? AND case_id=?"; // NON-NLS

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, dataSourceId);
            preparedStatement.setInt(2, correlationCase.getID());
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                eamDataSourceResult = getEamDataSourceFromResultSet(resultSet);
            }
            if (eamDataSourceResult != null) {
                dataSourceCacheByDsObjectId.put(getDataSourceByDSObjectIdCacheKey(correlationCase.getID(), eamDataSourceResult.getDataSourceObjectID()), eamDataSourceResult);
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting data source.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return eamDataSourceResult;
    }

    /**
     * Return a list of data sources in the DB
     *
     * @return list of data sources in the DB
     */
    @Override
    public List<CorrelationDataSource> getDataSources() throws CentralRepoException {
        Connection conn = connect();

        List<CorrelationDataSource> dataSources = new ArrayList<>();
        CorrelationDataSource eamDataSourceResult;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String sql = "SELECT * FROM data_sources";

        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                eamDataSourceResult = getEamDataSourceFromResultSet(resultSet);
                dataSources.add(eamDataSourceResult);
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting all data sources.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return dataSources;
    }

    /**
     * Updates the MD5 hash value in an existing data source in the database.
     *
     * @param eamDataSource The data source to update
     */
    @Override
    public void updateDataSourceMd5Hash(CorrelationDataSource eamDataSource) throws CentralRepoException {
        updateDataSourceStringValue(eamDataSource, "md5", eamDataSource.getMd5());
    }

    /**
     * Updates the SHA-1 hash value in an existing data source in the database.
     *
     * @param eamDataSource The data source to update
     */
    @Override
    public void updateDataSourceSha1Hash(CorrelationDataSource eamDataSource) throws CentralRepoException {
        updateDataSourceStringValue(eamDataSource, "sha1", eamDataSource.getSha1());
    }

    /**
     * Updates the SHA-256 hash value in an existing data source in the
     * database.
     *
     * @param eamDataSource The data source to update
     */
    @Override
    public void updateDataSourceSha256Hash(CorrelationDataSource eamDataSource) throws CentralRepoException {
        updateDataSourceStringValue(eamDataSource, "sha256", eamDataSource.getSha256());
    }

    /**
     * Updates the specified value in an existing data source in the database.
     *
     * @param eamDataSource The data source to update
     * @param column        The name of the column to be updated
     * @param value         The value to assign to the specified column
     */
    private void updateDataSourceStringValue(CorrelationDataSource eamDataSource, String column, String value) throws CentralRepoException {
        if (eamDataSource == null) {
            throw new CentralRepoException("Correlation data source is null");
        }

        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "UPDATE data_sources "
                + "SET " + column + "=? "
                + "WHERE id=?";

        try {
            preparedStatement = conn.prepareStatement(sql);

            preparedStatement.setString(1, value);
            preparedStatement.setInt(2, eamDataSource.getID());

            preparedStatement.executeUpdate();
            //update the case in the cache
            dataSourceCacheByDsObjectId.put(getDataSourceByDSObjectIdCacheKey(eamDataSource.getCaseID(), eamDataSource.getDataSourceObjectID()), eamDataSource);
            dataSourceCacheById.put(getDataSourceByIdCacheKey(eamDataSource.getCaseID(), eamDataSource.getID()), eamDataSource);
        } catch (SQLException ex) {
            throw new CentralRepoException(String.format("Error updating data source (obj_id=%d).", eamDataSource.getDataSourceObjectID()), ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
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

        Connection conn = connect();

        PreparedStatement preparedStatement = null;

        String sql = "UPDATE data_sources SET name = ? WHERE id = ?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, newName);
            preparedStatement.setInt(2, eamDataSource.getID());
            preparedStatement.executeUpdate();

            CorrelationDataSource updatedDataSource = new CorrelationDataSource(
                    eamDataSource.getCaseID(),
                    eamDataSource.getID(),
                    eamDataSource.getDeviceID(),
                    newName,
                    eamDataSource.getDataSourceObjectID(),
                    eamDataSource.getMd5(),
                    eamDataSource.getSha1(),
                    eamDataSource.getSha256());

            dataSourceCacheByDsObjectId.put(getDataSourceByDSObjectIdCacheKey(updatedDataSource.getCaseID(), updatedDataSource.getDataSourceObjectID()), updatedDataSource);
            dataSourceCacheById.put(getDataSourceByIdCacheKey(updatedDataSource.getCaseID(), updatedDataSource.getID()), updatedDataSource);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error updating name of data source with ID " + eamDataSource.getDataSourceObjectID()
                    + " to " + newName, ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
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
        checkAddArtifactInstanceNulls(eamArtifact);

        // @@@ We should cache the case and data source IDs in memory
        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(eamArtifact.getCorrelationType());
        boolean artifactHasAnAccount = CentralRepoDbUtil.correlationAttribHasAnAccount(eamArtifact.getCorrelationType());

        String sql;
        // _instance table for accounts have an additional account_id column 
        if (artifactHasAnAccount) {
            sql = "INSERT INTO "
                    + tableName
                    + "(case_id, data_source_id, value, file_path, known_status, comment, file_obj_id, account_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + getConflictClause();
        } else {
            sql = "INSERT INTO "
                    + tableName
                    + "(case_id, data_source_id, value, file_path, known_status, comment, file_obj_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + getConflictClause();
        }

        try (Connection conn = connect();
                PreparedStatement preparedStatement = conn.prepareStatement(sql);) {

            if (!eamArtifact.getCorrelationValue().isEmpty()) {
                preparedStatement.setInt(1, eamArtifact.getCorrelationCase().getID());
                preparedStatement.setInt(2, eamArtifact.getCorrelationDataSource().getID());
                preparedStatement.setString(3, eamArtifact.getCorrelationValue());
                preparedStatement.setString(4, eamArtifact.getFilePath().toLowerCase());
                preparedStatement.setByte(5, eamArtifact.getKnownStatus().getFileKnownValue());

                if ("".equals(eamArtifact.getComment())) {
                    preparedStatement.setNull(6, Types.INTEGER);
                } else {
                    preparedStatement.setString(6, eamArtifact.getComment());
                }
                preparedStatement.setLong(7, eamArtifact.getFileObjectId());

                // set in the accountId only for artifacts that represent accounts
                if (artifactHasAnAccount) {
                    if (eamArtifact.getAccountId() >= 0) {
                        preparedStatement.setLong(8, eamArtifact.getAccountId());
                    } else {
                        preparedStatement.setNull(8, Types.INTEGER);
                    }
                }

                preparedStatement.executeUpdate();
            }

        } catch (SQLException ex) {
            throw new CentralRepoException("Error inserting new artifact into artifacts table.", ex); // NON-NLS
        }
    }

    /**
     * Gets the Central Repo account for the given account type and account ID.
     * Create a new account first, if one doesn't exist
     *
     * @param accountType     account type
     * @param accountUniqueID unique account identifier
     *
     * @return A matching account, either existing or newly created.
     *
     * @throws TskCoreException exception thrown if a critical error occurs
     *                          within TSK core
     */
    @Override
    public CentralRepoAccount getOrCreateAccount(CentralRepoAccountType crAccountType, String accountUniqueID) throws InvalidAccountIDException, CentralRepoException {
        // Get the account fom the accounts table
        String normalizedAccountID = CentralRepoAccount.normalizeAccountIdentifier(crAccountType, accountUniqueID);

        // insert the account.  If there is a conflict, ignore it.
        String insertSQL;
        switch (CentralRepoDbManager.getSavedDbChoice().getDbPlatform()) {
            case POSTGRESQL:
                insertSQL = "INSERT INTO accounts (account_type_id, account_unique_identifier) VALUES (?, ?) " + getConflictClause();  //NON-NLS
                break;
            case SQLITE:
                insertSQL = "INSERT OR IGNORE INTO accounts (account_type_id, account_unique_identifier) VALUES (?, ?) "; //NON-NLS
                break;
            default:
                throw new CentralRepoException(String.format("Cannot add account to currently selected CR database platform %s", CentralRepoDbManager.getSavedDbChoice().getDbPlatform())); //NON-NLS
        }

        try (Connection connection = connect();
                PreparedStatement preparedStatement = connection.prepareStatement(insertSQL);) {

            preparedStatement.setInt(1, crAccountType.getAccountTypeId());
            preparedStatement.setString(2, normalizedAccountID);

            preparedStatement.executeUpdate();

            // get the account from the db - should exist now.
            return getAccount(crAccountType, normalizedAccountID);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error adding an account to CR database.", ex);
        }
    }

    @Override
    public Optional<CentralRepoAccountType> getAccountTypeByName(String accountTypeName) throws CentralRepoException {
        try {
            return accountTypesCache.get(accountTypeName, () -> getCRAccountTypeFromDb(accountTypeName));
        } catch (CacheLoader.InvalidCacheLoadException | ExecutionException ex) {
            throw new CentralRepoException("Error looking up CR account type in cache.", ex);
        }
    }

    @Override
    public Collection<CentralRepoAccountType> getAllAccountTypes() throws CentralRepoException {

        Collection<CentralRepoAccountType> accountTypes = new ArrayList<>();

        String sql = "SELECT * FROM account_types";
        try (Connection conn = connect();
                PreparedStatement preparedStatement = conn.prepareStatement(sql);) {

            try (ResultSet resultSet = preparedStatement.executeQuery();) {
                while (resultSet.next()) {
                    Account.Type acctType = new Account.Type(resultSet.getString("type_name"), resultSet.getString("display_name"));
                    CentralRepoAccountType crAccountType = new CentralRepoAccountType(resultSet.getInt("id"), acctType, resultSet.getInt("correlation_type_id"));

                    accountTypes.add(crAccountType);
                }
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting account types from central repository.", ex); // NON-NLS
        }
        return accountTypes;
    }

    /**
     * Gets the CR account type for the specified type name.
     *
     * @param accountTypeName account type name to look for
     *
     * @return CR account type
     *
     * @throws CentralRepoException
     */
    private Optional<CentralRepoAccountType> getCRAccountTypeFromDb(String accountTypeName) throws CentralRepoException {

        String sql = "SELECT * FROM account_types WHERE type_name = ?";
        try (Connection conn = connect();
                PreparedStatement preparedStatement = conn.prepareStatement(sql);) {

            preparedStatement.setString(1, accountTypeName);
            try (ResultSet resultSet = preparedStatement.executeQuery();) {
                if (resultSet.next()) {
                    Account.Type acctType = new Account.Type(accountTypeName, resultSet.getString("display_name"));
                    CentralRepoAccountType crAccountType = new CentralRepoAccountType(resultSet.getInt("id"), acctType, resultSet.getInt("correlation_type_id"));
                    accountTypesCache.put(accountTypeName, Optional.of(crAccountType));
                    return Optional.of(crAccountType);
                } else {
                    accountTypesCache.put(accountTypeName, Optional.empty());
                    return Optional.empty();
                }
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting correlation type by id.", ex); // NON-NLS
        }
    }

    /**
     * Get the CR account with the given account type and the unique account
     * identifier. Looks in the cache first. If not found in cache, reads from
     * the database and saves in cache.
     *
     * Returns null if the account is not found in the cache and not in the
     * database.
     *
     * @param crAccountType   account type to look for
     * @param accountUniqueID unique account id
     *
     * @return CentralRepoAccount for the give type/id. May return null if not
     *         found.
     *
     * @throws CentralRepoException      If there is an error accessing Central
     *                                   Repository.
     * @throws InvalidAccountIDException If the account identifier is not valid.
     */
    @Override
    public CentralRepoAccount getAccount(CentralRepoAccountType crAccountType, String accountUniqueID) throws InvalidAccountIDException, CentralRepoException {
        String normalizedAccountID = CentralRepoAccount.normalizeAccountIdentifier(crAccountType, accountUniqueID);
        CentralRepoAccount crAccount = accountsCache.getIfPresent(Pair.of(crAccountType, normalizedAccountID));
        if (crAccount == null) {
            crAccount = getCRAccountFromDb(crAccountType, normalizedAccountID);
            if (crAccount != null) {
                accountsCache.put(Pair.of(crAccountType, normalizedAccountID), crAccount);
            }
        }

        return crAccount;
    }

    /**
     * Get the Account with the given account type and account identifier, from
     * the database.
     *
     * @param accountType     account type
     * @param accountUniqueID unique account identifier
     *
     * @return Account, returns NULL is no matching account found
     *
     * @throws TskCoreException exception thrown if a critical error occurs
     *                          within TSK core
     */
    private CentralRepoAccount getCRAccountFromDb(CentralRepoAccountType crAccountType, String accountUniqueID) throws CentralRepoException {

        CentralRepoAccount account = null;

        String sql = "SELECT * FROM accounts WHERE account_type_id =  ? AND account_unique_identifier = ?";
        try (Connection connection = connect();
                PreparedStatement preparedStatement = connection.prepareStatement(sql);) {

            preparedStatement.setInt(1, crAccountType.getAccountTypeId());
            preparedStatement.setString(2, accountUniqueID);

            try (ResultSet resultSet = preparedStatement.executeQuery();) {
                if (resultSet.next()) {
                    account = new CentralRepoAccount(resultSet.getInt("id"), crAccountType, resultSet.getString("account_unique_identifier"));   //NON-NLS
                }
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting account type id", ex);
        }

        return account;
    }

    private void checkAddArtifactInstanceNulls(CorrelationAttributeInstance eamArtifact) throws CentralRepoException {
        if (eamArtifact == null) {
            throw new CentralRepoException("CorrelationAttribute is null");
        }
        if (eamArtifact.getCorrelationType() == null) {
            throw new CentralRepoException("Correlation type is null");
        }
        if (eamArtifact.getCorrelationValue() == null) {
            throw new CentralRepoException("Correlation value is null");
        }
        if (eamArtifact.getCorrelationValue().length() >= MAX_VALUE_LENGTH) {
            throw new CentralRepoException("Artifact value too long for central repository."
                    + "\nCorrelationArtifact ID: " + eamArtifact.getID()
                    + "\nCorrelationArtifact Type: " + eamArtifact.getCorrelationType().getDisplayName()
                    + "\nCorrelationArtifact Value: " + eamArtifact.getCorrelationValue());

        }
        if (eamArtifact.getCorrelationCase() == null) {
            throw new CentralRepoException("CorrelationAttributeInstance case is null");
        }
        if (eamArtifact.getCorrelationDataSource() == null) {
            throw new CentralRepoException("CorrelationAttributeInstance data source is null");
        }
        if (eamArtifact.getKnownStatus() == null) {
            throw new CentralRepoException("CorrelationAttributeInstance known status is null");
        }
    }

    @Override
    public List<CorrelationAttributeInstance> getArtifactInstancesByTypeValue(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException {
        if (value == null) {
            throw new CentralRepoException("Cannot get artifact instances for null value");
        }
        return getArtifactInstancesByTypeValues(aType, Arrays.asList(value));
    }

    @Override
    public List<CorrelationAttributeInstance> getArtifactInstancesByTypeValues(CorrelationAttributeInstance.Type aType, List<String> values) throws CentralRepoException, CorrelationAttributeNormalizationException {
        if (aType == null) {
            throw new CentralRepoException("Cannot get artifact instances for null type");
        }
        if (values == null || values.isEmpty()) {
            throw new CentralRepoException("Cannot get artifact instances without specified values");
        }
        return getCorrAttrInstances(prepareGetInstancesSql(aType, values), aType);
    }

    @Override
    public List<CorrelationAttributeInstance> getArtifactInstancesByTypeValuesAndCases(CorrelationAttributeInstance.Type aType, List<String> values, List<Integer> caseIds) throws CentralRepoException, CorrelationAttributeNormalizationException {
        if (aType == null) {
            throw new CentralRepoException("Cannot get artifact instances for null type");
        }
        if (values == null || values.isEmpty()) {
            throw new CentralRepoException("Cannot get artifact instances without specified values");
        }
        if (caseIds == null || caseIds.isEmpty()) {
            throw new CentralRepoException("Cannot get artifact instances without specified cases");
        }
        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(aType);
        String sql
                = " and "
                + tableName
                + ".case_id in ('";
        StringBuilder inValuesBuilder = new StringBuilder(prepareGetInstancesSql(aType, values));
        inValuesBuilder.append(sql);
        inValuesBuilder.append(caseIds.stream().map(String::valueOf).collect(Collectors.joining("', '")));
        inValuesBuilder.append("')");
        return getCorrAttrInstances(inValuesBuilder.toString(), aType);
    }

    /**
     * Get the select statement for retrieving correlation attribute instances
     * from the CR for a given type with values matching the specified values
     *
     * @param aType  The type of the artifact
     * @param values The list of correlation values to get
     *               CorrelationAttributeInstances for
     *
     * @return the select statement as a String
     *
     * @throws CorrelationAttributeNormalizationException
     */
    private String prepareGetInstancesSql(CorrelationAttributeInstance.Type aType, List<String> values) throws CorrelationAttributeNormalizationException, CentralRepoException {
        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(aType);
        String sql
                = "SELECT "
                + tableName
                + ".id as instance_id,"
                + tableName
                + ".value,"
                + tableName
                + ".file_obj_id,"
                + " cases.*, organizations.org_name, organizations.poc_name, organizations.poc_email, organizations.poc_phone, data_sources.id AS data_source_id, data_sources.name, device_id, file_path, known_status, comment, data_sources.datasource_obj_id, data_sources.md5, data_sources.sha1, data_sources.sha256 FROM "
                + tableName
                + " LEFT JOIN cases ON "
                + tableName
                + ".case_id=cases.id"
                + " LEFT JOIN organizations ON cases.org_id=organizations.id"
                + " LEFT JOIN data_sources ON "
                + tableName
                + ".data_source_id=data_sources.id"
                + " WHERE value IN (";
        StringBuilder inValuesBuilder = new StringBuilder(sql);
        for (String value : values) {
            if (value != null) {
                inValuesBuilder.append("'");
                inValuesBuilder.append(CorrelationAttributeNormalizer.normalize(aType, value));
                inValuesBuilder.append("',");
            }
        }
        inValuesBuilder.deleteCharAt(inValuesBuilder.length() - 1); //delete last comma
        inValuesBuilder.append(")");
        return inValuesBuilder.toString();
    }

    /**
     * Retrieves correlation attribute instances from the central repository
     * that match a given SQL query and correlation attribute type.
     *
     * @param sql      The SQL query.
     * @param attrType The correlation attribute type.
     *
     * @return The correlation attribute instanes.
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
    private List<CorrelationAttributeInstance> getCorrAttrInstances(String sql, CorrelationAttributeInstance.Type attrType) throws CorrelationAttributeNormalizationException, CentralRepoException {
        List<CorrelationAttributeInstance> corrAttrs = new ArrayList<>();
        Connection conn = connect();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                CorrelationAttributeInstance corrAttr = getCorrAttrFromResultSet(resultSet, attrType);
                corrAttrs.add(corrAttr);
            }
        } catch (SQLException ex) {
            throw new CentralRepoException(String.format("Error getting correlation attributes using query %s", sql), ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
        return corrAttrs;
    }

    /**
     * Retrieves number of artifact instances in the database that are
     * associated with the ArtifactType and artifactValue of the given artifact.
     *
     * @param aType The type of the artifact
     * @param value The correlation value
     *
     * @return Number of artifact instances having ArtifactType and
     *         ArtifactValue.
     */
    @Override
    public Long getCountArtifactInstancesByTypeValue(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException {
        String normalizedValue = CorrelationAttributeNormalizer.normalize(aType, value);

        Connection conn = connect();

        Long instanceCount = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(aType);
        String sql
                = "SELECT count(*) FROM "
                + tableName
                + " WHERE value=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, normalizedValue);
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            instanceCount = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting count of artifact instances by artifactType and artifactValue.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return instanceCount;
    }

    @Override
    public int getFrequencyPercentage(CorrelationAttributeInstance corAttr) throws CentralRepoException, CorrelationAttributeNormalizationException {
        if (corAttr == null) {
            throw new CentralRepoException("CorrelationAttribute is null");
        }
        Double uniqueTypeValueTuples = getCountUniqueCaseDataSourceTuplesHavingTypeValue(corAttr.getCorrelationType(), corAttr.getCorrelationValue()).doubleValue();
        Double uniqueCaseDataSourceTuples = getCountUniqueDataSources().doubleValue();
        Double commonalityPercentage = uniqueTypeValueTuples / uniqueCaseDataSourceTuples * 100;
        return commonalityPercentage.intValue();
    }

    /**
     * Retrieves number of unique caseDisplayName / dataSource tuples in the
     * database that are associated with the artifactType and artifactValue of
     * the given artifact.
     *
     * @param aType The type of the artifact
     * @param value The correlation value
     *
     * @return Number of unique tuples
     */
    @Override
    public Long getCountUniqueCaseDataSourceTuplesHavingTypeValue(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException {
        String normalizedValue = CorrelationAttributeNormalizer.normalize(aType, value);

        Connection conn = connect();

        Long instanceCount = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(aType);
        String sql
                = "SELECT count(*) FROM (SELECT DISTINCT case_id, data_source_id FROM "
                + tableName
                + " WHERE value=?) AS "
                + tableName
                + "_distinct_case_data_source_tuple";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, normalizedValue);
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            instanceCount = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error counting unique caseDisplayName/dataSource tuples having artifactType and artifactValue.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return instanceCount;
    }

    @Override
    public Long getCountCasesWithOtherInstances(CorrelationAttributeInstance instance) throws CentralRepoException, CorrelationAttributeNormalizationException {
        Long instanceCount = 0L;
        if (instance != null) {
            Long sourceObjID = instance.getFileObjectId();
            //The CorrelationAttributeInstance will have a CorrelationCase, however that correlation case's ID will be null if the case is not in the CR.
            int correlationCaseId = instance.getCorrelationCase().getID();
            int correlationDataSourceId = instance.getCorrelationDataSource().getID();
            String normalizedValue = CorrelationAttributeNormalizer.normalize(instance.getCorrelationType(), instance.getCorrelationValue());
            Connection conn = connect();
            PreparedStatement preparedStatement = null;
            String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(instance.getCorrelationType());
            ResultSet resultSet = null;

            try {
                if (correlationCaseId > 0 && sourceObjID != null && correlationDataSourceId > 0) {
                    //The CorrelationCase is in the Central repository.  
                    String sql
                            = "SELECT count(*) FROM (SELECT DISTINCT case_id FROM " //Get distinct cases with a matching value in the corresponding table from the central repository.
                            + tableName
                            + " WHERE value=? AND NOT (file_obj_id=? AND case_id=? AND data_source_id=?)) AS " //Check the file_obj_id AND case_id to ensure we ignore the currently selected instance. 
                            + tableName
                            + "_other_case_count";
                    preparedStatement = conn.prepareStatement(sql);
                    preparedStatement.setString(1, normalizedValue);
                    preparedStatement.setLong(2, sourceObjID);
                    preparedStatement.setInt(3, correlationCaseId);
                    preparedStatement.setInt(4, correlationDataSourceId);
                } else {
                    //The CorrelationCase is NOT in the central repository. 
                    String sql
                            = "SELECT count(*) FROM (SELECT DISTINCT case_id FROM " //Get all distinct cases with a matching value in the corresponding table from the central repository.
                            + tableName
                            + " WHERE value=? AS "
                            + tableName
                            + "_other_case_count";
                    preparedStatement = conn.prepareStatement(sql);
                    preparedStatement.setString(1, normalizedValue);
                }
                resultSet = preparedStatement.executeQuery();
                resultSet.next();
                instanceCount = resultSet.getLong(1);
            } catch (SQLException ex) {
                throw new CentralRepoException("Error counting unique caseDisplayName/dataSource tuples having artifactType and artifactValue.", ex); // NON-NLS
            } finally {
                CentralRepoDbUtil.closeResultSet(resultSet);
                CentralRepoDbUtil.closeStatement(preparedStatement);
                CentralRepoDbUtil.closeConnection(conn);
            }
        }

        return instanceCount;
    }

    @Override
    public Long getCountUniqueDataSources() throws CentralRepoException {
        Connection conn = connect();

        Long instanceCount = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String stmt = "SELECT count(*) FROM data_sources";

        try {
            preparedStatement = conn.prepareStatement(stmt);
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            instanceCount = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error counting data sources.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return instanceCount;
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
        Connection conn = connect();

        Long instanceCount = 0L;
        List<CorrelationAttributeInstance.Type> artifactTypes = getDefinedCorrelationTypes();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        //Create query to get count of all instances in the database for the specified case specific data source
        String sql = "SELECT 0 ";

        for (CorrelationAttributeInstance.Type type : artifactTypes) {
            String table_name = CentralRepoDbUtil.correlationTypeToInstanceTableName(type);
            sql
                    += "+ (SELECT count(*) FROM "
                    + table_name
                    + " WHERE data_source_id=" + correlationDataSource.getID() + ")";
        }
        try {
            preparedStatement = conn.prepareStatement(sql);

            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            instanceCount = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error counting artifact instances by caseName/dataSource.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return instanceCount;
    }

    /**
     * Adds an eamArtifact to an internal list to be later added to DB. Artifact
     * can have 1 or more Artifact Instances. Insert will be triggered by a
     * threshold or a call to commitAttributeInstancesBulk().
     *
     * @param eamArtifact The artifact to add
     */
    @Override
    public void addAttributeInstanceBulk(CorrelationAttributeInstance eamArtifact) throws CentralRepoException {

        if (eamArtifact.getCorrelationType() == null) {
            throw new CentralRepoException("Correlation type is null");
        }

        synchronized (bulkArtifacts) {
            if (bulkArtifacts.get(CentralRepoDbUtil.correlationTypeToInstanceTableName(eamArtifact.getCorrelationType())) == null) {
                bulkArtifacts.put(CentralRepoDbUtil.correlationTypeToInstanceTableName(eamArtifact.getCorrelationType()), new ArrayList<>());
            }
            bulkArtifacts.get(CentralRepoDbUtil.correlationTypeToInstanceTableName(eamArtifact.getCorrelationType())).add(eamArtifact);
            bulkArtifactsCount++;

            if (bulkArtifactsCount >= bulkArtifactsThreshold) {
                commitAttributeInstancesBulk();
            }
        }
    }

    /**
     * Get the conflict clause for bulk update statements
     *
     * @return The conflict clause for bulk update statements
     */
    protected abstract String getConflictClause();

    /**
     * Executes a bulk insert of the eamArtifacts added from the
     * addAttributeInstanceBulk() method
     */
    @Override
    public void commitAttributeInstancesBulk() throws CentralRepoException {
        List<CorrelationAttributeInstance.Type> artifactTypes = getDefinedCorrelationTypes();

        Connection conn = connect();
        PreparedStatement bulkPs = null;

        try {
            synchronized (bulkArtifacts) {
                if (bulkArtifactsCount == 0) {
                    return;
                }

                for (String tableName : bulkArtifacts.keySet()) {

                    String sql
                            = "INSERT INTO "
                            + tableName
                            + " (case_id, data_source_id, value, file_path, known_status, comment, file_obj_id) "
                            + "VALUES ((SELECT id FROM cases WHERE case_uid=? LIMIT 1), "
                            + "(SELECT id FROM data_sources WHERE datasource_obj_id=? AND case_id=? LIMIT 1), ?, ?, ?, ?, ?) "
                            + getConflictClause();

                    bulkPs = conn.prepareStatement(sql);

                    Collection<CorrelationAttributeInstance> eamArtifacts = bulkArtifacts.get(tableName);
                    for (CorrelationAttributeInstance eamArtifact : eamArtifacts) {

                        if (!eamArtifact.getCorrelationValue().isEmpty()) {

                            if (eamArtifact.getCorrelationCase() == null) {
                                throw new CentralRepoException("CorrelationAttributeInstance case is null for: "
                                        + "\n\tCorrelationArtifact ID: " + eamArtifact.getID()
                                        + "\n\tCorrelationArtifact Type: " + eamArtifact.getCorrelationType().getDisplayName()
                                        + "\n\tCorrelationArtifact Value: " + eamArtifact.getCorrelationValue());
                            }
                            if (eamArtifact.getCorrelationDataSource() == null) {
                                throw new CentralRepoException("CorrelationAttributeInstance data source is null for: "
                                        + "\n\tCorrelationArtifact ID: " + eamArtifact.getID()
                                        + "\n\tCorrelationArtifact Type: " + eamArtifact.getCorrelationType().getDisplayName()
                                        + "\n\tCorrelationArtifact Value: " + eamArtifact.getCorrelationValue());
                            }
                            if (eamArtifact.getKnownStatus() == null) {
                                throw new CentralRepoException("CorrelationAttributeInstance known status is null for: "
                                        + "\n\tCorrelationArtifact ID: " + eamArtifact.getID()
                                        + "\n\tCorrelationArtifact Type: " + eamArtifact.getCorrelationType().getDisplayName()
                                        + "\n\tCorrelationArtifact Value: " + eamArtifact.getCorrelationValue()
                                        + "\n\tEam Instance: "
                                        + "\n\t\tCaseId: " + eamArtifact.getCorrelationDataSource().getCaseID()
                                        + "\n\t\tDeviceID: " + eamArtifact.getCorrelationDataSource().getDeviceID());
                            }

                            if (eamArtifact.getCorrelationValue().length() < MAX_VALUE_LENGTH) {
                                bulkPs.setString(1, eamArtifact.getCorrelationCase().getCaseUUID());
                                bulkPs.setLong(2, eamArtifact.getCorrelationDataSource().getDataSourceObjectID());
                                bulkPs.setInt(3, eamArtifact.getCorrelationDataSource().getCaseID());
                                bulkPs.setString(4, eamArtifact.getCorrelationValue());
                                bulkPs.setString(5, eamArtifact.getFilePath());
                                bulkPs.setByte(6, eamArtifact.getKnownStatus().getFileKnownValue());
                                if ("".equals(eamArtifact.getComment())) {
                                    bulkPs.setNull(7, Types.INTEGER);
                                } else {
                                    bulkPs.setString(7, eamArtifact.getComment());
                                }
                                bulkPs.setLong(8, eamArtifact.getFileObjectId());
                                bulkPs.addBatch();
                            } else {
                                logger.log(Level.WARNING, ("Artifact value too long for central repository."
                                        + "\n\tCorrelationArtifact ID: " + eamArtifact.getID()
                                        + "\n\tCorrelationArtifact Type: " + eamArtifact.getCorrelationType().getDisplayName()
                                        + "\n\tCorrelationArtifact Value: " + eamArtifact.getCorrelationValue())
                                        + "\n\tEam Instance: "
                                        + "\n\t\tCaseId: " + eamArtifact.getCorrelationDataSource().getCaseID()
                                        + "\n\t\tDeviceID: " + eamArtifact.getCorrelationDataSource().getDeviceID()
                                        + "\n\t\tFilePath: " + eamArtifact.getFilePath());
                            }
                        }

                    }

                    bulkPs.executeBatch();
                    bulkArtifacts.get(tableName).clear();
                }

                TimingMetric timingMetric = HealthMonitor.getTimingMetric("Central Repository: Bulk insert");
                HealthMonitor.submitTimingMetric(timingMetric);

                // Reset state
                bulkArtifactsCount = 0;
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error inserting bulk artifacts.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeStatement(bulkPs);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Executes a bulk insert of the cases
     */
    @Override
    public void bulkInsertCases(List<CorrelationCase> cases) throws CentralRepoException {
        if (cases == null) {
            throw new CentralRepoException("cases argument is null");
        }

        if (cases.isEmpty()) {
            return;
        }

        Connection conn = connect();

        int counter = 0;
        PreparedStatement bulkPs = null;
        try {
            String sql = "INSERT INTO cases(case_uid, org_id, case_name, creation_date, case_number, "
                    + "examiner_name, examiner_email, examiner_phone, notes) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + getConflictClause();
            bulkPs = conn.prepareStatement(sql);

            for (CorrelationCase eamCase : cases) {
                bulkPs.setString(1, eamCase.getCaseUUID());
                if (null == eamCase.getOrg()) {
                    bulkPs.setNull(2, Types.INTEGER);
                } else {
                    bulkPs.setInt(2, eamCase.getOrg().getOrgID());
                }
                bulkPs.setString(3, eamCase.getDisplayName());
                bulkPs.setString(4, eamCase.getCreationDate());

                if ("".equals(eamCase.getCaseNumber())) {
                    bulkPs.setNull(5, Types.INTEGER);
                } else {
                    bulkPs.setString(5, eamCase.getCaseNumber());
                }
                if ("".equals(eamCase.getExaminerName())) {
                    bulkPs.setNull(6, Types.INTEGER);
                } else {
                    bulkPs.setString(6, eamCase.getExaminerName());
                }
                if ("".equals(eamCase.getExaminerEmail())) {
                    bulkPs.setNull(7, Types.INTEGER);
                } else {
                    bulkPs.setString(7, eamCase.getExaminerEmail());
                }
                if ("".equals(eamCase.getExaminerPhone())) {
                    bulkPs.setNull(8, Types.INTEGER);
                } else {
                    bulkPs.setString(8, eamCase.getExaminerPhone());
                }
                if ("".equals(eamCase.getNotes())) {
                    bulkPs.setNull(9, Types.INTEGER);
                } else {
                    bulkPs.setString(9, eamCase.getNotes());
                }

                bulkPs.addBatch();

                counter++;

                // limit a batch's max size to bulkArtifactsThreshold
                if (counter >= bulkArtifactsThreshold) {
                    bulkPs.executeBatch();
                    counter = 0;
                }
            }
            // send the remaining batch records
            bulkPs.executeBatch();
        } catch (SQLException ex) {
            throw new CentralRepoException("Error inserting bulk cases.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeStatement(bulkPs);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Update a correlation attribute instance in the database with that in the
     * associated CorrelationAttribute object.
     *
     * @param eamArtifact The correlation attribute whose database instance will
     *                    be updated.
     *
     * @throws CentralRepoException
     */
    @Override
    public void updateAttributeInstanceComment(CorrelationAttributeInstance eamArtifact) throws CentralRepoException {

        if (eamArtifact == null) {
            throw new CentralRepoException("CorrelationAttributeInstance is null");
        }
        if (eamArtifact.getCorrelationCase() == null) {
            throw new CentralRepoException("Correlation case is null");
        }
        if (eamArtifact.getCorrelationDataSource() == null) {
            throw new CentralRepoException("Correlation data source is null");
        }
        Connection conn = connect();
        PreparedStatement preparedQuery = null;
        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(eamArtifact.getCorrelationType());
        String sqlUpdate
                = "UPDATE "
                + tableName
                + " SET comment=? "
                + "WHERE case_id=? "
                + "AND data_source_id=? "
                + "AND value=? "
                + "AND file_path=?";

        try {
            preparedQuery = conn.prepareStatement(sqlUpdate);
            preparedQuery.setString(1, eamArtifact.getComment());
            preparedQuery.setInt(2, eamArtifact.getCorrelationCase().getID());
            preparedQuery.setInt(3, eamArtifact.getCorrelationDataSource().getID());
            preparedQuery.setString(4, eamArtifact.getCorrelationValue());
            preparedQuery.setString(5, eamArtifact.getFilePath().toLowerCase());
            preparedQuery.executeUpdate();
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting/setting artifact instance comment=" + eamArtifact.getComment(), ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeStatement(preparedQuery);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

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
    @Override
    public CorrelationAttributeInstance getCorrelationAttributeInstance(CorrelationAttributeInstance.Type type, CorrelationCase correlationCase,
            CorrelationDataSource correlationDataSource, long objectID) throws CentralRepoException, CorrelationAttributeNormalizationException {

        if (correlationCase == null) {
            throw new CentralRepoException("Correlation case is null");
        }

        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        CorrelationAttributeInstance correlationAttributeInstance = null;

        try {

            String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(type);
            String sql
                    = "SELECT id, value, file_path, known_status, comment FROM "
                    + tableName
                    + " WHERE case_id=?"
                    + " AND file_obj_id=?";

            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, correlationCase.getID());
            preparedStatement.setInt(2, (int) objectID);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                int instanceId = resultSet.getInt(1);
                String value = resultSet.getString(2);
                String filePath = resultSet.getString(3);
                int knownStatus = resultSet.getInt(4);
                String comment = resultSet.getString(5);

                correlationAttributeInstance = new CorrelationAttributeInstance(type, value,
                        instanceId, correlationCase, correlationDataSource, filePath, comment, TskData.FileKnown.valueOf((byte) knownStatus), objectID);
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting notable artifact instances.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return correlationAttributeInstance;
    }

    /**
     * Find a correlation attribute in the Central Repository database given the
     * instance type, case, data source, value, and file path.
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
    @Override
    public CorrelationAttributeInstance getCorrelationAttributeInstance(CorrelationAttributeInstance.Type type, CorrelationCase correlationCase,
            CorrelationDataSource correlationDataSource, String value, String filePath) throws CentralRepoException, CorrelationAttributeNormalizationException {

        if (correlationCase == null) {
            throw new CentralRepoException("Correlation case is null");
        }
        if (correlationDataSource == null) {
            throw new CentralRepoException("Correlation data source is null");
        }
        if (filePath == null) {
            throw new CentralRepoException("Correlation file path is null");
        }

        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        CorrelationAttributeInstance correlationAttributeInstance = null;

        try {
            String normalizedValue = CorrelationAttributeNormalizer.normalize(type, value);

            String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(type);
            String sql
                    = "SELECT id, known_status, comment FROM "
                    + tableName
                    + " WHERE case_id=?"
                    + " AND data_source_id=?"
                    + " AND value=?"
                    + " AND file_path=?";

            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, correlationCase.getID());
            preparedStatement.setInt(2, correlationDataSource.getID());
            preparedStatement.setString(3, normalizedValue);
            preparedStatement.setString(4, filePath.toLowerCase());
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                int instanceId = resultSet.getInt(1);
                int knownStatus = resultSet.getInt(2);
                String comment = resultSet.getString(3);
                //null objectId used because we only fall back to using this method when objectID was not available
                correlationAttributeInstance = new CorrelationAttributeInstance(type, value,
                        instanceId, correlationCase, correlationDataSource, filePath, comment, TskData.FileKnown.valueOf((byte) knownStatus), null);
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting notable artifact instances.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return correlationAttributeInstance;
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
        if (eamArtifact == null) {
            throw new CentralRepoException("CorrelationAttribute is null");
        }
        if (knownStatus == null) {
            throw new CentralRepoException("Known status is null");
        }

        if (eamArtifact.getCorrelationCase() == null) {
            throw new CentralRepoException("Correlation case is null");
        }
        if (eamArtifact.getCorrelationDataSource() == null) {
            throw new CentralRepoException("Correlation data source is null");
        }

        Connection conn = connect();

        PreparedStatement preparedUpdate = null;
        PreparedStatement preparedQuery = null;
        ResultSet resultSet = null;

        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(eamArtifact.getCorrelationType());

        String sqlQuery
                = "SELECT id FROM "
                + tableName
                + " WHERE case_id=? "
                + "AND data_source_id=? "
                + "AND value=? "
                + "AND file_path=?";

        String sqlUpdate
                = "UPDATE "
                + tableName
                + " SET known_status=? WHERE id=?";

        try {
            preparedQuery = conn.prepareStatement(sqlQuery);
            preparedQuery.setInt(1, eamArtifact.getCorrelationCase().getID());
            preparedQuery.setInt(2, eamArtifact.getCorrelationDataSource().getID());
            preparedQuery.setString(3, eamArtifact.getCorrelationValue());
            preparedQuery.setString(4, eamArtifact.getFilePath());
            resultSet = preparedQuery.executeQuery();
            if (resultSet.next()) {
                int instance_id = resultSet.getInt("id");
                preparedUpdate = conn.prepareStatement(sqlUpdate);

                preparedUpdate.setByte(1, knownStatus.getFileKnownValue());
                preparedUpdate.setInt(2, instance_id);

                preparedUpdate.executeUpdate();
            } else {
                // In this case, the user is tagging something that isn't in the database,
                // which means the case and/or datasource may also not be in the database.
                // We could improve effiency by keeping a list of all datasources and cases
                // in the database, but we don't expect the user to be tagging large numbers
                // of items (that didn't have the CE ingest module run on them) at once.
                CorrelationCase correlationCaseWithId = getCaseByUUID(eamArtifact.getCorrelationCase().getCaseUUID());
                if (null == getDataSource(correlationCaseWithId, eamArtifact.getCorrelationDataSource().getDataSourceObjectID())) {
                    newDataSource(eamArtifact.getCorrelationDataSource());
                }
                eamArtifact.setKnownStatus(knownStatus);
                addArtifactInstance(eamArtifact);
            }

        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting/setting artifact instance knownStatus=" + knownStatus.getName(), ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedUpdate);
            CentralRepoDbUtil.closeStatement(preparedQuery);
            CentralRepoDbUtil.closeConnection(conn);
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

        String normalizedValue = CorrelationAttributeNormalizer.normalize(aType, value);

        Connection conn = connect();

        Long badInstances = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(aType);
        String sql
                = "SELECT count(*) FROM "
                + tableName
                + " WHERE value=? AND known_status=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, normalizedValue);
            preparedStatement.setByte(2, TskData.FileKnown.BAD.getFileKnownValue());
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            badInstances = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting count of notable artifact instances.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return badInstances;
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

        String normalizedValue = CorrelationAttributeNormalizer.normalize(aType, value);

        Connection conn = connect();

        Collection<String> caseNames = new LinkedHashSet<>();

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(aType);
        String sql
                = "SELECT DISTINCT case_name FROM "
                + tableName
                + " INNER JOIN cases ON "
                + tableName
                + ".case_id=cases.id WHERE "
                + tableName
                + ".value=? AND "
                + tableName
                + ".known_status=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, normalizedValue);
            preparedStatement.setByte(2, TskData.FileKnown.BAD.getFileKnownValue());
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                caseNames.add(resultSet.getString("case_name"));
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting notable artifact instances.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return caseNames.stream().collect(Collectors.toList());
    }

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
    @Override
    public List<String> getListCasesHavingArtifactInstances(CorrelationAttributeInstance.Type aType, String value) throws CentralRepoException, CorrelationAttributeNormalizationException {

        String normalizedValue = CorrelationAttributeNormalizer.normalize(aType, value);

        Connection conn = connect();

        Collection<String> caseNames = new LinkedHashSet<>();

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(aType);
        String sql
                = "SELECT DISTINCT case_name FROM "
                + tableName
                + " INNER JOIN cases ON "
                + tableName
                + ".case_id=cases.id WHERE "
                + tableName
                + ".value=? ";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, normalizedValue);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                caseNames.add(resultSet.getString("case_name"));
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting notable artifact instances.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return caseNames.stream().collect(Collectors.toList());
    }

    /**
     * Remove a reference set and all entries contained in it.
     *
     * @param referenceSetID
     *
     * @throws CentralRepoException
     */
    @Override
    public void deleteReferenceSet(int referenceSetID) throws CentralRepoException {
        deleteReferenceSetEntries(referenceSetID);
        deleteReferenceSetEntry(referenceSetID);
    }

    /**
     * Remove the entry for this set from the reference_sets table
     *
     * @param referenceSetID
     *
     * @throws CentralRepoException
     */
    private void deleteReferenceSetEntry(int referenceSetID) throws CentralRepoException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "DELETE FROM reference_sets WHERE id=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, referenceSetID);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new CentralRepoException("Error deleting reference set " + referenceSetID, ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Remove all entries for this reference set from the reference tables
     * (Currently only removes entries from the reference_file table)
     *
     * @param referenceSetID
     *
     * @throws CentralRepoException
     */
    private void deleteReferenceSetEntries(int referenceSetID) throws CentralRepoException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "DELETE FROM %s WHERE reference_set_id=?";

        // When other reference types are added, this will need to loop over all the tables
        String fileTableName = CentralRepoDbUtil.correlationTypeToReferenceTableName(getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID));

        try {
            preparedStatement = conn.prepareStatement(String.format(sql, fileTableName));
            preparedStatement.setInt(1, referenceSetID);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new CentralRepoException("Error deleting files from reference set " + referenceSetID, ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Check whether a reference set with the given parameters exists in the
     * central repository. Used to check whether reference sets saved in the
     * settings are still present.
     *
     * @param referenceSetID
     * @param setName
     * @param version
     *
     * @return true if a matching entry exists in the central repository
     *
     * @throws CentralRepoException
     */
    @Override
    public boolean referenceSetIsValid(int referenceSetID, String setName, String version) throws CentralRepoException {
        CentralRepoFileSet refSet = this.getReferenceSetByID(referenceSetID);
        if (refSet == null) {
            return false;
        }

        return (refSet.getSetName().equals(setName) && refSet.getVersion().equals(version));
    }

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
    @Override
    public boolean isFileHashInReferenceSet(String hash, int referenceSetID) throws CentralRepoException, CorrelationAttributeNormalizationException {
        return isValueInReferenceSet(hash, referenceSetID, CorrelationAttributeInstance.FILES_TYPE_ID);
    }

    @Override
    public HashHitInfo lookupHash(String hash, int referenceSetID) throws CentralRepoException, CorrelationAttributeNormalizationException {
        int correlationTypeID = CorrelationAttributeInstance.FILES_TYPE_ID;
        String normalizeValued = CorrelationAttributeNormalizer.normalize(this.getCorrelationTypeById(correlationTypeID), hash);

        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT value,comment FROM %s WHERE value=? AND reference_set_id=?";

        String fileTableName = CentralRepoDbUtil.correlationTypeToReferenceTableName(getCorrelationTypeById(correlationTypeID));

        try {
            preparedStatement = conn.prepareStatement(String.format(sql, fileTableName));
            preparedStatement.setString(1, normalizeValued);
            preparedStatement.setInt(2, referenceSetID);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String comment = resultSet.getString("comment");
                String hashFound = resultSet.getString("value");
                HashHitInfo found = new HashHitInfo(hashFound, "", "");
                found.addComment(comment);
                return found;
            } else {
                return null;
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error determining if value (" + normalizeValued + ") is in reference set " + referenceSetID, ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Check if the given value is in a specific reference set
     *
     * @param value
     * @param referenceSetID
     * @param correlationTypeID
     *
     * @return true if the value is found in the reference set
     */
    @Override
    public boolean isValueInReferenceSet(String value, int referenceSetID, int correlationTypeID) throws CentralRepoException, CorrelationAttributeNormalizationException {

        String normalizeValued = CorrelationAttributeNormalizer.normalize(this.getCorrelationTypeById(correlationTypeID), value);

        Connection conn = connect();

        Long matchingInstances = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT count(*) FROM %s WHERE value=? AND reference_set_id=?";

        String fileTableName = CentralRepoDbUtil.correlationTypeToReferenceTableName(getCorrelationTypeById(correlationTypeID));

        try {
            preparedStatement = conn.prepareStatement(String.format(sql, fileTableName));
            preparedStatement.setString(1, normalizeValued);
            preparedStatement.setInt(2, referenceSetID);
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            matchingInstances = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error determining if value (" + normalizeValued + ") is in reference set " + referenceSetID, ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return 0 < matchingInstances;
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

        //this should be done here so that we can be certain that aType and value are valid before we proceed
        String normalizeValued = CorrelationAttributeNormalizer.normalize(aType, value);

        // TEMP: Only support file correlation type
        if (aType.getId() != CorrelationAttributeInstance.FILES_TYPE_ID) {
            return false;
        }

        Connection conn = connect();

        Long badInstances = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT count(*) FROM %s WHERE value=? AND known_status=?";

        try {
            preparedStatement = conn.prepareStatement(String.format(sql, CentralRepoDbUtil.correlationTypeToReferenceTableName(aType)));
            preparedStatement.setString(1, normalizeValued);
            preparedStatement.setByte(2, TskData.FileKnown.BAD.getFileKnownValue());
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            badInstances = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error determining if artifact is notable by reference.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return 0 < badInstances;
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
        if (type == null) {
            throw new CentralRepoException("Correlation type is null");
        }

        if (instanceTableCallback == null) {
            throw new CentralRepoException("Callback interface is null");
        }

        Connection conn = connect();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(type);
        StringBuilder sql = new StringBuilder();
        sql.append("select * from ");
        sql.append(tableName);

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            resultSet = preparedStatement.executeQuery();
            instanceTableCallback.process(resultSet);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting all artifact instances from instances table", ex);
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Process the Artifact instance in the EamDb give a where clause
     *
     * @param type                  EamArtifact.Type to search for
     * @param instanceTableCallback callback to process the instance
     * @param whereClause           query string to execute
     *
     * @throws CentralRepoException
     */
    @Override
    public void processInstanceTableWhere(CorrelationAttributeInstance.Type type, String whereClause, InstanceTableCallback instanceTableCallback) throws CentralRepoException {
        if (type == null) {
            throw new CentralRepoException("Correlation type is null");
        }

        if (instanceTableCallback == null) {
            throw new CentralRepoException("Callback interface is null");
        }

        if (whereClause == null) {
            throw new CentralRepoException("Where clause is null");
        }

        Connection conn = connect();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(type);
        StringBuilder sql = new StringBuilder(300);
        sql.append("select * from ")
                .append(tableName)
                .append(" WHERE ")
                .append(whereClause);

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            resultSet = preparedStatement.executeQuery();
            instanceTableCallback.process(resultSet);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting all artifact instances from instances table", ex);
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
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

        if (instanceTableCallback == null) {
            throw new CentralRepoException("Callback interface is null");
        }

        if (selectClause == null) {
            throw new CentralRepoException("Select clause is null");
        }

        Connection conn = connect();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        StringBuilder sql = new StringBuilder(300);
        sql.append("select ")
                .append(selectClause);

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            resultSet = preparedStatement.executeQuery();
            instanceTableCallback.process(resultSet);
        } catch (SQLException ex) {
            throw new CentralRepoException("Error running query", ex);
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    @Override
    public void executeCommand(String sql, List<Object> params) throws CentralRepoException {

        try (Connection conn = connect();) {

            PreparedStatement preparedStatement = conn.prepareStatement(sql);

            // Fill in the params
            if (params != null) {
                int paramIndex = 1;
                for (Object param : params) {
                    preparedStatement.setObject(paramIndex, param);
                    paramIndex += 1;
                }
            }
            // execute the prepared statement
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new CentralRepoException(String.format("Error executing prepared statement for SQL %s", sql), ex);
        }
    }

    @Override
    public void executeQuery(String sql, List<Object> params, CentralRepositoryDbQueryCallback queryCallback) throws CentralRepoException {
        if (queryCallback == null) {
            throw new CentralRepoException("Query callback is null");
        }

        try (Connection conn = connect();) {
            PreparedStatement preparedStatement = conn.prepareStatement(sql);

            // fill in the params
            if (params != null) {
                int paramIndex = 1;
                for (Object param : params) {
                    preparedStatement.setObject(paramIndex, param);
                    paramIndex += 1;
                }
            }
            // execute query, and the callback to process result
            try (ResultSet resultSet = preparedStatement.executeQuery();) {
                queryCallback.process(resultSet);
            }
        } catch (SQLException ex) {
            throw new CentralRepoException(String.format("Error executing prepared statement for SQL query %s", sql), ex);
        }
    }

    @Override
    public CentralRepoOrganization newOrganization(CentralRepoOrganization eamOrg) throws CentralRepoException {
        if (eamOrg == null) {
            throw new CentralRepoException("EamOrganization is null");
        } else if (eamOrg.getOrgID() != -1) {
            throw new CentralRepoException("EamOrganization already has an ID");
        }

        Connection conn = connect();
        ResultSet generatedKeys = null;
        PreparedStatement preparedStatement = null;
        String sql = "INSERT INTO organizations(org_name, poc_name, poc_email, poc_phone) VALUES (?, ?, ?, ?) "
                + getConflictClause();

        try {
            preparedStatement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setString(1, eamOrg.getName());
            preparedStatement.setString(2, eamOrg.getPocName());
            preparedStatement.setString(3, eamOrg.getPocEmail());
            preparedStatement.setString(4, eamOrg.getPocPhone());

            preparedStatement.executeUpdate();
            generatedKeys = preparedStatement.getGeneratedKeys();
            if (generatedKeys.next()) {
                eamOrg.setOrgID((int) generatedKeys.getLong(1));
                return eamOrg;
            } else {
                throw new SQLException("Creating user failed, no ID obtained.");
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error inserting new organization.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(generatedKeys);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
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
        Connection conn = connect();

        List<CentralRepoOrganization> orgs = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM organizations";

        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                orgs.add(getEamOrganizationFromResultSet(resultSet));
            }
            return orgs;

        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting all organizations.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
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
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM organizations WHERE id=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, orgID);
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return getEamOrganizationFromResultSet(resultSet);

        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting organization by id.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Get the organization associated with the given reference set.
     *
     * @param referenceSetID ID of the reference set
     *
     * @return The organization object
     *
     * @throws CentralRepoException
     */
    @Override
    public CentralRepoOrganization getReferenceSetOrganization(int referenceSetID) throws CentralRepoException {

        CentralRepoFileSet globalSet = getReferenceSetByID(referenceSetID);
        if (globalSet == null) {
            throw new CentralRepoException("Reference set with ID " + referenceSetID + " not found");
        }
        return (getOrganizationByID(globalSet.getOrgID()));
    }

    /**
     * Tests that an organization passed in as an argument is valid
     *
     * @param org
     *
     * @throws CentralRepoException if invalid
     */
    private void testArgument(CentralRepoOrganization org) throws CentralRepoException {
        if (org == null) {
            throw new CentralRepoException("EamOrganization is null");
        } else if (org.getOrgID() == -1) {
            throw new CentralRepoException("Organization  has -1 row ID");
        }
    }

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
    @Override
    public CentralRepoExaminer getOrInsertExaminer(String examinerLoginName) throws CentralRepoException {

        String querySQL = "SELECT * FROM examiners WHERE login_name = '" + SleuthkitCase.escapeSingleQuotes(examinerLoginName) + "'";
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(querySQL);) {

            if (resultSet.next()) {
                return new CentralRepoExaminer(resultSet.getLong("id"), resultSet.getString("login_name"));
            } else {
                // Could not find this user in the Examiner table, add a row for it.
                try {
                    String insertSQL;
                    switch (CentralRepoDbManager.getSavedDbChoice().getDbPlatform()) {
                        case POSTGRESQL:
                            insertSQL = "INSERT INTO examiners (login_name) VALUES ('" + SleuthkitCase.escapeSingleQuotes(examinerLoginName) + "')" + getConflictClause();  //NON-NLS
                            break;
                        case SQLITE:
                            insertSQL = "INSERT OR IGNORE INTO examiners (login_name) VALUES ('" + SleuthkitCase.escapeSingleQuotes(examinerLoginName) + "')"; //NON-NLS
                            break;
                        default:
                            throw new CentralRepoException(String.format("Cannot add examiner to currently selected CR database platform %s", CentralRepoDbManager.getSavedDbChoice().getDbPlatform())); //NON-NLS
                    }
                    statement.execute(insertSQL);

                    // Query the table again to get the row for the user
                    try (ResultSet resultSet2 = statement.executeQuery(querySQL)) {
                        if (resultSet2.next()) {
                            return new CentralRepoExaminer(resultSet2.getLong("id"), resultSet2.getString("login_name"));
                        } else {
                            throw new CentralRepoException("Error getting examiner for name = " + examinerLoginName);
                        }
                    }

                } catch (SQLException ex) {
                    throw new CentralRepoException("Error inserting row in examiners", ex);
                }
            }

        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting examiner for name = " + examinerLoginName, ex);
        }
    }

    /**
     * Update an existing organization.
     *
     * @param updatedOrganization the values the Organization with the same ID
     *                            will be updated to in the database.
     *
     * @throws CentralRepoException
     */
    @Override
    public void updateOrganization(CentralRepoOrganization updatedOrganization) throws CentralRepoException {
        testArgument(updatedOrganization);

        Connection conn = connect();
        PreparedStatement preparedStatement = null;
        String sql = "UPDATE organizations SET org_name = ?, poc_name = ?, poc_email = ?, poc_phone = ? WHERE id = ?";
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, updatedOrganization.getName());
            preparedStatement.setString(2, updatedOrganization.getPocName());
            preparedStatement.setString(3, updatedOrganization.getPocEmail());
            preparedStatement.setString(4, updatedOrganization.getPocPhone());
            preparedStatement.setInt(5, updatedOrganization.getOrgID());
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new CentralRepoException("Error updating organization.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    @Override
    public void deleteOrganization(CentralRepoOrganization organizationToDelete) throws CentralRepoException {
        testArgument(organizationToDelete);

        Connection conn = connect();
        PreparedStatement checkIfUsedStatement = null;
        ResultSet resultSet = null;
        String checkIfUsedSql = "SELECT (select count(*) FROM cases WHERE org_id=?) + (select count(*) FROM reference_sets WHERE org_id=?)";
        PreparedStatement deleteOrgStatement = null;
        String deleteOrgSql = "DELETE FROM organizations WHERE id=?";
        try {
            checkIfUsedStatement = conn.prepareStatement(checkIfUsedSql);
            checkIfUsedStatement.setInt(1, organizationToDelete.getOrgID());
            checkIfUsedStatement.setInt(2, organizationToDelete.getOrgID());
            resultSet = checkIfUsedStatement.executeQuery();
            resultSet.next();
            if (resultSet.getLong(1) > 0) {
                throw new CentralRepoException("Can not delete organization which is currently in use by a case or reference set in the central repository.");
            }
            deleteOrgStatement = conn.prepareStatement(deleteOrgSql);
            deleteOrgStatement.setInt(1, organizationToDelete.getOrgID());
            deleteOrgStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new CentralRepoException("Error executing query when attempting to delete organization by id.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(checkIfUsedStatement);
            CentralRepoDbUtil.closeStatement(deleteOrgStatement);
            CentralRepoDbUtil.closeConnection(conn);
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
        if (eamGlobalSet == null) {
            throw new CentralRepoException("EamGlobalSet is null");
        }

        if (eamGlobalSet.getFileKnownStatus() == null) {
            throw new CentralRepoException("File known status on the EamGlobalSet is null");
        }

        if (eamGlobalSet.getType() == null) {
            throw new CentralRepoException("Type on the EamGlobalSet is null");
        }

        Connection conn = connect();

        PreparedStatement preparedStatement1 = null;
        PreparedStatement preparedStatement2 = null;
        ResultSet resultSet = null;
        String sql1 = "INSERT INTO reference_sets(org_id, set_name, version, known_status, read_only, type, import_date) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + getConflictClause();
        String sql2 = "SELECT id FROM reference_sets WHERE org_id=? AND set_name=? AND version=? AND import_date=? LIMIT 1";

        try {
            preparedStatement1 = conn.prepareStatement(sql1);
            preparedStatement1.setInt(1, eamGlobalSet.getOrgID());
            preparedStatement1.setString(2, eamGlobalSet.getSetName());
            preparedStatement1.setString(3, eamGlobalSet.getVersion());
            preparedStatement1.setInt(4, eamGlobalSet.getFileKnownStatus().getFileKnownValue());
            preparedStatement1.setBoolean(5, eamGlobalSet.isReadOnly());
            preparedStatement1.setInt(6, eamGlobalSet.getType().getId());
            preparedStatement1.setString(7, eamGlobalSet.getImportDate().toString());

            preparedStatement1.executeUpdate();

            preparedStatement2 = conn.prepareStatement(sql2);
            preparedStatement2.setInt(1, eamGlobalSet.getOrgID());
            preparedStatement2.setString(2, eamGlobalSet.getSetName());
            preparedStatement2.setString(3, eamGlobalSet.getVersion());
            preparedStatement2.setString(4, eamGlobalSet.getImportDate().toString());

            resultSet = preparedStatement2.executeQuery();
            resultSet.next();
            return resultSet.getInt("id");

        } catch (SQLException ex) {
            throw new CentralRepoException("Error inserting new global set.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement1);
            CentralRepoDbUtil.closeStatement(preparedStatement2);
            CentralRepoDbUtil.closeConnection(conn);
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
        Connection conn = connect();

        PreparedStatement preparedStatement1 = null;
        ResultSet resultSet = null;
        String sql1 = "SELECT * FROM reference_sets WHERE id=?";

        try {
            preparedStatement1 = conn.prepareStatement(sql1);
            preparedStatement1.setInt(1, referenceSetID);
            resultSet = preparedStatement1.executeQuery();
            if (resultSet.next()) {
                return getEamGlobalSetFromResultSet(resultSet);
            } else {
                return null;
            }

        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting reference set by id.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement1);
            CentralRepoDbUtil.closeConnection(conn);
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

        if (correlationType == null) {
            throw new CentralRepoException("Correlation type is null");
        }

        List<CentralRepoFileSet> results = new ArrayList<>();
        Connection conn = connect();

        PreparedStatement preparedStatement1 = null;
        ResultSet resultSet = null;
        String sql1 = "SELECT * FROM reference_sets WHERE type=" + correlationType.getId();

        try {
            preparedStatement1 = conn.prepareStatement(sql1);
            resultSet = preparedStatement1.executeQuery();
            while (resultSet.next()) {
                results.add(getEamGlobalSetFromResultSet(resultSet));
            }

        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting reference sets.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement1);
            CentralRepoDbUtil.closeConnection(conn);
        }
        return results;
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
        if (eamGlobalFileInstance.getKnownStatus() == null) {
            throw new CentralRepoException("Known status of EamGlobalFileInstance is null");
        }
        if (correlationType == null) {
            throw new CentralRepoException("Correlation type is null");
        }

        Connection conn = connect();

        PreparedStatement preparedStatement = null;

        String sql = "INSERT INTO %s(reference_set_id, value, known_status, comment) VALUES (?, ?, ?, ?) "
                + getConflictClause();

        try {
            preparedStatement = conn.prepareStatement(String.format(sql, CentralRepoDbUtil.correlationTypeToReferenceTableName(correlationType)));
            preparedStatement.setInt(1, eamGlobalFileInstance.getGlobalSetID());
            preparedStatement.setString(2, eamGlobalFileInstance.getMD5Hash());
            preparedStatement.setByte(3, eamGlobalFileInstance.getKnownStatus().getFileKnownValue());
            preparedStatement.setString(4, eamGlobalFileInstance.getComment());
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new CentralRepoException("Error inserting new reference instance into reference_ table.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
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
        Connection conn = connect();

        PreparedStatement preparedStatement1 = null;
        ResultSet resultSet = null;
        String sql1 = "SELECT * FROM reference_sets WHERE set_name=? AND version=?";

        try {
            preparedStatement1 = conn.prepareStatement(sql1);
            preparedStatement1.setString(1, referenceSetName);
            preparedStatement1.setString(2, version);
            resultSet = preparedStatement1.executeQuery();
            return (resultSet.next());

        } catch (SQLException ex) {
            throw new CentralRepoException("Error testing whether reference set exists (name: " + referenceSetName
                    + " version: " + version, ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement1);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Insert the bulk collection of Reference Type Instances
     *
     * @throws CentralRepoException
     */
    @Override
    public void bulkInsertReferenceTypeEntries(Set<CentralRepoFileInstance> globalInstances, CorrelationAttributeInstance.Type contentType) throws CentralRepoException {
        if (contentType == null) {
            throw new CentralRepoException("Correlation type is null");
        }
        if (globalInstances == null) {
            throw new CentralRepoException("Null set of EamGlobalFileInstance");
        }

        Connection conn = connect();

        PreparedStatement bulkPs = null;
        try {
            conn.setAutoCommit(false);

            // FUTURE: have a separate global_files table for each Type.
            String sql = "INSERT INTO %s(reference_set_id, value, known_status, comment) VALUES (?, ?, ?, ?) "
                    + getConflictClause();

            bulkPs = conn.prepareStatement(String.format(sql, CentralRepoDbUtil.correlationTypeToReferenceTableName(contentType)));

            for (CentralRepoFileInstance globalInstance : globalInstances) {
                if (globalInstance.getKnownStatus() == null) {
                    throw new CentralRepoException("EamGlobalFileInstance with value " + globalInstance.getMD5Hash() + " has null known status");
                }

                bulkPs.setInt(1, globalInstance.getGlobalSetID());
                bulkPs.setString(2, globalInstance.getMD5Hash());
                bulkPs.setByte(3, globalInstance.getKnownStatus().getFileKnownValue());
                bulkPs.setString(4, globalInstance.getComment());
                bulkPs.addBatch();
            }

            bulkPs.executeBatch();
            conn.commit();
        } catch (SQLException | CentralRepoException ex) {
            try {
                conn.rollback();
            } catch (SQLException ex2) {
                // We're alredy in an error state
            }
            throw new CentralRepoException("Error inserting bulk artifacts.", ex); // NON-NLS           
        } finally {
            CentralRepoDbUtil.closeStatement(bulkPs);
            CentralRepoDbUtil.closeConnection(conn);
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
        String normalizeValued = CorrelationAttributeNormalizer.normalize(aType, aValue);

        Connection conn = connect();

        List<CentralRepoFileInstance> globalFileInstances = new ArrayList<>();
        PreparedStatement preparedStatement1 = null;
        ResultSet resultSet = null;
        String sql1 = "SELECT * FROM %s WHERE value=?";

        try {
            preparedStatement1 = conn.prepareStatement(String.format(sql1, CentralRepoDbUtil.correlationTypeToReferenceTableName(aType)));
            preparedStatement1.setString(1, normalizeValued);
            resultSet = preparedStatement1.executeQuery();
            while (resultSet.next()) {
                globalFileInstances.add(getEamGlobalFileInstanceFromResultSet(resultSet));
            }

        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting reference instances by type and value.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement1);
            CentralRepoDbUtil.closeConnection(conn);
        }

        return globalFileInstances;
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
        if (newType == null) {
            throw new CentralRepoException("Correlation type is null");
        }
        int typeId;
        if (-1 == newType.getId()) {
            typeId = newCorrelationTypeNotKnownId(newType);
        } else {
            typeId = newCorrelationTypeKnownId(newType);
        }

        synchronized (typeCache) {
            typeCache.put(newType.getId(), newType);
        }
        return typeId;
    }

    /**
     * Helper function which adds a new EamArtifact.Type to the db without an
     * id.
     *
     * @param newType New type to add.
     *
     * @return ID of this new Correlation Type
     *
     * @throws CentralRepoException
     */
    public int newCorrelationTypeNotKnownId(CorrelationAttributeInstance.Type newType) throws CentralRepoException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        PreparedStatement preparedStatementQuery = null;
        ResultSet resultSet = null;
        int typeId = 0;
        String insertSql;
        String querySql;
        // if we have a known ID, use it, if not (is -1) let the db assign it.
        insertSql = "INSERT INTO correlation_types(display_name, db_table_name, supported, enabled) VALUES (?, ?, ?, ?) " + getConflictClause();

        querySql = "SELECT * FROM correlation_types WHERE display_name=? AND db_table_name=?";

        try {
            preparedStatement = conn.prepareStatement(insertSql);

            preparedStatement.setString(1, newType.getDisplayName());
            preparedStatement.setString(2, newType.getDbTableName());
            preparedStatement.setInt(3, newType.isSupported() ? 1 : 0);
            preparedStatement.setInt(4, newType.isEnabled() ? 1 : 0);

            preparedStatement.executeUpdate();

            preparedStatementQuery = conn.prepareStatement(querySql);
            preparedStatementQuery.setString(1, newType.getDisplayName());
            preparedStatementQuery.setString(2, newType.getDbTableName());

            resultSet = preparedStatementQuery.executeQuery();
            if (resultSet.next()) {
                CorrelationAttributeInstance.Type correlationType = getCorrelationTypeFromResultSet(resultSet);
                typeId = correlationType.getId();
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error inserting new correlation type.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeStatement(preparedStatementQuery);
            CentralRepoDbUtil.closeConnection(conn);
        }
        return typeId;
    }

    /**
     * Helper function which adds a new EamArtifact.Type to the db.
     *
     * @param newType New type to add.
     *
     * @return ID of this new Correlation Type
     *
     * @throws CentralRepoException
     */
    private int newCorrelationTypeKnownId(CorrelationAttributeInstance.Type newType) throws CentralRepoException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        PreparedStatement preparedStatementQuery = null;
        ResultSet resultSet = null;
        int typeId = 0;
        String insertSql;
        String querySql;
        // if we have a known ID, use it, if not (is -1) let the db assign it.
        insertSql = "INSERT INTO correlation_types(id, display_name, db_table_name, supported, enabled) VALUES (?, ?, ?, ?, ?) " + getConflictClause();

        querySql = "SELECT * FROM correlation_types WHERE display_name=? AND db_table_name=?";

        try {
            preparedStatement = conn.prepareStatement(insertSql);

            preparedStatement.setInt(1, newType.getId());
            preparedStatement.setString(2, newType.getDisplayName());
            preparedStatement.setString(3, newType.getDbTableName());
            preparedStatement.setInt(4, newType.isSupported() ? 1 : 0);
            preparedStatement.setInt(5, newType.isEnabled() ? 1 : 0);

            preparedStatement.executeUpdate();

            preparedStatementQuery = conn.prepareStatement(querySql);
            preparedStatementQuery.setString(1, newType.getDisplayName());
            preparedStatementQuery.setString(2, newType.getDbTableName());

            resultSet = preparedStatementQuery.executeQuery();
            if (resultSet.next()) {
                CorrelationAttributeInstance.Type correlationType = getCorrelationTypeFromResultSet(resultSet);
                typeId = correlationType.getId();
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error inserting new correlation type.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeStatement(preparedStatementQuery);
            CentralRepoDbUtil.closeConnection(conn);
        }
        return typeId;
    }

    @Override
    public List<CorrelationAttributeInstance.Type> getDefinedCorrelationTypes() throws CentralRepoException {

        synchronized (typeCache) {
            if (isCRTypeCacheInitialized == false) {
                getCorrelationTypesFromCr();
            }
            return new ArrayList<>(typeCache.asMap().values());
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
        Connection conn = connect();

        List<CorrelationAttributeInstance.Type> aTypes = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM correlation_types WHERE enabled=1";

        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                aTypes.add(getCorrelationTypeFromResultSet(resultSet));
            }
            return aTypes;

        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting enabled correlation types.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
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
        Connection conn = connect();

        List<CorrelationAttributeInstance.Type> aTypes = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM correlation_types WHERE supported=1";

        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                aTypes.add(getCorrelationTypeFromResultSet(resultSet));
            }
            return aTypes;

        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting supported correlation types.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
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
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "UPDATE correlation_types SET display_name=?, db_table_name=?, supported=?, enabled=? WHERE id=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, aType.getDisplayName());
            preparedStatement.setString(2, aType.getDbTableName());
            preparedStatement.setInt(3, aType.isSupported() ? 1 : 0);
            preparedStatement.setInt(4, aType.isEnabled() ? 1 : 0);
            preparedStatement.setInt(5, aType.getId());
            preparedStatement.executeUpdate();
            synchronized (typeCache) {
                typeCache.put(aType.getId(), aType);
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error updating correlation type.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
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
            synchronized (typeCache) {
                return typeCache.get(typeId, () -> getCorrelationTypeByIdFromCr(typeId));
            }
        } catch (CacheLoader.InvalidCacheLoadException ignored) {
            //lambda valueloader returned a null value and cache can not store null values this is normal if the correlation type does not exist in the central repo yet
            return null;
        } catch (ExecutionException ex) {
            throw new CentralRepoException("Error getting correlation type", ex);
        }
    }

    /**
     * Get the EamArtifact.Type that has the given Type.Id from the central repo
     *
     * @param typeId Type.Id of Correlation Type to get
     *
     * @return EamArtifact.Type or null if it doesn't exist.
     *
     * @throws CentralRepoException
     */
    private CorrelationAttributeInstance.Type getCorrelationTypeByIdFromCr(int typeId) throws CentralRepoException {
        Connection conn = connect();

        CorrelationAttributeInstance.Type aType;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM correlation_types WHERE id=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, typeId);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                aType = getCorrelationTypeFromResultSet(resultSet);
                return aType;
            } else {
                throw new CentralRepoException("Failed to find entry for correlation type ID = " + typeId);
            }

        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting correlation type by id.", ex); // NON-NLS
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

    /**
     * Reads the correlation types from the database and loads them up in the
     * cache.
     *
     * @throws CentralRepoException If there is an error.
     */
    private void getCorrelationTypesFromCr() throws CentralRepoException {

        // clear out the cache
        synchronized (typeCache) {
            typeCache.invalidateAll();
            isCRTypeCacheInitialized = false;
        }

        String sql = "SELECT * FROM correlation_types";
        try (Connection conn = connect();
                PreparedStatement preparedStatement = conn.prepareStatement(sql);
                ResultSet resultSet = preparedStatement.executeQuery();) {

            synchronized (typeCache) {
                while (resultSet.next()) {
                    CorrelationAttributeInstance.Type aType = getCorrelationTypeFromResultSet(resultSet);
                    typeCache.put(aType.getId(), aType);
                }
                isCRTypeCacheInitialized = true;
            }
        } catch (SQLException ex) {
            throw new CentralRepoException("Error getting correlation types.", ex); // NON-NLS
        }
    }

    /**
     * Convert a ResultSet to a EamCase object
     *
     * @param resultSet A resultSet with a set of values to create a EamCase
     *                  object.
     *
     * @return fully populated EamCase object, or null
     *
     * @throws SQLException when an expected column name is not in the resultSet
     */
    private CorrelationCase getEamCaseFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }

        CentralRepoOrganization eamOrg = null;

        resultSet.getInt("org_id");
        if (!resultSet.wasNull()) {

            eamOrg = new CentralRepoOrganization(resultSet.getInt("org_id"),
                    resultSet.getString("org_name"),
                    resultSet.getString("poc_name"),
                    resultSet.getString("poc_email"),
                    resultSet.getString("poc_phone"));
        }

        CorrelationCase eamCase = new CorrelationCase(resultSet.getInt("case_id"), resultSet.getString("case_uid"), eamOrg, resultSet.getString("case_name"),
                resultSet.getString("creation_date"), resultSet.getString("case_number"), resultSet.getString("examiner_name"),
                resultSet.getString("examiner_email"), resultSet.getString("examiner_phone"), resultSet.getString("notes"));

        return eamCase;
    }

    private CorrelationDataSource getEamDataSourceFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }

        CorrelationDataSource eamDataSource = new CorrelationDataSource(
                resultSet.getInt("case_id"),
                resultSet.getInt("id"),
                resultSet.getString("device_id"),
                resultSet.getString("name"),
                resultSet.getLong("datasource_obj_id"),
                resultSet.getString("md5"),
                resultSet.getString("sha1"),
                resultSet.getString("sha256")
        );

        return eamDataSource;
    }

    private CorrelationAttributeInstance.Type getCorrelationTypeFromResultSet(ResultSet resultSet) throws CentralRepoException, SQLException {
        if (null == resultSet) {
            return null;
        }

        CorrelationAttributeInstance.Type eamArtifactType = new CorrelationAttributeInstance.Type(
                resultSet.getInt("id"),
                resultSet.getString("display_name"),
                resultSet.getString("db_table_name"),
                resultSet.getBoolean("supported"),
                resultSet.getBoolean("enabled")
        );

        return eamArtifactType;
    }

    /**
     * Convert a ResultSet to a EamArtifactInstance object
     *
     * @param resultSet A resultSet with a set of values to create a
     *                  EamArtifactInstance object.
     *
     * @return fully populated EamArtifactInstance, or null
     *
     * @throws SQLException when an expected column name is not in the resultSet
     */
    private CorrelationAttributeInstance getCorrAttrFromResultSet(ResultSet resultSet, CorrelationAttributeInstance.Type aType) throws SQLException, CentralRepoException, CorrelationAttributeNormalizationException {
        if (null == resultSet) {
            return null;
        }

        CentralRepoOrganization eamOrg = new CentralRepoOrganization(resultSet.getInt("org_id"),
                resultSet.getString("org_name"),
                resultSet.getString("poc_name"),
                resultSet.getString("poc_email"),
                resultSet.getString("poc_phone"));

        return new CorrelationAttributeInstance(
                aType,
                resultSet.getString("value"),
                resultSet.getInt("instance_id"),
                new CorrelationCase(resultSet.getInt("id"), resultSet.getString("case_uid"), eamOrg, resultSet.getString("case_name"),
                        resultSet.getString("creation_date"), resultSet.getString("case_number"), resultSet.getString("examiner_name"),
                        resultSet.getString("examiner_email"), resultSet.getString("examiner_phone"), resultSet.getString("notes")),
                new CorrelationDataSource(
                        resultSet.getInt("id"), resultSet.getInt("data_source_id"), resultSet.getString("device_id"), resultSet.getString("name"),
                        resultSet.getLong("datasource_obj_id"), resultSet.getString("md5"), resultSet.getString("sha1"), resultSet.getString("sha256")),
                resultSet.getString("file_path"),
                resultSet.getString("comment"),
                TskData.FileKnown.valueOf(resultSet.getByte("known_status")),
                resultSet.getLong("file_obj_id"));
    }

    private CentralRepoOrganization getEamOrganizationFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }

        return new CentralRepoOrganization(
                resultSet.getInt("id"),
                resultSet.getString("org_name"),
                resultSet.getString("poc_name"),
                resultSet.getString("poc_email"),
                resultSet.getString("poc_phone")
        );
    }

    private CentralRepoFileSet getEamGlobalSetFromResultSet(ResultSet resultSet) throws SQLException, CentralRepoException {
        if (null == resultSet) {
            return null;
        }

        return new CentralRepoFileSet(
                resultSet.getInt("id"),
                resultSet.getInt("org_id"),
                resultSet.getString("set_name"),
                resultSet.getString("version"),
                TskData.FileKnown.valueOf(resultSet.getByte("known_status")),
                resultSet.getBoolean("read_only"),
                CentralRepository.getInstance().getCorrelationTypeById(resultSet.getInt("type")),
                LocalDate.parse(resultSet.getString("import_date"))
        );
    }

    private CentralRepoFileInstance getEamGlobalFileInstanceFromResultSet(ResultSet resultSet) throws SQLException, CentralRepoException, CorrelationAttributeNormalizationException {
        if (null == resultSet) {
            return null;
        }

        return new CentralRepoFileInstance(
                resultSet.getInt("id"),
                resultSet.getInt("reference_set_id"),
                resultSet.getString("value"),
                TskData.FileKnown.valueOf(resultSet.getByte("known_status")),
                resultSet.getString("comment")
        );
    }

    private String getPlatformSpecificInsertSQL(String sql) throws CentralRepoException {

        switch (CentralRepoDbManager.getSavedDbChoice().getDbPlatform()) {
            case POSTGRESQL:
                return "INSERT " + sql + " ON CONFLICT DO NOTHING"; //NON-NLS
            case SQLITE:
                return "INSERT OR IGNORE " + sql;

            default:
                throw new CentralRepoException("Unknown Central Repo DB platform" + CentralRepoDbManager.getSavedDbChoice().getDbPlatform());
        }
    }

    /**
     * Determine if a specific column already exists in a specific table
     *
     * @param tableName  the table to check for the specified column
     * @param columnName the name of the column to check for
     *
     * @return true if the column exists, false if the column does not exist
     *
     * @throws CentralRepoException
     */
    abstract boolean doesColumnExist(Connection conn, String tableName, String columnName) throws SQLException;

    /**
     * Upgrade the schema of the database (if needed)
     *
     * @throws CentralRepoException
     */
    @Messages({"AbstractSqlEamDb.upgradeSchema.incompatible=The selected Central Repository is not compatible with the current version of the application, please upgrade the application if you wish to use this Central Repository.",
        "# {0} - minorVersion",
        "AbstractSqlEamDb.badMinorSchema.message=Bad value for schema minor version ({0}) - database is corrupt.",
        "AbstractSqlEamDb.failedToReadMinorVersion.message=Failed to read schema minor version for Central Repository.",
        "# {0} - majorVersion",
        "AbstractSqlEamDb.badMajorSchema.message=Bad value for schema version ({0}) - database is corrupt.",
        "AbstractSqlEamDb.failedToReadMajorVersion.message=Failed to read schema version for Central Repository.",
        "# {0} - platformName",
        "AbstractSqlEamDb.cannotUpgrage.message=Currently selected database platform \"{0}\" can not be upgraded."})
    @Override
    public void upgradeSchema() throws CentralRepoException, SQLException, IncompatibleCentralRepoException {

        ResultSet resultSet = null;
        Statement statement = null;
        PreparedStatement preparedStatement = null;
        Connection conn = null;
        CentralRepoPlatforms selectedPlatform = null;
        try {

            conn = connect(false);
            conn.setAutoCommit(false);
            statement = conn.createStatement();
            selectedPlatform = CentralRepoDbManager.getSavedDbChoice().getDbPlatform();
            int minorVersion = 0;
            String minorVersionStr = null;
            resultSet = statement.executeQuery("SELECT value FROM db_info WHERE name='" + RdbmsCentralRepo.SCHEMA_MINOR_VERSION_KEY + "'");
            if (resultSet.next()) {
                minorVersionStr = resultSet.getString("value");
                try {
                    minorVersion = Integer.parseInt(minorVersionStr);
                } catch (NumberFormatException ex) {
                    throw new CentralRepoException("Bad value for schema minor version (" + minorVersionStr + ") - database is corrupt", Bundle.AbstractSqlEamDb_badMinorSchema_message(minorVersionStr), ex);
                }
            } else {
                throw new CentralRepoException("Failed to read schema minor version from db_info table", Bundle.AbstractSqlEamDb_failedToReadMinorVersion_message());
            }

            int majorVersion = 0;
            String majorVersionStr = null;
            resultSet = statement.executeQuery("SELECT value FROM db_info WHERE name='" + RdbmsCentralRepo.SCHEMA_MAJOR_VERSION_KEY + "'");
            if (resultSet.next()) {
                majorVersionStr = resultSet.getString("value");
                try {
                    majorVersion = Integer.parseInt(majorVersionStr);
                } catch (NumberFormatException ex) {
                    throw new CentralRepoException("Bad value for schema version (" + majorVersionStr + ") - database is corrupt", Bundle.AbstractSqlEamDb_badMajorSchema_message(majorVersionStr), ex);
                }
            } else {
                throw new CentralRepoException("Failed to read schema major version from db_info table", Bundle.AbstractSqlEamDb_failedToReadMajorVersion_message());
            }

            /*
             * IMPORTANT: The code that follows had a bug in it prior to Autopsy
             * 4.10.0. The consequence of the bug is that the schema version
             * number is always reset to 1.0 or 1.1 if a Central Repository is
             * opened by an Autopsy 4.9.1 or earlier client. To cope with this,
             * there is an effort in updates to 1.2 and greater to not retry
             * schema updates that may already have been done once.
             */
            CaseDbSchemaVersionNumber dbSchemaVersion = new CaseDbSchemaVersionNumber(majorVersion, minorVersion);

            //compare the major versions for compatability 
            //we can not use the CaseDbSchemaVersionNumber.isCompatible method 
            //because it is specific to case db schema versions only supporting major versions greater than 1
            if (SOFTWARE_CR_DB_SCHEMA_VERSION.getMajor() < dbSchemaVersion.getMajor()) {
                throw new IncompatibleCentralRepoException(Bundle.AbstractSqlEamDb_upgradeSchema_incompatible());
            }
            if (dbSchemaVersion.equals(SOFTWARE_CR_DB_SCHEMA_VERSION)) {
                logger.log(Level.INFO, "Central Repository is up to date");
                return;
            }
            if (dbSchemaVersion.compareTo(SOFTWARE_CR_DB_SCHEMA_VERSION) > 0) {
                logger.log(Level.INFO, "Central Repository is of newer version than software creates");
                return;
            }

            /*
             * Update to 1.1
             */
            if (dbSchemaVersion.compareTo(new CaseDbSchemaVersionNumber(1, 1)) < 0) {
                statement.execute("ALTER TABLE reference_sets ADD COLUMN known_status INTEGER;"); //NON-NLS
                statement.execute("ALTER TABLE reference_sets ADD COLUMN read_only BOOLEAN;"); //NON-NLS
                statement.execute("ALTER TABLE reference_sets ADD COLUMN type INTEGER;"); //NON-NLS

                // There's an outide chance that the user has already made an organization with the default name,
                // and the default org being missing will not impact any database operations, so continue on
                // regardless of whether this succeeds.
                CentralRepoDbUtil.insertDefaultOrganization(conn);
            }

            /*
             * Update to 1.2
             */
            if (dbSchemaVersion.compareTo(new CaseDbSchemaVersionNumber(1, 2)) < 0) {
                final String addIntegerColumnTemplate = "ALTER TABLE %s ADD COLUMN %s INTEGER;";  //NON-NLS

                final String addSsidTableTemplate = RdbmsCentralRepoFactory.getCreateArtifactInstancesTableTemplate(selectedPlatform);
                final String addCaseIdIndexTemplate = RdbmsCentralRepoFactory.getAddCaseIdIndexTemplate();
                final String addDataSourceIdIndexTemplate = RdbmsCentralRepoFactory.getAddDataSourceIdIndexTemplate();
                final String addValueIndexTemplate = RdbmsCentralRepoFactory.getAddValueIndexTemplate();
                final String addKnownStatusIndexTemplate = RdbmsCentralRepoFactory.getAddKnownStatusIndexTemplate();
                final String addObjectIdIndexTemplate = RdbmsCentralRepoFactory.getAddObjectIdIndexTemplate();

                final String addAttributeSql;
                //get the data base specific code for creating a new _instance table
                switch (selectedPlatform) {
                    case POSTGRESQL:
                        addAttributeSql = "INSERT INTO correlation_types(id, display_name, db_table_name, supported, enabled) VALUES (?, ?, ?, ?, ?) " + getConflictClause();  //NON-NLS
                        break;
                    case SQLITE:
                        addAttributeSql = "INSERT OR IGNORE INTO correlation_types(id, display_name, db_table_name, supported, enabled) VALUES (?, ?, ?, ?, ?)";  //NON-NLS
                        break;
                    default:
                        throw new CentralRepoException("Currently selected database platform \"" + selectedPlatform.name() + "\" can not be upgraded.", Bundle.AbstractSqlEamDb_cannotUpgrage_message(selectedPlatform.name()));
                }

                final String dataSourcesTableName = "data_sources";
                final String dataSourceObjectIdColumnName = "datasource_obj_id";
                if (!doesColumnExist(conn, dataSourcesTableName, dataSourceObjectIdColumnName)) {
                    statement.execute(String.format(addIntegerColumnTemplate, dataSourcesTableName, dataSourceObjectIdColumnName)); //NON-NLS
                }
                final String dataSourceObjectIdIndexTemplate = "CREATE INDEX IF NOT EXISTS datasource_object_id ON data_sources (%s)";
                statement.execute(String.format(dataSourceObjectIdIndexTemplate, dataSourceObjectIdColumnName));
                List<String> instaceTablesToAdd = new ArrayList<>();
                //update central repository to be able to store new correlation attributes 
                final String wirelessNetworksDbTableName = "wireless_networks";
                instaceTablesToAdd.add(wirelessNetworksDbTableName + "_instances");
                final String macAddressDbTableName = "mac_address";
                instaceTablesToAdd.add(macAddressDbTableName + "_instances");
                final String imeiNumberDbTableName = "imei_number";
                instaceTablesToAdd.add(imeiNumberDbTableName + "_instances");
                final String iccidNumberDbTableName = "iccid_number";
                instaceTablesToAdd.add(iccidNumberDbTableName + "_instances");
                final String imsiNumberDbTableName = "imsi_number";
                instaceTablesToAdd.add(imsiNumberDbTableName + "_instances");

                //add the wireless_networks attribute to the correlation_types table
                preparedStatement = conn.prepareStatement(addAttributeSql);
                preparedStatement.setInt(1, CorrelationAttributeInstance.SSID_TYPE_ID);
                preparedStatement.setString(2, Bundle.CorrelationType_SSID_displayName());
                preparedStatement.setString(3, wirelessNetworksDbTableName);
                preparedStatement.setInt(4, 1);
                preparedStatement.setInt(5, 1);
                preparedStatement.execute();

                //add the mac_address attribute to the correlation_types table
                preparedStatement = conn.prepareStatement(addAttributeSql);
                preparedStatement.setInt(1, CorrelationAttributeInstance.MAC_TYPE_ID);
                preparedStatement.setString(2, Bundle.CorrelationType_MAC_displayName());
                preparedStatement.setString(3, macAddressDbTableName);
                preparedStatement.setInt(4, 1);
                preparedStatement.setInt(5, 1);
                preparedStatement.execute();

                //add the imei_number attribute to the correlation_types table
                preparedStatement = conn.prepareStatement(addAttributeSql);
                preparedStatement.setInt(1, CorrelationAttributeInstance.IMEI_TYPE_ID);
                preparedStatement.setString(2, Bundle.CorrelationType_IMEI_displayName());
                preparedStatement.setString(3, imeiNumberDbTableName);
                preparedStatement.setInt(4, 1);
                preparedStatement.setInt(5, 1);
                preparedStatement.execute();

                //add the imsi_number attribute to the correlation_types table
                preparedStatement = conn.prepareStatement(addAttributeSql);
                preparedStatement.setInt(1, CorrelationAttributeInstance.IMSI_TYPE_ID);
                preparedStatement.setString(2, Bundle.CorrelationType_IMSI_displayName());
                preparedStatement.setString(3, imsiNumberDbTableName);
                preparedStatement.setInt(4, 1);
                preparedStatement.setInt(5, 1);
                preparedStatement.execute();

                //add the iccid_number attribute to the correlation_types table
                preparedStatement = conn.prepareStatement(addAttributeSql);
                preparedStatement.setInt(1, CorrelationAttributeInstance.ICCID_TYPE_ID);
                preparedStatement.setString(2, Bundle.CorrelationType_ICCID_displayName());
                preparedStatement.setString(3, iccidNumberDbTableName);
                preparedStatement.setInt(4, 1);
                preparedStatement.setInt(5, 1);
                preparedStatement.execute();

                //create a new _instances tables and add indexes for their columns
                for (String tableName : instaceTablesToAdd) {
                    statement.execute(String.format(addSsidTableTemplate, tableName, tableName));
                    statement.execute(String.format(addCaseIdIndexTemplate, tableName, tableName));
                    statement.execute(String.format(addDataSourceIdIndexTemplate, tableName, tableName));
                    statement.execute(String.format(addValueIndexTemplate, tableName, tableName));
                    statement.execute(String.format(addKnownStatusIndexTemplate, tableName, tableName));
                }

                //add file_obj_id column to _instances table which do not already have it
                String instance_type_dbname;
                final String objectIdColumnName = "file_obj_id";
                for (CorrelationAttributeInstance.Type type : CorrelationAttributeInstance.getDefaultCorrelationTypes()) {
                    instance_type_dbname = CentralRepoDbUtil.correlationTypeToInstanceTableName(type);
                    if (!doesColumnExist(conn, instance_type_dbname, objectIdColumnName)) {
                        statement.execute(String.format(addIntegerColumnTemplate, instance_type_dbname, objectIdColumnName)); //NON-NLS
                    }
                    statement.execute(String.format(addObjectIdIndexTemplate, instance_type_dbname, instance_type_dbname));
                }

                /*
                 * Add hash columns to the data_sources table.
                 */
                if (!doesColumnExist(conn, dataSourcesTableName, "md5")) {
                    statement.execute("ALTER TABLE data_sources ADD COLUMN md5 TEXT DEFAULT NULL");
                }
                if (!doesColumnExist(conn, dataSourcesTableName, "sha1")) {
                    statement.execute("ALTER TABLE data_sources ADD COLUMN sha1 TEXT DEFAULT NULL");
                }
                if (!doesColumnExist(conn, dataSourcesTableName, "sha256")) {
                    statement.execute("ALTER TABLE data_sources ADD COLUMN sha256 TEXT DEFAULT NULL");
                }

                /*
                 * Drop the db_info table and add it back in with the name
                 * column having a UNIQUE constraint. The name column could now
                 * be used as the primary key, but the essentially useless id
                 * column is retained for the sake of backwards compatibility.
                 * Note that the creation schema version number is set to 0.0 to
                 * indicate that it is unknown.
                 */
                String creationMajorVer;
                resultSet = statement.executeQuery("SELECT value FROM db_info WHERE name = '" + RdbmsCentralRepo.CREATION_SCHEMA_MAJOR_VERSION_KEY + "'");
                if (resultSet.next()) {
                    creationMajorVer = resultSet.getString("value");
                } else {
                    creationMajorVer = "0";
                }
                String creationMinorVer;
                resultSet = statement.executeQuery("SELECT value FROM db_info WHERE name = '" + RdbmsCentralRepo.CREATION_SCHEMA_MINOR_VERSION_KEY + "'");
                if (resultSet.next()) {
                    creationMinorVer = resultSet.getString("value");
                } else {
                    creationMinorVer = "0";
                }
                statement.execute("DROP TABLE db_info");
                if (selectedPlatform == CentralRepoPlatforms.POSTGRESQL) {
                    statement.execute("CREATE TABLE db_info (id SERIAL, name TEXT UNIQUE NOT NULL, value TEXT NOT NULL)");
                } else {
                    statement.execute("CREATE TABLE db_info (id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL, value TEXT NOT NULL)");
                }
                statement.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.SCHEMA_MAJOR_VERSION_KEY + "','" + majorVersionStr + "')");
                statement.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.SCHEMA_MINOR_VERSION_KEY + "','" + minorVersionStr + "')");
                statement.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.CREATION_SCHEMA_MAJOR_VERSION_KEY + "','" + creationMajorVer + "')");
                statement.execute("INSERT INTO db_info (name, value) VALUES ('" + RdbmsCentralRepo.CREATION_SCHEMA_MINOR_VERSION_KEY + "','" + creationMinorVer + "')");
            }
            /*
             * Update to 1.3
             */
            if (dbSchemaVersion.compareTo(new CaseDbSchemaVersionNumber(1, 3)) < 0) {
                switch (selectedPlatform) {
                    case POSTGRESQL:
                        statement.execute("ALTER TABLE data_sources DROP CONSTRAINT datasource_unique");
                        //unique constraint for upgraded data_sources table is purposefully different than new data_sources table
                        statement.execute("ALTER TABLE data_sources ADD CONSTRAINT datasource_unique UNIQUE (case_id, device_id, name, datasource_obj_id)");

                        break;
                    case SQLITE:
                        statement.execute("DROP INDEX IF EXISTS data_sources_name");
                        statement.execute("DROP INDEX IF EXISTS data_sources_object_id");
                        statement.execute("ALTER TABLE data_sources RENAME TO old_data_sources");
                        //unique constraint for upgraded data_sources table is purposefully different than new data_sources table
                        statement.execute("CREATE TABLE IF NOT EXISTS data_sources (id integer primary key autoincrement NOT NULL,"
                                + "case_id integer NOT NULL,device_id text NOT NULL,name text NOT NULL,datasource_obj_id integer,"
                                + "md5 text DEFAULT NULL,sha1 text DEFAULT NULL,sha256 text DEFAULT NULL,"
                                + "foreign key (case_id) references cases(id) ON UPDATE SET NULL ON DELETE SET NULL,"
                                + "CONSTRAINT datasource_unique UNIQUE (case_id, device_id, name, datasource_obj_id))");
                        statement.execute(RdbmsCentralRepoFactory.getAddDataSourcesNameIndexStatement());
                        statement.execute(RdbmsCentralRepoFactory.getAddDataSourcesObjectIdIndexStatement());
                        statement.execute("INSERT INTO data_sources SELECT * FROM old_data_sources");
                        statement.execute("DROP TABLE old_data_sources");
                        break;
                    default:
                        throw new CentralRepoException("Currently selected database platform \"" + selectedPlatform.name() + "\" can not be upgraded.", Bundle.AbstractSqlEamDb_cannotUpgrage_message(selectedPlatform.name()));
                }
            }

            // Upgrade to 1.4
            (new CentralRepoDbUpgrader13To14()).upgradeSchema(dbSchemaVersion, conn);

            // Upgrade to 1.5
            (new CentralRepoDbUpgrader14To15()).upgradeSchema(dbSchemaVersion, conn);

            // Upgrade to 1.6
            (new CentralRepoDbUpgrader15To16()).upgradeSchema(dbSchemaVersion, conn);

            updateSchemaVersion(conn);
            conn.commit();
            logger.log(Level.INFO, String.format("Central Repository schema updated to version %s", SOFTWARE_CR_DB_SCHEMA_VERSION));
        } catch (SQLException | CentralRepoException ex) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex2) {
                logger.log(Level.SEVERE, String.format("Central Repository rollback of failed schema update to %s failed", SOFTWARE_CR_DB_SCHEMA_VERSION), ex2);
            }
            throw ex;
        } finally {
            CentralRepoDbUtil.closeResultSet(resultSet);
            CentralRepoDbUtil.closeStatement(preparedStatement);
            CentralRepoDbUtil.closeStatement(statement);
            CentralRepoDbUtil.closeConnection(conn);
        }
    }

}
