/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import static org.sleuthkit.autopsy.timeline.datamodel.eventtype.ArtifactEventType.LOGGER;
import org.sleuthkit.datamodel.HashUtility;

/**
 * Provides logic for selecting common files from all data sources and all cases
 * in the Central Repo.
 */
public abstract class InterCaseCommonAttributeSearcher extends AbstractCommonAttributeSearcher {
    //CONSIDER: we should create an interface which specifies the findFiles feature
    //  instead of an abstract class and then have two abstract classes:
    //  inter- and intra- which implement the interface and then 4 subclasses
    //  2 for each abstract class: singlecase/allcase; singledatasource/all datasource
    EamDb dbManager;
    /**
     * Implements the algorithm for getting common files across all data sources
     * and all cases. Can filter on mime types conjoined by logical AND.
     *
     * @param filterByMediaMimeType match only on files whose mime types can be
     * broadly categorized as media types
     * @param filterByDocMimeType match only on files whose mime types can be
     * broadly categorized as document types
     *
     * @throws EamDbException
     */
    InterCaseCommonAttributeSearcher(boolean filterByMediaMimeType, boolean filterByDocMimeType) throws EamDbException {
        filterByMedia = filterByMediaMimeType;
        filterByDoc = filterByDocMimeType;
        dbManager = EamDb.getInstance();
    }

    /**
     * @param artifactInstances all 'common files' in central repo
     * @param commonFiles matches must ultimately have appeared in this collection
     * @return collated map of instance counts to lists of matches
     */
    Map<Integer, List<CommonAttributeValue>> gatherIntercaseResults(Map<Integer, String> commonFiles, Map<Integer, Integer> commonFileCases) {

        Map<String, CommonAttributeValue> interCaseCommonFiles = new HashMap<>();

        for (int commonAttrId : commonFiles.keySet()) {

            String md5 = commonFiles.get(commonAttrId);
            if (md5 == null || HashUtility.isNoDataMd5(md5)) {
                continue;
            }

            try {
                int caseId = commonFileCases.get(commonAttrId);
                CorrelationCase autopsyCrCase = dbManager.getCaseById(caseId);
                final String correlationCaseDisplayName = autopsyCrCase.getDisplayName(); 
                // we don't *have* all the information for the rows in the CR,
                //  so we need to consult the present case via the SleuthkitCase object
                // Later, when the FileInstanceNodde is built. Therefore, build node generators for now.

                if(interCaseCommonFiles.containsKey(md5)) {
                    //Add to intercase metaData
                    final CommonAttributeValue md5Metadata = interCaseCommonFiles.get(md5);
                    final Iterator<FileInstanceNodeGenerator> identitcalFileInstanceMetadata = interCaseCommonFiles.get(md5).getMetadata().iterator();
                    FileInstanceNodeGenerator nodeGenerator = FileInstanceNodeGenerator.createInstance(identitcalFileInstanceMetadata, commonAttrId);
                    md5Metadata.addFileInstanceMetadata(nodeGenerator, correlationCaseDisplayName);

                } else {
                    CommonAttributeValue md5Metadata = new CommonAttributeValue(md5);
                    final Iterator<FileInstanceNodeGenerator> identitcalFileInstanceMetadata = md5Metadata.getMetadata().iterator();
                    FileInstanceNodeGenerator nodeGenerator = FileInstanceNodeGenerator.createInstance(identitcalFileInstanceMetadata, commonAttrId);
                    md5Metadata.addFileInstanceMetadata(nodeGenerator, correlationCaseDisplayName);
                    interCaseCommonFiles.put(md5, md5Metadata);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error getting artifact instances from database.", ex); // NON-NLS
            }
           
        }
        
        Map<Integer, List<CommonAttributeValue>> instanceCollatedCommonFiles = collateMatchesByNumberOfInstances(interCaseCommonFiles);        
        
        return instanceCollatedCommonFiles;
    }

    protected CorrelationCase getCorrelationCaseFromId(int correlationCaseId) throws EamDbException, Exception {
        for (CorrelationCase cCase : this.dbManager.getCases()) {
            if (cCase.getID() == correlationCaseId) {
                return cCase;
            }
        }
        throw new Exception("Cannot locate case.");
    }
}
