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

import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopAccountResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopDeviceAttachedResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopDomainsResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopProgramsResult;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.UserActivitySummary.TopWebSearchResult;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * Wrapper class for converting
 * org.sleuthkit.autopsy.contentutils.UserActivitySummary functionality into a
 * DefaultArtifactUpdateGovernor used by UserActivityPanel tab.
 */
public class UserActivitySummaryGetter implements DefaultArtifactUpdateGovernor {

    private static final Set<Integer> ARTIFACT_UPDATE_TYPE_IDS = new HashSet<>(Arrays.asList(
            ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID(),
            ARTIFACT_TYPE.TSK_MESSAGE.getTypeID(),
            ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(),
            ARTIFACT_TYPE.TSK_CALLLOG.getTypeID(),
            ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID(),
            ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID(),
            ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID()
    ));

    private final UserActivitySummary userActivity;

    public UserActivitySummaryGetter() {
        userActivity = new UserActivitySummary();
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return Collections.unmodifiableSet(ARTIFACT_UPDATE_TYPE_IDS);
    }

    /**
     * Gets a list of recent domains based on the datasource.
     *
     * @param dataSource The datasource to query for recent domains.
     * @param count      The max count of items to return.
     *
     * @return The list of items retrieved from the database.
     *
     * @throws InterruptedException
     */
    public List<TopDomainsResult> getRecentDomains(DataSource dataSource, int count) throws TskCoreException, SleuthkitCaseProviderException {
        return userActivity.getRecentDomains(dataSource, count);
    }

    /**
     * Retrieves most recent web searches by most recent date grouped by search
     * term.
     *
     * @param dataSource The data source.
     * @param count      The maximum number of records to be shown (must be >
     *                   0).
     *
     * @return The list of most recent web searches where most recent search
     *         appears first.
     *
     * @throws
     * org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<TopWebSearchResult> getMostRecentWebSearches(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        return userActivity.getMostRecentWebSearches(dataSource, count);
    }

    /**
     * Retrieves most recent devices used by most recent date attached.
     *
     * @param dataSource The data source.
     * @param count      The maximum number of records to be shown (must be >
     *                   0).
     *
     * @return The list of most recent devices attached where most recent device
     *         attached appears first.
     *
     * @throws
     * org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<TopDeviceAttachedResult> getRecentDevices(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        return userActivity.getRecentDevices(dataSource, count);
    }

    /**
     * Retrieves most recent account used by most recent date for a message
     * sent.
     *
     * @param dataSource The data source.
     * @param count      The maximum number of records to be shown (must be >
     *                   0).
     *
     * @return The list of most recent accounts used where the most recent
     *         account by last message sent occurs first.
     *
     * @throws
     * org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    @Messages({
        "DataSourceUserActivitySummary_getRecentAccounts_emailMessage=Email Message",
        "DataSourceUserActivitySummary_getRecentAccounts_calllogMessage=Call Log",})
    public List<TopAccountResult> getRecentAccounts(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        return userActivity.getRecentAccounts(dataSource, count);
    }

    /**
     * Retrieves the top programs results for the given data source limited to
     * the count provided as a parameter. The highest run times are at the top
     * of the list. If that information isn't available the last run date is
     * used. If both, the last run date and the number of run times are
     * unavailable, the programs will be sorted alphabetically, the count will
     * be ignored and all items will be returned.
     *
     * @param dataSource The datasource. If the datasource is null, an empty
     *                   list will be returned.
     * @param count      The number of results to return. This value must be > 0
     *                   or an IllegalArgumentException will be thrown.
     *
     * @return The sorted list and limited to the count if last run or run count
     *         information is available on any item.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<TopProgramsResult> getTopPrograms(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        return userActivity.getTopPrograms(dataSource, count);
    }
}
