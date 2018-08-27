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
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance.Type;

/**
 * Algorithm which finds files anywhere in the Central Repo which also occur in
 * present case.
 */
public class AllInterCaseCommonAttributeSearcher extends InterCaseCommonAttributeSearcher {

    /**
     *
     * @param filterByMediaMimeType match only on files whose mime types can be
     * broadly categorized as media types
     * @param filterByDocMimeType match only on files whose mime types can be
     * broadly categorized as document types
     * @throws EamDbException
     */
    public AllInterCaseCommonAttributeSearcher(Map<Long, String> dataSourceIdMap, boolean filterByMediaMimeType, boolean filterByDocMimeType, Type corAttrType) throws EamDbException {
        super(dataSourceIdMap, filterByMediaMimeType, filterByDocMimeType, corAttrType, percentageThreshold);
    }

    @Override
    public CommonAttributeSearchResults findFiles() throws TskCoreException, NoCurrentCaseException, SQLException, EamDbException {
        InterCaseSearchResultsProcessor eamDbAttrInst = new InterCaseSearchResultsProcessor(this.getDataSourceIdToNameMap());
        Map<Integer, List<CommonAttributeValue>> interCaseCommonFiles = eamDbAttrInst.findInterCaseCommonAttributeValues(Case.getCurrentCase(), corAttrType);
        return new CommonAttributeSearchResults(interCaseCommonFiles, this.frequencyPercentageThreshold);
    }

    @Override
    String buildTabTitle() {
        final String buildCategorySelectionString = this.buildCategorySelectionString();
        final String titleTemplate = Bundle.AbstractCommonFilesMetadataBuilder_buildTabTitle_titleInterAll();
        return String.format(titleTemplate, new Object[]{buildCategorySelectionString});
    }
}
