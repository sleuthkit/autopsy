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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Main class to perform the domain search.
 */
public class DomainSearch {

    private final static Logger logger = Logger.getLogger(DomainSearch.class.getName());
    private static final int MAXIMUM_CACHE_SIZE = 10;
    private static final Cache<DiscoveryKeyUtils.SearchKey, Map<DiscoveryKeyUtils.GroupKey, List<Result>>> searchCache = CacheBuilder.newBuilder()
            .maximumSize(MAXIMUM_CACHE_SIZE)
            .build();

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
    public static Map<DiscoveryKeyUtils.GroupKey, Integer> getGroupSizes(String userName,
            List<AbstractFilter> filters,
            DiscoveryAttributes.AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod domainSortingMethod,
            SleuthkitCase caseDb, CentralRepository centralRepoDb) throws DiscoveryException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
    public static List<Result> getDomainsInGroup(String userName,
            List<AbstractFilter> filters,
            DiscoveryAttributes.AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod domainSortingMethod,
            DiscoveryKeyUtils.GroupKey groupKey,
            int startingEntry,
            int numberOfEntries,
            SleuthkitCase caseDb, CentralRepository centralRepoDb) throws DiscoveryException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Run the Domain search. Caching new results for access at later time.
     *
     * @param userName           The name of the user performing the search.
     * @param filters            The filters to apply
     * @param groupAttributeType The attribute to use for grouping
     * @param groupSortingType   The method to use to sort the groups
     * @param domainSortingMethod  The method to use to sort the domains within
     *                           the groups
     * @param caseDb             The case database
     * @param centralRepoDb      The central repository database. Can be null if
     *                           not needed.
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws DiscoveryException
     */
    private static Map<DiscoveryKeyUtils.GroupKey, List<Result>> runDomainSearch(String userName,
            List<AbstractFilter> filters,
            DiscoveryAttributes.AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod domainSortingMethod,
            SleuthkitCase caseDb, CentralRepository centralRepoDb) throws DiscoveryException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Private constructor for DomainSearch class.
     */
    private DomainSearch() {
        // Class should not be instantiated
    }

}
