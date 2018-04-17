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
 * Provides logic for selecting common files from all data sources.
 */
final class AllDataSources extends CommonFilesMetaDataBuilder {

    private static final String WHERE_CLAUSE = "md5 in (select md5 from tsk_files where (known != 1 OR known IS NULL)%s GROUP BY  md5 HAVING  COUNT(*) > 1) order by md5";

    public AllDataSources(Map<Long, String> dataSourceIdMap, boolean filterByMediaMimeType, boolean filterByDocMimeType) {
        super(dataSourceIdMap, filterByMediaMimeType, filterByDocMimeType);
    }

    @Override
    protected String buildSqlSelectStatement() {
        Object[] args = new String[] {CommonFilesMetaDataBuilder.SELECT_PREFIX};
        return String.format(WHERE_CLAUSE, args);
    }
}
