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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Contains most of the methods for gathering data from the DB and CR for the
 * OtherOccurrencesPanel.
 */
class OtherOccurrenceUtilities {

    private static final Logger logger = Logger.getLogger(OtherOccurrenceUtilities.class.getName());

    private OtherOccurrenceUtilities() {
    }

    /**
     * Determine what attributes can be used for correlation based on the node.
     * If EamDB is not enabled, get the default Files correlation.
     *
     * @param node The node to correlate
     *
     * @return A list of attributes that can be used for correlation
     */
    static Collection<CorrelationAttributeInstance> getCorrelationAttributesFromNode(Node node, AbstractFile file) {
        Collection<CorrelationAttributeInstance> ret = new ArrayList<>();

        // correlate on blackboard artifact attributes if they exist and supported
        BlackboardArtifact bbArtifact = getBlackboardArtifactFromNode(node);
        if (bbArtifact != null && CentralRepository.isEnabled()) {
            ret.addAll(CorrelationAttributeUtil.makeCorrAttrsForCorrelation(bbArtifact));
        }

        // we can correlate based on the MD5 if it is enabled      
        if (file != null && CentralRepository.isEnabled() && file.getSize() > 0) {
            try {

                List<CorrelationAttributeInstance.Type> artifactTypes = CentralRepository.getInstance().getDefinedCorrelationTypes();
                String md5 = file.getMd5Hash();
                if (md5 != null && !md5.isEmpty() && null != artifactTypes && !artifactTypes.isEmpty()) {
                    for (CorrelationAttributeInstance.Type aType : artifactTypes) {
                        if (aType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                            CorrelationCase corCase = CentralRepository.getInstance().getCase(Case.getCurrentCase());
                            try {
                                ret.add(new CorrelationAttributeInstance(
                                        aType,
                                        md5,
                                        corCase,
                                        CorrelationDataSource.fromTSKDataSource(corCase, file.getDataSource()),
                                        file.getParentPath() + file.getName(),
                                        "",
                                        file.getKnown(),
                                        file.getId()));
                            } catch (CorrelationAttributeNormalizationException ex) {
                                logger.log(Level.INFO, String.format("Unable to check create CorrelationAttribtueInstance for value %s and type %s.", md5, aType.toString()), ex);
                            }
                            break;
                        }
                    }
                }
            } catch (CentralRepoException | TskCoreException ex) {
                logger.log(Level.SEVERE, "Error connecting to DB", ex); // NON-NLS
            }
            // If EamDb not enabled, get the Files default correlation type to allow Other Occurances to be enabled.  
        } else if (file != null && file.getSize() > 0) {
            String md5 = file.getMd5Hash();
            if (md5 != null && !md5.isEmpty()) {
                try {
                    final CorrelationAttributeInstance.Type fileAttributeType
                            = CorrelationAttributeInstance.getDefaultCorrelationTypes()
                                    .stream()
                                    .filter(attrType -> attrType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID)
                                    .findAny()
                                    .get();
                    //The Central Repository is not enabled
                    ret.add(new CorrelationAttributeInstance(fileAttributeType, md5, null, null, "", "", TskData.FileKnown.UNKNOWN, file.getId()));
                } catch (CentralRepoException ex) {
                    logger.log(Level.SEVERE, "Error connecting to DB", ex); // NON-NLS
                } catch (CorrelationAttributeNormalizationException ex) {
                    logger.log(Level.INFO, String.format("Unable to create CorrelationAttributeInstance for value %s", md5), ex); // NON-NLS
                }
            }
        }
        return ret;
    }

    /**
     * Get the associated BlackboardArtifact from a node, if it exists.
     *
     * @param node The node
     *
     * @return The associated BlackboardArtifact, or null
     */
    static BlackboardArtifact getBlackboardArtifactFromNode(Node node) {
        BlackboardArtifactTag nodeBbArtifactTag = node.getLookup().lookup(BlackboardArtifactTag.class);
        BlackboardArtifact nodeBbArtifact = node.getLookup().lookup(BlackboardArtifact.class);

        if (nodeBbArtifactTag != null) {
            return nodeBbArtifactTag.getArtifact();
        } else if (nodeBbArtifact != null) {
            return nodeBbArtifact;
        }

        return null;

    }

    /**
     * Get the associated AbstractFile from a node, if it exists.
     *
     * @param node The node
     *
     * @return The associated AbstractFile, or null
     */
    static AbstractFile getAbstractFileFromNode(Node node) {
        BlackboardArtifactTag nodeBbArtifactTag = node.getLookup().lookup(BlackboardArtifactTag.class);
        ContentTag nodeContentTag = node.getLookup().lookup(ContentTag.class);
        BlackboardArtifact nodeBbArtifact = node.getLookup().lookup(BlackboardArtifact.class);
        AbstractFile nodeAbstractFile = node.getLookup().lookup(AbstractFile.class);

        if (nodeBbArtifactTag != null) {
            Content content = nodeBbArtifactTag.getContent();
            if (content instanceof AbstractFile) {
                return (AbstractFile) content;
            }
        } else if (nodeContentTag != null) {
            Content content = nodeContentTag.getContent();
            if (content instanceof AbstractFile) {
                return (AbstractFile) content;
            }
        } else if (nodeBbArtifact != null) {
            Content content;
            try {
                content = nodeBbArtifact.getSleuthkitCase().getContentById(nodeBbArtifact.getObjectID());
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error retrieving blackboard artifact", ex); // NON-NLS
                return null;
            }

            if (content instanceof AbstractFile) {
                return (AbstractFile) content;
            }
        } else if (nodeAbstractFile != null) {
            return nodeAbstractFile;
        }

        return null;
    }

    /**
     * Query the central repo database (if enabled) and the case database to
     * find all artifact instances correlated to the given central repository
     * artifact. If the central repo is not enabled, this will only return files
     * from the current case with matching MD5 hashes.
     *
     * @param corAttr CorrelationAttribute to query for
     *
     * @return A collection of correlated artifact instances
     */
    static Map<UniquePathKey, OtherOccurrenceNodeInstanceData> getCorrelatedInstances(AbstractFile file, String deviceId, String dataSourceName, CorrelationAttributeInstance corAttr) {
        // @@@ Check exception
        try {
            final Case openCase = Case.getCurrentCaseThrows();
            String caseUUID = openCase.getName();
            HashMap<UniquePathKey, OtherOccurrenceNodeInstanceData> nodeDataMap = new HashMap<>();

            if (CentralRepository.isEnabled()) {
                List<CorrelationAttributeInstance> instances = CentralRepository.getInstance().getArtifactInstancesByTypeValue(corAttr.getCorrelationType(), corAttr.getCorrelationValue());

                for (CorrelationAttributeInstance artifactInstance : instances) {

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
                    OtherOccurrenceNodeInstanceData newNode = new OtherOccurrenceNodeInstanceData(artifactInstance, corAttr.getCorrelationType(), corAttr.getCorrelationValue());
                    UniquePathKey uniquePathKey = new UniquePathKey(newNode);
                    nodeDataMap.put(uniquePathKey, newNode);
                }
                if (file != null && corAttr.getCorrelationType().getDisplayName().equals("Files")) {
                    List<AbstractFile> caseDbFiles = getCaseDbMatches(corAttr, openCase, file);

                    for (AbstractFile caseDbFile : caseDbFiles) {
                        addOrUpdateNodeData(openCase, nodeDataMap, caseDbFile);
                    }
                }
            }
            return nodeDataMap;
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error getting artifact instances from database.", ex); // NON-NLS
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.INFO, "Error getting artifact instances from database.", ex); // NON-NLS
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
        } catch (TskCoreException ex) {
            // do nothing. 
            // @@@ Review this behavior
            logger.log(Level.SEVERE, "Exception while querying open case.", ex); // NON-NLS
        }

        return new HashMap<>(
                0);
    }

    /**
     * Get all other abstract files in the current case with the same MD5 as the
     * selected node.
     *
     * @param corAttr  The CorrelationAttribute containing the MD5 to search for
     * @param openCase The current case
     * @param file     The current file.
     *
     * @return List of matching AbstractFile objects
     *
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     * @throws CentralRepoException
     */
    static List<AbstractFile> getCaseDbMatches(CorrelationAttributeInstance corAttr, Case openCase, AbstractFile file) throws NoCurrentCaseException, TskCoreException, CentralRepoException {
        List<AbstractFile> caseDbArtifactInstances = new ArrayList<>();
        if (file != null) {
            String md5 = corAttr.getCorrelationValue();
            SleuthkitCase tsk = openCase.getSleuthkitCase();
            List<AbstractFile> matches = tsk.findAllFilesWhere(String.format("md5 = '%s'", new Object[]{md5}));

            for (AbstractFile fileMatch : matches) {
                if (file.equals(fileMatch)) {
                    continue; // If this is the file the user clicked on
                }
                caseDbArtifactInstances.add(fileMatch);
            }
        }
        return caseDbArtifactInstances;

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
    static void addOrUpdateNodeData(final Case autopsyCase, Map<UniquePathKey, OtherOccurrenceNodeInstanceData> nodeDataMap, AbstractFile newFile) throws TskCoreException, CentralRepoException {

        OtherOccurrenceNodeInstanceData newNode = new OtherOccurrenceNodeInstanceData(newFile, autopsyCase);

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
                OtherOccurrenceNodeInstanceData prevInstance = nodeDataMap.get(uniquePathKey);
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
    static String makeDataSourceString(String caseUUID, String deviceId, String dataSourceName) {
        return caseUUID + deviceId + dataSourceName;
    }

    @NbBundle.Messages({"OtherOccurrencesPanel.earliestCaseNotAvailable= Not Enabled."})
    /**
     * Gets the list of Eam Cases and determines the earliest case creation
     * date. Sets the label to display the earliest date string to the user.
     */
    static String getEarliestCaseDate() throws CentralRepoException {
        String dateStringDisplay = Bundle.OtherOccurrencesPanel_earliestCaseNotAvailable();

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

    /**
     * Create a cvs file of occurrences for the given parameters.
     *
     * @param destFile           Output file for the csv data.
     * @param abstractFile       Source file.
     * @param correlationAttList List of correclationAttributeInstances, should
     *                           not be null.
     * @param dataSourceName     Name of the data source.
     * @param deviceId           Device id.
     *
     * @throws IOException
     */
    static void writeOtherOccurrencesToFileAsCSV(File destFile, AbstractFile abstractFile, Collection<CorrelationAttributeInstance> correlationAttList, String dataSourceName, String deviceId) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(destFile.toPath())) {
            //write headers 
            StringBuilder headers = new StringBuilder("\"");
            headers.append(Bundle.OtherOccurrencesPanel_csvHeader_case())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_dataSource())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_attribute())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_value())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_known())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_path())
                    .append(OtherOccurrenceNodeInstanceData.getCsvItemSeparator()).append(Bundle.OtherOccurrencesPanel_csvHeader_comment())
                    .append('"').append(System.getProperty("line.separator"));
            writer.write(headers.toString());
            //write content
            for (CorrelationAttributeInstance corAttr : correlationAttList) {
                Map<UniquePathKey, OtherOccurrenceNodeInstanceData> correlatedNodeDataMap = new HashMap<>(0);
                // get correlation and reference set instances from DB
                correlatedNodeDataMap.putAll(getCorrelatedInstances(abstractFile, deviceId, dataSourceName, corAttr));
                for (OtherOccurrenceNodeInstanceData nodeData : correlatedNodeDataMap.values()) {
                    writer.write(nodeData.toCsvString());
                }
            }
        }
    }
}
