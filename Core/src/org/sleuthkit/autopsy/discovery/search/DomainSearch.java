/*
 * Autopsy
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
package org.sleuthkit.autopsy.discovery.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Main class to perform the domain search.
 */
public class DomainSearch {
    
    private final DomainSearchCache searchCache;
    
    public DomainSearch() {
        this(new DomainSearchCache());
    }
    
    DomainSearch(DomainSearchCache cache) {
        this.searchCache = cache;
    }
    
    /**
     * Run the domain search to get the group keys and sizes. Clears cache of
     * search results, caching new results for access at later time.
     *
     * @param userName            The name of the user performing the search.
     * @param filters             The filters to apply
     * @param groupAttributeType  The attribute to use for grouping
     * @param groupSortingType    The method to use to sort the groups
     * @param domainSortingMethod The method to use to sort the domains within
     *                            the groups
     * @param caseDb              The case database
     * @param centralRepoDb       The central repository database. Can be null
     *                            if not needed.
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws DiscoveryException
     */
    public Map<GroupKey, Integer> getGroupSizes(String userName,
            List<AbstractFilter> filters,
            DiscoveryAttributes.AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod domainSortingMethod,
            SleuthkitCase caseDb, CentralRepository centralRepoDb) throws DiscoveryException {
        
        final Map<GroupKey, List<Result>> searchResults = searchCache.get(
                userName, filters, groupAttributeType, groupSortingType, 
                domainSortingMethod, caseDb, centralRepoDb);

        // Transform the cached results into a map of group key to group size.
        final LinkedHashMap<GroupKey, Integer> groupSizes = new LinkedHashMap<>();
        for (GroupKey groupKey : searchResults.keySet()) {
            groupSizes.put(groupKey, searchResults.get(groupKey).size());
        }

        return groupSizes;
    }

    /**
     * Get the domains from the specified group from the cache, if the the group
     * was not cached perform a search caching the groups.
     *
     * @param userName            The name of the user performing the search.
     * @param filters             The filters to apply
     * @param groupAttributeType  The attribute to use for grouping
     * @param groupSortingType    The method to use to sort the groups
     * @param domainSortingMethod The method to use to sort the Domains within
     *                            the groups
     * @param groupKey            The key which uniquely identifies the group to
     *                            get entries from
     * @param startingEntry       The first entry to return
     * @param numberOfEntries     The number of entries to return
     * @param caseDb              The case database
     * @param centralRepoDb       The central repository database. Can be null
     *                            if not needed.
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws DiscoveryException
     */
    public List<Result> getDomainsInGroup(String userName,
            List<AbstractFilter> filters,
            DiscoveryAttributes.AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod domainSortingMethod,
            GroupKey groupKey, int startingEntry, int numberOfEntries,
            SleuthkitCase caseDb, CentralRepository centralRepoDb) throws DiscoveryException {
        
        final Map<GroupKey, List<Result>> searchResults = searchCache.get(
                userName, filters, groupAttributeType, groupSortingType, 
                domainSortingMethod, caseDb, centralRepoDb);
        final List<Result> domainsInGroup = searchResults.get(groupKey);

        final List<Result> page = new ArrayList<>();
        for (int i = startingEntry; (i < startingEntry + numberOfEntries)
                && (i < domainsInGroup.size()); i++) {
            page.add(domainsInGroup.get(i));
        }

        return page;
    }
}
