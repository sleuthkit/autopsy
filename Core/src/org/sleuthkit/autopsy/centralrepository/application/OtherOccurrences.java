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
package org.sleuthkit.autopsy.centralrepository.application;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountInstance;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Contains most of the methods for gathering data from the DB and CR for the
 * OtherOccurrencesPanel.
 */
public final class OtherOccurrences {

    private static final Logger logger = Logger.getLogger(OtherOccurrences.class.getName());

    private static final String UUID_PLACEHOLDER_STRING = "NoCorrelationAttributeInstance";

    private OtherOccurrences() {
    }

    /**
     * Determine what attributes can be used for correlation based on the node.
     *
     * @param node      The node to correlate
     * @param osAccount the osAccount to correlate
     *
     * @return A list of attributes that can be used for correlation
     */
    public static Collection<CorrelationAttributeInstance> getCorrelationAttributeFromOsAccount(Node node, OsAccount osAccount) {
        Optional<String> osAccountAddr = osAccount.getAddr();
        if (osAccountAddr.isPresent()) {
            try {
                for (OsAccountInstance instance : osAccount.getOsAccountInstances()) {
                    List<CorrelationAttributeInstance> correlationAttributeInstances = CorrelationAttributeUtil.makeCorrAttrsForSearch(instance);
                    if (!correlationAttributeInstances.isEmpty()) {
                        return correlationAttributeInstances;
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.INFO, String.format("Unable to check create CorrelationAttribtueInstance for osAccount %s.", osAccountAddr.get()), ex);
            }
        }
        return Collections.emptyList();
    }

    /**
     * Query the central repo database (if enabled) and the case database to
     * find all artifact instances correlated to the given central repository
     * artifact.
     *
     * @param deviceId       The device ID for the current data source.
     * @param dataSourceName The name of the current data source.
     * @param corAttr        CorrelationAttribute to query for
     *
     * @return A collection of correlated artifact instances
     */
    public static Map<UniquePathKey, NodeData> getCorrelatedInstances(String deviceId, String dataSourceName, CorrelationAttributeInstance corAttr) {
        // @@@ Check exception
        try {
            final Case openCase = Case.getCurrentCaseThrows();
            String caseUUID = openCase.getName();
            HashMap<UniquePathKey, NodeData> nodeDataMap = new HashMap<>();

            if (CentralRepository.isEnabled()) {
                List<CorrelationAttributeInstance> instances = CentralRepository.getInstance().getArtifactInstancesByTypeValue(corAttr.getCorrelationType(), corAttr.getCorrelationValue());

                for (CorrelationAttributeInstance artifactInstance : instances) {

                    // Only add the attribute if it isn't the object the user selected.
                    // We consider it to be a different object if at least one of the following is true:
                    // - the case UUID is different
                    // - the data source name is different
                    // - the data source device ID is different
                    // - the object id for the underlying file is different
                    if (artifactInstance.getCorrelationCase().getCaseUUID().equals(caseUUID)
                            && (!StringUtils.isBlank(dataSourceName) && artifactInstance.getCorrelationDataSource().getName().equals(dataSourceName))
                            && (!StringUtils.isBlank(deviceId) && artifactInstance.getCorrelationDataSource().getDeviceID().equals(deviceId))) {
                        Long foundObjectId = artifactInstance.getFileObjectId();
                        Long currentObjectId = corAttr.getFileObjectId();
                        if (foundObjectId != null && currentObjectId != null && foundObjectId.equals(currentObjectId)) {
                            continue;
                        }
                    }
                    NodeData newNode = new NodeData(artifactInstance, corAttr.getCorrelationType(), corAttr.getCorrelationValue());
                    UniquePathKey uniquePathKey = new UniquePathKey(newNode);
                    nodeDataMap.put(uniquePathKey, newNode);
                }
            }
            return nodeDataMap;
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error getting artifact instances from database.", ex); // NON-NLS
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.INFO, "Error getting artifact instances from database.", ex); // NON-NLS
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        }

        return new HashMap<>(
                0);
    }

    /**
     * Adds the file to the nodeDataMap map if it does not already exist
     *
     * @param autopsyCase
     * @param nodeDataMap
     * @param newFile
     *
     * @throws TskCoreException
     * @throws CentralRepoException
     */
    public static void addOrUpdateNodeData(final Case autopsyCase, Map<UniquePathKey, NodeData> nodeDataMap, AbstractFile newFile) throws TskCoreException, CentralRepoException {

        NodeData newNode = new NodeData(newFile, autopsyCase);

        // If the caseDB object has a notable tag associated with it, update
        // the known status to BAD
        if (newNode.getKnown() != TskData.FileKnown.BAD) {
            List<ContentTag> fileMatchTags = autopsyCase.getServices().getTagsManager().getContentTagsByContent(newFile);
            for (ContentTag tag : fileMatchTags) {
                TskData.FileKnown tagKnownStatus = tag.getName().getKnownStatus();
                if (tagKnownStatus.equals(TskData.FileKnown.BAD)) {
                    newNode.updateKnown(TskData.FileKnown.BAD);
                    break;
                }
            }
        }

        // Make a key to see if the file is already in the map
        UniquePathKey uniquePathKey = new UniquePathKey(newNode);

        // If this node is already in the list, the only thing we need to do is
        // update the known status to BAD if the caseDB version had known status BAD.
        // Otherwise this is a new node so add the new node to the map.
        if (nodeDataMap.containsKey(uniquePathKey)) {
            if (newNode.getKnown() == TskData.FileKnown.BAD) {
                NodeData prevInstance = nodeDataMap.get(uniquePathKey);
                prevInstance.updateKnown(newNode.getKnown());
            }
        } else {
            nodeDataMap.put(uniquePathKey, newNode);
        }
    }

    /**
     * Create a unique string to be used as a key for deduping data sources as
     * best as possible
     */
    public static String makeDataSourceString(String caseUUID, String deviceId, String dataSourceName) {
        return caseUUID + deviceId + dataSourceName;
    }

    /**
     * Gets the list of Eam Cases and determines the earliest case creation
     * date. Sets the label to display the earliest date string to the user.
     */
    public static String getEarliestCaseDate() throws CentralRepoException {
        String dateStringDisplay = "";

        if (CentralRepository.isEnabled()) {
            LocalDateTime earliestDate = LocalDateTime.now(DateTimeZone.UTC);
            DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
            CentralRepository dbManager = CentralRepository.getInstance();
            List<CorrelationCase> cases = dbManager.getCases();
            for (CorrelationCase aCase : cases) {
                LocalDateTime caseDate;
                try {
                    caseDate = LocalDateTime.fromDateFields(datetimeFormat.parse(aCase.getCreationDate()));

                    if (caseDate.isBefore(earliestDate)) {
                        earliestDate = caseDate;
                        dateStringDisplay = aCase.getCreationDate();
                    }
                } catch (ParseException ex) {
                    throw new CentralRepoException("Failed to format case creation date " + aCase.getCreationDate(), ex);
                }
            }
        }

        return dateStringDisplay;
    }

    @NbBundle.Messages({
        "OtherOccurrences.csvHeader.case=Case",
        "OtherOccurrences.csvHeader.device=Device",
        "OtherOccurrences.csvHeader.dataSource=Data Source",
        "OtherOccurrences.csvHeader.attribute=Matched Attribute",
        "OtherOccurrences.csvHeader.value=Attribute Value",
        "OtherOccurrences.csvHeader.known=Known",
        "OtherOccurrences.csvHeader.path=Path",
        "OtherOccurrences.csvHeader.comment=Comment"
    })

    /**
     * Create a cvs file of occurrences for the given parameters.
     *
     * @param destFile           Output file for the csv data.
     * @param correlationAttList List of correclationAttributeInstances, should
     *                           not be null.
     * @param dataSourceName     Name of the data source.
     * @param deviceId           Device id.
     *
     * @throws IOException
     */
    public static void writeOtherOccurrencesToFileAsCSV(File destFile, Collection<CorrelationAttributeInstance> correlationAttList, String dataSourceName, String deviceId) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(destFile.toPath())) {
            //write headers 
            StringBuilder headers = new StringBuilder("\"");
            headers.append(Bundle.OtherOccurrences_csvHeader_case())
                    .append(NodeData.getCsvItemSeparator()).append(Bundle.OtherOccurrences_csvHeader_dataSource())
                    .append(NodeData.getCsvItemSeparator()).append(Bundle.OtherOccurrences_csvHeader_attribute())
                    .append(NodeData.getCsvItemSeparator()).append(Bundle.OtherOccurrences_csvHeader_value())
                    .append(NodeData.getCsvItemSeparator()).append(Bundle.OtherOccurrences_csvHeader_known())
                    .append(NodeData.getCsvItemSeparator()).append(Bundle.OtherOccurrences_csvHeader_path())
                    .append(NodeData.getCsvItemSeparator()).append(Bundle.OtherOccurrences_csvHeader_comment())
                    .append('"').append(System.getProperty("line.separator"));
            writer.write(headers.toString());
            //write content
            for (CorrelationAttributeInstance corAttr : correlationAttList) {
                Map<UniquePathKey, NodeData> correlatedNodeDataMap = new HashMap<>(0);
                // get correlation and reference set instances from DB
                correlatedNodeDataMap.putAll(getCorrelatedInstances(deviceId, dataSourceName, corAttr));
                for (NodeData nodeData : correlatedNodeDataMap.values()) {
                    writer.write(nodeData.toCsvString());
                }
            }
        }
    }

    /**
     * Get a placeholder string to use in place of case uuid when it isn't
     * available
     *
     * @return UUID_PLACEHOLDER_STRING
     */
    public static String getPlaceholderUUID() {
        return UUID_PLACEHOLDER_STRING;
    }
}
