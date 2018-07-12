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
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * 
 * 
 */
public class SingleCaseEamDbCommonFilesAlgorithm extends EamDbCommonFilesAlgorithm {
    
    private final int corrleationCaseId;
    
    /**
     * 
     * @param correlationCaseId
     * @param filterByMediaMimeType
     * @param filterByDocMimeType
     * @throws EamDbException 
     */
    public SingleCaseEamDbCommonFilesAlgorithm(int correlationCaseId, boolean filterByMediaMimeType, boolean filterByDocMimeType) throws EamDbException {
        super(filterByMediaMimeType, filterByDocMimeType);
        
        this.corrleationCaseId = correlationCaseId;
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
     * @throws Exception 
     */
    @Override
    public CommonFilesMetadata findFiles() throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException, Exception { 
        
        CorrelationCase cCase = this.getCorrelationCaseFromId(this.corrleationCaseId);
        
        return this.findFiles(cCase);
    }
    
}
