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
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.AnalysisSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Wrapper class for converting
 * org.sleuthkit.autopsy.contentutils.AnalysisSummary functionality into a
 * DefaultArtifactUpdateGovernor used by data source analysis tab.
 */
public class AnalysisSummaryGetter implements DefaultArtifactUpdateGovernor {

    /**
     * @SuppressWarnings("deprecation") - we need to support already existing
     * interesting file and artifact hits.
     */
    @SuppressWarnings("deprecation")
    private static final Set<Integer> ARTIFACT_UPDATE_TYPE_IDS = new HashSet<>(Arrays.asList(
            ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
            ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID(),
            ARTIFACT_TYPE.TSK_INTERESTING_ITEM.getTypeID(),
            ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID(),
            ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()
    ));

    private final AnalysisSummary analysisSummary;

    /**
     * Main constructor.
     */
    public AnalysisSummaryGetter() {
        analysisSummary = new AnalysisSummary();
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return Collections.unmodifiableSet(ARTIFACT_UPDATE_TYPE_IDS);
    }

    /**
     * Gets counts for hashset hits.
     *
     * @param dataSource The datasource for which to identify hashset hits.
     *
     * @return The hashset set name with the number of hits in descending order.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<Pair<String, Long>> getHashsetCounts(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        return analysisSummary.getHashsetCounts(dataSource);
    }

    /**
     * Gets counts for keyword hits.
     *
     * @param dataSource The datasource for which to identify keyword hits.
     *
     * @return The keyword set name with the number of hits in descending order.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<Pair<String, Long>> getKeywordCounts(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        return analysisSummary.getKeywordCounts(dataSource);
    }

    /**
     * Gets counts for interesting item hits.
     *
     * @param dataSource The datasource for which to identify interesting item
     *                   hits.
     *
     * @return The interesting item set name with the number of hits in
     *         descending order.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<Pair<String, Long>> getInterestingItemCounts(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        return analysisSummary.getInterestingItemCounts(dataSource);
    }
}
