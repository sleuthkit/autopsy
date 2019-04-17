/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Collects the auto ingest job node data stored in the manifest file
 * coordination service nodes.
 */
final class AutoIngestJobNodeDataCollector {

    private static final Logger logger = Logger.getLogger(AutoIngestJobNodeDataCollector.class.getName());
        
    static List<AutoIngestJobNodeData> getNodeData() throws CoordinationServiceException, InterruptedException {
        final CoordinationService coordinationService = CoordinationService.getInstance();
        final List<String> nodePaths = coordinationService.getNodeList(CoordinationService.CategoryNode.MANIFESTS);
        final List<AutoIngestJobNodeData> nodeDataList = new ArrayList<>();
        for (String nodePath : nodePaths) {
            try {
                final byte[] nodeBytes = coordinationService.getNodeData(CoordinationService.CategoryNode.MANIFESTS, nodePath);
                AutoIngestJobNodeData nodeData = new AutoIngestJobNodeData(nodeBytes);
                nodeDataList.add(nodeData);
            } catch (AutoIngestJobNodeData.InvalidDataException ex) {
                logger.log(Level.WARNING, String.format("Error reading node data from manifest file coordination service node %s", nodePath), ex); // NON-NLS
            }
        }
        return nodeDataList;
    }

    /**
     * Prevents instantiation of this utility class.
     */
    private AutoIngestJobNodeDataCollector() {
    }

}
