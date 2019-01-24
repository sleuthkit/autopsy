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
package org.sleuthkit.autopsy.casemodule;

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
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Queries the coordination service to collect the multi-user case node data
 * stored in the case directory lock ZooKeeper nodes.
 */
final class MulitUserCaseNodeDataCollector {

    private static final Logger logger = Logger.getLogger(MulitUserCaseNodeDataCollector.class.getName());
    private static final String CASE_AUTO_INGEST_LOG_NAME = "AUTO_INGEST_LOG.TXT"; //NON-NLS
    private static final String RESOURCES_LOCK_SUFFIX = "_RESOURCES"; //NON-NLS

    /**
     * Queries the coordination service to collect the multi-user case node data
     * stored in the case directory lock ZooKeeper nodes.
     *
     * @return A list of CaseNodedata objects that convert data for a case
     *         directory lock coordination service node to and from byte arrays.
     *
     * @throws CoordinationServiceException If there is an error
     */
    public static List<CaseNodeData> getNodeData() throws CoordinationService.CoordinationServiceException {
        final List<CaseNodeData> cases = new ArrayList<>();
        final CoordinationService coordinationService = CoordinationService.getInstance();
        final List<String> nodeList = coordinationService.getNodeList(CoordinationService.CategoryNode.CASES);
        for (String nodeName : nodeList) {
            /*
             * Ignore auto ingest case name lock nodes.
             */
            final Path nodeNameAsPath = Paths.get(nodeName);
            if (!(nodeNameAsPath.toString().contains("\\") || nodeNameAsPath.toString().contains("//"))) {
                continue;
            }

            /*
             * Ignore case auto ingest log lock nodes and resource lock nodes.
             */
            final String lastNodeNameComponent = nodeNameAsPath.getFileName().toString();
            if (lastNodeNameComponent.equals(CASE_AUTO_INGEST_LOG_NAME)) {
                continue;
            }

            /*
             * Ignore case resources lock nodes.
             */
            if (lastNodeNameComponent.endsWith(RESOURCES_LOCK_SUFFIX)) {
                continue;
            }

            /*
             * Get the data from the case directory lock node. This data may not
             * exist for "legacy" nodes. If it is missing, create it.
             */
            try {
                CaseNodeData nodeData;
                byte[] nodeBytes = coordinationService.getNodeData(CoordinationService.CategoryNode.CASES, nodeName);
                if (nodeBytes != null && nodeBytes.length > 0) {
                    nodeData = new CaseNodeData(nodeBytes);
                    if (nodeData.getVersion() == 0) {
                        /*
                         * Version 0 case node data was only written if errors
                         * occurred during an auto ingest job and consisted of
                         * only the set errors flag.
                         */
                        nodeData = createNodeDataFromCaseMetadata(nodeName, true);
                    }
                } else {
                    nodeData = createNodeDataFromCaseMetadata(nodeName, false);
                }
                cases.add(nodeData);

            } catch (CoordinationService.CoordinationServiceException | InterruptedException | IOException | ParseException | CaseMetadata.CaseMetadataException ex) {
                logger.log(Level.SEVERE, String.format("Error getting coordination service node data for %s", nodeName), ex);
            }

        }
        return cases;
    }

    /**
     * Creates and saves case directory lock coordination service node data from
     * the metadata file for the case associated with the node.
     *
     * @param nodeName       The coordination service node name, i.e., the case
     *                       directory path.
     * @param errorsOccurred Whether or not errors occurred during an auto
     *                       ingest job for the case.
     *
     * @return A CaseNodedata object.
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
    private static CaseNodeData createNodeDataFromCaseMetadata(String nodeName, boolean errorsOccurred) throws IOException, CaseMetadata.CaseMetadataException, ParseException, CoordinationService.CoordinationServiceException, InterruptedException {
        CaseNodeData nodeData = null;
        Path caseDirectoryPath = Paths.get(nodeName).toRealPath(LinkOption.NOFOLLOW_LINKS);
        File caseDirectory = caseDirectoryPath.toFile();
        if (caseDirectory.exists()) {
            File[] files = caseDirectory.listFiles();
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(CaseMetadata.getFileExtension())) {
                    CaseMetadata metadata = new CaseMetadata(Paths.get(file.getAbsolutePath()));
                    nodeData = new CaseNodeData(metadata);
                    nodeData.setErrorsOccurred(errorsOccurred);
                    break;
                }
            }
        }
        if (nodeData != null) {
            CoordinationService coordinationService = CoordinationService.getInstance();
            coordinationService.setNodeData(CoordinationService.CategoryNode.CASES, nodeName, nodeData.toArray());
            return nodeData;
        } else {
            throw new IOException(String.format("Could not find case metadata file for %s", nodeName));
        }
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private MulitUserCaseNodeDataCollector() {
    }

}
