/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 - 2020 Basis Technology Corp.
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

import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultUpdateGovernor;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TypesSummary;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Wrapper class for converting org.sleuthkit.autopsy.contentutils.TypesSummary
 * functionality into a DefaultArtifactUpdateGovernor used by
 * DataSourceSummaryCountsPanel.
 */
public class TypesSummaryGetter implements DefaultUpdateGovernor {

    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS = new HashSet<>(
            Arrays.asList(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED));

    private final TypesSummary typesSummary;

    /**
     * Main constructor.
     */
    public TypesSummaryGetter() {
        typesSummary = new TypesSummary();
    }

    @Override
    public boolean isRefreshRequired(ModuleContentEvent evt) {
        return true;
    }

    @Override
    public boolean isRefreshRequired(AbstractFile file) {
        return true;
    }

    @Override
    public boolean isRefreshRequired(IngestManager.IngestJobEvent evt) {
        return (evt != null && INGEST_JOB_EVENTS.contains(evt));
    }

    @Override
    public Set<IngestManager.IngestJobEvent> getIngestJobEventUpdates() {
        return Collections.unmodifiableSet(INGEST_JOB_EVENTS);
    }

    /**
     * Get count of regular files (not directories) in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfFiles(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return typesSummary.getCountOfFiles(currentDataSource);
    }

    /**
     * Get count of allocated files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfAllocatedFiles(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return typesSummary.getCountOfAllocatedFiles(currentDataSource);
    }

    /**
     * Get count of unallocated files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfUnallocatedFiles(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return typesSummary.getCountOfUnallocatedFiles(currentDataSource);
    }

    /**
     * Get count of directories in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfDirectories(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return typesSummary.getCountOfDirectories(currentDataSource);
    }

    /**
     * Get count of slack files in a data source.
     *
     * @param currentDataSource The data source.
     *
     * @return The count.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfSlackFiles(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return typesSummary.getCountOfSlackFiles(currentDataSource);
    }
}
