/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.discovery.search.DiscoveryAttributes.AttributeType;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.SearchKey;
import org.sleuthkit.autopsy.textsummarizer.TextSummarizer;
import org.sleuthkit.autopsy.textsummarizer.TextSummary;

/**
 * Main class to perform the file search.
 */
public class FileSearch {

    private final static Logger logger = Logger.getLogger(FileSearch.class.getName());
    private static final int MAXIMUM_CACHE_SIZE = 10;
    private static final Cache<SearchKey, Map<GroupKey, List<Result>>> searchCache = CacheBuilder.newBuilder()
            .maximumSize(MAXIMUM_CACHE_SIZE)
            .build();

    /**
     * Run the file search and returns the SearchResults object for debugging.
     * Caching new results for access at later time.
     *
     * @param userName           The name of the user performing the search.
     * @param filters            The filters to apply
     * @param groupAttributeType The attribute to use for grouping
     * @param groupSortingType   The method to use to sort the groups
     * @param fileSortingMethod  The method to use to sort the files within the
     *                           groups
     * @param caseDb             The case database
     * @param centralRepoDb      The central repository database. Can be null if
     *                           not needed.
     * @param context            The SearchContext the search is being performed
     *                           from.
     *
     * @return The raw search results
     *
     * @throws DiscoveryException
     * @throws SearchCancellationException - Thrown when the user has cancelled
     *                                     the search.
     */
    static SearchResults runFileSearchDebug(String userName,
            List<AbstractFilter> filters,
            AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod fileSortingMethod,
            SleuthkitCase caseDb, CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {
        // Make a list of attributes that we want to add values for. This ensures the
        // ResultFile objects will have all needed fields set when it's time to group
        // and sort them. For example, if we're grouping by central repo frequency, we need
        // to make sure we've loaded those values before grouping.
        List<AttributeType> attributesNeededForGroupingOrSorting = new ArrayList<>();
        attributesNeededForGroupingOrSorting.add(groupAttributeType);
        attributesNeededForGroupingOrSorting.addAll(fileSortingMethod.getRequiredAttributes());

        // Run the queries for each filter
        List<Result> results = SearchFiltering.runQueries(filters, caseDb, centralRepoDb, context);

        // Add the data to resultFiles for any attributes needed for sorting and grouping
        addAttributes(attributesNeededForGroupingOrSorting, results, caseDb, centralRepoDb, context);

        // Collect everything in the search results
        SearchResults searchResults = new SearchResults(groupSortingType, groupAttributeType, fileSortingMethod);
        searchResults.add(results);

        // Sort and group the results
        searchResults.sortGroupsAndFiles();
        Map<GroupKey, List<Result>> resultHashMap = searchResults.toLinkedHashMap();
        SearchKey searchKey = new SearchKey(userName, filters, groupAttributeType, groupSortingType, fileSortingMethod);
        synchronized (searchCache) {
            searchCache.put(searchKey, resultHashMap);
        }
        return searchResults;
    }

    /**
     * Run the file search to get the group keys and sizes. Clears cache of
     * search results, caching new results for access at later time.
     *
     * @param userName           The name of the user performing the search.
     * @param filters            The filters to apply
     * @param groupAttributeType The attribute to use for grouping
     * @param groupSortingType   The method to use to sort the groups
     * @param fileSortingMethod  The method to use to sort the files within the
     *                           groups
     * @param caseDb             The case database
     * @param centralRepoDb      The central repository database. Can be null if
     *                           not needed.
     * @param context            The SearchContext the search is being performed
     *                           from.
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws DiscoveryException
     * @throws SearchCancellationException - Thrown when the user has cancelled
     *                                     the search.
     */
    public static Map<GroupKey, Integer> getGroupSizes(String userName,
            List<AbstractFilter> filters,
            AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod fileSortingMethod,
            SleuthkitCase caseDb, CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {
        Map<GroupKey, List<Result>> searchResults = runFileSearch(userName, filters,
                groupAttributeType, groupSortingType, fileSortingMethod, caseDb, centralRepoDb, context);
        LinkedHashMap<GroupKey, Integer> groupSizes = new LinkedHashMap<>();
        for (GroupKey groupKey : searchResults.keySet()) {
            if (context.searchIsCancelled()) {
                throw new SearchCancellationException("The search was cancelled before group sizes were finished being calculated");
            }
            groupSizes.put(groupKey, searchResults.get(groupKey).size());
        }
        return groupSizes;
    }

    /**
     * Get the files from the specified group from the cache, if the the group
     * was not cached perform a search caching the groups.
     *
     * @param userName           The name of the user performing the search.
     * @param filters            The filters to apply
     * @param groupAttributeType The attribute to use for grouping
     * @param groupSortingType   The method to use to sort the groups
     * @param fileSortingMethod  The method to use to sort the files within the
     *                           groups
     * @param groupKey           The key which uniquely identifies the group to
     *                           get entries from
     * @param startingEntry      The first entry to return
     * @param numberOfEntries    The number of entries to return
     * @param caseDb             The case database
     * @param centralRepoDb      The central repository database. Can be null if
     *                           not needed.
     * @param context            The SearchContext the search is being performed
     *                           from.
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws DiscoveryException
     * @throws SearchCancellationException - Thrown when the user has cancelled
     *                                     the search.
     */
    public static List<Result> getFilesInGroup(String userName,
            List<AbstractFilter> filters,
            AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod fileSortingMethod,
            GroupKey groupKey,
            int startingEntry,
            int numberOfEntries,
            SleuthkitCase caseDb, CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {
        //the group should be in the cache at this point
        List<Result> filesInGroup = null;
        SearchKey searchKey = new SearchKey(userName, filters, groupAttributeType, groupSortingType, fileSortingMethod);
        Map<GroupKey, List<Result>> resultsMap;
        synchronized (searchCache) {
            resultsMap = searchCache.getIfPresent(searchKey);
        }
        if (resultsMap != null) {
            filesInGroup = resultsMap.get(groupKey);
        }
        List<Result> page = new ArrayList<>();
        if (filesInGroup == null) {
            logger.log(Level.INFO, "Group {0} was not cached, performing search to cache all groups again", groupKey);
            runFileSearch(userName, filters, groupAttributeType, groupSortingType, fileSortingMethod, caseDb, centralRepoDb, context);
            synchronized (searchCache) {
                resultsMap = searchCache.getIfPresent(searchKey.getKeyString());
            }
            if (resultsMap != null) {
                filesInGroup = resultsMap.get(groupKey);
            }
            if (filesInGroup == null) {
                logger.log(Level.WARNING, "Group {0} did not exist in cache or new search results", groupKey);
                return page; //group does not exist
            }
        }
        // Check that there is data after the starting point
        if (filesInGroup.size() < startingEntry) {
            logger.log(Level.WARNING, "Group only contains {0} files, starting entry of {1} is too large.", new Object[]{filesInGroup.size(), startingEntry});
            return page;
        }
        // Add files to the page
        for (int i = startingEntry; (i < startingEntry + numberOfEntries)
                && (i < filesInGroup.size()); i++) {
            page.add(filesInGroup.get(i));
        }
        return page;
    }

    /**
     * Get a summary for the specified AbstractFile. If no TextSummarizers exist
     * get the beginning of the file.
     *
     * @param file The AbstractFile to summarize.
     *
     * @return The summary or beginning of the specified file as a String.
     */
    @NbBundle.Messages({"FileSearch.documentSummary.noPreview=No preview available.",
        "FileSearch.documentSummary.noBytes=No bytes read for document, unable to display preview."})
    public static TextSummary summarize(AbstractFile file) {
        TextSummary summary = null;
        TextSummarizer localSummarizer;
        synchronized (searchCache) {
            localSummarizer = SummaryHelpers.getLocalSummarizer();
        }
        if (localSummarizer != null) {
            try {
                //a summary of length 40 seems to fit without vertical scroll bars
                summary = localSummarizer.summarize(file, 40);
            } catch (IOException ex) {
                return new TextSummary(Bundle.FileSearch_documentSummary_noPreview(), null, 0);
            }
        }
        if (summary == null || StringUtils.isBlank(summary.getSummaryText())) {
            //summary text was empty grab the beginning of the file 
            summary = SummaryHelpers.getDefaultSummary(file);
        }
        return summary;
    }

    /**
     * Run the file search. Caching new results for access at later time.
     *
     * @param userName           The name of the user performing the search.
     * @param filters            The filters to apply
     * @param groupAttributeType The attribute to use for grouping
     * @param groupSortingType   The method to use to sort the groups
     * @param fileSortingMethod  The method to use to sort the files within the
     *                           groups
     * @param caseDb             The case database
     * @param centralRepoDb      The central repository database. Can be null if
     *                           not needed.
     * @param context            The SearchContext the search is being performed
     *                           from.
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws DiscoveryException
     * @throws SearchCancellationException - Thrown when the user has cancelled
     *                                     the search.
     */
    public static Map<GroupKey, List<Result>> runFileSearch(String userName,
            List<AbstractFilter> filters,
            AttributeType groupAttributeType,
            Group.GroupSortingAlgorithm groupSortingType,
            ResultsSorter.SortingMethod fileSortingMethod,
            SleuthkitCase caseDb, CentralRepository centralRepoDb, SearchContext context) throws DiscoveryException, SearchCancellationException {

        // Make a list of attributes that we want to add values for. This ensures the
        // ResultFile objects will have all needed fields set when it's time to group
        // and sort them. For example, if we're grouping by central repo frequency, we need
        // to make sure we've loaded those values before grouping.
        List<AttributeType> attributesNeededForGroupingOrSorting = new ArrayList<>();
        attributesNeededForGroupingOrSorting.add(groupAttributeType);
        attributesNeededForGroupingOrSorting.addAll(fileSortingMethod.getRequiredAttributes());

        // Run the queries for each filter
        List<Result> results = SearchFiltering.runQueries(filters, caseDb, centralRepoDb, context);

        // Add the data to resultFiles for any attributes needed for sorting and grouping
        addAttributes(attributesNeededForGroupingOrSorting, results, caseDb, centralRepoDb, context);

        // Collect everything in the search results
        SearchResults searchResults = new SearchResults(groupSortingType, groupAttributeType, fileSortingMethod);
        searchResults.add(results);
        Map<GroupKey, List<Result>> resultHashMap = searchResults.toLinkedHashMap();
        SearchKey searchKey = new SearchKey(userName, filters, groupAttributeType, groupSortingType, fileSortingMethod);
        synchronized (searchCache) {
            searchCache.put(searchKey, resultHashMap);
        }
        // Return a version of the results in general Java objects
        return resultHashMap;
    }

    /**
     * Add any attributes corresponding to the attribute list to the given
     * result files. For example, specifying the KeywordListAttribute will
     * populate the list of keyword set names in the ResultFile objects.
     *
     * @param attrs         The attributes to add to the list of result files
     * @param results       The result files
     * @param caseDb        The case database
     * @param centralRepoDb The central repository database. Can be null if not
     *                      needed.
     * @param context       The SearchContext the search is being performed
     *                      from.
     *
     * @throws DiscoveryException
     * @throws SearchCancellationException - Thrown when the user has cancelled
     *                                     the search.
     */
    private static void addAttributes(List<AttributeType> attrs, List<Result> results, SleuthkitCase caseDb, CentralRepository centralRepoDb, SearchContext context)
            throws DiscoveryException, SearchCancellationException {
        for (AttributeType attr : attrs) {
            attr.addAttributeToResults(results, caseDb, centralRepoDb, context);
        }
    }

    private FileSearch() {
        // Class should not be instantiated
    }

}
