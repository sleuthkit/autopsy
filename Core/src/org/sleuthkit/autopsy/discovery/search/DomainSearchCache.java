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
package org.sleuthkit.autopsy.discovery.search;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.SearchKey;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Caches results for domain searches initiated by the user in the Discovery
 * panel. Uses a Guava Cache as a backing data structure. See
 * DomainSearchCacheLoader for database querying in the event of a cache miss.
 */
class DomainSearchCache {

    private static final int MAXIMUM_CACHE_SIZE = 10;
    private static final LoadingCache<SearchKey, Map<GroupKey, List<Result>>> cache
            = CacheBuilder.newBuilder()
                    .maximumSize(MAXIMUM_CACHE_SIZE)
                    .build(new DomainSearchCacheLoader());

    /**
     * Get domain search results matching the given parameters. If no results
     * are found, the cache will automatically load them.
     *
     *
     * @param userName           The name of the user performing the search.
     * @param filters            The filters to apply.
     * @param groupAttributeType The attribute to use for grouping.
     * @param groupSortingType   The method to use to sort the groups.
     * @param fileSortingMethod  The method to use to sort the domains within
     *                           the groups.
     * @param caseDb             The case database.
     * @param centralRepoDb      The central repository database. Can be null if
     *                           not needed.
     * @param context            The SearchContext the search is being performed
     *                           from.
     *
     * @return Domain search results matching the given parameters.
     *
     * @throws DiscoveryException
     * @throws SearchCancellationException - Thrown when the user has cancelled
     *                                     the search.
     */
    Map<GroupKey, List<Result>> get(String userName,
            List<AbstractFilter> filters,
            DiscoveryAttributes.AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod domainSortingMethod,
            SleuthkitCase caseDb, CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {
        try {
            final SearchKey searchKey = new SearchKey(userName, filters, groupAttributeType,
                    groupSortingType, domainSortingMethod, caseDb, centralRepoDb, context);
            return cache.get(searchKey);
        } catch (ExecutionException ex) {
            throw new DiscoveryException("Error fetching results from cache", ex.getCause());
        }
    }
}
