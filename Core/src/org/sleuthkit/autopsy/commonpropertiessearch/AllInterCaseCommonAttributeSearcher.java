/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance.Type;
import static org.sleuthkit.autopsy.commonpropertiessearch.AbstractCommonAttributeSearcher.MEDIA_PICS_VIDEO_MIME_TYPES;

/**
 * Algorithm which finds files anywhere in the Central Repo which also occur in
 * present case.
 */
public class AllInterCaseCommonAttributeSearcher extends InterCaseCommonAttributeSearcher {

    /**
     *
     * @param filterByMediaMimeType match only on files whose mime types can be
     *                              broadly categorized as media types
     * @param filterByDocMimeType   match only on files whose mime types can be
     *                              broadly categorized as document types
     * @param corAttrType           attribute type
     * @param percentageThreshold   omit any matches with frequency above this
     *                              threshold
     *
     * @throws CentralRepoException
     */
    public AllInterCaseCommonAttributeSearcher(boolean filterByMediaMimeType, boolean filterByDocMimeType, Type corAttrType, int percentageThreshold) throws CentralRepoException {
        super(filterByMediaMimeType, filterByDocMimeType, corAttrType, percentageThreshold);
    }

    @Override
    public CommonAttributeCountSearchResults findMatchesByCount() throws TskCoreException, NoCurrentCaseException, SQLException, CentralRepoException {
        InterCaseSearchResultsProcessor eamDbAttrInst = new InterCaseSearchResultsProcessor(corAttrType);
        Set<String> mimeTypesToFilterOn = new HashSet<>();
        if (isFilterByMedia()) {
            mimeTypesToFilterOn.addAll(MEDIA_PICS_VIDEO_MIME_TYPES);
        }
        if (isFilterByDoc()) {
            mimeTypesToFilterOn.addAll(TEXT_FILES_MIME_TYPES);
        }
        Map<Integer, CommonAttributeValueList> interCaseCommonFiles = eamDbAttrInst.findInterCaseValuesByCount(Case.getCurrentCase(), mimeTypesToFilterOn);
        return new CommonAttributeCountSearchResults(interCaseCommonFiles, this.frequencyPercentageThreshold, this.corAttrType);
    }

    @Override
    public CommonAttributeCaseSearchResults findMatchesByCase() throws TskCoreException, NoCurrentCaseException, SQLException, CentralRepoException {
        InterCaseSearchResultsProcessor eamDbAttrInst = new InterCaseSearchResultsProcessor(corAttrType);
        Set<String> mimeTypesToFilterOn = new HashSet<>();
        if (isFilterByMedia()) {
            mimeTypesToFilterOn.addAll(MEDIA_PICS_VIDEO_MIME_TYPES);
        }
        if (isFilterByDoc()) {
            mimeTypesToFilterOn.addAll(TEXT_FILES_MIME_TYPES);
        }
        Map<String, Map<String, CommonAttributeValueList>> interCaseCommonFiles = eamDbAttrInst.findInterCaseValuesByCase(Case.getCurrentCase(), mimeTypesToFilterOn);
        return new CommonAttributeCaseSearchResults(interCaseCommonFiles, this.frequencyPercentageThreshold, this.corAttrType);
    }

    @NbBundle.Messages({
        "# {0} - attr type",
        "# {1} - threshold string",
        "AllInterCaseCommonAttributeSearcher.buildTabTitle.titleInterAll=Common Properties (All Central Repository Cases, {0}{1})"})
    @Override
    String getTabTitle() {
        String typeString = this.corAttrType.getDisplayName();
        if (typeString.equals("Files")) {
            typeString = this.buildCategorySelectionString();
        }
        return Bundle.AllInterCaseCommonAttributeSearcher_buildTabTitle_titleInterAll(typeString, this.getPercentThresholdString());
    }
}
