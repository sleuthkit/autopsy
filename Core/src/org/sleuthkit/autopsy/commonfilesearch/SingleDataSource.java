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

import java.util.Map;

/**
 * Provides logic for selecting common files from a single data source.
 */
class SingleDataSource extends CommonFilesMetaDataBuilder {

    private static final String WHERE_CLAUSE = "%s md5 in (select md5 from tsk_files where md5 in (select md5 from tsk_files where (known != 1 OR known IS NULL) and data_source_obj_id=%s) GROUP BY md5 HAVING COUNT(*) > 1) order by md5";
    private final Long selectedDataSourceId;

    public SingleDataSource(Long dataSourceId, Map<Long, String> dataSourceIdMap) {
        super(dataSourceIdMap);
        this.selectedDataSourceId = dataSourceId;
    }

    @Override
    protected String buildSqlSelectStatement() {
        Object[] args = new String[]{CommonFilesMetaDataBuilder.SELECT_PREFIX, Long.toString(this.selectedDataSourceId)};
        return String.format(SingleDataSource.WHERE_CLAUSE, args);
    }
}
