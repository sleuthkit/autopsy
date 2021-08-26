/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
import org.sleuthkit.autopsy.datasourcesummary.datamodel.MimeTypeSummary;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Wrapper class for converting org.sleuthkit.autopsy.contentutils.TypesSummary
 * functionality into a DefaultArtifactUpdateGovernor used by TypesPanel tab.
 */
public class MimeTypeSummaryGetter implements DefaultUpdateGovernor {

    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS = new HashSet<>(
            Arrays.asList(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED));

    private final MimeTypeSummary mimeTypeSummary;

    /**
     * Main constructor.
     */
    public MimeTypeSummaryGetter() {
        mimeTypeSummary = new MimeTypeSummary();
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
     * Get the number of files in the case database for the current data source
     * which have the specified mimetypes.
     *
     * @param currentDataSource the data source which we are finding a file
     *                          count
     *
     * @param setOfMimeTypes    the set of mime types which we are finding the
     *                          number of occurences of
     *
     * @return a Long value which represents the number of occurrences of the
     *         specified mime types in the current case for the specified data
     *         source, null if no count was retrieved
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfFilesForMimeTypes(DataSource currentDataSource, Set<String> setOfMimeTypes)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return mimeTypeSummary.getCountOfFilesForMimeTypes(currentDataSource, setOfMimeTypes);
    }

    /**
     * Get the number of files in the case database for the current data source
     * which do not have the specified mimetypes.
     *
     * @param currentDataSource the data source which we are finding a file
     *                          count
     *
     * @param setOfMimeTypes    the set of mime types that should be excluded.
     *
     * @return a Long value which represents the number of files that do not
     *         have the specific mime type, but do have a mime type.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfFilesNotInMimeTypes(DataSource currentDataSource, Set<String> setOfMimeTypes)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return mimeTypeSummary.getCountOfFilesNotInMimeTypes(currentDataSource, setOfMimeTypes);
    }

    /**
     * Get a count of all regular files in a datasource.
     *
     * @param dataSource The datasource.
     *
     * @return The count of regular files.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfAllRegularFiles(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return mimeTypeSummary.getCountOfAllRegularFiles(dataSource);
    }

    /**
     * Gets the number of files in the data source with no assigned mime type.
     *
     * @param currentDataSource The data source.
     *
     * @return The number of files with no mime type or null if there is an
     *         issue searching the data source.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws SQLException
     */
    public Long getCountOfFilesWithNoMimeType(DataSource currentDataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, SQLException {
        return mimeTypeSummary.getCountOfFilesWithNoMimeType(currentDataSource);
    }
}
