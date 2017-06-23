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
import java.sql.Types;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.sleuthkit.autopsy.coreutils.Logger;

/**
 *
 * SQLite manager implementation
 *
 */
public abstract class AbstractSqlEamDb implements EamDb {

    private final static Logger LOGGER = Logger.getLogger(AbstractSqlEamDb.class.getName());

    protected final List<EamArtifact.Type> DEFAULT_ARTIFACT_TYPES;

    private int bulkArtifactsCount;
    private int bulkGlobalArtifactsCount;
    protected int bulkArtifactsThreshold;
    private final Map<String, Collection<EamArtifact>> bulkArtifacts;
    private final Map<String, Collection<EamGlobalFileInstance>> bulkGlobalArtifacts;
    private final List<String> badTags;

    /**
     * Connect to the DB and initialize it.
     *
     * @throws UnknownHostException, EamDbException
     */
    protected AbstractSqlEamDb() {
        badTags = new ArrayList<String>();
        bulkArtifactsCount = 0;
        bulkGlobalArtifactsCount = 0;
        bulkArtifacts = new HashMap<>();
        bulkGlobalArtifacts = new HashMap<>();

        DEFAULT_ARTIFACT_TYPES = EamArtifact.getDefaultArtifactTypes();

        for (EamArtifact.Type type : DEFAULT_ARTIFACT_TYPES) {
            bulkArtifacts.put(type.getName(), new ArrayList<>());
            bulkGlobalArtifacts.put(type.getName(), new ArrayList<>());
        }
    }

    /**
     * Check to see if the database schema exists and is the current version. -
     * If it doesn't exist, initialize it and load default content. - If it is
     * not the current version, update it. - If it is already initialized and is
     * the current version, do nothing.
     *
     * Note: this should be call after the connectionPool is initialized.
     */
//    protected void confirmDatabaseSchema() throws EamDbException {
//        int schema_version;
//        try {
//            schema_version = Integer.parseInt(getDbInfo("SCHEMA_VERSION"));
//        } catch (EamDbException | NumberFormatException ex) {
//            // error likely means we have not initialized the schema
//            schema_version = 0;
//            LOGGER.log(Level.WARNING, "Could not find SCHEMA_VERSION in db_info table, assuming database is not initialized.", ex); // NON-NLS
//        }
//
//        if (0 == schema_version) {
////            initializeDatabaseSchema();
//            insertDefaultContent();
//        } else if (SCHEMA_VERSION > schema_version) {
//            // FUTURE: upgrade schema
//        }
//        // else, schema is current
//    }

    /**
     * Setup and create a connection to the selected database implementation
     */
    protected abstract Connection connect() throws EamDbException;

    /**
     * Get the list of tags recognized as "Bad"
     *
     * @return The list of bad tags
     */
    @Override
    public List<String> getBadTags() {
        synchronized (badTags) {
            return new ArrayList<>(badTags);
        }
    }

    /**
     * Set the tags recognized as "Bad"
     *
     * @param tags The tags to consider bad
     */
    @Override
    public void setBadTags(List<String> tags) {
        synchronized (badTags) {
            badTags.clear();
            badTags.addAll(tags);
        }
    }

    /**
     * Add a new name/value pair in the db_info table.
     *
     * @param name  Key to set
     * @param value Value to set
     *
     * @throws EamDbException
     */
    @Override
    public void newDbInfo(String name, String value) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "INSERT INTO db_info (name, value) VALUES (?, ?)";
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, name);
            preparedStatement.setString(2, value);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new EamDbException("Error adding new name/value pair to db_info.", ex);
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }

    }

    /**
     * Get the value for the given name from the name/value db_info table.
     *
     * @param name Name to search for
     *
     * @return value associated with name.
     *
     * @throws EamDbException
     */
    @Override
    public String getDbInfo(String name) throws EamDbException {
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
            throw new EamDbException("Error getting value for name.", ex);
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return value;
    }

    /**
     * Update the value for a name in the name/value db_info table.
     *
     * @param name  Name to find
     * @param value Value to assign to name.
     *
     * @throws EamDbException
     */
    @Override
    public void updateDbInfo(String name, String value) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "UPDATE db_info SET value=? WHERE name=?";
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, value);
            preparedStatement.setString(2, name);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new EamDbException("Error updating value for name.", ex);
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
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
    public void newCase(EamCase eamCase) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;

        String sql = "INSERT INTO cases(case_uid, org_id, case_name, creation_date, case_number, "
                + "examiner_name, examiner_email, examiner_phone, notes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            preparedStatement = conn.prepareStatement(sql);

            preparedStatement.setString(1, eamCase.getCaseUUID());
            if (null == eamCase.getOrg()) {
                preparedStatement.setNull(2, Types.INTEGER);
            } else {
                preparedStatement.setInt(2, eamCase.getOrg().getOrgID());
            }
            preparedStatement.setString(3, eamCase.getDisplayName());
            preparedStatement.setString(4, eamCase.getCreationDate());
            preparedStatement.setString(5, eamCase.getCaseNumber());
            preparedStatement.setString(6, eamCase.getExaminerName());
            preparedStatement.setString(7, eamCase.getExaminerEmail());
            preparedStatement.setString(8, eamCase.getExaminerPhone());
            preparedStatement.setString(9, eamCase.getNotes());

            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting new case.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Updates an existing Case in the database
     *
     * @param eamCase The case to update
     */
    @Override
    public void updateCase(EamCase eamCase) throws EamDbException {
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
            preparedStatement.setString(4, eamCase.getCaseNumber());
            preparedStatement.setString(5, eamCase.getExaminerName());
            preparedStatement.setString(6, eamCase.getExaminerEmail());
            preparedStatement.setString(7, eamCase.getExaminerPhone());
            preparedStatement.setString(8, eamCase.getNotes());
            preparedStatement.setString(9, eamCase.getCaseUUID());

            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new EamDbException("Error updating case.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
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
    public EamCase getCaseDetails(String caseUUID) throws EamDbException {
        Connection conn = connect();

        EamCase eamCaseResult = null;
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
                eamCaseResult = getEnterpriseArtifactManagerCaseFromResultSet(resultSet);
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error getting case details.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return eamCaseResult;
    }

    /**
     * Retrieves cases that are in DB.
     *
     * @return List of cases
     */
    @Override
    public List<EamCase> getCases() throws EamDbException {
        Connection conn = connect();

        List<EamCase> cases = new ArrayList<>();
        EamCase eamCaseResult;
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
                eamCaseResult = getEnterpriseArtifactManagerCaseFromResultSet(resultSet);
                cases.add(eamCaseResult);
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error getting all cases.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return cases;
    }

    /**
     * Creates new Data Source in the database
     *
     * @param eamDataSource the data source to add
     */
    @Override
    public void newDataSource(EamDataSource eamDataSource) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;

        String sql = "INSERT INTO data_sources(device_id, name) VALUES (?, ?)";

        try {
            preparedStatement = conn.prepareStatement(sql);

            preparedStatement.setString(1, eamDataSource.getDeviceID());
            preparedStatement.setString(2, eamDataSource.getName());

            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting new data source.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Updates a Data Source in the database
     *
     * @param eamDataSource the data source to update
     */
    @Override
    public void updateDataSource(EamDataSource eamDataSource) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "UPDATE data_sources SET name=? WHERE device_id=?";

        try {
            preparedStatement = conn.prepareStatement(sql);

            preparedStatement.setString(1, eamDataSource.getName());
            preparedStatement.setString(2, eamDataSource.getDeviceID());

            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new EamDbException("Error updating case.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Retrieves Data Source details based on data source device ID
     *
     * @param dataSourceDeviceId the data source device ID number
     *
     * @return The data source
     */
    @Override
    public EamDataSource getDataSourceDetails(String dataSourceDeviceId) throws EamDbException {
        Connection conn = connect();

        EamDataSource eamDataSourceResult = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String sql = "SELECT * FROM data_sources WHERE device_id=?"; // NON-NLS

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, dataSourceDeviceId);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                eamDataSourceResult = getEnterpriseArtifactManagerDataSourceFromResultSet(resultSet);
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error getting case details.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return eamDataSourceResult;
    }

    /**
     * Return a list of data sources in the DB
     *
     * @return list of data sources in the DB
     */
    @Override
    public List<EamDataSource> getDataSources() throws EamDbException {
        Connection conn = connect();

        List<EamDataSource> dataSources = new ArrayList<>();
        EamDataSource eamDataSourceResult;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String sql = "SELECT * FROM data_sources";

        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                eamDataSourceResult = getEnterpriseArtifactManagerDataSourceFromResultSet(resultSet);
                dataSources.add(eamDataSourceResult);
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error getting all data sources.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return dataSources;
    }

    private String artifactTypeToTableName(EamArtifact.Type type) {
        return type.getName() + "_instances";
    }

    /**
     * Inserts new Artifact(s) into the database. Should add associated Case and
     * Data Source first.
     *
     * @param eamArtifact The artifact to add
     */
    @Override
    public void addArtifact(EamArtifact eamArtifact) throws EamDbException {
        Connection conn = connect();

        List<EamArtifactInstance> eamInstances = eamArtifact.getInstances();
        PreparedStatement preparedStatement = null;

        String tableName = artifactTypeToTableName(eamArtifact.getArtifactType());
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ");
        sql.append(tableName);
        sql.append("(case_id, data_source_id, value, file_path, known_status, comment) ");
        sql.append("VALUES ((SELECT id FROM cases WHERE case_uid=? LIMIT 1), ");
        sql.append("(SELECT id FROM data_sources WHERE device_id=? LIMIT 1), ?, ?, ?, ?)");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            for (EamArtifactInstance eamInstance : eamInstances) {
                preparedStatement.setString(1, eamInstance.getEamCase().getCaseUUID());
                preparedStatement.setString(2, eamInstance.getEamDataSource().getDeviceID());
                preparedStatement.setString(3, eamArtifact.getArtifactValue());
                preparedStatement.setString(4, eamInstance.getFilePath());
                preparedStatement.setString(5, eamInstance.getKnownStatus().name());
                preparedStatement.setString(6, eamInstance.getComment());

                preparedStatement.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting new artifact into artifacts table.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Retrieves eamArtifact instances from the database that are associated with
     * the eamArtifactType and eamArtifactValue of the given eamArtifact.
     *
     * @param eamArtifact The type/value to look up (artifact with 0 instances)
     *
     * @return List of artifact instances for a given type/value
     */
    @Override
    public List<EamArtifactInstance> getArtifactInstancesByTypeValue(EamArtifact eamArtifact) throws EamDbException {
        Connection conn = connect();

        List<EamArtifactInstance> artifactInstances = new ArrayList<>();

        EamArtifactInstance artifactInstance;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = artifactTypeToTableName(eamArtifact.getArtifactType());
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT cases.case_name, cases.case_uid, data_sources.name, device_id, file_path, known_status, comment FROM ");
        sql.append(tableName);
        sql.append(" LEFT JOIN cases ON ");
        sql.append(tableName);
        sql.append(".case_id=cases.id");
        sql.append(" LEFT JOIN data_sources ON ");
        sql.append(tableName);
        sql.append(".data_source_id=data_sources.id");
        sql.append(" WHERE value=?");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            preparedStatement.setString(1, eamArtifact.getArtifactValue());
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                artifactInstance = getEnterpriseArtifactManagerArtifactInstanceFromResultSet(resultSet);
                artifactInstances.add(artifactInstance);
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error getting artifact instances by artifactType and artifactValue.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return artifactInstances;
    }

    /**
     * Retrieves eamArtifact instances from the database that are associated with
     * the aType and filePath
     *
     * @param aType    EamArtifact.Type to search for
     * @param filePath File path to search for
     *
     * @return List of 0 or more EnterpriseArtifactManagerArtifactInstances
     *
     * @throws EamDbException
     */
    @Override
    public List<EamArtifactInstance> getArtifactInstancesByPath(EamArtifact.Type aType, String filePath) throws EamDbException {
        Connection conn = connect();

        List<EamArtifactInstance> artifactInstances = new ArrayList<>();

        EamArtifactInstance artifactInstance;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = artifactTypeToTableName(aType);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT cases.case_name, cases.case_uid, data_sources.name, device_id, file_path, known_status, comment FROM ");
        sql.append(tableName);
        sql.append(" LEFT JOIN cases ON ");
        sql.append(tableName);
        sql.append(".case_id=cases.id");
        sql.append(" LEFT JOIN data_sources ON ");
        sql.append(tableName);
        sql.append(".data_source_id=data_sources.id");
        sql.append(" WHERE file_path=?");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            preparedStatement.setString(1, filePath);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                artifactInstance = getEnterpriseArtifactManagerArtifactInstanceFromResultSet(resultSet);
                artifactInstances.add(artifactInstance);
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error getting artifact instances by artifactType and artifactValue.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return artifactInstances;
    }

    /**
     * Retrieves number of artifact instances in the database that are
     * associated with the ArtifactType and artifactValue of the given artifact.
     *
     * @param eamArtifact Artifact with artifactType and artifactValue to search
     *                   for
     *
     * @return Number of artifact instances having ArtifactType and
     *         ArtifactValue.
     */
    @Override
    public Long getCountArtifactInstancesByTypeValue(EamArtifact eamArtifact) throws EamDbException {
        Connection conn = connect();

        Long instanceCount = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = artifactTypeToTableName(eamArtifact.getArtifactType());
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT count(*) FROM ");
        sql.append(tableName);
        sql.append(" WHERE value=?");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            preparedStatement.setString(1, eamArtifact.getArtifactValue());
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            instanceCount = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new EamDbException("Error getting count of artifact instances by artifactType and artifactValue.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return instanceCount;
    }

    /**
     * Using the ArtifactType and ArtifactValue from the given eamArtfact,
     * compute the ratio of: (The number of unique case_id/datasource_id tuples
     * where Type/Value is found) divided by (The total number of unique
     * case_id/datasource_id tuples in the database) expressed as a percentage.
     *
     * @param eamArtifact Artifact with artifactType and artifactValue to search
     *                   for
     *
     * @return Int between 0 and 100
     */
    @Override
    public int getCommonalityPercentageForTypeValue(EamArtifact eamArtifact) throws EamDbException {
        Double uniqueTypeValueTuples = getCountUniqueCaseDataSourceTuplesHavingTypeValue(eamArtifact).doubleValue();
        Double uniqueCaseDataSourceTuples = getCountUniqueCaseDataSourceTuples().doubleValue();
        Double commonalityPercentage = uniqueTypeValueTuples / uniqueCaseDataSourceTuples * 100;
        return commonalityPercentage.intValue();
    }

    /**
     * Retrieves number of unique caseDisplayName / dataSource tuples in the
     * database that are associated with the artifactType and artifactValue of
     * the given artifact.
     *
     * @param eamArtifact Artifact with artifactType and artifactValue to search
     *                   for
     *
     * @return Number of unique tuples
     */
    @Override
    public Long getCountUniqueCaseDataSourceTuplesHavingTypeValue(EamArtifact eamArtifact) throws EamDbException {
        Connection conn = connect();

        Long instanceCount = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = artifactTypeToTableName(eamArtifact.getArtifactType());
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT count(*) FROM (SELECT DISTINCT case_id, data_source_id FROM ");
        sql.append(tableName);
        sql.append(" WHERE value=?) AS ");
        sql.append(tableName);
        sql.append("_distinct_case_data_source_tuple");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            preparedStatement.setString(1, eamArtifact.getArtifactValue());
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            instanceCount = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new EamDbException("Error counting unique caseDisplayName/dataSource tuples having artifactType and artifactValue.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return instanceCount;
    }

    /**
     * Retrieves number of unique caseDisplayName/dataSource tuples in the
     * database.
     *
     * @return Number of unique tuples
     */
    @Override
    public Long getCountUniqueCaseDataSourceTuples() throws EamDbException {
        Connection conn = connect();

        Long instanceCount = 0L;
        List<EamArtifact.Type> artifactTypes = getCorrelationArtifactTypes();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT 0 ");

        for (EamArtifact.Type type : artifactTypes) {
            // TODO: when/if custom types are allowed, make this more safe.
            String table_name = type.getName() + "_instances";

            sql.append("+ (SELECT count(*) FROM (SELECT DISTINCT case_id, data_source_id FROM ");
            sql.append(table_name);
            sql.append(") AS ");
            sql.append(table_name);
            sql.append("_distinct_case_data_source_tuple) ");
        }

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            instanceCount = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new EamDbException("Error counting unique caseDisplayName/dataSource tuples.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return instanceCount;
    }

    /**
     * Retrieves number of eamArtifact instances in the database that are
     * associated with the caseDisplayName and dataSource of the given
     * eamArtifact instance.
     *
     * @param eamInstance Instance with caseName and dataSource to search for
     *
     * @param eamInstance Instance with caseDisplayName and dataSource to search
     *                   for
     *
     * @return Number of artifact instances having caseDisplayName and
     *         dataSource
     */
    @Override
    public Long getCountArtifactInstancesByCaseDataSource(EamArtifactInstance eamInstance) throws EamDbException {
        Connection conn = connect();

        Long instanceCount = 0L;
        List<EamArtifact.Type> artifactTypes = getCorrelationArtifactTypes();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        // Figure out sql variables or subqueries
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT 0 ");

        for (EamArtifact.Type type : artifactTypes) {
            String table_name = type.getName() + "_instances";

            sql.append("+ (SELECT count(*) FROM ");
            sql.append(table_name);
            sql.append(" WHERE case_id=(SELECT id FROM cases WHERE case_uid=?) and data_source_id=(SELECT id FROM data_sources WHERE device_id=?))");
        }

        try {
            preparedStatement = conn.prepareStatement(sql.toString());

            for (int i = 0; i < artifactTypes.size(); ++i) {
                preparedStatement.setString(2 * i + 1, eamInstance.getEamCase().getCaseUUID());
                preparedStatement.setString(2 * i + 2, eamInstance.getEamDataSource().getDeviceID());
            }

            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            instanceCount = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new EamDbException("Error counting artifact instances by caseName/dataSource.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return instanceCount;
    }

    /**
     * Adds an eamArtifact to an internal list to be later added to DB. Artifact
     * can have 1 or more Artifact Instances. Insert will be triggered by a
     * threshold or a call to bulkInsertArtifacts().
     *
     * @param eamArtifact The artifact to add
     */
    @Override
    public void prepareBulkArtifact(EamArtifact eamArtifact) throws EamDbException {

        synchronized (bulkArtifacts) {
            bulkArtifacts.get(eamArtifact.getArtifactType().getName()).add(eamArtifact);
            bulkArtifactsCount++;

            if (bulkArtifactsCount >= bulkArtifactsThreshold) {
                bulkInsertArtifacts();
            }
        }
    }

    /**
     * Executes a bulk insert of the eamArtifacts added from the
     * prepareBulkArtifact() method
     */
    @Override
    public void bulkInsertArtifacts() throws EamDbException {
        List<EamArtifact.Type> artifactTypes = getCorrelationArtifactTypes();

        Connection conn = connect();
        PreparedStatement bulkPs = null;

        try {
            synchronized (bulkArtifacts) {
                if (bulkArtifactsCount == 0) {
                    return;
                }

                for (EamArtifact.Type type : artifactTypes) {

                    String tableName = artifactTypeToTableName(type);
                    StringBuilder sql = new StringBuilder();
                    sql.append("INSERT INTO ");
                    sql.append(tableName);
                    sql.append(" (case_id, data_source_id, value, file_path, known_status, comment) ");
                    sql.append("VALUES ((SELECT id FROM cases WHERE case_uid=? LIMIT 1), ");
                    sql.append("(SELECT id FROM data_sources WHERE device_id=? LIMIT 1), ?, ?, ?, ?)");

                    bulkPs = conn.prepareStatement(sql.toString());

                    Collection<EamArtifact> eamArtifacts = bulkArtifacts.get(type.getName());
                    for (EamArtifact eamArtifact : eamArtifacts) {
                        List<EamArtifactInstance> eamInstances = eamArtifact.getInstances();

                        for (EamArtifactInstance eamInstance : eamInstances) {
                            bulkPs.setString(1, eamInstance.getEamCase().getCaseUUID());
                            bulkPs.setString(2, eamInstance.getEamDataSource().getDeviceID());
                            bulkPs.setString(3, eamArtifact.getArtifactValue());
                            bulkPs.setString(4, eamInstance.getFilePath());
                            bulkPs.setString(5, eamInstance.getKnownStatus().name());
                            bulkPs.setString(6, eamInstance.getComment());
                            bulkPs.addBatch();
                        }
                    }

                    bulkPs.executeBatch();
                    bulkArtifacts.get(type.getName()).clear();
                }

                // Reset state
                bulkArtifactsCount = 0;
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting bulk artifacts.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(bulkPs);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Executes a bulk insert of the cases
     */
    @Override
    public void bulkInsertCases(List<EamCase> cases) throws EamDbException {
        Connection conn = connect();

        if (cases.isEmpty()) {
            return;
        }

        int counter = 0;
        PreparedStatement bulkPs = null;
        try {
            String sql = "INSERT INTO cases(case_uid, org_id, case_name, creation_date, case_number, "
                    + "examiner_name, examiner_email, examiner_phone, notes) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            bulkPs = conn.prepareStatement(sql);

            for (EamCase eamCase : cases) {
                bulkPs.setString(1, eamCase.getCaseUUID());
                if (null == eamCase.getOrg()) {
                    bulkPs.setNull(2, Types.INTEGER);
                } else {
                    bulkPs.setInt(2, eamCase.getOrg().getOrgID());
                }
                bulkPs.setString(3, eamCase.getDisplayName());
                bulkPs.setString(4, eamCase.getCreationDate());
                bulkPs.setString(5, eamCase.getCaseNumber());
                bulkPs.setString(6, eamCase.getExaminerName());
                bulkPs.setString(7, eamCase.getExaminerEmail());
                bulkPs.setString(8, eamCase.getExaminerPhone());
                bulkPs.setString(9, eamCase.getNotes());
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
            throw new EamDbException("Error inserting bulk cases.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(bulkPs);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Sets an eamArtifact instance as knownStatus = "Bad". If eamArtifact exists,
     * it is updated. If eamArtifact does not exist nothing happens
     *
     * @param eamArtifact Artifact containing exactly one (1) ArtifactInstance.
     */
    @Override
    public void setArtifactInstanceKnownBad(EamArtifact eamArtifact) throws EamDbException {
        Connection conn = connect();

        if (1 != eamArtifact.getInstances().size()) {
            throw new EamDbException("Error: Artifact must have exactly one (1) Artifact Instance to set known bad."); // NON-NLS
        }

        List<EamArtifactInstance> eamInstances = eamArtifact.getInstances();
        EamArtifactInstance eamInstance = eamInstances.get(0);

        PreparedStatement preparedUpdate = null;
        PreparedStatement preparedQuery = null;
        ResultSet resultSet = null;

        String tableName = artifactTypeToTableName(eamArtifact.getArtifactType());

        StringBuilder sqlQuery = new StringBuilder();
        sqlQuery.append("SELECT id FROM ");
        sqlQuery.append(tableName);
        sqlQuery.append(" WHERE case_id=(SELECT id FROM cases WHERE case_uid=?) ");
        sqlQuery.append("AND data_source_id=(SELECT id FROM data_sources WHERE device_id=?) ");
        sqlQuery.append("AND value=? ");
        sqlQuery.append("AND file_path=?");

        StringBuilder sqlUpdate = new StringBuilder();
        sqlUpdate.append("UPDATE ");
        sqlUpdate.append(tableName);
        sqlUpdate.append(" SET known_status=?, comment=? ");
        sqlUpdate.append("WHERE id=?");

        try {
            preparedQuery = conn.prepareStatement(sqlQuery.toString());
            preparedQuery.setString(1, eamInstance.getEamCase().getCaseUUID());
            preparedQuery.setString(2, eamInstance.getEamDataSource().getDeviceID());
            preparedQuery.setString(3, eamArtifact.getArtifactValue());
            preparedQuery.setString(4, eamInstance.getFilePath());
            resultSet = preparedQuery.executeQuery();
            if (resultSet.next()) {
                int instance_id = resultSet.getInt("id");
                preparedUpdate = conn.prepareStatement(sqlUpdate.toString());

                preparedUpdate.setString(1, EamArtifactInstance.KnownStatus.BAD.name());
                preparedUpdate.setString(2, eamInstance.getComment());
                preparedUpdate.setInt(3, instance_id);

                preparedUpdate.executeUpdate();
            } else {
                eamArtifact.getInstances().get(0).setKnownStatus(EamArtifactInstance.KnownStatus.BAD);
                addArtifact(eamArtifact);
            }

        } catch (SQLException ex) {
            throw new EamDbException("Error getting/setting artifact instance knownStatus=Bad.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedUpdate);
            EamDbUtil.closePreparedStatement(preparedQuery);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Gets list of matching eamArtifact instances that have knownStatus = "Bad".
     *
     * @param eamArtifact Artifact containing Type and Value
     *
     * @return List with 0 or more matching eamArtifact instances.
     */
    @Override
    public List<EamArtifactInstance> getArtifactInstancesKnownBad(EamArtifact eamArtifact) throws EamDbException {
        Connection conn = connect();

        List<EamArtifactInstance> artifactInstances = new ArrayList<>();

        EamArtifactInstance artifactInstance;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = artifactTypeToTableName(eamArtifact.getArtifactType());
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT cases.case_name, cases.case_uid, data_sources.name, device_id, file_path, known_status, comment FROM ");
        sql.append(tableName);
        sql.append(" LEFT JOIN cases ON ");
        sql.append(tableName);
        sql.append(".case_id=cases.id");
        sql.append(" LEFT JOIN data_sources ON ");
        sql.append(tableName);
        sql.append(".data_source_id=data_sources.id");
        sql.append(" WHERE value=? AND known_status=?");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            preparedStatement.setString(1, eamArtifact.getArtifactValue());
            preparedStatement.setString(2, EamArtifactInstance.KnownStatus.BAD.name());
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                artifactInstance = getEnterpriseArtifactManagerArtifactInstanceFromResultSet(resultSet);
                artifactInstances.add(artifactInstance);
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error getting known bad artifact instances.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return artifactInstances;
    }

    /**
     * Count matching eamArtifacts instances that have knownStatus = "Bad".
     *
     * @param eamArtifact Artifact containing Type and Value
     *
     * @return Number of matching eamArtifacts
     */
    @Override
    public Long getCountArtifactInstancesKnownBad(EamArtifact eamArtifact) throws EamDbException {
        Connection conn = connect();

        Long badInstances = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = artifactTypeToTableName(eamArtifact.getArtifactType());
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT count(*) FROM ");
        sql.append(tableName);
        sql.append(" WHERE value=? AND known_status=?");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            preparedStatement.setString(1, eamArtifact.getArtifactValue());
            preparedStatement.setString(2, EamArtifactInstance.KnownStatus.BAD.name());
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            badInstances = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new EamDbException("Error getting count of known bad artifact instances.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return badInstances;
    }

    /**
     * Gets list of distinct case display names, where each case has 1+ Artifact
     * Instance matching eamArtifact with knownStatus = "Bad".
     *
     * @param eamArtifact Artifact containing Type and Value
     *
     * @return List of cases containing this artifact with instances marked as
     *         bad
     *
     * @throws EamDbException
     */
    @Override
    public List<String> getListCasesHavingArtifactInstancesKnownBad(EamArtifact eamArtifact) throws EamDbException {
        Connection conn = connect();

        Collection<String> caseNames = new LinkedHashSet<>();

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = artifactTypeToTableName(eamArtifact.getArtifactType());
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT case_name FROM ");
        sql.append(tableName);
        sql.append(" INNER JOIN cases ON ");
        sql.append(tableName);
        sql.append(".case_id=cases.id WHERE ");
        sql.append(tableName);
        sql.append(".value=? AND ");
        sql.append(tableName);
        sql.append(".known_status=?");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            preparedStatement.setString(1, eamArtifact.getArtifactValue());
            preparedStatement.setString(2, EamArtifactInstance.KnownStatus.BAD.name());
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                caseNames.add(resultSet.getString("case_name"));
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error getting known bad artifact instances.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return caseNames.stream().collect(Collectors.toList());
    }

    /**
     * Is the artifact globally known as bad?
     *
     * @param eamArtifact Artifact containing Type and Value
     *
     * @return Global known status of the artifact
     */
    @Override
    public boolean isArtifactGlobalKnownBad(EamArtifact eamArtifact) throws EamDbException {

        // TEMP: Only support file types
        if (!eamArtifact.getArtifactType().equals(getCorrelationArtifactTypeByName("FILES"))) {
            return false;
        }

        Connection conn = connect();

        Long badInstances = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT count(*) FROM global_files WHERE value=? AND known_status=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, eamArtifact.getArtifactValue());
            preparedStatement.setString(2, EamArtifactInstance.KnownStatus.BAD.name());
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            badInstances = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new EamDbException("Error determining if artifact is globally known bad.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return 0 < badInstances;
    }

    /**
     * Add a new organization
     *
     * @param eamOrg The organization to add
     *
     * @throws EamDbException
     */
    @Override
    public void newOrganization(EamOrganization eamOrg) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "INSERT INTO organizations(org_name, poc_name, poc_email, poc_phone) VALUES (?, ?, ?, ?)";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, eamOrg.getName());
            preparedStatement.setString(2, eamOrg.getPocName());
            preparedStatement.setString(3, eamOrg.getPocEmail());
            preparedStatement.setString(4, eamOrg.getPocPhone());

            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting new organization.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Get all organizations
     *
     * @return A list of all organizations
     *
     * @throws EamDbException
     */
    @Override
    public List<EamOrganization> getOrganizations() throws EamDbException {
        Connection conn = connect();

        List<EamOrganization> orgs = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM organizations";

        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                orgs.add(getEnterpriseArtifactManagerOrganizationFromResultSet(resultSet));
            }
            return orgs;

        } catch (SQLException ex) {
            throw new EamDbException("Error getting all organizations.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Get an organization having the given ID
     *
     * @param orgID The id to look up
     *
     * @return The organization with the given ID
     *
     * @throws EamDbException
     */
    @Override
    public EamOrganization getOrganizationByID(int orgID) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM organizations WHERE id=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, orgID);
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return getEnterpriseArtifactManagerOrganizationFromResultSet(resultSet);

        } catch (SQLException ex) {
            throw new EamDbException("Error getting organization by id.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Add a new Global Set
     *
     * @param eamGlobalSet The global set to add
     *
     * @return The ID of the new global set
     *
     * @throws EamDbException
     */
    @Override
    public int newGlobalSet(EamGlobalSet eamGlobalSet) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement1 = null;
        PreparedStatement preparedStatement2 = null;
        ResultSet resultSet = null;
        String sql1 = "INSERT INTO global_reference_sets(org_id, set_name, version, import_date) VALUES (?, ?, ?, ?)";
        String sql2 = "SELECT id FROM global_reference_sets WHERE org_id=? AND set_name=? AND version=? AND import_date=? LIMIT 1";

        try {
            preparedStatement1 = conn.prepareStatement(sql1);
            preparedStatement1.setInt(1, eamGlobalSet.getOrgID());
            preparedStatement1.setString(2, eamGlobalSet.getSetName());
            preparedStatement1.setString(3, eamGlobalSet.getVersion());
            preparedStatement1.setString(4, eamGlobalSet.getImportDate().toString());

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
            throw new EamDbException("Error inserting new global set.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement1);
            EamDbUtil.closePreparedStatement(preparedStatement2);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Get a global set by ID
     *
     * @param globalSetID The ID to look up
     *
     * @return The global set associated with the ID
     *
     * @throws EamDbException
     */
    @Override
    public EamGlobalSet getGlobalSetByID(int globalSetID) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement1 = null;
        ResultSet resultSet = null;
        String sql1 = "SELECT * FROM global_reference_sets WHERE id=?";

        try {
            preparedStatement1 = conn.prepareStatement(sql1);
            preparedStatement1.setInt(1, globalSetID);
            resultSet = preparedStatement1.executeQuery();
            resultSet.next();
            return getEnterpriseArtifactManagerGlobalSetFromResultSet(resultSet);

        } catch (SQLException ex) {
            throw new EamDbException("Error getting global set by id.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement1);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Add a new global file instance
     *
     * @param eamGlobalFileInstance The global file instance to add
     *
     * @throws EamDbException
     */
    @Override
    public void addGlobalFileInstance(EamGlobalFileInstance eamGlobalFileInstance) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;

        String sql = "INSERT INTO global_files(global_reference_set_id, value, known_status, comment) VALUES (?, ?, ?, ?)";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, eamGlobalFileInstance.getGlobalSetID());
            preparedStatement.setString(2, eamGlobalFileInstance.getMD5Hash());
            preparedStatement.setString(3, eamGlobalFileInstance.getKnownStatus().name());
            preparedStatement.setString(4, eamGlobalFileInstance.getComment());
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting new global file instance into global_files table.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Add a new global file instance to the bulk collection
     * 
     * @param eamGlobalFileInstance The global file instance to add
     * @throws EamDbException 
     */
    @Override
    public void prepareGlobalFileInstance(EamGlobalFileInstance eamGlobalFileInstance) throws EamDbException {
        synchronized (bulkGlobalArtifacts) {
            bulkGlobalArtifacts.get("FILES").add(eamGlobalFileInstance); // NON-NLS
            bulkGlobalArtifactsCount++;

            if (bulkGlobalArtifactsCount >= bulkArtifactsThreshold) {
                bulkInsertGlobalFileInstances();
            }
        }
    }

    /**
     * Insert the bulk collection of Global File Instances
     * 
     * @throws EamDbException 
     */
    @Override
    public void bulkInsertGlobalFileInstances() throws EamDbException {
        List<EamArtifact.Type> artifactTypes = getCorrelationArtifactTypes();

        Connection conn = connect();
        synchronized (bulkGlobalArtifacts) {
            if (bulkGlobalArtifactsCount == 0) {
                return;
            }

            PreparedStatement bulkPs = null;
            try {
                for (EamArtifact.Type type : artifactTypes) {
                    String sql = "INSERT INTO global_files(global_reference_set_id, value, known_status, comment) VALUES (?, ?, ?, ?)";

                    bulkPs = conn.prepareStatement(sql);

                    Collection<EamGlobalFileInstance> eamGlobalFileInstances = bulkGlobalArtifacts.get(type.getName());
                    for (EamGlobalFileInstance eamGlobalFileInstance : eamGlobalFileInstances) {

                        bulkPs.setInt(1, eamGlobalFileInstance.getGlobalSetID());
                        bulkPs.setString(2, eamGlobalFileInstance.getMD5Hash());
                        bulkPs.setString(3, eamGlobalFileInstance.getKnownStatus().name());
                        bulkPs.setString(4, eamGlobalFileInstance.getComment());
                        bulkPs.addBatch();
                    }

                    bulkPs.executeBatch();
                    bulkGlobalArtifacts.get(type.getName()).clear();
                }

                // Reset state
                bulkGlobalArtifactsCount = 0;
            } catch (SQLException ex) {
                throw new EamDbException("Error inserting bulk artifacts.", ex); // NON-NLS
            } finally {
                EamDbUtil.closePreparedStatement(bulkPs);
                EamDbUtil.closeConnection(conn);
            }
        }
    }

    /**
     * Get all global file instances having a given MD5 hash
     * 
     * @param MD5Hash The hash to lookup
     * @return List of all global file instances with a given hash
     * @throws EamDbException 
     */
    @Override
    public List<EamGlobalFileInstance> getGlobalFileInstancesByHash(String MD5Hash) throws EamDbException {
        Connection conn = connect();

        List<EamGlobalFileInstance> globalFileInstances = new ArrayList<>();
        PreparedStatement preparedStatement1 = null;
        ResultSet resultSet = null;
        String sql1 = "SELECT * FROM global_files WHERE value=?";

        try {
            preparedStatement1 = conn.prepareStatement(sql1);
            preparedStatement1.setString(1, MD5Hash);
            resultSet = preparedStatement1.executeQuery();
            while (resultSet.next()) {
                globalFileInstances.add(getEnterpriseArtifactManagerGlobalFileInstanceFromResultSet(resultSet));
            }
            return globalFileInstances;

        } catch (SQLException ex) {
            throw new EamDbException("Error getting global set by id.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement1);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Add a new EamArtifact.Type to the db.
     *
     * @param newType New type to add.
     * @throws EamDbException 
     */
    @Override
    public void newCorrelationArtifactType(EamArtifact.Type newType) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;

        String sql = "INSERT INTO artifact_types(name, supported, enabled) VALUES (?, ?, ?)";

        try {
            preparedStatement = conn.prepareStatement(sql);

            preparedStatement.setString(1, newType.getName());
            preparedStatement.setInt(2, newType.isSupported() ? 1 : 0);
            preparedStatement.setInt(3, newType.isEnabled() ? 1 : 0);

            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting new correlation artifact type.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Get the list of EamArtifact.Type's that will be used to correlate
     * artifacts.
     *
     * @return List of EamArtifact.Type's. If none are defined in the database,
     *         the default list will be returned.
     * @throws EamDbException 
     */
    @Override
    public List<EamArtifact.Type> getCorrelationArtifactTypes() throws EamDbException {
        Connection conn = connect();

        List<EamArtifact.Type> aTypes = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM artifact_types";

        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                aTypes.add(getCorrelationArtifactTypeFromResultSet(resultSet));
            }
            return aTypes;

        } catch (SQLException ex) {
            throw new EamDbException("Error getting all correlation artifact types.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Get the list of enabled EamArtifact.Type's that will be used to correlate
     * artifacts.
     *
     * @return List of enabled EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     * @throws EamDbException 
     */
    @Override
    public List<EamArtifact.Type> getEnabledCorrelationArtifactTypes() throws EamDbException {
        Connection conn = connect();

        List<EamArtifact.Type> aTypes = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM artifact_types WHERE enabled=1";

        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                aTypes.add(getCorrelationArtifactTypeFromResultSet(resultSet));
            }
            return aTypes;

        } catch (SQLException ex) {
            throw new EamDbException("Error getting enabled correlation artifact types.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Get the list of supported EamArtifact.Type's that can be used to
     * correlate artifacts.
     *
     * @return List of supported EamArtifact.Type's. If none are defined in the
     *         database, the default list will be returned.
     * @throws EamDbException 
     */
    @Override
    public List<EamArtifact.Type> getSupportedCorrelationArtifactTypes() throws EamDbException {
        Connection conn = connect();

        List<EamArtifact.Type> aTypes = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM artifact_types WHERE supported=1";

        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                aTypes.add(getCorrelationArtifactTypeFromResultSet(resultSet));
            }
            return aTypes;

        } catch (SQLException ex) {
            throw new EamDbException("Error getting supported correlation artifact types.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Update a EamArtifact.Type.
     *
     * @param aType EamArtifact.Type to update.
     * @throws EamDbException 
     */
    @Override
    public void updateCorrelationArtifactType(EamArtifact.Type aType) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        String sql = "UPDATE artifact_types SET name=?, supported=?, enabled=? WHERE id=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, aType.getName());
            preparedStatement.setInt(2, aType.isSupported() ? 1 : 0);
            preparedStatement.setInt(3, aType.isEnabled() ? 1 : 0);
            preparedStatement.setInt(4, aType.getId());
            preparedStatement.executeUpdate();

        } catch (SQLException ex) {
            throw new EamDbException("Error getting correlation artifact type by name.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }

    }

    /**
     * Get the EamArtifact.Type that has name of typeName.
     *
     * @param typeName Name of Type to get
     *
     * @return EamArtifact.Type or null if it doesn't exist.
     * @throws EamDbException 
     */
    @Override
    public EamArtifact.Type getCorrelationArtifactTypeByName(String typeName) throws EamDbException {
        Connection conn = connect();

        EamArtifact.Type aType;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM artifact_types WHERE name=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, typeName);
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            aType = getCorrelationArtifactTypeFromResultSet(resultSet);
            return aType;

        } catch (SQLException ex) {
            throw new EamDbException("Error getting correlation artifact type by name.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
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
    private EamCase getEnterpriseArtifactManagerCaseFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }

        EamOrganization eamOrg = null;

        resultSet.getInt("org_id");
        if (!resultSet.wasNull()) {

            eamOrg = new EamOrganization(resultSet.getInt("org_id"),
                    resultSet.getString("org_name"),
                    resultSet.getString("poc_name"),
                    resultSet.getString("poc_email"),
                    resultSet.getString("poc_phone"));
        }

        EamCase eamCase = new EamCase(resultSet.getString("case_uid"), resultSet.getString("case_name"));
        eamCase.setID(resultSet.getInt("case_id"));
        eamCase.setOrg(eamOrg);
        eamCase.setCreationDate(resultSet.getString("creation_date"));
        eamCase.setCaseNumber(resultSet.getString("case_number"));
        eamCase.setExaminerName(resultSet.getString("examiner_name"));
        eamCase.setExaminerEmail(resultSet.getString("examiner_email"));
        eamCase.setExaminerPhone(resultSet.getString("examiner_phone"));
        eamCase.setNotes(resultSet.getString("notes"));

        return eamCase;
    }

    private EamDataSource getEnterpriseArtifactManagerDataSourceFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }

        EamDataSource eamDataSource = new EamDataSource(
                resultSet.getInt("id"),
                resultSet.getString("device_id"),
                resultSet.getString("name")
        );

        return eamDataSource;
    }

    private EamArtifact.Type getCorrelationArtifactTypeFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }

        EamArtifact.Type eamArtifactType = new EamArtifact.Type(
                resultSet.getInt("id"),
                resultSet.getString("name"),
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
    private EamArtifactInstance getEnterpriseArtifactManagerArtifactInstanceFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }
        EamArtifactInstance eamArtifactInstance = new EamArtifactInstance(
                new EamCase(resultSet.getString("case_uid"), resultSet.getString("case_name")),
                new EamDataSource(resultSet.getString("device_id"), resultSet.getString("name")),
                resultSet.getString("file_path"),
                resultSet.getString("comment"),
                EamArtifactInstance.KnownStatus.valueOf(resultSet.getString("known_status")),
                EamArtifactInstance.GlobalStatus.LOCAL
        );

        return eamArtifactInstance;
    }

    private EamOrganization getEnterpriseArtifactManagerOrganizationFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }

        EamOrganization eamOrganization = new EamOrganization(
                resultSet.getInt("id"),
                resultSet.getString("org_name"),
                resultSet.getString("poc_name"),
                resultSet.getString("poc_email"),
                resultSet.getString("poc_phone")
        );

        return eamOrganization;
    }

    private EamGlobalSet getEnterpriseArtifactManagerGlobalSetFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }

        EamGlobalSet eamGlobalSet = new EamGlobalSet(
                resultSet.getInt("id"),
                resultSet.getInt("org_id"),
                resultSet.getString("set_name"),
                resultSet.getString("version"),
                LocalDate.parse(resultSet.getString("import_date"))
        );

        return eamGlobalSet;
    }

    private EamGlobalFileInstance getEnterpriseArtifactManagerGlobalFileInstanceFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }

        EamGlobalFileInstance eamGlobalFileInstance = new EamGlobalFileInstance(
                resultSet.getInt("id"),
                resultSet.getInt("global_reference_set_id"),
                resultSet.getString("value"),
                EamArtifactInstance.KnownStatus.valueOf(resultSet.getString("known_status")),
                resultSet.getString("comment")
        );

        return eamGlobalFileInstance;
    }

}
