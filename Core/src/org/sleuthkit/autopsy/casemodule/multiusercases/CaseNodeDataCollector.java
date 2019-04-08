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
package org.sleuthkit.autopsy.casemodule.multiusercases;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData.CaseNodeDataException;
import static org.sleuthkit.autopsy.casemodule.multiusercases.CoordinationServiceUtils.isCaseAutoIngestLogNodePath;
import static org.sleuthkit.autopsy.casemodule.multiusercases.CoordinationServiceUtils.isCaseNameNodePath;
import static org.sleuthkit.autopsy.casemodule.multiusercases.CoordinationServiceUtils.isCaseResourcesNodePath;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Collects the multi-user case node data stored in the case directory
 * coordination service nodes.
 */
final public class CaseNodeDataCollector {

    private static final Logger logger = Logger.getLogger(CaseNodeDataCollector.class.getName());

    /**
     * Collects the multi-user case node data stored in the case directory
     * coordination service nodes.
     *
     * @return The node data for the multi-user cases known to the coordination
     *         service.
     *
     * @throws CoordinationServiceException If there is an error interacting
     *                                      with the coordination service.
     * @throws InterruptedException         If the current thread is interrupted
     *                                      while waiting for the coordination
     *                                      service.
     */
    public static List<CaseNodeData> getNodeData() throws CoordinationServiceException, InterruptedException {
        final List<CaseNodeData> nodeDataList = new ArrayList<>();
        final CoordinationService coordinationService = CoordinationService.getInstance();
        final List<String> nodePaths = coordinationService.getNodeList(CoordinationService.CategoryNode.CASES);
        for (String nodePath : nodePaths) {
            /*
             * Skip the case name, case resources, and case auto ingest log
             * coordination service nodes. They are not used to store case data.
             */
            if (isCaseNameNodePath(nodePath) || isCaseResourcesNodePath(nodePath) || isCaseAutoIngestLogNodePath(nodePath)) {
                continue;
            }

            /*
             * Get the case node data from the case directory coordination service node.
             */
            try {
                final CaseNodeData nodeData = CaseNodeData.readCaseNodeData(nodePath);
                nodeDataList.add(nodeData);
            } catch (CaseNodeDataException | InterruptedException ex) {
                logger.log(Level.WARNING, String.format("Error reading case node data from %s", nodePath), ex);
            }

        }
        return nodeDataList;
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private CaseNodeDataCollector() {
    }
    
}
