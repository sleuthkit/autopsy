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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Algorithm which finds files anywhere in the Central Repo which also occur in
 * present case.
 */
public class AllCasesEamDbCommonFilesAlgorithm extends InterCaseCommonFilesMetadataBuilder {
    
    public AllCasesEamDbCommonFilesAlgorithm(boolean filterByMediaMimeType, boolean filterByDocMimeType) throws EamDbException {
        super(filterByMediaMimeType, filterByDocMimeType);
    }    
    
    @Override
    public CommonFilesMetadata findFiles() throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException, Exception {
        Map<Integer, List<Md5Metadata>> interCaseCommonFiles = new HashMap<>();

        EamDbAttributeInstancesAlgorithm eamDbAttrInst = new EamDbAttributeInstancesAlgorithm();
        eamDbAttrInst.processCorrelationCaseAttributeValues(Case.getCurrentCase());
        interCaseCommonFiles = gatherIntercaseResults(eamDbAttrInst.getIntercaseCommonValuesMap(), eamDbAttrInst.getIntercaseCommonCasesMap());

        return new CommonFilesMetadata(interCaseCommonFiles);
    }

    @Override
    String buildCategorySelectionString() {
        //TODO
        return "";
    }
}
