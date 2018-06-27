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
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.InstanceTableCallback;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Used to process and return CorrelationCase md5s from the EamDB for CommonFilesSearch.
 */
class EamDbAttributeInstancesAlgorithm {

    private static final Logger logger = Logger.getLogger(CommonFilesPanel.class.getName());
    
    private final Map<Integer, String>  intercaseCommonValuesMap = new HashMap<>();
    private final Map<Integer, String>  intercaseCommonDatasourcesMap = new HashMap<>();
    
    void processCorrelationCaseAttributeValues(Case currentCase) {
        
        try {
            EamDbAttributeInstancesCallback instancetableCallback = new EamDbAttributeInstancesCallback();
            EamDb DbManager = EamDb.getInstance();
            CorrelationAttribute.Type fileType = EamDb.getInstance().getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
            DbManager.processCaseInstancesTable(fileType, DbManager.getCase(currentCase), instancetableCallback);

            intercaseCommonValuesMap.putAll(instancetableCallback.getCorrelationIdValueMap());
            intercaseCommonDatasourcesMap.putAll(instancetableCallback.getCorrelationIdDatasourceMap());
        } catch (EamDbException ex) {
            logger.log(Level.SEVERE, "Error accessing EamDb processing CaseInstancesTable.", ex);
        }
 
    }
    
    Map<Integer, String> getIntercaseCommonValuesMap() {
        return Collections.unmodifiableMap(intercaseCommonValuesMap);
    }
    
       Map<Integer, String> getIntercaseCommonDatasourcesMap() {
        return Collections.unmodifiableMap(intercaseCommonDatasourcesMap);
    }

    /**
     * Callback to use with processCaseInstancesTable which generates a list of
     * md5s for common files search
     */
    private class EamDbAttributeInstancesCallback implements InstanceTableCallback {

        private final Map<Integer, String> correlationIdToValueMap = new HashMap<>();
        private final Map<Integer, String> correlationIdToDatasourceMap = new HashMap<>();

        @Override
        public void process(ResultSet resultSet) {
            try {
                while (resultSet.next()) {
                    int resultId = InstanceTableCallback.getId(resultSet);
                    correlationIdToValueMap.put(resultId, InstanceTableCallback.getValue(resultSet));
                    correlationIdToDatasourceMap.put(resultId, InstanceTableCallback.getValue(resultSet));
                }
            } catch (SQLException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        Map<Integer, String> getCorrelationIdValueMap() {
            return Collections.unmodifiableMap(correlationIdToValueMap);
        }
        
        Map<Integer, String> getCorrelationIdDatasourceMap() {
            return Collections.unmodifiableMap(correlationIdToDatasourceMap);
        }

    }
}
