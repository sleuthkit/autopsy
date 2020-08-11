/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import java.beans.PropertyChangeEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.guiutils.RefreshThrottler;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.DATA_ADDED;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Refreshes the CVTFilterPanel.
 */
abstract class CVTFilterRefresher implements RefreshThrottler.Refresher {

    private static final Logger logger = Logger.getLogger(CVTFilterRefresher.class.getName());
    /**
     * contains all of the gui control specific update code. Refresh will call
     * this method with an involkLater so that the updating of the swing
     * controls can happen on the EDT.
     *
     * @param data
     */
    abstract void updateFilterPanel(FilterPanelData data);

    @Override
    public void refresh() {
        try {
            Integer startTime;
            Integer endTime;
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();

            // Fetch Min/Max start times
            try (SleuthkitCase.CaseDbQuery dbQuery = skCase.executeQuery("SELECT MAX(date_time) as end,  MIN(date_time) as start from account_relationships")) {
                // ResultSet is closed by CasDBQuery
                ResultSet rs = dbQuery.getResultSet();
                rs.next();
                startTime = rs.getInt("start"); // NON-NLS
                endTime = rs.getInt("end"); // NON-NLS
                
            }
            // Get the devices with CVT artifacts
            List<Integer> deviceObjIds = new ArrayList<>();
            try (SleuthkitCase.CaseDbQuery queryResult = skCase.executeQuery("SELECT DISTINCT data_source_obj_id FROM account_relationships")) {
                // ResultSet is closed by CasDBQuery
                ResultSet rs = queryResult.getResultSet();
                while (rs.next()) {
                    deviceObjIds.add(rs.getInt(1));
                }
            }

            // The map key is the Content name instead of the data source name
            // to match how the CVT filters work.
            Map<String, DataSource> dataSourceMap = new HashMap<>();
            for (DataSource dataSource : skCase.getDataSources()) {
                if (deviceObjIds.contains((int) dataSource.getId())) {
                    String dsName = skCase.getContentById(dataSource.getId()).getName();
                    dataSourceMap.put(dsName, dataSource);
                }
            }

            List<Account.Type> accountTypesInUse = skCase.getCommunicationsManager().getAccountTypesInUse();

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    updateFilterPanel(new FilterPanelData(dataSourceMap, accountTypesInUse, startTime, endTime));
                }
            });

        } catch (SQLException | TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to update CVT filter panel.", ex);
        } catch (NoCurrentCaseException notUsed) {
            /**
             * Case is closed, do nothing.
             */
        }

    }

    @Override
    public boolean isRefreshRequired(PropertyChangeEvent evt) {
        String eventType = evt.getPropertyName();
        if (eventType.equals(DATA_ADDED.toString())) {
            // Indicate that a refresh may be needed, unless the data added is Keyword or Hashset hits
            ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
            return (null != eventData
                    && (eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()
                    || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT.getTypeID()
                    || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG.getTypeID()
                    || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()));
        }

        return false;
    }

    /**
     * Class to hold the data for setting up the filter panel gui controls.
     */
    class FilterPanelData {

        private final Map<String, DataSource> dataSourceMap;
        private final Integer startTime;
        private final Integer endTime;
        private final List<Account.Type> accountTypesInUse;

        FilterPanelData(Map<String, DataSource> dataSourceMap, List<Account.Type> accountTypesInUse, Integer startTime, Integer endTime) {
            this.dataSourceMap = dataSourceMap;
            this.startTime = startTime;
            this.endTime = endTime;
            this.accountTypesInUse = accountTypesInUse;
        }

        Map<String, DataSource> getDataSourceMap() {
            return dataSourceMap;
        }

        Integer getStartTime() {
            return startTime;
        }

        Integer getEndTime() {
            return endTime;
        }

        List<Account.Type> getAccountTypesInUse() {
            return accountTypesInUse;
        }

    }

}
