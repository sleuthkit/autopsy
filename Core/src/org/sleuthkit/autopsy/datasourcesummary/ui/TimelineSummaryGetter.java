/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultUpdateGovernor;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineSummary;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TimelineSummary.TimelineSummaryData;

/**
 * Provides data source summary information pertaining to Timeline data.
 */
public class TimelineSummaryGetter implements DefaultUpdateGovernor {

    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS = new HashSet<>(
            Arrays.asList(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED));

    private final TimelineSummary timelineSummary;

    /**
     * Default constructor.
     */
    public TimelineSummaryGetter() {
        timelineSummary = new TimelineSummary();
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
     * Retrieves timeline summary data.
     *
     * @param dataSource    The data source for which timeline data will be
     *                      retrieved.
     * @param recentDaysNum The maximum number of most recent days' activity to
     *                      include.
     *
     * @return The retrieved data.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public TimelineSummaryData getData(DataSource dataSource, int recentDaysNum) throws SleuthkitCaseProviderException, TskCoreException {
        return timelineSummary.getTimelineSummaryData(dataSource, recentDaysNum);
    }
}
