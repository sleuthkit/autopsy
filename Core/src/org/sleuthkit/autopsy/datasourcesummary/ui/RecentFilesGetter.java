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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import java.util.List;
import java.util.Set;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentAttachmentDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentDownloadDetails;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.RecentFilesSummary.RecentFileDetails;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Wrapper class for converting
 * org.sleuthkit.autopsy.contentutils.RecentFilesSummary functionality into a
 * DefaultArtifactUpdateGovernor used by Recent Files Data Summary tab.
 */
public class RecentFilesGetter implements DefaultArtifactUpdateGovernor {

    private static final Set<Integer> ARTIFACT_UPDATE_TYPE_IDS = new HashSet<>(Arrays.asList(
            BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID(),
            BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID(),
            BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT.getTypeID(),
            BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(),
            BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()
    ));

    private final RecentFilesSummary recentSummary;

    /**
     * Default constructor.
     */
    public RecentFilesGetter() {
        recentSummary = new RecentFilesSummary();
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return Collections.unmodifiableSet(ARTIFACT_UPDATE_TYPE_IDS);
    }

    /**
     * Return a list of the most recently opened documents based on the
     * TSK_RECENT_OBJECT artifact.
     *
     * @param dataSource The data source to query.
     * @param maxCount   The maximum number of results to return, pass 0 to get
     *                   a list of all results.
     *
     * @return A list RecentFileDetails representing the most recently opened
     *         documents or an empty list if none were found.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<RecentFileDetails> getRecentlyOpenedDocuments(DataSource dataSource, int maxCount) throws SleuthkitCaseProviderException, TskCoreException {
        return recentSummary.getRecentlyOpenedDocuments(dataSource, maxCount);
    }

    /**
     * Return a list of the most recent downloads based on the value of the the
     * artifact TSK_DATETIME_ACCESSED attribute.
     *
     * @param dataSource Data source to query.
     * @param maxCount   Maximum number of results to return, passing 0 will
     *                   return all results.
     *
     * @return A list of RecentFileDetails objects or empty list if none were
     *         found.
     *
     * @throws TskCoreException
     * @throws SleuthkitCaseProviderException
     */
    public List<RecentDownloadDetails> getRecentDownloads(DataSource dataSource, int maxCount) throws TskCoreException, SleuthkitCaseProviderException {
        return recentSummary.getRecentDownloads(dataSource, maxCount);
    }

    /**
     * Returns a list of the most recent message attachments.
     *
     * @param dataSource Data source to query.
     * @param maxCount   Maximum number of results to return, passing 0 will
     *                   return all results.
     *
     * @return A list of RecentFileDetails of the most recent attachments.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<RecentAttachmentDetails> getRecentAttachments(DataSource dataSource, int maxCount) throws SleuthkitCaseProviderException, TskCoreException {
        return recentSummary.getRecentAttachments(dataSource, maxCount);
    }
}
