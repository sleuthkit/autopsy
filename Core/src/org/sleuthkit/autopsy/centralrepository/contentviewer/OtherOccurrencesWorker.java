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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.contentviewer.OtherOccurrencesWorker.OtherOccurrencesData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskException;

/**
 *
 *
 */
class OtherOccurrencesWorker extends SwingWorker<OtherOccurrencesData, Void> {

    private static final Logger logger = Logger.getLogger(OtherOccurrencesWorker.class.getName());

    private final Node node;

    OtherOccurrencesWorker(Node node) {
        this.node = node;
    }

    @Override
    protected OtherOccurrencesData doInBackground() throws Exception {
        AbstractFile file = OtherOccurrenceUtilities.getAbstractFileFromNode(node);
        String deviceId = "";
        String dataSourceName = "";
        Map<String, CorrelationCase> caseNames = new HashMap<>();
        try {
            if (file != null) {
                Content dataSource = file.getDataSource();
                deviceId = Case.getCurrentCaseThrows().getSleuthkitCase().getDataSource(dataSource.getId()).getDeviceId();
                dataSourceName = dataSource.getName();
            }
        } catch (TskException | NoCurrentCaseException ex) {
            // do nothing. 
            // @@@ Review this behavior
            return null;
        }
        Collection<CorrelationAttributeInstance> correlationAttributes = OtherOccurrenceUtilities.getCorrelationAttributesFromNode(node, file);

        int totalCount = 0;
        Set<String> dataSources = new HashSet<>();
        for (CorrelationAttributeInstance corAttr : correlationAttributes) {
            for (OtherOccurrenceNodeInstanceData nodeData : OtherOccurrenceUtilities.getCorrelatedInstances(file, deviceId, dataSourceName, corAttr).values()) {
                if (nodeData.isCentralRepoNode()) {
                    try {
                        dataSources.add(OtherOccurrenceUtilities.makeDataSourceString(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID(), nodeData.getDeviceID(), nodeData.getDataSourceName()));
                        caseNames.put(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID(), nodeData.getCorrelationAttributeInstance().getCorrelationCase());
                    } catch (CentralRepoException ex) {
                        logger.log(Level.WARNING, "Unable to get correlation case for displaying other occurrence for case: " + nodeData.getCaseName(), ex);
                    }
                } else {
                    try {
                        dataSources.add(OtherOccurrenceUtilities.makeDataSourceString(Case.getCurrentCaseThrows().getName(), nodeData.getDeviceID(), nodeData.getDataSourceName()));
                        caseNames.put(Case.getCurrentCaseThrows().getName(), new CorrelationCase(Case.getCurrentCaseThrows().getName(), Case.getCurrentCaseThrows().getDisplayName()));
                    } catch (NoCurrentCaseException ex) {
                        logger.log(Level.WARNING, "No current case open for other occurrences", ex);
                    }
                }
                totalCount++;
            }
        }

        return new OtherOccurrencesData(file, dataSourceName, deviceId, caseNames, totalCount, dataSources.size());
    }

    static class OtherOccurrencesData {
        private final String deviceId;
        private final AbstractFile file;
        private final String dataSourceName;
        private final Map<String, CorrelationCase> caseMap;
        private final int instanceDataCount;
        private final int dataSourceCount;

        private OtherOccurrencesData(AbstractFile file, String dataSourceName, String deviceId, Map<String, CorrelationCase> caseMap, int instanceCount, int dataSourceCount) {
            this.file = file;
            this.deviceId = deviceId;
            this.dataSourceName = dataSourceName;
            this.caseMap = caseMap;
            this.instanceDataCount = instanceCount;
            this.dataSourceCount = dataSourceCount;
        }
        
        public String getDeviceId() {
            return deviceId;
        }

        public AbstractFile getFile() {
            return file;
        }

        public String getDataSourceName() {
            return dataSourceName;
        }

        public Map<String, CorrelationCase> getCaseMap() {
            return caseMap;
        }

        public int getInstanceDataCount() {
            return instanceDataCount;
        }

        public int getDataSourceCount() {
            return dataSourceCount;
        }

    }
}
