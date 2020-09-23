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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.centralrepository.ingestmodule.CentralRepoIngestModuleFactory;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides information about how a datasource relates to a previous case. NOTE:
 * This code is fragile and has certain expectations about how the central
 * repository handles creating artifacts. So, if the central repository changes
 * ingest process, this code could break. This code expects that the central
 * repository ingest module:
 *
 * a) Creates a TSK_INTERESTING_FILE_HIT artifact for a file whose hash is in
 * the central repository as a notable file.
 *
 * b) Creates a TSK_INTERESTING_ARTIFACT_HIT artifact for a matching id in the
 * central repository.
 *
 * c) The created artifact will have a TSK_COMMENT attribute attached where one
 * of the sources for the attribute matches
 * CentralRepoIngestModuleFactory.getModuleName(). The module display name at
 * time of ingest will match CentralRepoIngestModuleFactory.getModuleName() as
 * well.
 *
 * d) The content of that TSK_COMMENT attribute will be of the form "Previous
 * Case: case1,case2...caseN"
 */
public class PastCasesSummary implements DefaultArtifactUpdateGovernor {

    /**
     * Return type for results items in the past cases tab.
     */
    public static class PastCasesResult {

        private final List<Pair<String, Long>> sameIdsResults;
        private final List<Pair<String, Long>> taggedNotable;

        /**
         * Main constructor.
         *
         * @param sameIdsResults Data for the cases with same id table.
         * @param taggedNotable  Data for the tagged notable table.
         */
        public PastCasesResult(List<Pair<String, Long>> sameIdsResults, List<Pair<String, Long>> taggedNotable) {
            this.sameIdsResults = sameIdsResults;
            this.taggedNotable = taggedNotable;
        }

        /**
         * @return Data for the cases with same id table.
         */
        public List<Pair<String, Long>> getSameIdsResults() {
            return sameIdsResults;
        }

        /**
         * @return Data for the tagged notable table.
         */
        public List<Pair<String, Long>> getTaggedNotable() {
            return taggedNotable;
        }
    }

    private static final Set<Integer> ARTIFACT_UPDATE_TYPE_IDS = new HashSet<>(Arrays.asList(
            ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
            ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()
    ));

    private static final String CENTRAL_REPO_INGEST_NAME = CentralRepoIngestModuleFactory.getModuleName().toUpperCase().trim();
    private static final BlackboardAttribute.Type TYPE_COMMENT = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_COMMENT);
    private static final BlackboardAttribute.Type TYPE_ASSOCIATED_ARTIFACT = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT);

    private static final Set<Integer> CR_DEVICE_TYPE_IDS = new HashSet<>(Arrays.asList(
            ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID(),
            ARTIFACT_TYPE.TSK_DEVICE_INFO.getTypeID(),
            ARTIFACT_TYPE.TSK_SIM_ATTACHED.getTypeID(),
            ARTIFACT_TYPE.TSK_WIFI_NETWORK_ADAPTER.getTypeID()
    ));

    private static final String CASE_SEPARATOR = ",";
    private static final String PREFIX_END = ":";

    private final SleuthkitCaseProvider caseProvider;
    private final java.util.logging.Logger logger;

    /**
     * Main constructor.
     */
    public PastCasesSummary() {
        this(
                SleuthkitCaseProvider.DEFAULT,
                org.sleuthkit.autopsy.coreutils.Logger.getLogger(PastCasesSummary.class.getName())
        );

    }

    /**
     * Main constructor with external dependencies specified. This constructor
     * is designed with unit testing in mind since mocked dependencies can be
     * utilized.
     *
     * @param provider The object providing the current SleuthkitCase.
     * @param logger   The logger to use.
     */
    public PastCasesSummary(
            SleuthkitCaseProvider provider,
            java.util.logging.Logger logger) {

        this.caseProvider = provider;
        this.logger = logger;
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return ARTIFACT_UPDATE_TYPE_IDS;
    }

    /**
     * Given the provided sources for an attribute, aims to determine if one of
     * those sources is the Central Repository Ingest Module.
     *
     * @param sources The list of sources found on an attribute.
     *
     * @return Whether or not this attribute (and subsequently the parent
     *         artifact) is created by the Central Repository Ingest Module.
     */
    private static boolean isCentralRepoGenerated(List<String> sources) {
        if (sources == null) {
            return false;
        }

        return sources.stream().anyMatch((str) -> {
            return str != null && CENTRAL_REPO_INGEST_NAME.equalsIgnoreCase(str.trim());
        });
    }

    /**
     * Gets a list of cases from the TSK_COMMENT of an artifact. The cases
     * string is expected to be of a form of "Previous Case:
     * case1,case2...caseN".
     *
     * @param artifact The artifact.
     *
     * @return The list of cases if found or empty list if not.
     */
    private static List<String> getCasesFromArtifact(BlackboardArtifact artifact) {
        if (artifact == null) {
            return Collections.emptyList();
        }

        BlackboardAttribute commentAttr = null;
        try {
            commentAttr = artifact.getAttribute(TYPE_COMMENT);
        } catch (TskCoreException ignored) {
            // ignore if no attribute can be found
        }

        if (commentAttr == null) {
            return Collections.emptyList();
        }

        if (!isCentralRepoGenerated(commentAttr.getSources())) {
            return Collections.emptyList();
        }

        String commentStr = commentAttr.getValueString();

        int prefixCharIdx = commentStr.indexOf(PREFIX_END);
        if (prefixCharIdx < 0 || prefixCharIdx >= commentStr.length() - 1) {
            return Collections.emptyList();
        }

        String justCasesStr = commentStr.substring(prefixCharIdx + 1).trim();
        return Stream.of(justCasesStr.split(CASE_SEPARATOR))
                .map(String::trim)
                .collect(Collectors.toList());

    }

    /**
     * Given a stream of case ids, groups the strings in a case-insensitive
     * manner, and then provides a list of cases and the occurrence count sorted
     * from max to min.
     *
     * @param cases A stream of cases.
     *
     * @return The list of unique cases and their occurrences sorted from max to
     *         min.
     */
    private List<Pair<String, Long>> getCaseCounts(Stream<String> cases) {
        Collection<List<String>> groupedCases = cases
                // group by case insensitive compare of cases
                .collect(Collectors.groupingBy((caseStr) -> caseStr.toUpperCase().trim()))
                .values();

        return groupedCases
                .stream()
                // get any cases where an actual case is found
                .filter((lst) -> lst != null && lst.size() > 0)
                // get non-normalized (i.e. not all caps) case name and number of items found
                .map((lst) -> Pair.of(lst.get(0), (long) lst.size()))
                // sorted descending
                .sorted((a, b) -> -Long.compare(a.getValue(), b.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Given an artifact with a TYPE_ASSOCIATED_ARTIFACT attribute, retrieves
     * the related artifact.
     *
     * @param artifact The artifact with the TYPE_ASSOCIATED_ARTIFACT attribute.
     *
     * @return The artifact if found or null if not.
     *
     * @throws SleuthkitCaseProviderException
     */
    private BlackboardArtifact getParentArtifact(BlackboardArtifact artifact) throws SleuthkitCaseProviderException {
        Long parentId = DataSourceInfoUtilities.getLongOrNull(artifact, TYPE_ASSOCIATED_ARTIFACT);
        if (parentId == null) {
            return null;
        }

        SleuthkitCase skCase = caseProvider.get();
        try {
            return skCase.getArtifactByArtifactId(parentId);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING,
                    String.format("There was an error fetching the parent artifact of a TSK_INTERESTING_ARTIFACT_HIT (parent id: %d)", parentId),
                    ex);
            return null;
        }
    }

    /**
     * Returns true if the artifact has an associated artifact of a device type.
     *
     * @param artifact The artifact.
     *
     * @return True if there is a device associated artifact.
     *
     * @throws SleuthkitCaseProviderException
     */
    private boolean hasDeviceAssociatedArtifact(BlackboardArtifact artifact) throws SleuthkitCaseProviderException {
        BlackboardArtifact parent = getParentArtifact(artifact);
        if (parent == null) {
            return false;
        }

        return CR_DEVICE_TYPE_IDS.contains(parent.getArtifactTypeID());
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

        if (dataSource == null) {
            return null;
        }

        SleuthkitCase skCase = caseProvider.get();

        List<String> deviceArtifactCases = new ArrayList<>();
        List<String> nonDeviceArtifactCases = new ArrayList<>();

        for (BlackboardArtifact artifact : skCase.getBlackboard().getArtifacts(ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID(), dataSource.getId())) {
            List<String> cases = getCasesFromArtifact(artifact);
            if (cases == null || cases.isEmpty()) {
                continue;
            }

            if (hasDeviceAssociatedArtifact(artifact)) {
                deviceArtifactCases.addAll(cases);
            } else {
                nonDeviceArtifactCases.addAll(cases);
            }
        }

        Stream<String> filesCases = skCase.getBlackboard().getArtifacts(ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(), dataSource.getId()).stream()
                .flatMap((art) -> getCasesFromArtifact(art).stream());

        return new PastCasesResult(
                getCaseCounts(deviceArtifactCases.stream()),
                getCaseCounts(Stream.concat(filesCases, nonDeviceArtifactCases.stream()))
        );
    }
}
