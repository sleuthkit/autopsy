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
package org.sleuthkit.autopsy.casemodule.datasourcesummary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestJobInfo.IngestJobStatusType;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Wrapper object for a DataSource and the information associated with it.
 *
 */
class DataSourceSummary {

    private final DataSource dataSource;
    private String status = "";
    private final String type;
    private final long filesCount;
    private final long resultsCount;
    private final long tagsCount;

    /**
     * Construct a new DataSourceSummary object
     *
     * @param dSource         the DataSource being wrapped by this object
     * @param typeValue       the Type sting to be associated with this
     *                        DataSource
     * @param numberOfFiles   the number of files found in this DataSource
     * @param numberOfResults the number of artifacts found in this DataSource
     * @param numberOfTags    the number of tagged content objects in this
     *                        DataSource
     */
    DataSourceSummary(DataSource dSource, String typeValue, Long numberOfFiles, Long numberOfResults, Long numberOfTags) {
        dataSource = dSource;
        updateStatus();
        type = typeValue == null ? "" : typeValue;
        filesCount = numberOfFiles == null ? 0 : numberOfFiles;
        resultsCount = numberOfResults == null ? 0 : numberOfResults;
        tagsCount = numberOfTags == null ? 0 : numberOfTags;
    }

    void updateStatus() {
        try {
            IngestJobQueryCallback callback = new IngestJobQueryCallback();
            Case.getCurrentCaseThrows().getSleuthkitCase().getCaseDbAccessManager().select("status_id FROM ingest_jobs WHERE obj_id=" + dataSource.getId(), callback);
            status = callback.getStatus();
        } catch (NoCurrentCaseException | TskCoreException ex) {

        }
    }

    /**
     * Get the DataSource
     *
     * @return the dataSource
     */
    DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Get the type of this DataSource
     *
     * @return the type
     */
    String getType() {
        return type;
    }

    /**
     * Get the number of files in this DataSource
     *
     * @return the filesCount
     */
    long getFilesCount() {
        return filesCount;
    }

    /**
     * Get the number of artifacts in this DataSource
     *
     * @return the resultsCount
     */
    long getResultsCount() {
        return resultsCount;
    }

    String getIngestStatus(){
        return status;
    }
    
    /**
     * Get the number of tagged content objects in this DataSource
     *
     * @return the tagsCount
     */
    long getTagsCount() {
        return tagsCount;
    }

    class IngestJobQueryCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

        IngestJobStatusType jobStatus = null;

        @Override
        public void process(ResultSet rs) {
            try {
                while (rs.next()) {
                    IngestJobStatusType currentStatus = IngestJobStatusType.fromID(rs.getInt("status_id"));
                    if (currentStatus == IngestJobStatusType.COMPLETED) {
                        jobStatus = currentStatus;
                    } else if (currentStatus == IngestJobStatusType.STARTED) {
                        jobStatus = currentStatus;
                        return;
                    }
                }
            } catch (SQLException ex) {
                System.out.println("EEEP");
            }
        }

        String getStatus() {
            if (jobStatus == null) {
                return "";
            } else {
                return jobStatus.getDisplayName();
            }
        }
    }
}
