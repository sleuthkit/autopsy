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
 * Provides logic for selecting common files from a single data source.
 */
final public class SingleIntraCaseCommonAttributeSearcher extends IntraCaseCommonAttributeSearcher {

    private static final String WHERE_CLAUSE = "%s md5 in (select md5 from tsk_files where md5 in (select md5 from tsk_files where (known != " + FileKnown.KNOWN.getFileKnownValue() + " OR known IS NULL) and data_source_obj_id=%s%s) GROUP BY md5 HAVING COUNT(DISTINCT data_source_obj_id) > 1) order by md5"; //NON-NLS
    private final Long selectedDataSourceId;
    private final String dataSourceName;

    /**
     * Implements the algorithm for getting common files that appear at least
     * once in the given data source
     *
     * @param dataSourceId          data source id for which common files must
     *                              appear at least once
     * @param dataSourceIdMap       a map of obj_id to datasource name
     * @param filterByMediaMimeType match only on files whose mime types can be
     *                              broadly categorized as media types
     * @param filterByDocMimeType   match only on files whose mime types can be
     *                              broadly categorized as document types
     * @param percentageThreshold   omit any matches with frequency above this threshold
     */
    public SingleIntraCaseCommonAttributeSearcher(Long dataSourceId, Map<Long, String> dataSourceIdMap, boolean filterByMediaMimeType, boolean filterByDocMimeType, int percentageThreshold) {
        super(dataSourceIdMap, filterByMediaMimeType, filterByDocMimeType, percentageThreshold);
        this.selectedDataSourceId = dataSourceId;
        this.dataSourceName = dataSourceIdMap.get(this.selectedDataSourceId);
    }

    @Override
    protected String buildSqlSelectStatement() {
        Object[] args = new String[]{SELECT_PREFIX, Long.toString(this.selectedDataSourceId), determineMimeTypeFilter()};
        return String.format(SingleIntraCaseCommonAttributeSearcher.WHERE_CLAUSE, args);
    }

    @NbBundle.Messages({
        "# {0} - data source name",
        "# {1} - build category",
        "# {2} - threshold string",
        "SingleIntraCaseCommonAttributeSearcher.buildTabTitle.titleIntraSingle=Common Properties (Data Source: {0}, {1}{2})"})
    @Override
    String getTabTitle() {
        return Bundle.SingleIntraCaseCommonAttributeSearcher_buildTabTitle_titleIntraSingle(this.dataSourceName, this.buildCategorySelectionString(), this.getPercentThresholdString());
    }
}
