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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Helper class for getting hash set hits, keyword hits, and interesting item
 * hits within a datasource.
 */
public class AnalysisSummary {

    private static final BlackboardAttribute.Type TYPE_SET_NAME = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SET_NAME);
    private static final Set<String> EXCLUDED_KEYWORD_SEARCH_ITEMS = new HashSet<>();

    private AnalysisSummary() {
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
    public static List<Pair<String, Long>> getHashsetCounts(DataSource dataSource) throws NoCurrentCaseException, TskCoreException {
        return getCountsData(dataSource, TYPE_SET_NAME, ARTIFACT_TYPE.TSK_HASHSET_HIT);
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
    public static List<Pair<String, Long>> getKeywordCounts(DataSource dataSource) throws NoCurrentCaseException, TskCoreException {
        return getCountsData(dataSource, TYPE_SET_NAME, ARTIFACT_TYPE.TSK_KEYWORD_HIT).stream()
                // make sure we have a valid set and that that set does not belong to the set of excluded items
                .filter((pair) -> pair != null && pair.getKey() != null && !EXCLUDED_KEYWORD_SEARCH_ITEMS.contains(pair.getKey().toUpperCase().trim()))
                .collect(Collectors.toList());
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
    public static List<Pair<String, Long>> getInterestingItemCounts(DataSource dataSource) throws NoCurrentCaseException, TskCoreException {
        return getCountsData(dataSource, TYPE_SET_NAME, ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT, ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT);
    }

    /**
     * Get counts for the artifact of the specified type.
     *
     * @param dataSource    The datasource.
     * @param keyType       The attribute to use as the key type.
     * @param artifactTypes The types of artifacts for which to query.
     *
     * @return A list of key value pairs where the key is the attribute type
     *         value and the value is the count of items found. This list is
     *         sorted by the count descending max to min.
     *
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     */
    private static List<Pair<String, Long>> getCountsData(DataSource dataSource, BlackboardAttribute.Type keyType, ARTIFACT_TYPE... artifactTypes)
            throws NoCurrentCaseException, TskCoreException {

        if (dataSource == null) {
            return Collections.emptyList();
        }

        List<BlackboardArtifact> artifacts = new ArrayList<>();

        // get all artifacts in one list for each artifact type
        for (ARTIFACT_TYPE type : artifactTypes) {
            artifacts.addAll(Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard().getArtifacts(type.getTypeID(), dataSource.getId()));
        }

        // group those based on the value of the attribute type that should serve as a key
        Map<String, Long> countedKeys = artifacts.stream()
                .map((art) -> {
                    String key = DataSourceInfoUtilities.getStringOrNull(art, keyType);
                    return (StringUtils.isBlank(key)) ? null : key;
                })
                .filter((key) -> key != null)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // sort from max to min counts
        return countedKeys.entrySet().stream()
                .map((e) -> Pair.of(e.getKey(), e.getValue()))
                .sorted((a, b) -> -a.getValue().compareTo(b.getValue()))
                .collect(Collectors.toList());
    }
}
