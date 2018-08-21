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
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * 
 * 
 */
public class SingleInterCaseCommonAttributeSearcher extends InterCaseCommonAttributeSearcher {
    
    private final int corrleationCaseId;
    private String correlationCaseName;
    
    /**
     * 
     * @param correlationCaseId
     * @param filterByMediaMimeType
     * @param filterByDocMimeType
     * @throws EamDbException 
     */
    public SingleInterCaseCommonAttributeSearcher(int correlationCaseId, Map<Long, String> dataSourceIdMap, boolean filterByMediaMimeType, boolean filterByDocMimeType, int percentageThreshold) throws EamDbException {
        super(dataSourceIdMap,filterByMediaMimeType, filterByDocMimeType, percentageThreshold);
        
        this.corrleationCaseId = correlationCaseId;
        this.correlationCaseName = "";
    }
    
    /**
     * Collect metadata required to render the tree table where matches must 
     * occur in the case with the given ID.
     * 
     * @param correlationCaseId id of case where matches must occur (no other matches will be shown)
     * @return business object needed to populate tree table with results
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     * @throws SQLException
     * @throws EamDbException
     */
    @Override
    public CommonAttributeSearchResults findFiles() throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException { 
        
        CorrelationCase cCase = this.getCorrelationCaseFromId(this.corrleationCaseId);
        correlationCaseName = cCase.getDisplayName();
        return this.findFiles(cCase);
    }

    CommonAttributeSearchResults findFiles(CorrelationCase correlationCase) throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException {
        InterCaseSearchResultsProcessor eamDbAttrInst = new InterCaseSearchResultsProcessor(this.getDataSourceIdToNameMap());
        Map<Integer, List<CommonAttributeValue>> interCaseCommonFiles = eamDbAttrInst.findSingleInterCaseCommonAttributeValues(Case.getCurrentCase(), correlationCase);

        return new CommonAttributeSearchResults(interCaseCommonFiles, this.frequencyPercentageThreshold);
    }
    
    @Override
    String buildTabTitle() {
        final String buildCategorySelectionString = this.buildCategorySelectionString();
        final String titleTemplate = Bundle.AbstractCommonFilesMetadataBuilder_buildTabTitle_titleInterSingle();
        return String.format(titleTemplate, new Object[]{correlationCaseName, buildCategorySelectionString});
    }
}