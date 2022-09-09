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
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.application.NodeData;
import org.sleuthkit.autopsy.centralrepository.application.OtherOccurrences;
import org.sleuthkit.autopsy.centralrepository.contentviewer.OtherOccurrencesNodeWorker.OtherOccurrencesData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.TskContentItem;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.TskException;

/**
 * A SwingWorker that gathers data for the OtherOccurencesPanel which appears in
 * the dataContentViewerOtherCases panel.
 */
class OtherOccurrencesNodeWorker extends SwingWorker<OtherOccurrencesData, Void> {

    private static final Logger logger = Logger.getLogger(OtherOccurrencesNodeWorker.class.getName());

    private final Node node;

    /**
     * Constructs a new instance for the given node.
     *
     * @param node
     */
    OtherOccurrencesNodeWorker(Node node) {
        this.node = node;
    }

    @Override
    protected OtherOccurrencesData doInBackground() throws Exception {
        OtherOccurrencesData data = null;
        if (CentralRepository.isEnabled()) {
            OsAccount osAccount = node.getLookup().lookup(OsAccount.class);
            String deviceId = "";
            String dataSourceName = "";
            Map<String, CorrelationCase> caseNames = new HashMap<>();
            Case currentCase = Case.getCurrentCaseThrows();
            //the file is currently being used for determining a correlation instance is not the selected instance 
            // for the purposes of ignoring the currently selected item
            AbstractFile file = node.getLookup().lookup(AbstractFile.class);
            try {
                if (file != null) {
                    Content dataSource = file.getDataSource();
                    deviceId = currentCase.getSleuthkitCase().getDataSource(dataSource.getId()).getDeviceId();
                    dataSourceName = dataSource.getName();
                }
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Exception occurred while trying to get the data source, current case, and device id for an AbstractFile in the other occurrences viewer", ex);
                return data;
            }
            Collection<CorrelationAttributeInstance> correlationAttributes = new ArrayList<>();
            if (osAccount != null) {
                correlationAttributes.addAll(OtherOccurrences.getCorrelationAttributeFromOsAccount(node, osAccount));
            } else {
                TskContentItem<?> contentItem = node.getLookup().lookup(TskContentItem.class);
                Content content = null;
                if (contentItem != null) {
                    content = contentItem.getTskContent();
                } else { //fallback and check ContentTags 
                    ContentTag nodeContentTag = node.getLookup().lookup(ContentTag.class);
                    BlackboardArtifactTag nodeBbArtifactTag = node.getLookup().lookup(BlackboardArtifactTag.class);
                    if (nodeBbArtifactTag != null) {
                        content = nodeBbArtifactTag.getArtifact();
                    } else if (nodeContentTag != null) {
                        content = nodeContentTag.getContent();
                    }
                }
                if (content != null) {
                    if (content instanceof AbstractFile) {
                        correlationAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((AbstractFile) content));
                    } else if (content instanceof AnalysisResult) {
                        correlationAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((AnalysisResult) content));
                    } else if (content instanceof DataArtifact) {
                        correlationAttributes.addAll(CorrelationAttributeUtil.makeCorrAttrsForSearch((DataArtifact) content));
                    }
                }
            }
            int totalCount = 0;
            Set<String> dataSources = new HashSet<>();
            String currentCaseName = Case.getCurrentCase().getName();
            for (CorrelationAttributeInstance corAttr : correlationAttributes) {
                for (NodeData nodeData : OtherOccurrences.getCorrelatedInstances(deviceId, dataSourceName, corAttr).values()) {
                    try {
                        if(!currentCaseName.equals(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID())) {
                            dataSources.add(OtherOccurrences.makeDataSourceString(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID(), nodeData.getDeviceID(), nodeData.getDataSourceName()));
                            caseNames.put(nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID(), nodeData.getCorrelationAttributeInstance().getCorrelationCase());
                        }
                    } catch (CentralRepoException ex) {
                        logger.log(Level.WARNING, "Unable to get correlation case for displaying other occurrence for case: " + nodeData.getCaseName(), ex);
                    }
                    totalCount++;
                    if (isCancelled()) {
                        break;
                    }
                }
            }
            if (!isCancelled()) {
                data = new OtherOccurrencesData(correlationAttributes, file, dataSourceName, deviceId, caseNames, totalCount, dataSources.size(), OtherOccurrences.getEarliestCaseDate());
            }
        }
        return data;
    }

    /**
     * Object to store all of the data gathered in the OtherOccurrencesWorker
     * doInBackground method.
     */
    static class OtherOccurrencesData {

        private final String deviceId;
        private final AbstractFile file;
        private final String dataSourceName;
        private final Map<String, CorrelationCase> caseMap;
        private final int instanceDataCount;
        private final int dataSourceCount;
        private final String earliestCaseDate;
        private final Collection<CorrelationAttributeInstance> correlationAttributes;

        private OtherOccurrencesData(Collection<CorrelationAttributeInstance> correlationAttributes, AbstractFile file, String dataSourceName, String deviceId, Map<String, CorrelationCase> caseMap, int instanceCount, int dataSourceCount, String earliestCaseDate) {
            this.file = file;
            this.deviceId = deviceId;
            this.dataSourceName = dataSourceName;
            this.caseMap = caseMap;
            this.instanceDataCount = instanceCount;
            this.dataSourceCount = dataSourceCount;
            this.earliestCaseDate = earliestCaseDate;
            this.correlationAttributes = correlationAttributes;
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

        /**
         * Returns the earliest date in the case.
         *
         * @return Formatted date string, or message that one was not found.
         */
        public String getEarliestCaseDate() {
            return earliestCaseDate;
        }

        public Collection<CorrelationAttributeInstance> getCorrelationAttributes() {
            return correlationAttributes;
        }
    }
}
