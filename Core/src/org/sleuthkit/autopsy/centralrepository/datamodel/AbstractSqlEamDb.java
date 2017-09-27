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
import java.util.Set;
import org.sleuthkit.autopsy.casemodule.Case;

import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskData;

/**
 *
 * SQLite manager implementation
 *
 */
public abstract class AbstractSqlEamDb implements EamDb {

    private final static Logger LOGGER = Logger.getLogger(AbstractSqlEamDb.class.getName());

    protected final List<CorrelationAttribute.Type> DEFAULT_CORRELATION_TYPES;

    private int bulkArtifactsCount;
    protected int bulkArtifactsThreshold;
    private final Map<String, Collection<CorrelationAttribute>> bulkArtifacts;
    private final List<String> badTags;

    /**
     * Connect to the DB and initialize it.
     *
     * @throws UnknownHostException, EamDbException
     */
    protected AbstractSqlEamDb() throws EamDbException{
        badTags = new ArrayList<>();
        bulkArtifactsCount = 0;
        bulkArtifacts = new HashMap<>();

        DEFAULT_CORRELATION_TYPES = CorrelationAttribute.getDefaultCorrelationTypes();
        DEFAULT_CORRELATION_TYPES.forEach((type) -> {
            bulkArtifacts.put(type.getDbTableName(), new ArrayList<>());
        });
    }

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
    public void newCase(CorrelationCase eamCase) throws EamDbException {
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
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting new case.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }
    }

     /**
     * Creates new Case in the database from the given case
     * 
     * @param case The case to add
     */
    @Override    
    public CorrelationCase newCase(Case autopsyCase) throws EamDbException{
        if(autopsyCase == null){
            throw new EamDbException("Case is null");
        }
        
        CorrelationCase curCeCase = new CorrelationCase(
                -1,
                autopsyCase.getName(), // unique case ID
                EamOrganization.getDefault(),
                autopsyCase.getDisplayName(),
                autopsyCase.getCreatedDate(),
                autopsyCase.getNumber(),
                autopsyCase.getExaminer(),
                null,
                null,
                null);        
        newCase(curCeCase);
        return curCeCase;
    }

    /**
     * Updates an existing Case in the database
     *
     * @param eamCase The case to update
     */
    @Override
    public void updateCase(CorrelationCase eamCase) throws EamDbException {
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
    public CorrelationCase getCaseByUUID(String caseUUID) throws EamDbException {
        // @@@ We should have a cache here...
        
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
    public List<CorrelationCase> getCases() throws EamDbException {
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
    public void newDataSource(CorrelationDataSource eamDataSource) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;

        String sql = "INSERT INTO data_sources(device_id, case_id, name) VALUES (?, ?, ?)";

        try {
            preparedStatement = conn.prepareStatement(sql);

            preparedStatement.setString(1, eamDataSource.getDeviceID());
            preparedStatement.setInt(2,eamDataSource.getCaseID());
            preparedStatement.setString(3, eamDataSource.getName());

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
//    @Override
//    public void updateDataSource(CorrelationDataSource eamDataSource) throws EamDbException {
//        Connection conn = connect();
// BC: This needs to be updated because device_id is not unique.  Query needs to also use case_id
//        PreparedStatement preparedStatement = null;
//        String sql = "UPDATE data_sources SET name=? WHERE device_id=? AND case_id=?";
//
//        try {
//            preparedStatement = conn.prepareStatement(sql);
//
//            preparedStatement.setString(1, eamDataSource.getName());
//            preparedStatement.setString(2, eamDataSource.getDeviceID());
//            preparedStatement.setInt(3, eamDataSource.getCaseID());
//
//            preparedStatement.executeUpdate();
//        } catch (SQLException ex) {
//            throw new EamDbException("Error updating case.", ex); // NON-NLS
//        } finally {
//            EamDbUtil.closePreparedStatement(preparedStatement);
//            EamDbUtil.closeConnection(conn);
//        }
//    }

    /**
     * Retrieves Data Source details based on data source device ID
     *
     * @param dataSourceDeviceId the data source device ID number
     *
     * @return The data source
     */
    @Override
    public CorrelationDataSource getDataSourceDetails(String dataSourceDeviceId, CorrelationCase correlationCase) throws EamDbException {
        Connection conn = connect();

        CorrelationDataSource eamDataSourceResult = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String sql = "SELECT * FROM data_sources WHERE device_id=? AND case_id=?"; // NON-NLS

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, dataSourceDeviceId);
            preparedStatement.setInt(2, correlationCase.getID());
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                eamDataSourceResult = getEamDataSourceFromResultSet(resultSet);
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
    public List<CorrelationDataSource> getDataSources() throws EamDbException {
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
            throw new EamDbException("Error getting all data sources.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return dataSources;
    }

    /**
     * Inserts new Artifact(s) into the database. Should add associated Case and
     * Data Source first.
     *
     * @param eamArtifact The artifact to add
     */
    @Override
    public void addArtifact(CorrelationAttribute eamArtifact) throws EamDbException {
        Connection conn = connect();

        List<CorrelationAttributeInstance> eamInstances = eamArtifact.getInstances();
        PreparedStatement preparedStatement = null;

        
        // @@@ We should cache the case and data source IDs in memory
        String tableName = EamDbUtil.correlationTypeToInstanceTableName(eamArtifact.getCorrelationType());
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ");
        sql.append(tableName);
        sql.append("(case_id, data_source_id, value, file_path, known_status, comment) ");
        sql.append("VALUES ((SELECT id FROM cases WHERE case_uid=? LIMIT 1), ");
        sql.append("(SELECT id FROM data_sources WHERE device_id=? AND case_id=? LIMIT 1), ?, ?, ?, ?)");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            for (CorrelationAttributeInstance eamInstance : eamInstances) {
                if(! eamArtifact.getCorrelationValue().isEmpty()){
                    preparedStatement.setString(1, eamInstance.getCorrelationCase().getCaseUUID());
                    preparedStatement.setString(2, eamInstance.getCorrelationDataSource().getDeviceID());
                    preparedStatement.setInt(3, eamInstance.getCorrelationDataSource().getCaseID());
                    preparedStatement.setString(4, eamArtifact.getCorrelationValue());
                    preparedStatement.setString(5, eamInstance.getFilePath());
                    preparedStatement.setByte(6, eamInstance.getKnownStatus().getFileKnownValue());
                    if ("".equals(eamInstance.getComment())) {
                        preparedStatement.setNull(7, Types.INTEGER);
                    } else {
                        preparedStatement.setString(7, eamInstance.getComment());
                    }

                    preparedStatement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting new artifact into artifacts table.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Retrieves eamArtifact instances from the database that are associated
     * with the eamArtifactType and eamArtifactValue of the given eamArtifact.
     *
     * @param eamArtifact The type/value to look up (artifact with 0 instances)
     *
     * @return List of artifact instances for a given type/value
     */
    @Override
    public List<CorrelationAttributeInstance> getArtifactInstancesByTypeValue(CorrelationAttribute.Type aType, String value) throws EamDbException {
        Connection conn = connect();

        List<CorrelationAttributeInstance> artifactInstances = new ArrayList<>();

        CorrelationAttributeInstance artifactInstance;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = EamDbUtil.correlationTypeToInstanceTableName(aType);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT cases.case_name, cases.case_uid, data_sources.name, device_id, file_path, known_status, comment, data_sources.case_id FROM ");
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
            preparedStatement.setString(1, value);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                artifactInstance = getEamArtifactInstanceFromResultSet(resultSet);
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
    @Override
    public List<CorrelationAttributeInstance> getArtifactInstancesByPath(CorrelationAttribute.Type aType, String filePath) throws EamDbException {
        Connection conn = connect();

        List<CorrelationAttributeInstance> artifactInstances = new ArrayList<>();

        CorrelationAttributeInstance artifactInstance;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = EamDbUtil.correlationTypeToInstanceTableName(aType);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT cases.case_name, cases.case_uid, data_sources.name, device_id, file_path, known_status, comment, data_sources.case_id FROM ");
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
                artifactInstance = getEamArtifactInstanceFromResultSet(resultSet);
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
     *                    for
     *
     * @return Number of artifact instances having ArtifactType and
     *         ArtifactValue.
     */
    @Override
    public Long getCountArtifactInstancesByTypeValue(CorrelationAttribute.Type aType, String value) throws EamDbException {
        Connection conn = connect();

        Long instanceCount = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = EamDbUtil.correlationTypeToInstanceTableName(aType);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT count(*) FROM ");
        sql.append(tableName);
        sql.append(" WHERE value=?");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            preparedStatement.setString(1, value);
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


    @Override
    public int getFrequencyPercentage(CorrelationAttribute corAttr) throws EamDbException {
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
     * @param eamArtifact Artifact with artifactType and artifactValue to search
     *                    for
     *
     * @return Number of unique tuples
     */
    @Override
    public Long getCountUniqueCaseDataSourceTuplesHavingTypeValue(CorrelationAttribute.Type aType, String value) throws EamDbException {
        Connection conn = connect();

        Long instanceCount = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = EamDbUtil.correlationTypeToInstanceTableName(aType);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT count(*) FROM (SELECT DISTINCT case_id, data_source_id FROM ");
        sql.append(tableName);
        sql.append(" WHERE value=?) AS ");
        sql.append(tableName);
        sql.append("_distinct_case_data_source_tuple");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            preparedStatement.setString(1, value);
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


    @Override
    public Long getCountUniqueDataSources() throws EamDbException {
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
            throw new EamDbException("Error counting data sources.", ex); // NON-NLS
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
     * @param caseUUID     Case ID to search for
     * @param dataSourceID Data source ID to search for
     *
     * @return Number of artifact instances having caseDisplayName and
     *         dataSource
     */
    @Override
    public Long getCountArtifactInstancesByCaseDataSource(String caseUUID, String dataSourceID) throws EamDbException {
        Connection conn = connect();

        Long instanceCount = 0L;
        List<CorrelationAttribute.Type> artifactTypes = getDefinedCorrelationTypes();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        // Figure out sql variables or subqueries
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT 0 ");

        for (CorrelationAttribute.Type type : artifactTypes) {
            String table_name = EamDbUtil.correlationTypeToInstanceTableName(type);

            sql.append("+ (SELECT count(*) FROM ");
            sql.append(table_name);
            sql.append(" WHERE case_id=(SELECT id FROM cases WHERE case_uid=?) and data_source_id=(SELECT id FROM data_sources WHERE device_id=?))");
        }

        try {
            preparedStatement = conn.prepareStatement(sql.toString());

            for (int i = 0; i < artifactTypes.size(); ++i) {
                preparedStatement.setString(2 * i + 1, caseUUID);
                preparedStatement.setString(2 * i + 2, dataSourceID);
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
    public void prepareBulkArtifact(CorrelationAttribute eamArtifact) throws EamDbException {

        synchronized (bulkArtifacts) {
            bulkArtifacts.get(eamArtifact.getCorrelationType().getDbTableName()).add(eamArtifact);
            bulkArtifactsCount++;

            if (bulkArtifactsCount >= bulkArtifactsThreshold) {
                bulkInsertArtifacts();
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
     * prepareBulkArtifact() method
     */
    @Override
    public void bulkInsertArtifacts() throws EamDbException {
        List<CorrelationAttribute.Type> artifactTypes = getDefinedCorrelationTypes();

        Connection conn = connect();
        PreparedStatement bulkPs = null;

        try {
            synchronized (bulkArtifacts) {
                if (bulkArtifactsCount == 0) {
                    return;
                }

                for (CorrelationAttribute.Type type : artifactTypes) {

                    String tableName = EamDbUtil.correlationTypeToInstanceTableName(type);
                    StringBuilder sql = new StringBuilder();
                    sql.append("INSERT INTO ");
                    sql.append(tableName);
                    sql.append(" (case_id, data_source_id, value, file_path, known_status, comment) ");
                    sql.append("VALUES ((SELECT id FROM cases WHERE case_uid=? LIMIT 1), ");
                    sql.append("(SELECT id FROM data_sources WHERE device_id=? AND case_id=? LIMIT 1), ?, ?, ?, ?) ");
                    sql.append(getConflictClause());

                    bulkPs = conn.prepareStatement(sql.toString());

                    Collection<CorrelationAttribute> eamArtifacts = bulkArtifacts.get(type.getDbTableName());
                    for (CorrelationAttribute eamArtifact : eamArtifacts) {
                        List<CorrelationAttributeInstance> eamInstances = eamArtifact.getInstances();

                        for (CorrelationAttributeInstance eamInstance : eamInstances) {                            
                            if(! eamArtifact.getCorrelationValue().isEmpty()){
                                bulkPs.setString(1, eamInstance.getCorrelationCase().getCaseUUID());
                                bulkPs.setString(2, eamInstance.getCorrelationDataSource().getDeviceID());
                                bulkPs.setInt(3, eamInstance.getCorrelationDataSource().getCaseID());
                                bulkPs.setString(4, eamArtifact.getCorrelationValue());
                                bulkPs.setString(5, eamInstance.getFilePath());
                                bulkPs.setByte(6, eamInstance.getKnownStatus().getFileKnownValue());
                                if ("".equals(eamInstance.getComment())) {
                                    bulkPs.setNull(7, Types.INTEGER);
                                } else {
                                    bulkPs.setString(7, eamInstance.getComment());
                                }
                                bulkPs.addBatch();
                            }
                        }
                    }

                    bulkPs.executeBatch();
                    bulkArtifacts.get(type.getDbTableName()).clear();
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
    public void bulkInsertCases(List<CorrelationCase> cases) throws EamDbException {
        Connection conn = connect();

        if (cases.isEmpty()) {
            return;
        }

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
            throw new EamDbException("Error inserting bulk cases.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(bulkPs);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Sets an eamArtifact instance to the given knownStatus. If eamArtifact
     * exists, it is updated. If eamArtifact does not exist it is added
     * with the given status.
     *
     * @param eamArtifact Artifact containing exactly one (1) ArtifactInstance.
     * @param FileKnown The status to change the artifact to
     */
    @Override
    public void setArtifactInstanceKnownStatus(CorrelationAttribute eamArtifact, TskData.FileKnown knownStatus) throws EamDbException {
        Connection conn = connect();

        if (1 != eamArtifact.getInstances().size()) {
            throw new EamDbException("Error: Artifact must have exactly one (1) Artifact Instance to set as notable."); // NON-NLS
        }

        List<CorrelationAttributeInstance> eamInstances = eamArtifact.getInstances();
        CorrelationAttributeInstance eamInstance = eamInstances.get(0);

        PreparedStatement preparedUpdate = null;
        PreparedStatement preparedQuery = null;
        ResultSet resultSet = null;

        String tableName = EamDbUtil.correlationTypeToInstanceTableName(eamArtifact.getCorrelationType());

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
            preparedQuery.setString(1, eamInstance.getCorrelationCase().getCaseUUID());
            preparedQuery.setString(2, eamInstance.getCorrelationDataSource().getDeviceID());
            preparedQuery.setString(3, eamArtifact.getCorrelationValue());
            preparedQuery.setString(4, eamInstance.getFilePath());
            resultSet = preparedQuery.executeQuery();
            if (resultSet.next()) {
                int instance_id = resultSet.getInt("id");
                preparedUpdate = conn.prepareStatement(sqlUpdate.toString());

                preparedUpdate.setByte(1, knownStatus.getFileKnownValue());
                // NOTE: if the user tags the same instance as BAD multiple times,
                // the comment from the most recent tagging is the one that will
                // prevail in the DB.
                if ("".equals(eamInstance.getComment())) {
                    preparedUpdate.setNull(2, Types.INTEGER);
                } else {
                    preparedUpdate.setString(2, eamInstance.getComment());
                }
                preparedUpdate.setInt(3, instance_id);

                preparedUpdate.executeUpdate();
            } else {
                // In this case, the user is tagging something that isn't in the database,
                // which means the case and/or datasource may also not be in the database.
                // We could improve effiency by keeping a list of all datasources and cases
                // in the database, but we don't expect the user to be tagging large numbers
                // of items (that didn't have the CE ingest module run on them) at once.
                CorrelationCase correlationCase = getCaseByUUID(eamInstance.getCorrelationCase().getCaseUUID());
                if(null == correlationCase){
                    newCase(eamInstance.getCorrelationCase());
                    correlationCase = getCaseByUUID(eamInstance.getCorrelationCase().getCaseUUID());
                }
                
                if (null == getDataSourceDetails(eamInstance.getCorrelationDataSource().getDeviceID(), correlationCase)) {
                    newDataSource(eamInstance.getCorrelationDataSource());
                }
                
                eamArtifact.getInstances().get(0).setKnownStatus(knownStatus);
                addArtifact(eamArtifact);
            }

        } catch (SQLException ex) {
            throw new EamDbException("Error getting/setting artifact instance knownStatus=" + knownStatus.getName(), ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedUpdate);
            EamDbUtil.closePreparedStatement(preparedQuery);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Gets list of matching eamArtifact instances that have knownStatus =
     * "Bad".
     *
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     * 
     * @return List with 0 or more matching eamArtifact instances.
     */
    @Override
    public List<CorrelationAttributeInstance> getArtifactInstancesKnownBad(CorrelationAttribute.Type aType, String value) throws EamDbException {
        Connection conn = connect();

        List<CorrelationAttributeInstance> artifactInstances = new ArrayList<>();

        CorrelationAttributeInstance artifactInstance;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = EamDbUtil.correlationTypeToInstanceTableName(aType);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT cases.case_name, cases.case_uid, data_sources.name, device_id, file_path, known_status, comment, data_sources.case_id FROM ");
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
            preparedStatement.setString(1, value);
            preparedStatement.setByte(2, TskData.FileKnown.BAD.getFileKnownValue());
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                artifactInstance = getEamArtifactInstanceFromResultSet(resultSet);
                artifactInstances.add(artifactInstance);
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error getting notable artifact instances.", ex); // NON-NLS
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
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return Number of matching eamArtifacts
     */
    @Override
    public Long getCountArtifactInstancesKnownBad(CorrelationAttribute.Type aType, String value) throws EamDbException {
        Connection conn = connect();

        Long badInstances = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = EamDbUtil.correlationTypeToInstanceTableName(aType);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT count(*) FROM ");
        sql.append(tableName);
        sql.append(" WHERE value=? AND known_status=?");

        try {
            preparedStatement = conn.prepareStatement(sql.toString());
            preparedStatement.setString(1, value);
            preparedStatement.setByte(2, TskData.FileKnown.BAD.getFileKnownValue());
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            badInstances = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new EamDbException("Error getting count of notable artifact instances.", ex); // NON-NLS
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
     * @param aType EamArtifact.Type to search for
     * @param value Value to search for
     *
     * @return List of cases containing this artifact with instances marked as
     *         bad
     *
     * @throws EamDbException
     */
    @Override
    public List<String> getListCasesHavingArtifactInstancesKnownBad(CorrelationAttribute.Type aType, String value) throws EamDbException {
        Connection conn = connect();

        Collection<String> caseNames = new LinkedHashSet<>();

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        String tableName = EamDbUtil.correlationTypeToInstanceTableName(aType);
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
            preparedStatement.setString(1, value);
            preparedStatement.setByte(2, TskData.FileKnown.BAD.getFileKnownValue());
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                caseNames.add(resultSet.getString("case_name"));
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error getting notable artifact instances.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }

        return caseNames.stream().collect(Collectors.toList());
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
    public boolean isArtifactlKnownBadByReference(CorrelationAttribute.Type aType, String value) throws EamDbException {

        // TEMP: Only support file correlation type
        if (aType.getId() != CorrelationAttribute.FILES_TYPE_ID) {
            return false;
        }

        Connection conn = connect();

        Long badInstances = 0L;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT count(*) FROM %s WHERE value=? AND known_status=?";

        try {
            preparedStatement = conn.prepareStatement(String.format(sql, EamDbUtil.correlationTypeToReferenceTableName(aType)));
            preparedStatement.setString(1, value);
            preparedStatement.setByte(2, TskData.FileKnown.BAD.getFileKnownValue());
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            badInstances = resultSet.getLong(1);
        } catch (SQLException ex) {
            throw new EamDbException("Error determining if artifact is notable by reference.", ex); // NON-NLS
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
                orgs.add(getEamOrganizationFromResultSet(resultSet));
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
            return getEamOrganizationFromResultSet(resultSet);

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
    public int newReferencelSet(EamGlobalSet eamGlobalSet) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement1 = null;
        PreparedStatement preparedStatement2 = null;
        ResultSet resultSet = null;
        String sql1 = "INSERT INTO reference_sets(org_id, set_name, version, import_date) VALUES (?, ?, ?, ?)";
        String sql2 = "SELECT id FROM reference_sets WHERE org_id=? AND set_name=? AND version=? AND import_date=? LIMIT 1";

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
     * Get a reference set by ID
     *
     * @param referenceSetID The ID to look up
     *
     * @return The global set associated with the ID
     *
     * @throws EamDbException
     */
    @Override
    public EamGlobalSet getReferenceSetByID(int referenceSetID) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement1 = null;
        ResultSet resultSet = null;
        String sql1 = "SELECT * FROM reference_sets WHERE id=?";

        try {
            preparedStatement1 = conn.prepareStatement(sql1);
            preparedStatement1.setInt(1, referenceSetID);
            resultSet = preparedStatement1.executeQuery();
            resultSet.next();
            return getEamGlobalSetFromResultSet(resultSet);

        } catch (SQLException ex) {
            throw new EamDbException("Error getting reference set by id.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement1);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Add a new reference instance
     *
     * @param eamGlobalFileInstance The reference instance to add
     * @param correlationType       Correlation Type that this Reference
     *                              Instance is
     *
     * @throws EamDbException
     */
    @Override
    public void addReferenceInstance(EamGlobalFileInstance eamGlobalFileInstance, CorrelationAttribute.Type correlationType) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;

        String sql = "INSERT INTO %s(reference_set_id, value, known_status, comment) VALUES (?, ?, ?, ?)";

        try {
            preparedStatement = conn.prepareStatement(String.format(sql, EamDbUtil.correlationTypeToReferenceTableName(correlationType)));
            preparedStatement.setInt(1, eamGlobalFileInstance.getGlobalSetID());
            preparedStatement.setString(2, eamGlobalFileInstance.getMD5Hash());
            preparedStatement.setByte(3, eamGlobalFileInstance.getKnownStatus().getFileKnownValue());
            preparedStatement.setString(4, eamGlobalFileInstance.getComment());
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting new reference instance into reference_ table.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }
    }

    /**
     * Insert the bulk collection of Reference Type Instances
     *
     * @throws EamDbException
     */
    @Override
    public void bulkInsertReferenceTypeEntries(Set<EamGlobalFileInstance> globalInstances, CorrelationAttribute.Type contentType) throws EamDbException {
        Connection conn = connect();
        
        PreparedStatement bulkPs = null;
        try {
            conn.setAutoCommit(false);
            
            // FUTURE: have a separate global_files table for each Type.
            String sql = "INSERT INTO %s(reference_set_id, value, known_status, comment) VALUES (?, ?, ?, ?) "
                    + getConflictClause();

            bulkPs = conn.prepareStatement(String.format(sql, EamDbUtil.correlationTypeToReferenceTableName(contentType)));

            for (EamGlobalFileInstance globalInstance : globalInstances) {
                bulkPs.setInt(1, globalInstance.getGlobalSetID());
                bulkPs.setString(2, globalInstance.getMD5Hash());
                bulkPs.setByte(3, globalInstance.getKnownStatus().getFileKnownValue());
                bulkPs.setString(4, globalInstance.getComment());
                bulkPs.addBatch();
            }

            bulkPs.executeBatch();
            conn.commit();
        } catch (SQLException ex) {
            try{
                conn.rollback();
            } catch (SQLException ex2){
                // We're alredy in an error state
            }
            throw new EamDbException("Error inserting bulk artifacts.", ex); // NON-NLS           
        } finally {
            EamDbUtil.closePreparedStatement(bulkPs);
            EamDbUtil.closeConnection(conn);
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
     * @throws EamDbException
     */
    @Override
    public List<EamGlobalFileInstance> getReferenceInstancesByTypeValue(CorrelationAttribute.Type aType, String aValue) throws EamDbException {
        Connection conn = connect();

        List<EamGlobalFileInstance> globalFileInstances = new ArrayList<>();
        PreparedStatement preparedStatement1 = null;
        ResultSet resultSet = null;
        String sql1 = "SELECT * FROM %s WHERE value=?";

        try {
            preparedStatement1 = conn.prepareStatement(String.format(sql1, EamDbUtil.correlationTypeToReferenceTableName(aType)));
            preparedStatement1.setString(1, aValue);
            resultSet = preparedStatement1.executeQuery();
            while (resultSet.next()) {
                globalFileInstances.add(getEamGlobalFileInstanceFromResultSet(resultSet));
            }
            return globalFileInstances;

        } catch (SQLException ex) {
            throw new EamDbException("Error getting reference instances by type and value.", ex); // NON-NLS
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
     *
     * @return ID of this new Correlation Type
     *
     * @throws EamDbException
     */
    @Override
    public int newCorrelationType(CorrelationAttribute.Type newType) throws EamDbException {
        Connection conn = connect();

        PreparedStatement preparedStatement = null;
        PreparedStatement preparedStatementQuery = null;
        ResultSet resultSet = null;
        int typeId = 0;
        String insertSql;
        String querySql;
        // if we have a known ID, use it, if not (is -1) let the db assign it.
        if (-1 == newType.getId()) {
            insertSql = "INSERT INTO correlation_types(display_name, db_table_name, supported, enabled) VALUES (?, ?, ?, ?)";
        } else {
            insertSql = "INSERT INTO correlation_types(id, display_name, db_table_name, supported, enabled) VALUES (?, ?, ?, ?, ?)";
        }
        querySql = "SELECT id FROM correlation_types WHERE display_name=? AND db_table_name=?";

        try {
            preparedStatement = conn.prepareStatement(insertSql);

            if (-1 == newType.getId()) {
                preparedStatement.setString(1, newType.getDisplayName());
                preparedStatement.setString(2, newType.getDbTableName());
                preparedStatement.setInt(3, newType.isSupported() ? 1 : 0);
                preparedStatement.setInt(4, newType.isEnabled() ? 1 : 0);
            } else {
                preparedStatement.setInt(1, newType.getId());
                preparedStatement.setString(2, newType.getDisplayName());
                preparedStatement.setString(3, newType.getDbTableName());
                preparedStatement.setInt(4, newType.isSupported() ? 1 : 0);
                preparedStatement.setInt(5, newType.isEnabled() ? 1 : 0);
            }

            preparedStatement.executeUpdate();

            preparedStatementQuery = conn.prepareStatement(querySql);
            preparedStatementQuery.setString(1, newType.getDisplayName());
            preparedStatementQuery.setString(2, newType.getDbTableName());

            resultSet = preparedStatementQuery.executeQuery();
            if (resultSet.next()) {
                CorrelationAttribute.Type correlationType = getCorrelationTypeFromResultSet(resultSet);
                typeId = correlationType.getId();
            }
        } catch (SQLException ex) {
            throw new EamDbException("Error inserting new correlation type.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closePreparedStatement(preparedStatementQuery);
            EamDbUtil.closeResultSet(resultSet);
            EamDbUtil.closeConnection(conn);
        }
        return typeId;
    }


    @Override
    public List<CorrelationAttribute.Type> getDefinedCorrelationTypes() throws EamDbException {
        Connection conn = connect();

        List<CorrelationAttribute.Type> aTypes = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM correlation_types";

        try {
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                aTypes.add(getCorrelationTypeFromResultSet(resultSet));
            }
            return aTypes;

        } catch (SQLException ex) {
            throw new EamDbException("Error getting all correlation types.", ex); // NON-NLS
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
     *
     * @throws EamDbException
     */
    @Override
    public List<CorrelationAttribute.Type> getEnabledCorrelationTypes() throws EamDbException {
        Connection conn = connect();

        List<CorrelationAttribute.Type> aTypes = new ArrayList<>();
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
            throw new EamDbException("Error getting enabled correlation types.", ex); // NON-NLS
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
     *
     * @throws EamDbException
     */
    @Override
    public List<CorrelationAttribute.Type> getSupportedCorrelationTypes() throws EamDbException {
        Connection conn = connect();

        List<CorrelationAttribute.Type> aTypes = new ArrayList<>();
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
            throw new EamDbException("Error getting supported correlation types.", ex); // NON-NLS
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
     *
     * @throws EamDbException
     */
    @Override
    public void updateCorrelationType(CorrelationAttribute.Type aType) throws EamDbException {
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

        } catch (SQLException ex) {
            throw new EamDbException("Error updating correlation type.", ex); // NON-NLS
        } finally {
            EamDbUtil.closePreparedStatement(preparedStatement);
            EamDbUtil.closeConnection(conn);
        }

    }

    /**
     * Get the EamArtifact.Type that has the given Type.Id.
     *
     * @param typeId Type.Id of Correlation Type to get
     *
     * @return EamArtifact.Type or null if it doesn't exist.
     *
     * @throws EamDbException
     */
    @Override
    public CorrelationAttribute.Type getCorrelationTypeById(int typeId) throws EamDbException {
        Connection conn = connect();

        CorrelationAttribute.Type aType;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        String sql = "SELECT * FROM correlation_types WHERE id=?";

        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, typeId);
            resultSet = preparedStatement.executeQuery();
            resultSet.next();
            aType = getCorrelationTypeFromResultSet(resultSet);
            return aType;

        } catch (SQLException ex) {
            throw new EamDbException("Error getting correlation type by id.", ex); // NON-NLS
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
    private CorrelationCase getEamCaseFromResultSet(ResultSet resultSet) throws SQLException {
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

        CorrelationCase eamCase = new CorrelationCase(resultSet.getInt("case_id"), resultSet.getString("case_uid"), resultSet.getString("case_name"));
        eamCase.setOrg(eamOrg);
        eamCase.setCreationDate(resultSet.getString("creation_date"));
        eamCase.setCaseNumber(resultSet.getString("case_number"));
        eamCase.setExaminerName(resultSet.getString("examiner_name"));
        eamCase.setExaminerEmail(resultSet.getString("examiner_email"));
        eamCase.setExaminerPhone(resultSet.getString("examiner_phone"));
        eamCase.setNotes(resultSet.getString("notes"));

        return eamCase;
    }

    private CorrelationDataSource getEamDataSourceFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }

        CorrelationDataSource eamDataSource = new CorrelationDataSource(
                resultSet.getInt("id"),
                resultSet.getString("device_id"),
                resultSet.getString("name"),
                resultSet.getInt("case_id")
        );

        return eamDataSource;
    }

    private CorrelationAttribute.Type getCorrelationTypeFromResultSet(ResultSet resultSet) throws EamDbException, SQLException {
        if (null == resultSet) {
            return null;
        }

        CorrelationAttribute.Type eamArtifactType = new CorrelationAttribute.Type(
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
    private CorrelationAttributeInstance getEamArtifactInstanceFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }
        CorrelationAttributeInstance eamArtifactInstance = new CorrelationAttributeInstance(
                new CorrelationCase(resultSet.getString("case_uid"), resultSet.getString("case_name")),
                new CorrelationDataSource(-1, resultSet.getString("device_id"), resultSet.getString("name"), resultSet.getInt("case_id")),
                resultSet.getString("file_path"),
                resultSet.getString("comment"),
                TskData.FileKnown.valueOf(resultSet.getByte("known_status")),
                CorrelationAttributeInstance.GlobalStatus.LOCAL
        );

        return eamArtifactInstance;
    }

    private EamOrganization getEamOrganizationFromResultSet(ResultSet resultSet) throws SQLException {
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

    private EamGlobalSet getEamGlobalSetFromResultSet(ResultSet resultSet) throws SQLException {
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

    private EamGlobalFileInstance getEamGlobalFileInstanceFromResultSet(ResultSet resultSet) throws SQLException {
        if (null == resultSet) {
            return null;
        }

        EamGlobalFileInstance eamGlobalFileInstance = new EamGlobalFileInstance(
                resultSet.getInt("id"),
                resultSet.getInt("reference_set_id"),
                resultSet.getString("value"),
                TskData.FileKnown.valueOf(resultSet.getByte("known_status")),
                resultSet.getString("comment")
        );

        return eamGlobalFileInstance;
    }

}
