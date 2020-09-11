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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.centralrepository.ingestmodule.CentralRepoIngestModuleFactory;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo;
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
public class DataSourcePastCasesSummary {

    /**
     * Exception that is thrown in the event that a data source has not been
     * ingested with the Central Repository Ingest Module.
     */
    public static class NotCentralRepoIngestedException extends Exception {

        /**
         * Main constructor.
         *
         * @param string Error message.
         */
        public NotCentralRepoIngestedException(String string) {
            super(string);
        }
    }

    private static final String CENTRAL_REPO_INGEST_NAME = CentralRepoIngestModuleFactory.getModuleName().toUpperCase().trim();
    private static final BlackboardAttribute.Type TYPE_COMMENT = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_COMMENT);

    private static final String CASE_SEPARATOR = ",";
    private static final String PREFIX_END = ":";

    private final SleuthkitCaseProvider caseProvider;

    /**
     * Main constructor.
     */
    public DataSourcePastCasesSummary() {
        this(SleuthkitCaseProvider.DEFAULT);

    }

    /**
     * Main constructor with external dependencies specified. This constructor
     * is designed with unit testing in mind since mocked dependencies can be
     * utilized.
     *
     * @param provider The object providing the current SleuthkitCase.
     */
    public DataSourcePastCasesSummary(SleuthkitCaseProvider provider) {
        this.caseProvider = provider;
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
            return (str == null)
                    ? false
                    : CENTRAL_REPO_INGEST_NAME.equals(str.toUpperCase().trim());
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
     * Retrieves past cases associated with a specific artifact type.
     *
     * @param dataSource   The datasource.
     * @param artifactType The artifact type.
     *
     * @return A list of key value pairs mapping the case to the count of
     *         instances that case appeared for the given artifact type. The
     *         case is sorted from max to min descending.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws NotCentralRepoIngestedException
     */
    private List<Pair<String, Long>> getPastCases(DataSource dataSource, ARTIFACT_TYPE artifactType)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, NotCentralRepoIngestedException {

        throwOnNotCentralRepoIngested(dataSource);

        Collection<List<String>> cases = this.caseProvider.get().getBlackboard().getArtifacts(artifactType.getTypeID(), dataSource.getId())
                .stream()
                // convert to list of cases where there is a TSK_COMMENT from the central repo
                .flatMap((art) -> getCasesFromArtifact(art).stream())
                // group by case insensitive compare of cases
                .collect(Collectors.groupingBy((caseStr) -> caseStr.toUpperCase().trim()))
                .values();

        return cases
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
     * Returns true if the ingest job info contains an ingest module that
     * matches the Central Repo Module ingest display name.
     *
     * @param info The info.
     *
     * @return True if there is a central repo ingest match.
     */
    private boolean hasCentralRepoIngest(IngestJobInfo info) {
        if (info == null || info.getIngestModuleInfo() == null) {
            return false;
        }

        return info.getIngestModuleInfo().stream()
                .anyMatch((moduleInfo) -> {
                    return StringUtils.isNotBlank(moduleInfo.getDisplayName())
                            && moduleInfo.getDisplayName().toUpperCase().trim().equals(CENTRAL_REPO_INGEST_NAME);
                });
    }

    /**
     * Returns true if the central repository ingest module has been run on the
     * datasource.
     *
     * @param dataSource The data source.
     *
     * @return True if there is an ingest job pertaining to the data source
     *         where an ingest module matches the central repo ingest module
     *         display name.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public boolean isCentralRepoIngested(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException {
        if (dataSource == null) {
            return false;
        }

        long dataSourceId = dataSource.getId();

        return this.caseProvider.get().getIngestJobs().stream()
                .anyMatch((ingestJob) -> {
                    return ingestJob != null
                            && ingestJob.getObjectId() == dataSourceId
                            && hasCentralRepoIngest(ingestJob);
                });

    }

    /**
     * Throws an exception if the current data source has not been ingested with
     * the Central Repository Ingest Module.
     *
     * @param dataSource The data source to check if it has been ingested with
     *                   the Central Repository Ingest Module.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws NotCentralRepoIngestedException
     */
    private void throwOnNotCentralRepoIngested(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, NotCentralRepoIngestedException {

        if (!isCentralRepoIngested(dataSource)) {
            String objectId = (dataSource == null) ? "<null>" : String.valueOf(dataSource.getId());
            String message = String.format("Data source: %s has not been ingested with the Central Repository Ingest Module.", objectId);
            throw new NotCentralRepoIngestedException(message);
        }
    }

    /**
     * Get all cases that share notable files with the given data source.
     *
     * @param dataSource The data source.
     *
     * @return A list of key value pairs mapping the case to the count of
     *         instances that case appeared. The case is sorted from max to min
     *         descending.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws NotCentralRepoIngestedException
     */
    public List<Pair<String, Long>> getPastCasesWithNotableFile(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, NotCentralRepoIngestedException {
        return getPastCases(dataSource, ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
    }

    /**
     * Get all cases that share a common central repository id with the given
     * data source.
     *
     * @param dataSource The data source.
     *
     * @return A list of key value pairs mapping the case to the count of
     *         instances that case appeared. The case is sorted from max to min
     *         descending.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     * @throws NotCentralRepoIngestedException
     */
    public List<Pair<String, Long>> getPastCasesWithSameId(DataSource dataSource)
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException, NotCentralRepoIngestedException {
        return getPastCases(dataSource, ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT);
    }
}
