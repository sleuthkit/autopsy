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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo.IngestJobStatusType;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Wrapper object for a DataSource and the information associated with it.
 *
 */
class DataSourceSummary {
    
    private static final Logger logger = Logger.getLogger(DataSourceSummary.class.getName());
    private static final String INGEST_JOB_STATUS_QUERY = "status_id FROM ingest_jobs WHERE obj_id=";
    private final DataSource dataSource;
    private IngestJobStatusType status = null;
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
        getStatusFromDatabase();
        type = typeValue == null ? "" : typeValue;
        filesCount = numberOfFiles == null ? 0 : numberOfFiles;
        resultsCount = numberOfResults == null ? 0 : numberOfResults;
        tagsCount = numberOfTags == null ? 0 : numberOfTags;
    }

    /**
     * Get the status of the ingest job from the case database
     */
    private void getStatusFromDatabase() {
        try {
            IngestJobQueryCallback callback = new IngestJobQueryCallback();
            Case.getCurrentCaseThrows().getSleuthkitCase().getCaseDbAccessManager().select(INGEST_JOB_STATUS_QUERY + dataSource.getId(), callback);
            status = callback.getStatus();
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting status for data source from case database", ex);
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
     * Manually set the ingest job status
     *
     * @param ingestStatus the status which the ingest job should have
     *                     currently, null to display empty string
     */
    void setIngestStatus(IngestJobStatusType ingestStatus) {
        status = ingestStatus;
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

    /**
     * Get the IngestJobStatusType associated with this data source.
     *
     * @return the IngestJobStatusType associated with this data source. Can be
     *         null if the IngestJobStatusType is not STARTED or COMPLETED.
     */
    IngestJobStatusType getIngestStatus() {
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

    /**
     * Callback to parse result set, getting the status to be associated with
     * this data source
     */
    class IngestJobQueryCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {
        
        private IngestJobStatusType jobStatus = null;
        
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
                logger.log(Level.WARNING, "Error getting status for ingest job", ex);
            }
        }

        /**
         * Get the status which was determined for this callback
         *
         * @return the status of the data source which was queried for
         */
        IngestJobStatusType getStatus() {
            return jobStatus;
        }
        
    }
}
