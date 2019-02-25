/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance.Type;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.InstanceTableCallback;
import org.sleuthkit.autopsy.commonpropertiessearch.AbstractCommonAttributeInstance.NODE_TYPE;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.HashUtility;

/**
 * Used to process and return CorrelationCase values from the EamDB for
 * CommonFilesSearch.
 */
final class InterCaseSearchResultsProcessor {

    /**
     * The CorrelationAttributeInstance.Type this Processor will query on
     */
    private final Type correlationType;

    private static final Logger LOGGER = Logger.getLogger(CommonAttributePanel.class.getName());

    /**
     * The initial CorrelationAttributeInstance ids lookup query.
     */
    private final String interCaseWhereClause;

    /**
     * The single CorrelationAttributeInstance object retrieval query
     */
    private final String singleInterCaseWhereClause;

    /**
     * Used in the InterCaseCommonAttributeSearchers to find common attribute
     * instances and generate nodes at the UI level.
     *
     * @param dataSources the cases to filter and correlate on
     * @param theType     the type of CR data to search
     */
    InterCaseSearchResultsProcessor(CorrelationAttributeInstance.Type theType) {
        this.correlationType = theType;
        interCaseWhereClause = getInterCaseWhereClause();
        singleInterCaseWhereClause = getSingleInterCaseWhereClause();
    }

    private String getInterCaseWhereClause() {
        return "case_id=%s AND (known_status !=%s OR known_status IS NULL)";
    }

    private String getSingleInterCaseWhereClause() {
        return "case_id=%s AND (known_status !=%s OR known_status IS NULL)";
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
            EamDb dbManager = EamDb.getInstance();
            dbManager.processInstanceTableWhere(correlationType, String.format("id = %s", attrbuteId), instancetableCallback);

            return instancetableCallback.getCorrelationAttribute();

        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing InstanceTable row.", ex);
        }

        return null;
    }

    /**
     * Given the current case, fins all intercase common files from the EamDb
     * and builds maps of case name to maps of data source name to
     * CommonAttributeValueList.
     *
     * @param currentCase The current TSK Case.
     *
     * @return map of Case name to Maps of Datasources and their
     *         CommonAttributeValueLists
     */
    Map<String, Map<String, CommonAttributeValueList>> findInterCaseValuesByCase(Case currentCase) {
        try {

            EamDb dbManager = EamDb.getInstance();

            int caseId = dbManager.getCase(currentCase).getID();
            InterCaseByCaseCallback instancetableCallback = new InterCaseByCaseCallback(caseId);
            dbManager.processInstanceTableWhere(correlationType, String.format(interCaseWhereClause, caseId,
                    TskData.FileKnown.KNOWN.getFileKnownValue()),
                    instancetableCallback);

            return instancetableCallback.getInstanceCollatedCommonFiles();

        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }
        return new HashMap<>();
    }

    /**
     * Given the current case, fins all intercase common files from the EamDb
     * and builds maps of obj id to md5 and case.
     *
     * @param currentCase The current TSK Case.
     */
    Map<Integer, CommonAttributeValueList> findInterCaseValuesByCount(Case currentCase) {
        try {

            EamDb dbManager = EamDb.getInstance();

            int caseId = dbManager.getCase(currentCase).getID();
            InterCaseByCountCallback instancetableCallback = new InterCaseByCountCallback(caseId);
            dbManager.processInstanceTableWhere(correlationType, String.format(interCaseWhereClause, caseId,
                    TskData.FileKnown.KNOWN.getFileKnownValue()),
                    instancetableCallback);

            return instancetableCallback.getInstanceCollatedCommonFiles();

        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }
        return new HashMap<>();
    }

    /**
     * Given the current case, and a specific case of interest, finds common
     * files which exist between cases from the EamDb. Builds maps of obj id to
     * md5 and case.
     *
     * @param currentCase The current TSK Case.
     * @param singleCase  The case of interest. Matches must exist in this case.
     */
    Map<Integer, CommonAttributeValueList> findSingleInterCaseValuesByCount(Case currentCase, CorrelationCase singleCase) {
        try {
            EamDb dbManager = EamDb.getInstance();
            int caseId = dbManager.getCase(currentCase).getID();
            int targetCaseId = singleCase.getID();
            InterCaseByCountCallback instancetableCallback = new InterCaseByCountCallback(caseId, targetCaseId);
            dbManager.processInstanceTableWhere(correlationType, String.format(singleInterCaseWhereClause, caseId,
                    TskData.FileKnown.KNOWN.getFileKnownValue()), instancetableCallback);
            return instancetableCallback.getInstanceCollatedCommonFiles();
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }
        return new HashMap<>();
    }

    /**
     * Given the current case, and a specific case of interest, finds common
     * files which exist between cases from the EamDb. Builds map of case name
     * to maps of data source name to CommonAttributeValueList.
     *
     * @param currentCase The current TSK Case.
     *
     * @return map of Case name to Maps of Datasources and their
     *         CommonAttributeValueLists
     *
     * @param currentCase The current TSK Case.
     * @param singleCase  The case of interest. Matches must exist in this case.
     */
    Map<String, Map<String, CommonAttributeValueList>> findSingleInterCaseValuesByCase(Case currentCase, CorrelationCase singleCase) {
        try {

            EamDb dbManager = EamDb.getInstance();
            int caseId = dbManager.getCase(currentCase).getID();
            int targetCaseId = singleCase.getID();
            InterCaseByCaseCallback instancetableCallback = new InterCaseByCaseCallback(caseId, targetCaseId);
            dbManager.processInstanceTableWhere(correlationType, String.format(singleInterCaseWhereClause, caseId,
                    TskData.FileKnown.KNOWN.getFileKnownValue()), instancetableCallback);

            return instancetableCallback.getInstanceCollatedCommonFiles();
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }
        return new HashMap<>();
    }

    /**
     * Callback to use with findInterCaseValuesByCount which generates a list of
     * md5s for common files search
     */
    private class InterCaseByCountCallback implements InstanceTableCallback {

        final Map<Integer, CommonAttributeValueList> instanceCollatedCommonFiles = new HashMap<>();
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
                while (resultSet.next()) {
                    String corValue = InstanceTableCallback.getValue(resultSet);
                    if (corValue == null || HashUtility.isNoDataMd5(corValue)) {
                        continue;
                    }
                    values.add(corValue);
                }
                for (String corValue : values) {
                    List<CorrelationAttributeInstance> instances = EamDb.getInstance().getArtifactInstancesByTypeValue(correlationType, corValue);
                    int size = instances.size();
                    if (size > 1) {
                        CommonAttributeValue commonAttributeValue = new CommonAttributeValue(corValue);
                        boolean anotherCase = false;
                        for (CorrelationAttributeInstance instance : instances) {
                            CentralRepoCommonAttributeInstance searchResult = new CentralRepoCommonAttributeInstance(instance.getID(), correlationType, NODE_TYPE.COUNT_NODE);
                            searchResult.setCurrentAttributeInst(instance);
                            commonAttributeValue.addInstance(searchResult);
                            if (!anotherCase && ((targetCase == 0 && instance.getCorrelationCase().getID() != caseID) || (targetCase == instance.getCorrelationCase().getID()))) {
                                anotherCase = true;
                            }
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
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "Error getting artifact instances from database.", ex); // NON-NLS
            } catch (EamDbException ex) {
                Exceptions.printStackTrace(ex);
            } catch (CorrelationAttributeNormalizationException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        Map<Integer, CommonAttributeValueList> getInstanceCollatedCommonFiles() {
            return Collections.unmodifiableMap(instanceCollatedCommonFiles);
        }
    }

    /**
     * Callback to use with findInterCaseValuesByCount which generates a list of
     * md5s for common files search
     */
    private class InterCaseByCaseCallback implements InstanceTableCallback {

        final Map<String, Map<String, CommonAttributeValueList>> caseCollatedDataSourceCollections = new HashMap<>();
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
                Set<String> values = new HashSet<>();
                while (resultSet.next()) {
                    String corValue = InstanceTableCallback.getValue(resultSet);
                    if (corValue == null || HashUtility.isNoDataMd5(corValue)) {
                        continue;
                    }
                    values.add(corValue);
                }
                for (String corValue : values) {
                    List<CorrelationAttributeInstance> instances = EamDb.getInstance().getArtifactInstancesByTypeValue(correlationType, corValue);
                    if (instances.size() > 1) {
                        boolean addToResults = targetCase == 0;
                        if (!addToResults) {
                            for (CorrelationAttributeInstance instance : instances) {
                                if (instance.getCorrelationCase().getID() == targetCase) {
                                    System.out.println("Target case found in results");
                                    addToResults = true;
                                    break;
                                }
                            }
                        }
                        else {
                            System.out.println("Target case is not set adding all results");
                        }
                        if (addToResults) {
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
                                CommonAttributeValue commonAttributeValue = new CommonAttributeValue(corValue);
                                commonAttributeValue.addInstance(searchResult);
                                valueList.addMetadataToList(commonAttributeValue);
                                dataSourceToFile.put(dataSourceNameKey, valueList);
                                caseCollatedDataSourceCollections.put(caseName, dataSourceToFile);
                            }
                        }
                    }
                }
            } catch (EamDbException | SQLException ex) {
                LOGGER.log(Level.WARNING, "Error getting artifact instances from database.", ex); // NON-NLS
            } catch (CorrelationAttributeNormalizationException ex) {
                Exceptions.printStackTrace(ex);
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
                EamDb dbManager = EamDb.getInstance();

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
            } catch (SQLException | EamDbException ex) {
                LOGGER.log(Level.WARNING, "Error getting single correlation artifact instance from database.", ex); // NON-NLS
            }
        }

        CorrelationAttributeInstance getCorrelationAttribute() {
            return correlationAttributeInstance;
        }
    }
}
