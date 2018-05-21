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
 * Provides logic for selecting common files from all data sources for all files to source for EamDB query.
 */
public class AllDataSourcesEamDbCommonFilesAlgorithm  extends CommonFilesMetadataBuilder {

    private static final String WHERE_CLAUSE = "%s md5 in (select md5 from tsk_files where (known != 1 OR known IS NULL)%s GROUP BY  md5) order by md5"; //NON-NLS

    private final EamDb dbManager;
    
    /**
     * Implements the algorithm for getting common files across all data
     * sources.
     *
     * @param dataSourceIdMap a map of obj_id to datasource name
     * @param filterByMediaMimeType match only on files whose mime types can be broadly categorized as media types
     * @param filterByDocMimeType match only on files whose mime types can be broadly categorized as document types
     */
    AllDataSourcesEamDbCommonFilesAlgorithm(Map<Long, String> dataSourceIdMap, boolean filterByMediaMimeType, boolean filterByDocMimeType) throws EamDbException {
        super(dataSourceIdMap, filterByMediaMimeType, filterByDocMimeType);

        dbManager = EamDb.getInstance();
    }
    
    CommonFilesMetadata findEamDbCommonFiles() throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException {
        return this.findEamDbCommonFiles(null);
    }
    
    CommonFilesMetadata findEamDbCommonFiles(int correlationCaseId) throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException, Exception { 
        
        CorrelationCase cCase = this.getCorrelationCaseFromId(correlationCaseId);
        
        return this.findEamDbCommonFiles(cCase);
    }
    
    /**
     * @param correlationCase Optionally null, otherwise a case, or could be a CR case ID
     * @return CommonFilesMetaData md5s to build Common Files search results.
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     * @throws SQLException
     * @throws EamDbException 
     */
    private CommonFilesMetadata findEamDbCommonFiles(CorrelationCase correlationCase) throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException {
        CommonFilesMetadata metaData  =  this.findCommonFiles(); 
        Map<String, Md5Metadata> commonFiles =  metaData.getMetadata();
        Collection<String> values = commonFiles.keySet();
         
        Map<String, Md5Metadata> interCaseCommonFiles =  new HashMap<>();
        try {
            
            Collection<CorrelationAttributeCommonInstance> artifactInstances = dbManager.getArtifactInstancesByCaseValues(correlationCase, values).stream()
                    .collect(Collectors.toList());    
            gatherIntercaseResults(artifactInstances, commonFiles, interCaseCommonFiles);
            
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting artifact instances from database.", ex); // NON-NLS
        } 
        // Builds intercase-only matches metadata
        return new CommonFilesMetadata(interCaseCommonFiles);
    
    }

    private void gatherIntercaseResults(Collection<CorrelationAttributeCommonInstance> artifactInstances, Map<String, Md5Metadata> commonFiles, Map<String, Md5Metadata> interCaseCommonFiles) {
        for (CorrelationAttributeCommonInstance instance : artifactInstances) {
            
            String md5 = instance.getValue();
            String dataSource = String.format("%s: %s", instance.getCorrelationCase().getDisplayName(), instance.getCorrelationDataSource().getName());
            
            if (md5 == null || HashUtility.isNoDataMd5(md5)) {
                continue;
            }
            //Builds a 3rd list which contains instances which are in commonFiles map, uses current case objectId
            if (commonFiles.containsKey(md5)) {
                // TODO sloppy, but we don't *have* all the information for the rows in the CR, so what do we do?
                Long objectId = commonFiles.get(md5).getMetadata().iterator().next().getObjectId();
                if(interCaseCommonFiles.containsKey(md5)) {
                    //Add to intercase metaData
                    final Md5Metadata md5Metadata = interCaseCommonFiles.get(md5);
                    md5Metadata.addFileInstanceMetadata(new FileInstanceMetadata(objectId, dataSource));
                    
                } else {
                    final List<FileInstanceMetadata> fileInstances = new ArrayList<>();
                    fileInstances.add(new FileInstanceMetadata(objectId, dataSource));
                    Md5Metadata md5Metadata = new Md5Metadata(md5, fileInstances);
                    interCaseCommonFiles.put(md5, md5Metadata);
                }
            } else {
                // TODO This should never happen. All current case files with potential matches are in comonFiles Map.
            }
        }
    }

    @Override
    protected String buildSqlSelectStatement() {
        Object[] args = new String[]{SELECT_PREFIX, determineMimeTypeFilter()};
        return String.format(WHERE_CLAUSE, args);
    }

    @Override
    protected String buildTabTitle() {
        final String buildCategorySelectionString = this.buildCategorySelectionString();
        final String titleTemplate = Bundle.CommonFilesMetadataBuilder_buildTabTitle_titleEamDb();
        return String.format(titleTemplate, new Object[]{buildCategorySelectionString});
    }

    private CorrelationCase getCorrelationCaseFromId(int correlationCaseId) throws EamDbException, Exception {
        //TODO is there a better way???
        for(CorrelationCase cCase : this.dbManager.getCases()){
            if(cCase.getID() == correlationCaseId){
                return cCase;
            }
        }
        throw new Exception("Cannont locate case.");
    }
}
