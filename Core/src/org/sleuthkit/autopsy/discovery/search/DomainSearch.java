/*
 * Autopsy
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
package org.sleuthkit.autopsy.discovery.search;

import java.awt.Image;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Main class to perform the domain search.
 */
public class DomainSearch {

    private final DomainSearchCache searchCache;
    private final DomainSearchThumbnailCache thumbnailCache;
    private final DomainSearchArtifactsCache artifactsCache;

    /**
     * Construct a new DomainSearch object.
     */
    public DomainSearch() {
        this(new DomainSearchCache(), new DomainSearchThumbnailCache(),
                new DomainSearchArtifactsCache());
    }

    /**
     * Construct a new DomainSearch object with an existing DomainSearchCache
     * and DomainSearchThumbnailCache.
     *
     * @param cache          The DomainSearchCache to use for this DomainSearch.
     * @param thumbnailCache The DomainSearchThumnailCache to use for this
     *                       DomainSearch.
     */
    DomainSearch(DomainSearchCache cache, DomainSearchThumbnailCache thumbnailCache,
            DomainSearchArtifactsCache artifactsCache) {
        this.searchCache = cache;
        this.thumbnailCache = thumbnailCache;
        this.artifactsCache = artifactsCache;
    }

    /**
     * Run the domain search to get the group keys and sizes. Clears cache of
     * search results, caching new results for access at later time.
     *
     * @param userName            The name of the user performing the search.
     * @param filters             The filters to apply.
     * @param groupAttributeType  The attribute to use for grouping.
     * @param groupSortingType    The method to use to sort the groups.
     * @param domainSortingMethod The method to use to sort the domains within
     *                            the groups.
     * @param caseDb              The case database.
     * @param centralRepoDb       The central repository database. Can be null
     *                            if not needed.
     * @param context             The SearchContext the search is being performed from.
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters.
     *
     * @throws DiscoveryException
     * @throws SearchCancellationException - Thrown when the user has cancelled
     *                                     the search.
     */
    public Map<GroupKey, Integer> getGroupSizes(String userName,
            List<AbstractFilter> filters,
            DiscoveryAttributes.AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod domainSortingMethod,
            SleuthkitCase caseDb, CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {

        final Map<GroupKey, List<Result>> searchResults = searchCache.get(
                userName, filters, groupAttributeType, groupSortingType,
                domainSortingMethod, caseDb, centralRepoDb, context);

        // Transform the cached results into a map of group key to group size.
        final LinkedHashMap<GroupKey, Integer> groupSizes = new LinkedHashMap<>();
        for (GroupKey groupKey : searchResults.keySet()) {
            if (context.searchIsCancelled()) {
                throw new SearchCancellationException("The search was cancelled before group sizes were finished being calculated");
            }
            groupSizes.put(groupKey, searchResults.get(groupKey).size());
        }

        return groupSizes;
    }

    /**
     * Get the domains from the specified group from the cache, if the the group
     * was not cached perform a search caching the groups.
     *
     * @param userName            The name of the user performing the search.
     * @param filters             The filters to apply.
     * @param groupAttributeType  The attribute to use for grouping.
     * @param groupSortingType    The method to use to sort the groups.
     * @param domainSortingMethod The method to use to sort the Domains within
     *                            the groups.
     * @param groupKey            The key which uniquely identifies the group to
     *                            get entries from.
     * @param startingEntry       The first entry to return.
     * @param numberOfEntries     The number of entries to return.
     * @param caseDb              The case database.
     * @param centralRepoDb       The central repository database. Can be null
     *                            if not needed.
     * @param context             The search context.
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters.
     *
     * @throws DiscoveryException
     */
    public List<Result> getDomainsInGroup(String userName,
            List<AbstractFilter> filters,
            DiscoveryAttributes.AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod domainSortingMethod,
            GroupKey groupKey, int startingEntry, int numberOfEntries,
            SleuthkitCase caseDb, CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {

        final Map<GroupKey, List<Result>> searchResults = searchCache.get(
                userName, filters, groupAttributeType, groupSortingType,
                domainSortingMethod, caseDb, centralRepoDb, context);
        final List<Result> domainsInGroup = searchResults.get(groupKey);
        final List<Result> page = new ArrayList<>();
        for (int i = startingEntry; (i < startingEntry + numberOfEntries)
                && (i < domainsInGroup.size()); i++) {
            page.add(domainsInGroup.get(i));
        }

        return page;
    }

    /**
     * Get a thumbnail representation of a domain name.
     *
     * Thumbnail candidates are JPEG files that have either TSK_WEB_DOWNLOAD or
     * TSK_WEB_CACHE artifacts that match the domain name (see the DomainSearch
     * getArtifacts() API). JPEG files are sorted by most recent if sourced from
     * TSK_WEB_DOWNLOADs and by size if sourced from TSK_WEB_CACHE artifacts.
     * The first suitable thumbnail is selected.
     *
     * @param thumbnailRequest Thumbnail request for domain.
     *
     * @return A thumbnail of the first matching JPEG, or a default thumbnail if
     *         no suitable JPEG exists.
     *
     * @throws DiscoveryException If there is an error with Discovery related
     *                            processing.
     */
    public Image getThumbnail(DomainSearchThumbnailRequest thumbnailRequest) throws DiscoveryException {
        return thumbnailCache.get(thumbnailRequest);
    }

    /**
     * Get all blackboard artifacts that match the requested domain name.
     *
     * Artifacts will be selected if the requested domain name is either an
     * exact match on a TSK_DOMAIN value or a substring match on a TSK_URL
     * value. String matching is case insensitive.
     *
     * @param artifactsRequest The request containing the case, artifact type,
     *                         and domain name.
     *
     * @return A list of blackboard artifacts that match the request criteria.
     *
     * @throws DiscoveryException If an exception is encountered during
     *                            processing.
     */
    public List<BlackboardArtifact> getArtifacts(DomainSearchArtifactsRequest artifactsRequest) throws DiscoveryException {
        return artifactsCache.get(artifactsRequest);
    }

    /**
     * Get a list of MiniTimelineResults one for each date any TSK_WEB artifacts
     * existed for, which contains a list of artifacts observed on that date.
     *
     * @param sleuthkitCase The case database for the search.
     * @param domain        The domain that artifacts are being requested for.
     *
     * @return The list of MiniTimelineResults
     *
     * @throws DiscoveryException if unable to get the artifacts or the date
     *                            attributes from an artifact.
     */
    public List<MiniTimelineResult> getAllArtifactsForDomain(SleuthkitCase sleuthkitCase, String domain) throws DiscoveryException {
        List<BlackboardArtifact> artifacts = new ArrayList<>();
        Map<String, List<BlackboardArtifact>> dateMap = new HashMap<>();
        if (!StringUtils.isBlank(domain)) {
            for (BlackboardArtifact.ARTIFACT_TYPE type : SearchData.Type.DOMAIN.getArtifactTypes()) {

                artifacts.addAll(getArtifacts(new DomainSearchArtifactsRequest(sleuthkitCase, domain, type)));
            }

            for (BlackboardArtifact artifact : artifacts) {
                String date;
                try {
                    date = getDate(artifact);
                } catch (TskCoreException ex) {
                    throw new DiscoveryException("Unable to get date for artifact with ID: " + artifact.getArtifactID(), ex);
                }
                if (!StringUtils.isBlank(date)) {
                    List<BlackboardArtifact> artifactList = dateMap.get(date);
                    if (artifactList == null) {
                        artifactList = new ArrayList<>();
                    }
                    artifactList.add(artifact);
                    dateMap.put(date, artifactList);
                }
            }
        }
        List<MiniTimelineResult> dateArtifactList = new ArrayList<>();

        for (String date : dateMap.keySet()) {
            dateArtifactList.add(new MiniTimelineResult(date, dateMap.get(date)));
        }
        return dateArtifactList;
    }

    /**
     * Private helper method to get a date from the artifact.
     *
     * @param artifact The artifact to get a date from.
     *
     * @return The date as a string in the form YYYY-MM-DD.
     *
     * @throws TskCoreException when unable to get the attributes for the
     *                          artifact.
     */
    private String getDate(BlackboardArtifact artifact) throws TskCoreException {
        for (BlackboardAttribute attribute : artifact.getAttributes()) {
            if (attribute.getAttributeType().getTypeName().startsWith("TSK_DATETIME")) {
                String dateString = TimeZoneUtils.getFormattedTime(attribute.getValueLong());
                if (dateString.length() >= 10) {
                    return dateString.substring(0, 10);
                }
            }
        }
        return "";
    }

}
