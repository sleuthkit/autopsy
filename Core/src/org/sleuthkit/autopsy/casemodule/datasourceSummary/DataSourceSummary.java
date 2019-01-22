/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.datasourceSummary;

import org.sleuthkit.datamodel.DataSource;

class DataSourceSummary {

    private final DataSource dataSource;
    private final String type;
    private final long filesCount;
    private final long resultsCount;
    private final long tagsCount;

    DataSourceSummary(DataSource dSource, String typeValue, Long numberOfFiles, Long numberOfResults, Long numberOfTags) {
        dataSource = dSource;
        type = typeValue == null ? "" : typeValue;
        filesCount = numberOfFiles == null ? 0 : numberOfFiles;
        resultsCount = numberOfResults == null ? 0 : numberOfResults;
        tagsCount = numberOfTags == null ? 0 : numberOfResults;
    }

    /**
     * @return the dataSource
     */
    DataSource getDataSource() {
        return dataSource;
    }

    /**
     * @return the type
     */
    String getType() {
        return type;
    }

    /**
     * @return the filesCount
     */
    long getFilesCount() {
        return filesCount;
    }

    /**
     * @return the resultsCount
     */
    long getResultsCount() {
        return resultsCount;
    }

    /**
     * @return the tagsCount
     */
    long getTagsCount() {
        return tagsCount;
    }
}
