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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeCommonInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import static org.sleuthkit.autopsy.commonfilesearch.CommonFilesMetadataBuilder.SELECT_PREFIX;
import static org.sleuthkit.autopsy.timeline.datamodel.eventtype.ArtifactEventType.LOGGER;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides logic for selecting common files from all data sources and all cases
 * in the Central Repo.
 */
public abstract class EamDbCommonFilesAlgorithm extends CommonFilesMetadataBuilder {
    //CONSIDER: we should create an interface which specifies the findFiles feature
    //  instead of an abstract class and then have two abstract classes:
    //  inter- and intra- which implement the interface and then 4 subclasses
    //  2 for each abstract class: singlecase/allcase; singledatasource/all datasource

    private static final String WHERE_CLAUSE = "%s md5 in (select md5 from tsk_files where (known != 1 OR known IS NULL)%s GROUP BY  md5) order by md5"; //NON-NLS

    private final EamDb dbManager;

    /**
     * Implements the algorithm for getting common files across all data sources
     * and all cases. Can filter on mime types conjoined by logical AND.
     *
     * @param dataSourceIdMap a map of obj_id to datasource name
     * @param filterByMediaMimeType match only on files whose mime types can be
     * broadly categorized as media types
     * @param filterByDocMimeType match only on files whose mime types can be
     * broadly categorized as document types
     *
     * @throws EamDbException
     */
    EamDbCommonFilesAlgorithm(Map<Long, String> dataSourceIdMap, boolean filterByMediaMimeType, boolean filterByDocMimeType) throws EamDbException {
        super(dataSourceIdMap, filterByMediaMimeType, filterByDocMimeType);

        dbManager = EamDb.getInstance();
    }

    @Override
    public CommonFilesMetadata findFiles() throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException, Exception {
        return this.findFiles(null);
    }

    protected CommonFilesMetadata findFiles(CorrelationCase correlationCase) throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException, Exception {
        Map<String, Md5Metadata> currentCaseMetadata = getMetadataForCurrentCase();
        Collection<String> values = currentCaseMetadata.keySet();
        int currentCaseId;
        Map<String, Md5Metadata> interCaseCommonFiles = new HashMap<>();
        try {
            // Need to include current Cases results for specific case comparison
            currentCaseId = dbManager.getCase(Case.getCurrentCase()).getID();            
			Collection<CorrelationAttributeCommonInstance> artifactInstances;
            if(this.dbManager == null){
                artifactInstances = new ArrayList<>(0);
            } else {
                artifactInstances = dbManager.getArtifactInstancesByCaseValues(correlationCase, values, currentCaseId).stream()
                    .collect(Collectors.toList());                
            }
            
            interCaseCommonFiles = gatherIntercaseResults(artifactInstances, currentCaseMetadata);

        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting artifact instances from database.", ex); // NON-NLS
        }
        // Builds intercase-only matches metadata
        return new CommonFilesMetadata(interCaseCommonFiles);
    }

    private Map<String, Md5Metadata> getMetadataForCurrentCase() throws NoCurrentCaseException, TskCoreException, SQLException, Exception {
        //we need the list of files in the present case so we can compare against the central repo
        CommonFilesMetadata metaData = super.findFiles();
        Map<String, Md5Metadata> commonFiles = metaData.getMetadata();
        return commonFiles;
    }

    private Map<String, Md5Metadata> gatherIntercaseResults(Collection<CorrelationAttributeCommonInstance> artifactInstances, Map<String, Md5Metadata> commonFiles) {

        Map<String, Md5Metadata> interCaseCommonFiles = new HashMap<>();

        for (CorrelationAttributeCommonInstance instance : artifactInstances) {

            String md5 = instance.getValue();
            final String correlationCaseDisplayName = instance.getCorrelationCase().getDisplayName();
            String dataSource = String.format("%s: %s", correlationCaseDisplayName, instance.getCorrelationDataSource().getName());

            if (md5 == null || HashUtility.isNoDataMd5(md5)) {
                continue;
            }
            //Builds a 3rd list which contains instances which are in currentCaseMetadata map, uses current case objectId
            if (commonFiles.containsKey(md5)) {
                // we don't *have* all the information for the rows in the CR, 
                //  so we need to consult the present case via the SleuthkitCase object
                Long objectId = commonFiles.get(md5).getMetadata().iterator().next().getObjectId();
                if(interCaseCommonFiles.containsKey(md5)) {
                    //Add to intercase metaData
                    final Md5Metadata md5Metadata = interCaseCommonFiles.get(md5);
                    md5Metadata.addFileInstanceMetadata(new FileInstanceMetadata(objectId, dataSource), correlationCaseDisplayName);
                    
                } else {
                    Md5Metadata md5Metadata = new Md5Metadata(md5);
                    md5Metadata.addFileInstanceMetadata(new FileInstanceMetadata(objectId, dataSource), correlationCaseDisplayName);
                    interCaseCommonFiles.put(md5, md5Metadata);
                }
            }
        }

        //ideally we would do this step via SQL in EamDb.getArtifactInstancesByCaseValues
        removeEntriesWithinOnlyOneCase(interCaseCommonFiles);
        
        return interCaseCommonFiles;
    }

    @Override
    protected String buildSqlSelectStatement() {
        Object[] args = new String[]{SELECT_PREFIX, determineMimeTypeFilter()};
        return String.format(WHERE_CLAUSE, args);
    }

    @Override
    protected String buildTabTitle() {
        //TODO come back to this
        final String buildCategorySelectionString = this.buildCategorySelectionString();
        final String titleTemplate = Bundle.CommonFilesMetadataBuilder_buildTabTitle_titleEamDb();
        return String.format(titleTemplate, new Object[]{buildCategorySelectionString});
    }

    protected CorrelationCase getCorrelationCaseFromId(int correlationCaseId) throws EamDbException, Exception {
        for (CorrelationCase cCase : this.dbManager.getCases()) {
            if (cCase.getID() == correlationCaseId) {
                return cCase;
            }
        }
        throw new Exception("Cannot locate case.");
    }

    private void removeEntriesWithinOnlyOneCase(Map<String, Md5Metadata> interCaseCommonFiles) {
        Collection<String> toRemove = new ArrayList<>();
        
        for(Map.Entry<String, Md5Metadata> entry : interCaseCommonFiles.entrySet()){
            if(!entry.getValue().isMultiDataSource()){
                toRemove.add(entry.getKey());
            }
        }
        
        for(String bogusEntry : toRemove){
            interCaseCommonFiles.remove(bogusEntry);
        }
    }
}
