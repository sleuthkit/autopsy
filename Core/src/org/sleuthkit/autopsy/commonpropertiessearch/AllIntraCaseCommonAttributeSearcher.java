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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.util.Map;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.TskData.FileKnown;

/**
 * Provides logic for selecting common files from all data sources.
 */
final public class AllIntraCaseCommonAttributeSearcher extends IntraCaseCommonAttributeSearcher {

    private static final String WHERE_CLAUSE = "%s md5 in (select md5 from tsk_files where (known != " + FileKnown.KNOWN.getFileKnownValue() + " OR known IS NULL)%s GROUP BY  md5 HAVING COUNT(DISTINCT data_source_obj_id) > 1) order by md5"; //NON-NLS

    /**
     * Implements the algorithm for getting common files across all data
     * sources.
     *
     * @param dataSourceIdMap       a map of obj_id to datasource name
     * @param filterByMediaMimeType match only on files whose mime types can be
     *                              broadly categorized as media types
     * @param filterByDocMimeType   match only on files whose mime types can be
     *                              broadly categorized as document types
     * @param percentageThreshold   omit any matches with frequency above this threshold
     */
    public AllIntraCaseCommonAttributeSearcher(Map<Long, String> dataSourceIdMap, boolean filterByMediaMimeType, boolean filterByDocMimeType, int percentageThreshold) {
        super(dataSourceIdMap, filterByMediaMimeType, filterByDocMimeType, percentageThreshold);

    }

    @Override
    protected String buildSqlSelectStatement() {
        Object[] args = new String[]{SELECT_PREFIX, determineMimeTypeFilter()};
        return String.format(WHERE_CLAUSE, args);
    }

    @NbBundle.Messages({
        "# {0} - build category",
        "# {1} - threshold string",
        "AllIntraCaseCommonAttributeSearcher.buildTabTitle.titleIntraAll=Common Properties (All Data Sources, {0}{1})"
    })
    @Override
    String getTabTitle() {
        return Bundle.AllIntraCaseCommonAttributeSearcher_buildTabTitle_titleIntraAll(this.buildCategorySelectionString(), this.getPercentThresholdString());
    }
}
