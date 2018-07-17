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
package org.sleuthkit.autopsy.commonfilesearch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.InstanceTableCallback;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Used to process and return CorrelationCase md5s from the EamDB for
 * CommonFilesSearch.
 */
final class InterCaseSearchResultsProcessor {

    private static final Logger LOGGER = Logger.getLogger(CommonAttributePanel.class.getName());

    private final Map<Integer, String> intercaseCommonValuesMap = new HashMap<>();
    private final Map<Integer, Integer> intercaseCommonCasesMap = new HashMap<>();

    CorrelationAttribute processCorrelationCaseSingleAttribute(int attrbuteId) {
        try {
            EamDbAttributeInstanceRowCallback instancetableCallback = new EamDbAttributeInstanceRowCallback();
            EamDb DbManager = EamDb.getInstance();
            CorrelationAttribute.Type fileType = DbManager.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
            DbManager.processInstanceTableRow(fileType, attrbuteId, instancetableCallback);

            return instancetableCallback.getCorrelationAttribute();

        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing InstanceTable row.", ex);
        }

        return null;
    }

    void processCorrelationCaseAttributeValues(Case currentCase) {

        try {
            EamDbAttributeInstancesCallback instancetableCallback = new EamDbAttributeInstancesCallback();
            EamDb DbManager = EamDb.getInstance();
            CorrelationAttribute.Type fileType = DbManager.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
            DbManager.processCaseInstancesTable(fileType, DbManager.getCase(currentCase), instancetableCallback);

            intercaseCommonValuesMap.putAll(instancetableCallback.getCorrelationIdValueMap());
            intercaseCommonCasesMap.putAll(instancetableCallback.getCorrelationIdToCaseMap());
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }

    }

    void processSingleCaseCorrelationCaseAttributeValues(Case currentCase, CorrelationCase singleCase) {

        try {
            EamDbAttributeInstancesCallback instancetableCallback = new EamDbAttributeInstancesCallback();
            EamDb DbManager = EamDb.getInstance();
            CorrelationAttribute.Type fileType = DbManager.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
            DbManager.processSingleCaseInstancesTable(fileType, DbManager.getCase(currentCase), singleCase, instancetableCallback);

            intercaseCommonValuesMap.putAll(instancetableCallback.getCorrelationIdValueMap());
            intercaseCommonCasesMap.putAll(instancetableCallback.getCorrelationIdToCaseMap());
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }
    }

    Map<Integer, String> getIntercaseCommonValuesMap() {
        return Collections.unmodifiableMap(intercaseCommonValuesMap);
    }

    Map<Integer, Integer> getIntercaseCommonCasesMap() {
        return Collections.unmodifiableMap(intercaseCommonCasesMap);
    }

    /**
     * Callback to use with processCaseInstancesTable which generates a list of
     * md5s for common files search
     */
    private class EamDbAttributeInstancesCallback implements InstanceTableCallback {

        private final Map<Integer, String> correlationIdToValueMap = new HashMap<>();
        private final Map<Integer, Integer> correlationIdToDatasourceMap = new HashMap<>();

        @Override
        public void process(ResultSet resultSet) {
            try {
                while (resultSet.next()) {
                    int resultId = InstanceTableCallback.getId(resultSet);
                    correlationIdToValueMap.put(resultId, InstanceTableCallback.getValue(resultSet));
                    correlationIdToDatasourceMap.put(resultId, InstanceTableCallback.getCaseId(resultSet));
                }
            } catch (SQLException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        Map<Integer, String> getCorrelationIdValueMap() {
            return Collections.unmodifiableMap(correlationIdToValueMap);
        }

        Map<Integer, Integer> getCorrelationIdToCaseMap() {
            return Collections.unmodifiableMap(correlationIdToDatasourceMap);
        }

    }

    /**
     * Callback to use with processCaseInstancesTable which generates a list of
     * md5s for common files search
     */
    private class EamDbAttributeInstanceRowCallback implements InstanceTableCallback {

        CorrelationAttribute correlationAttribute = null;

        @Override
        public void process(ResultSet resultSet) {
            try {
                EamDb DbManager = EamDb.getInstance();
                CorrelationAttribute.Type fileType = DbManager.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);

                while (resultSet.next()) {
                    CorrelationCase correlationCase = DbManager.getCaseById(InstanceTableCallback.getCaseId(resultSet));
                    CorrelationDataSource dataSource = DbManager.getDataSourceById(correlationCase, InstanceTableCallback.getDataSourceId(resultSet));
                    correlationAttribute = DbManager.getCorrelationAttribute(fileType,
                            correlationCase,
                            dataSource,
                            InstanceTableCallback.getValue(resultSet),
                            InstanceTableCallback.getFilePath(resultSet));

                }
            } catch (SQLException | EamDbException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        CorrelationAttribute getCorrelationAttribute() {
            return correlationAttribute;
        }
    }
}
