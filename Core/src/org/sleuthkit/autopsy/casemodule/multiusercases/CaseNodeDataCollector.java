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

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Queries the coordination service to collect the multi-user case node data
 * stored in the case directory lock ZooKeeper nodes.
 */
final public class CaseNodeDataCollector {

    private static final Logger logger = Logger.getLogger(CaseNodeDataCollector.class.getName());

    /**
     * Queries the coordination service to collect the multi-user case node data
     * stored in the case directory lock ZooKeeper nodes.
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
        final List<CaseNodeData> cases = new ArrayList<>();
        final CoordinationService coordinationService = CoordinationService.getInstance();
        final List<String> nodeList = coordinationService.getNodeList(CoordinationService.CategoryNode.CASES);
        for (String nodeName : nodeList) {
            if (CoordinationServiceUtils.isCaseNameNodePath(nodeName)
                    || CoordinationServiceUtils.isCaseResourcesNodePath(nodeName)
                    || CoordinationServiceUtils.isCaseAutoIngestLogNodePath(nodeName)) {
                continue;
            }

            /*
             * Get the data from the case directory lock node. This data may not
             * exist or may exist only in an older version. If it is missing or
             * incomplete, create or update it.
             */
            try {
                CaseNodeData nodeData;
                final byte[] nodeBytes = coordinationService.getNodeData(CoordinationService.CategoryNode.CASES, nodeName);
                if (nodeBytes != null && nodeBytes.length > 0) {
                    nodeData = new CaseNodeData(nodeBytes);
                    if (nodeData.getVersion() < CaseNodeData.getCurrentVersion()) {
                        nodeData = updateNodeData(nodeName, nodeData);
                    }
                } else {
                    nodeData = updateNodeData(nodeName, null);
                }
                if (nodeData != null) {
                    cases.add(nodeData);
                }

            } catch (CoordinationService.CoordinationServiceException | InterruptedException | IOException | ParseException | CaseMetadata.CaseMetadataException ex) {
                logger.log(Level.SEVERE, String.format("Error getting coordination service node data for %s", nodeName), ex);
            }

        }
        return cases;
    }

    /**
     * Updates the case directory lock coordination service node data for a
     * case.
     *
     * @param nodeName    The coordination service node name, i.e., the case
     *                    directory path.
     * @param oldNodeData The node data to be updated.
     *
     * @return A CaseNodedata object or null if the coordination service node is
     *         an "orphan" with no corresponding case directry.
     *
     * @throws IOException                  If there is an error writing the
     *                                      node data to a byte array.
     * @throws CaseMetadataException        If there is an error reading the
     *                                      case metadata file.
     * @throws ParseException               If there is an error parsing a date
     *                                      from the case metadata file.
     * @throws CoordinationServiceException If there is an error interacting
     *                                      with the coordination service.
     * @throws InterruptedException         If a coordination service operation
     *                                      is interrupted.
     */
    private static CaseNodeData updateNodeData(String nodeName, CaseNodeData oldNodeData) throws IOException, CaseMetadata.CaseMetadataException, ParseException, CoordinationService.CoordinationServiceException, InterruptedException {
        Path caseDirectoryPath = Paths.get(nodeName).toRealPath(LinkOption.NOFOLLOW_LINKS);
        File caseDirectory = caseDirectoryPath.toFile();
        if (!caseDirectory.exists()) {
            logger.log(Level.WARNING, String.format("Found orphan coordination service node %s, attempting clean up", caseDirectoryPath));
            deleteLockNodes(CoordinationService.getInstance(), caseDirectoryPath);
            return null;
        }

        CaseNodeData nodeData = null;
        if (oldNodeData == null || oldNodeData.getVersion() == 0) {
            File[] files = caseDirectory.listFiles();
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(CaseMetadata.getFileExtension())) {
                    CaseMetadata metadata = new CaseMetadata(Paths.get(file.getAbsolutePath()));
                    nodeData = new CaseNodeData(metadata);
                    if (oldNodeData != null) {
                        /*
                         * Version 0 case node data was only written if errors
                         * occurred during an auto ingest job.
                         */
                        nodeData.setErrorsOccurred(true);
                    }
                    break;
                }
            }
        }

        if (nodeData != null) {
            CoordinationService.getInstance().setNodeData(CoordinationService.CategoryNode.CASES, nodeName, nodeData.toArray());
        }

        return nodeData;
    }

    /**
     * Attempts to delete the coordination service lock nodes for a case,
     * logging any failures.
     *
     * @param coordinationService The coordination service.
     * @param caseDirectoryPath   The case directory path.
     */
    private static void deleteLockNodes(CoordinationService coordinationService, Path caseDirectoryPath) {
        deleteCoordinationServiceNode(coordinationService, CoordinationServiceUtils.getCaseResourcesNodePath(caseDirectoryPath));
        deleteCoordinationServiceNode(coordinationService, CoordinationServiceUtils.getCaseAutoIngestLogNodePath(caseDirectoryPath));
        deleteCoordinationServiceNode(coordinationService, CoordinationServiceUtils.getCaseDirectoryNodePath(caseDirectoryPath));
        deleteCoordinationServiceNode(coordinationService, CoordinationServiceUtils.getCaseNameNodePath(caseDirectoryPath));
    }

    /**
     * Attempts to delete a coordination service node, logging failure.
     *
     * @param coordinationService The coordination service.
     * @param nodeName            A node name.
     */
    private static void deleteCoordinationServiceNode(CoordinationService coordinationService, String nodeName) {
        try {
            coordinationService.deleteNode(CoordinationService.CategoryNode.CASES, nodeName);
        } catch (CoordinationService.CoordinationServiceException | InterruptedException ex) {
            logger.log(Level.WARNING, String.format("Error deleting coordination service node %s", nodeName), ex);
        }
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private CaseNodeDataCollector() {
    }

}
