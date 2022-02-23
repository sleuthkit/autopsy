/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import com.google.common.collect.Iterables;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance.Type;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.InstanceTableCallback;
import org.sleuthkit.autopsy.commonpropertiessearch.AbstractCommonAttributeInstance.NODE_TYPE;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * Used to process and return CorrelationCase values from the EamDB for
 * CommonFilesSearch.
 */
final class InterCaseSearchResultsProcessor {

    private static final Logger LOGGER = Logger.getLogger(CommonAttributePanel.class.getName());
    private static final String INTER_CASE_WHERE_CLAUSE = "case_id=%s AND (known_status !=%s OR known_status IS NULL)"; //NON-NLS
    /**
     * The CorrelationAttributeInstance.Type this Processor will query on
     */
    private final Type correlationType;

    /**
     * Used in the InterCaseCommonAttributeSearchers to find common attribute
     * instances and generate nodes at the UI level.
     *
     * @param dataSources the cases to filter and correlate on
     * @param theType     the type of CR data to search
     */
    InterCaseSearchResultsProcessor(CorrelationAttributeInstance.Type theType) {
        this.correlationType = theType;
    }

    /**
     * Finds a single CorrelationAttribute given an id.
     *
     * @param attrbuteId Row of CorrelationAttribute to retrieve from the EamDb
     *
     * @return CorrelationAttribute object representation of retrieved match
     */
    CorrelationAttributeInstance findSingleCorrelationAttribute(int attrbuteId) {
        try {

            InterCaseCommonAttributeRowCallback instancetableCallback = new InterCaseCommonAttributeRowCallback();
            CentralRepository dbManager = CentralRepository.getInstance();
            dbManager.processInstanceTableWhere(correlationType, String.format("id = %s", attrbuteId), instancetableCallback);

            return instancetableCallback.getCorrelationAttribute();

        } catch (CentralRepoException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing InstanceTable row.", ex);
        }

        return null;
    }

    /**
     * Get the portion of the select query which will get md5 values for files
     * from the current case which are potentially being correlated on.
     *
     * @param mimeTypesToFilterOn the set of mime types to filter on
     *
     * @return the portion of a query which follows the SELECT keyword for
     *         finding MD5s which we are correlating on
     *
     * @throws CentralRepoException
     */
    private String getFileQuery(Set<String> mimeTypesToFilterOn) throws CentralRepoException {
        String query;
        query = "md5 AS value FROM tsk_files WHERE known!=" + TskData.FileKnown.KNOWN.getFileKnownValue() + " AND md5 IS NOT NULL"; //NON-NLS
        if (!mimeTypesToFilterOn.isEmpty()) {
            query = query + " AND mime_type IS NOT NULL AND mime_type IN ('" + String.join("', '", mimeTypesToFilterOn) + "')";  //NON-NLS
        }
        return query;
    }

    /**
     * Given the current case, fins all intercase common files from the EamDb
     * and builds maps of case name to maps of data source name to
     * CommonAttributeValueList.
     *
     * @param currentCase         The current TSK Case.
     * @param mimeTypesToFilterOn the set of mime types to filter on
     *
     * @return map of Case name to Maps of Datasources and their
     *         CommonAttributeValueLists
     */
    Map<String, Map<String, CommonAttributeValueList>> findInterCaseValuesByCase(Case currentCase, Set<String> mimeTypesToFilterOn) {
        try {

            CentralRepository dbManager = CentralRepository.getInstance();
            int caseId = dbManager.getCase(currentCase).getID();
            InterCaseByCaseCallback instancetableCallback = new InterCaseByCaseCallback(caseId);
            if (correlationType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                currentCase.getSleuthkitCase().getCaseDbAccessManager().select(getFileQuery(mimeTypesToFilterOn), instancetableCallback);
            } else {
                dbManager.processInstanceTableWhere(correlationType, String.format(INTER_CASE_WHERE_CLAUSE, caseId,
                        TskData.FileKnown.KNOWN.getFileKnownValue()),
                        instancetableCallback);
            }
            return instancetableCallback.getInstanceCollatedCommonFiles();

        } catch (CentralRepoException | TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }
        return new HashMap<>();
    }

    /**
     * Given the current case, fins all intercase common files from the EamDb
     * and builds maps of obj id to value and case.
     *
     * @param currentCase         The current TSK Case.
     * @param mimeTypesToFilterOn the set of mime types to filter on
     *
     * @return map of number of instances to CommonAttributeValueLists
     */
    Map<Integer, CommonAttributeValueList> findInterCaseValuesByCount(Case currentCase, Set<String> mimeTypesToFilterOn) {
        try {

            CentralRepository dbManager = CentralRepository.getInstance();

            int caseId = dbManager.getCase(currentCase).getID();
            InterCaseByCountCallback instancetableCallback = new InterCaseByCountCallback(caseId);
            if (correlationType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                currentCase.getSleuthkitCase().getCaseDbAccessManager().select(getFileQuery(mimeTypesToFilterOn), instancetableCallback);
            } else {
                dbManager.processInstanceTableWhere(correlationType, String.format(INTER_CASE_WHERE_CLAUSE, caseId,
                        TskData.FileKnown.KNOWN.getFileKnownValue()),
                        instancetableCallback);
            }
            return instancetableCallback.getInstanceCollatedCommonFiles();

        } catch (CentralRepoException | TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }
        return new TreeMap<>();
    }

    /**
     * Given the current case, and a specific case of interest, finds common
     * files which exist between cases from the EamDb. Builds maps of obj id to
     * value and case.
     *
     * @param currentCase         The current TSK Case.
     * @param mimeTypesToFilterOn the set of mime types to filter on
     * @param singleCase          The case of interest. Matches must exist in
     *                            this case.
     *
     * @return map of number of instances to CommonAttributeValueLists
     */
    Map<Integer, CommonAttributeValueList> findSingleInterCaseValuesByCount(Case currentCase, Set<String> mimeTypesToFilterOn, CorrelationCase singleCase) {
        try {
            CentralRepository dbManager = CentralRepository.getInstance();
            int caseId = dbManager.getCase(currentCase).getID();
            int targetCaseId = singleCase.getID();
            InterCaseByCountCallback instancetableCallback = new InterCaseByCountCallback(caseId, targetCaseId);
            if (correlationType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                currentCase.getSleuthkitCase().getCaseDbAccessManager().select(getFileQuery(mimeTypesToFilterOn), instancetableCallback);
            } else {
                dbManager.processInstanceTableWhere(correlationType, String.format(INTER_CASE_WHERE_CLAUSE, caseId,
                        TskData.FileKnown.KNOWN.getFileKnownValue()),
                        instancetableCallback);
            }
            return instancetableCallback.getInstanceCollatedCommonFiles();
        } catch (CentralRepoException | TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }
        return new TreeMap<>();
    }

    /**
     * Given the current case, and a specific case of interest, finds common
     * files which exist between cases from the EamDb. Builds map of case name
     * to maps of data source name to CommonAttributeValueList.
     *
     * @param currentCase         The current TSK Case.
     * @param mimeTypesToFilterOn the set of mime types to filter on
     * @param singleCase          The case of interest. Matches must exist in
     *                            this case.
     *
     * @return map of Case name to Maps of Datasources and their
     *         CommonAttributeValueLists
     */
    Map<String, Map<String, CommonAttributeValueList>> findSingleInterCaseValuesByCase(Case currentCase, Set<String> mimeTypesToFilterOn, CorrelationCase singleCase) {
        try {

            CentralRepository dbManager = CentralRepository.getInstance();
            int caseId = dbManager.getCase(currentCase).getID();
            int targetCaseId = singleCase.getID();
            InterCaseByCaseCallback instancetableCallback = new InterCaseByCaseCallback(caseId, targetCaseId);
            if (correlationType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                currentCase.getSleuthkitCase().getCaseDbAccessManager().select(getFileQuery(mimeTypesToFilterOn), instancetableCallback);
            } else {
                dbManager.processInstanceTableWhere(correlationType, String.format(INTER_CASE_WHERE_CLAUSE, caseId,
                        TskData.FileKnown.KNOWN.getFileKnownValue()),
                        instancetableCallback);
            }
            return instancetableCallback.getInstanceCollatedCommonFiles();
        } catch (CentralRepoException | TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }
        return new HashMap<>();
    }

    /**
     * Callback to use with findInterCaseValuesByCount which generates a list of
     * values for common property search
     */
    private class InterCaseByCountCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback, InstanceTableCallback {

        private final TreeMap<Integer, CommonAttributeValueList> instanceCollatedCommonFiles = new TreeMap<>();
        private final int caseID;
        private final int targetCase;

        private InterCaseByCountCallback(int caseId) {
            this(caseId, 0);
        }

        private InterCaseByCountCallback(int caseId, int targetCase) {
            this.caseID = caseId;
            this.targetCase = targetCase;
        }

        @Override
        public void process(ResultSet resultSet) {
            try {
                Set<String> values = new HashSet<>();
                List<Integer> targetCases = new ArrayList<>();
                if (targetCase != 0) {
                    targetCases.add(caseID);
                    targetCases.add(targetCase);
                }
                while (resultSet.next()) {
                    String corValue = InstanceTableCallback.getValue(resultSet);
                    if (corValue == null || HashUtility.isNoDataMd5(corValue)) {
                        continue;
                    }
                    values.add(corValue);
                }
                for (String corValue : values) {
                    List<CorrelationAttributeInstance> instances;
                    if (targetCases.isEmpty()) {
                        instances = CentralRepository.getInstance().getArtifactInstancesByTypeValues(correlationType, Arrays.asList(corValue));
                    } else {
                        instances = CentralRepository.getInstance().getArtifactInstancesByTypeValuesAndCases(correlationType, Arrays.asList(corValue), targetCases);
                    }
                    int size = instances.stream().map(instance -> instance.getCorrelationDataSource().getID()).collect(Collectors.toSet()).size();
                    if (size > 1) {
                        CommonAttributeValue commonAttributeValue = new CommonAttributeValue(corValue);
                        boolean anotherCase = false;
                        for (CorrelationAttributeInstance instance : instances) {
                            CentralRepoCommonAttributeInstance searchResult = new CentralRepoCommonAttributeInstance(instance.getID(), correlationType, NODE_TYPE.COUNT_NODE);
                            searchResult.setCurrentAttributeInst(instance);
                            commonAttributeValue.addInstance(searchResult);
                            anotherCase = anotherCase || instance.getCorrelationCase().getID() != caseID;
                        }
                        if (anotherCase) {
                            if (instanceCollatedCommonFiles.containsKey(size)) {
                                instanceCollatedCommonFiles.get(size).addMetadataToList(commonAttributeValue);
                            } else {
                                CommonAttributeValueList value = new CommonAttributeValueList();
                                value.addMetadataToList(commonAttributeValue);
                                instanceCollatedCommonFiles.put(size, value);
                            }
                        }
                    }
                }
            } catch (SQLException | CentralRepoException | CorrelationAttributeNormalizationException ex) {
                LOGGER.log(Level.WARNING, "Error getting artifact instances from database.", ex); // NON-NLS
            }
        }

        Map<Integer, CommonAttributeValueList> getInstanceCollatedCommonFiles() {
            return Collections.unmodifiableSortedMap(instanceCollatedCommonFiles);
        }
    }

    /**
     * Callback to use with findInterCaseValuesByCase which generates a map of
     * maps of values for common property search
     */
    private class InterCaseByCaseCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback, InstanceTableCallback {

        private static final int VALUE_BATCH_SIZE = 500;
        private final Map<String, Map<String, CommonAttributeValueList>> caseCollatedDataSourceCollections = new HashMap<>();
        private final int caseID;
        private final int targetCase;

        private InterCaseByCaseCallback(int caseId) {
            this(caseId, 0);
        }

        private InterCaseByCaseCallback(int caseId, int targetCase) {
            this.caseID = caseId;
            this.targetCase = targetCase;
        }

        @Override
        public void process(ResultSet resultSet) {
            try {
                List<Integer> targetCases = new ArrayList<>();
                if (targetCase != 0) {
                    targetCases.add(caseID);
                    targetCases.add(targetCase);
                }
                Set<String> values = new HashSet<>();
                while (resultSet.next()) {
                    String corValue = InstanceTableCallback.getValue(resultSet);
                    if (corValue == null || HashUtility.isNoDataMd5(corValue)) {
                        continue;
                    }
                    values.add(corValue);
                }
                for (List<String> valuesChunk : Iterables.partition(values, VALUE_BATCH_SIZE)) {
                    List<CorrelationAttributeInstance> instances;
                    if (targetCases.isEmpty()) {
                        instances = CentralRepository.getInstance().getArtifactInstancesByTypeValues(correlationType, valuesChunk);
                    } else {
                        instances = CentralRepository.getInstance().getArtifactInstancesByTypeValuesAndCases(correlationType, valuesChunk, targetCases);
                    }
                    if (instances.size() > 1) {
                        for (CorrelationAttributeInstance instance : instances) {
                            CorrelationCase correlationCase = instance.getCorrelationCase();
                            String caseName = correlationCase.getDisplayName();
                            CorrelationDataSource correlationDatasource = instance.getCorrelationDataSource();
                            //label datasource with it's id for uniqueness done in same manner as ImageGallery does in the DataSourceCell class
                            String dataSourceNameKey = correlationDatasource.getName() + " (Id: " + correlationDatasource.getDataSourceObjectID() + ")";
                            if (!caseCollatedDataSourceCollections.containsKey(caseName)) {
                                caseCollatedDataSourceCollections.put(caseName, new HashMap<>());
                            }
                            Map<String, CommonAttributeValueList> dataSourceToFile = caseCollatedDataSourceCollections.get(caseName);
                            if (!dataSourceToFile.containsKey(dataSourceNameKey)) {
                                dataSourceToFile.put(dataSourceNameKey, new CommonAttributeValueList());
                            }
                            CommonAttributeValueList valueList = dataSourceToFile.get(dataSourceNameKey);
                            CentralRepoCommonAttributeInstance searchResult = new CentralRepoCommonAttributeInstance(instance.getID(), correlationType, NODE_TYPE.CASE_NODE);
                            searchResult.setCurrentAttributeInst(instance);
                            CommonAttributeValue commonAttributeValue = new CommonAttributeValue(instance.getCorrelationValue());
                            commonAttributeValue.addInstance(searchResult);
                            valueList.addMetadataToList(commonAttributeValue);
                            dataSourceToFile.put(dataSourceNameKey, valueList);
                            caseCollatedDataSourceCollections.put(caseName, dataSourceToFile);
                        }
                    }
                }
            } catch (CentralRepoException | SQLException | CorrelationAttributeNormalizationException ex) {
                LOGGER.log(Level.WARNING, "Error getting artifact instances from database.", ex); // NON-NLS
            }
        }

        Map<String, Map<String, CommonAttributeValueList>> getInstanceCollatedCommonFiles() {
            return Collections.unmodifiableMap(caseCollatedDataSourceCollections);
        }
    }

    /**
     * Callback to use with findSingleCorrelationAttribute which retrieves a
     * single CorrelationAttribute from the EamDb.
     */
    private class InterCaseCommonAttributeRowCallback implements InstanceTableCallback {

        CorrelationAttributeInstance correlationAttributeInstance = null;

        @Override
        public void process(ResultSet resultSet) {
            try {
                CentralRepository dbManager = CentralRepository.getInstance();

                while (resultSet.next()) {
                    CorrelationCase correlationCase = dbManager.getCaseById(InstanceTableCallback.getCaseId(resultSet));
                    CorrelationDataSource dataSource = dbManager.getDataSourceById(correlationCase, InstanceTableCallback.getDataSourceId(resultSet));
                    try {
                        long fileObjectId = InstanceTableCallback.getFileObjectId(resultSet);
                        if (fileObjectId != 0) {
                            correlationAttributeInstance = dbManager.getCorrelationAttributeInstance(correlationType,
                                    correlationCase, dataSource, fileObjectId);
                        } else {
                            correlationAttributeInstance = dbManager.getCorrelationAttributeInstance(correlationType,
                                    correlationCase,
                                    dataSource,
                                    InstanceTableCallback.getValue(resultSet),
                                    InstanceTableCallback.getFilePath(resultSet));
                        }
                    } catch (CorrelationAttributeNormalizationException ex) {
                        LOGGER.log(Level.INFO, "Unable to get CorrelationAttributeInstance.", ex); // NON-NLS
                    }

                }
            } catch (SQLException | CentralRepoException ex) {
                LOGGER.log(Level.WARNING, "Error getting single correlation artifact instance from database.", ex); // NON-NLS
            }
        }

        CorrelationAttributeInstance getCorrelationAttribute() {
            return correlationAttributeInstance;
        }
    }
}
