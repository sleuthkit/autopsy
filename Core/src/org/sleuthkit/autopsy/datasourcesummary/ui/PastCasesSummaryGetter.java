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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.PastCasesSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.PastCasesSummary.PastCasesResult;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Wrapper class for converting
 * org.sleuthkit.autopsy.contentutils.PastCasesSummary functionality into a
 * DefaultArtifactUpdateGovernor used by PastCases tab.
 */
public class PastCasesSummaryGetter implements DefaultArtifactUpdateGovernor {

    /**
     * @SuppressWarnings("deprecation") - we need to support already existing
     * interesting file and artifact hits.
     */
    @SuppressWarnings("deprecation")
    private static final Set<Integer> ARTIFACT_UPDATE_TYPE_IDS = new HashSet<>(Arrays.asList(
            ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
            ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID(),
            ARTIFACT_TYPE.TSK_INTERESTING_ITEM.getTypeID()
    ));

    private final PastCasesSummary pastSummary;

    public PastCasesSummaryGetter() {
        pastSummary = new PastCasesSummary();
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return Collections.unmodifiableSet(ARTIFACT_UPDATE_TYPE_IDS);
    }

    /**
     * Returns the past cases data to be shown in the past cases tab.
     *
     * @param dataSource The data source.
     *
     * @return The retrieved data or null if null dataSource.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public PastCasesResult getPastCasesData(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException {
        return pastSummary.getPastCasesData(dataSource);
    }
}
