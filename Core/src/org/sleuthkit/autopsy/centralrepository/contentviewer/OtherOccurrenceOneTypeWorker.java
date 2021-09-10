/*
 * Central Repository
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.application.NodeData;
import org.sleuthkit.autopsy.centralrepository.application.OtherOccurrences;
import org.sleuthkit.autopsy.centralrepository.application.UniquePathKey;
import org.sleuthkit.autopsy.centralrepository.contentviewer.OtherOccurrenceOneTypeWorker.OneTypeData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Swing worker for getting the Other Occurrence data for the Domain Discovery
 * window.
 *
 * This logic differs a bit from the OtherOcurrencesNodeWorker.
 */
class OtherOccurrenceOneTypeWorker extends SwingWorker<OneTypeData, Void> {

    private static final Logger logger = Logger.getLogger(OtherOccurrenceOneTypeWorker.class.getName());

    private final CorrelationAttributeInstance.Type aType;
    private final String value;
    private final AbstractFile file;
    private final String deviceId;
    private final String dataSourceName;

    /**
     * Construct the worker.
     *
     * @param aType
     * @param value
     * @param file           Source file, this maybe null.
     * @param deviceId       DeviceID string, this maybe an empty string.
     * @param dataSourceName DataSourceName, this maybe an empty string.
     */
    OtherOccurrenceOneTypeWorker(CorrelationAttributeInstance.Type aType, String value, AbstractFile file, String deviceId, String dataSourceName) {
        this.aType = aType;
        this.value = value;
        this.file = file;
        this.deviceId = deviceId;
        this.dataSourceName = dataSourceName;
    }

    @Override
    protected OneTypeData doInBackground() throws Exception {
        Map<String, CorrelationCase> caseNames = new HashMap<>();
        int totalCount = 0;
        Set<String> dataSources = new HashSet<>();
        Collection<CorrelationAttributeInstance> correlationAttributesToAdd = new ArrayList<>();
        String earliestDate = OtherOccurrences.getEarliestCaseDate();
        OneTypeData results = null;

        if (CentralRepository.isEnabled()) {
            List<CorrelationAttributeInstance> instances;
            instances = CentralRepository.getInstance().getArtifactInstancesByTypeValue(aType, value);
            HashMap<UniquePathKey, NodeData> nodeDataMap = new HashMap<>();
            String caseUUID = Case.getCurrentCase().getName();
            for (CorrelationAttributeInstance artifactInstance : instances) {
                if (isCancelled()) {
                    break;
                }

                // Only add the attribute if it isn't the object the user selected.
                // We consider it to be a different object if at least one of the following is true:
                // - the case UUID is different
                // - the data source name is different
                // - the data source device ID is different
                // - the file path is different
                if (artifactInstance.getCorrelationCase().getCaseUUID().equals(caseUUID)
                        && (!StringUtils.isBlank(dataSourceName) && artifactInstance.getCorrelationDataSource().getName().equals(dataSourceName))
                        && (!StringUtils.isBlank(deviceId) && artifactInstance.getCorrelationDataSource().getDeviceID().equals(deviceId))
                        && (file != null && artifactInstance.getFilePath().equalsIgnoreCase(file.getParentPath() + file.getName()))) {

                    continue;
                }
                correlationAttributesToAdd.add(artifactInstance);
                NodeData newNode = new NodeData(artifactInstance, aType, value);
                UniquePathKey uniquePathKey = new UniquePathKey(newNode);
                nodeDataMap.put(uniquePathKey, newNode);
            }

            for (NodeData nodeData : nodeDataMap.values()) {
                if (isCancelled()) {
                    break;
                }
                try {
                    dataSources.add(OtherOccurrences.makeDataSourceString(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID(), nodeData.getDeviceID(), nodeData.getDataSourceName()));
                    caseNames.put(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID(), nodeData.getCorrelationAttributeInstance().getCorrelationCase());
                } catch (CentralRepoException ex) {
                    logger.log(Level.WARNING, "Unable to get correlation case for displaying other occurrence for case: " + nodeData.getCaseName(), ex);
                }
                totalCount++;
            }
        }

        if (!isCancelled()) {
            results = new OneTypeData(caseNames, totalCount, dataSources.size(), earliestDate, correlationAttributesToAdd);
        }

        return results;
    }

    /**
     * Class to store the results of the worker thread.
     */
    static final class OneTypeData {

        private final Map<String, CorrelationCase> caseNames;
        private final int totalCount;
        private final int dataSourceCount;
        private final Collection<CorrelationAttributeInstance> correlationAttributesToAdd;
        private final String earliestCaseDate;

        /**
         * Construct the results.
         *
         * @param caseNames                  Map of correlation cases.
         * @param totalCount                 Total count of instances.
         * @param dataSourceCount            Data source count.
         * @param earliestCaseDate           Formatted string which contains the
         *                                   earliest case date.
         * @param correlationAttributesToAdd The attributes to add to the main
         *                                   panel list.
         */
        OneTypeData(Map<String, CorrelationCase> caseNames, int totalCount, int dataSourceCount, String earliestCaseDate, Collection<CorrelationAttributeInstance> correlationAttributesToAdd) {
            this.caseNames = caseNames;
            this.totalCount = totalCount;
            this.dataSourceCount = dataSourceCount;
            this.correlationAttributesToAdd = correlationAttributesToAdd;
            this.earliestCaseDate = earliestCaseDate;
        }

        public Map<String, CorrelationCase> getCaseNames() {
            return caseNames;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getDataSourceCount() {
            return dataSourceCount;
        }

        public Collection<CorrelationAttributeInstance> getCorrelationAttributesToAdd() {
            return correlationAttributesToAdd;
        }

        public String getEarliestCaseDate() {
            return earliestCaseDate;
        }
    }
}
