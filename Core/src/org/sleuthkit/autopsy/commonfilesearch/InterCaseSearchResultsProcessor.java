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
import org.sleuthkit.datamodel.TskData;

/**
 * Used to process and return CorrelationCase md5s from the EamDB for
 * CommonFilesSearch.
 */
final class InterCaseSearchResultsProcessor {

    private static final Logger LOGGER = Logger.getLogger(CommonAttributePanel.class.getName());

    // maps row ID to value
    private final Map<Integer, String> intercaseCommonValuesMap = new HashMap<>();
    // maps row ID to case ID
    private final Map<Integer, Integer> intercaseCommonCasesMap = new HashMap<>();
    private final String interCaseWhereClause = "value IN (SELECT value FROM file_instances"
                    + " WHERE value IN (SELECT value FROM  file_instances"
                    + " WHERE case_id=%s AND (known_status !=%s OR known_status IS NULL) GROUP BY value)"
                    + " GROUP BY value HAVING COUNT(DISTINCT case_id) > 1) ORDER BY value";
    private final String singleInterCaseWhereClause = "value IN (SELECT value FROM file_instances "
                + "WHERE value IN (SELECT value FROM  file_instances "
                + "WHERE case_id=%s AND (known_status !=%s OR known_status IS NULL) GROUP BY value) "
                + "AND (case_id=%s OR case_id=%s) GROUP BY value HAVING COUNT(DISTINCT case_id) > 1) ORDER BY value";
    

    /**
     * Finds a single CorrelationAttribute given an id.
     *
     * @param attrbuteId Row of CorrelationAttribute to retrieve from the EamDb
     * @return CorrelationAttribute object representation of retrieved match
     */
    CorrelationAttribute findSingleCorrelationAttribute(int attrbuteId) {
        try {
            InterCaseCommonAttributeRowCallback instancetableCallback = new InterCaseCommonAttributeRowCallback();
            EamDb DbManager = EamDb.getInstance();
            CorrelationAttribute.Type fileType = DbManager.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
            DbManager.processInstanceTableWhere(fileType, String.format("id = %s", attrbuteId), instancetableCallback);

            return instancetableCallback.getCorrelationAttribute();

        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing InstanceTable row.", ex);
        }

        return null;
    }

    /**
     * Given the current case, fins all intercase common files from the EamDb
     * and builds maps of obj id to md5 and case.
     *
     * @param currentCase The current TSK Case.
     */
    void findInterCaseCommonAttributeValues(Case currentCase) {
        try {
            InterCaseCommonAttributesCallback instancetableCallback = new InterCaseCommonAttributesCallback();
            EamDb DbManager = EamDb.getInstance();
            CorrelationAttribute.Type fileType = DbManager.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
            int caseId = DbManager.getCase(currentCase).getID();

            DbManager.processInstanceTableWhere(fileType, String.format(interCaseWhereClause, caseId,
                    TskData.FileKnown.KNOWN.getFileKnownValue()),
                    instancetableCallback);

        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }

    }

    /**
     * Given the current case, and a specific case of interest, finds common
     * files which exist between cases from the EamDb. Builds maps of obj id to
     * md5 and case.
     *
     * @param currentCase The current TSK Case.
     * @param singleCase The case of interest. Matches must exist in this case.
     */
    void findSingleInterCaseCommonAttributeValues(Case currentCase, CorrelationCase singleCase) {
        try {
            InterCaseCommonAttributesCallback instancetableCallback = new InterCaseCommonAttributesCallback();
            EamDb DbManager = EamDb.getInstance();
            CorrelationAttribute.Type fileType = DbManager.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
            int caseId = DbManager.getCase(currentCase).getID();
            int targetCaseId = singleCase.getID();
            DbManager.processInstanceTableWhere(fileType,  String.format(singleInterCaseWhereClause, caseId,
                    TskData.FileKnown.KNOWN.getFileKnownValue(), caseId, targetCaseId), instancetableCallback);
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
     * Callback to use with findInterCaseCommonAttributeValues which generates a
     * list of md5s for common files search
     */
    private class InterCaseCommonAttributesCallback implements InstanceTableCallback {

        @Override
        public void process(ResultSet resultSet) {
            try {
                while (resultSet.next()) {
                    int resultId = InstanceTableCallback.getId(resultSet);
                    intercaseCommonValuesMap.put(resultId, InstanceTableCallback.getValue(resultSet));
                    intercaseCommonCasesMap.put(resultId, InstanceTableCallback.getCaseId(resultSet));
                }
            } catch (SQLException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

    }

    /**
     * Callback to use with findSingleCorrelationAttribute which retrieves a
     * single CorrelationAttribute from the EamDb.
     */
    private class InterCaseCommonAttributeRowCallback implements InstanceTableCallback {

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
