/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.InstanceTableCallback;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.discovery.search.FileSearchData.Frequency;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
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
    private static final Cache<SearchKey, Map<GroupKey, List<ResultFile>>> searchCache = CacheBuilder.newBuilder()
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
     *
     * @return The raw search results
     *
     * @throws FileSearchException
     */
    static SearchResults runFileSearchDebug(String userName,
            List<AbstractFilter> filters,
            AttributeType groupAttributeType,
            FileGroup.GroupSortingAlgorithm groupSortingType,
            FileSorter.SortingMethod fileSortingMethod,
            SleuthkitCase caseDb, CentralRepository centralRepoDb) throws FileSearchException {
        // Make a list of attributes that we want to add values for. This ensures the
        // ResultFile objects will have all needed fields set when it's time to group
        // and sort them. For example, if we're grouping by central repo frequency, we need
        // to make sure we've loaded those values before grouping.
        List<AttributeType> attributesNeededForGroupingOrSorting = new ArrayList<>();
        attributesNeededForGroupingOrSorting.add(groupAttributeType);
        attributesNeededForGroupingOrSorting.addAll(fileSortingMethod.getRequiredAttributes());

        // Run the queries for each filter
        List<ResultFile> resultFiles = SearchFiltering.runQueries(filters, caseDb, centralRepoDb);

        // Add the data to resultFiles for any attributes needed for sorting and grouping
        addAttributes(attributesNeededForGroupingOrSorting, resultFiles, caseDb, centralRepoDb);

        // Collect everything in the search results
        SearchResults searchResults = new SearchResults(groupSortingType, groupAttributeType, fileSortingMethod);
        searchResults.add(resultFiles);

        // Sort and group the results
        searchResults.sortGroupsAndFiles();
        Map<GroupKey, List<ResultFile>> resultHashMap = searchResults.toLinkedHashMap();
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
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws FileSearchException
     */
    public static Map<GroupKey, Integer> getGroupSizes(String userName,
            List<AbstractFilter> filters,
            AttributeType groupAttributeType,
            FileGroup.GroupSortingAlgorithm groupSortingType,
            FileSorter.SortingMethod fileSortingMethod,
            SleuthkitCase caseDb, CentralRepository centralRepoDb) throws FileSearchException {
        Map<GroupKey, List<ResultFile>> searchResults = runFileSearch(userName, filters,
                groupAttributeType, groupSortingType, fileSortingMethod, caseDb, centralRepoDb);
        LinkedHashMap<GroupKey, Integer> groupSizes = new LinkedHashMap<>();
        for (GroupKey groupKey : searchResults.keySet()) {
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
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws FileSearchException
     */
    public static List<ResultFile> getFilesInGroup(String userName,
            List<AbstractFilter> filters,
            AttributeType groupAttributeType,
            FileGroup.GroupSortingAlgorithm groupSortingType,
            FileSorter.SortingMethod fileSortingMethod,
            GroupKey groupKey,
            int startingEntry,
            int numberOfEntries,
            SleuthkitCase caseDb, CentralRepository centralRepoDb) throws FileSearchException {
        //the group should be in the cache at this point
        List<ResultFile> filesInGroup = null;
        SearchKey searchKey = new SearchKey(userName, filters, groupAttributeType, groupSortingType, fileSortingMethod);
        Map<GroupKey, List<ResultFile>> resultsMap;
        synchronized (searchCache) {
            resultsMap = searchCache.getIfPresent(searchKey);
        }
        if (resultsMap != null) {
            filesInGroup = resultsMap.get(groupKey);
        }
        List<ResultFile> page = new ArrayList<>();
        if (filesInGroup == null) {
            logger.log(Level.INFO, "Group {0} was not cached, performing search to cache all groups again", groupKey);
            runFileSearch(userName, filters, groupAttributeType, groupSortingType, fileSortingMethod, caseDb, centralRepoDb);
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
     *
     * @return A LinkedHashMap grouped and sorted according to the parameters
     *
     * @throws FileSearchException
     */
    private static Map<GroupKey, List<ResultFile>> runFileSearch(String userName,
            List<AbstractFilter> filters,
            AttributeType groupAttributeType,
            FileGroup.GroupSortingAlgorithm groupSortingType,
            FileSorter.SortingMethod fileSortingMethod,
            SleuthkitCase caseDb, CentralRepository centralRepoDb) throws FileSearchException {

        // Make a list of attributes that we want to add values for. This ensures the
        // ResultFile objects will have all needed fields set when it's time to group
        // and sort them. For example, if we're grouping by central repo frequency, we need
        // to make sure we've loaded those values before grouping.
        List<AttributeType> attributesNeededForGroupingOrSorting = new ArrayList<>();
        attributesNeededForGroupingOrSorting.add(groupAttributeType);
        attributesNeededForGroupingOrSorting.addAll(fileSortingMethod.getRequiredAttributes());

        // Run the queries for each filter
        List<ResultFile> resultFiles = SearchFiltering.runQueries(filters, caseDb, centralRepoDb);

        // Add the data to resultFiles for any attributes needed for sorting and grouping
        addAttributes(attributesNeededForGroupingOrSorting, resultFiles, caseDb, centralRepoDb);

        // Collect everything in the search results
        SearchResults searchResults = new SearchResults(groupSortingType, groupAttributeType, fileSortingMethod);
        searchResults.add(resultFiles);
        Map<GroupKey, List<ResultFile>> resultHashMap = searchResults.toLinkedHashMap();
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
     * @param resultFiles   The result files
     * @param caseDb        The case database
     * @param centralRepoDb The central repository database. Can be null if not
     *                      needed.
     *
     * @throws FileSearchException
     */
    private static void addAttributes(List<AttributeType> attrs, List<ResultFile> resultFiles, SleuthkitCase caseDb, CentralRepository centralRepoDb)
            throws FileSearchException {
        for (AttributeType attr : attrs) {
            attr.addAttributeToResultFiles(resultFiles, caseDb, centralRepoDb);
        }
    }

    /**
     * Computes the CR frequency of all the given hashes and updates the list of
     * files.
     *
     * @param hashesToLookUp Hashes to find the frequency of
     * @param currentFiles   List of files to update with frequencies
     */
    private static void computeFrequency(Set<String> hashesToLookUp, List<ResultFile> currentFiles, CentralRepository centralRepoDb) {

        if (hashesToLookUp.isEmpty()) {
            return;
        }

        String hashes = String.join("','", hashesToLookUp);
        hashes = "'" + hashes + "'";
        try {
            CorrelationAttributeInstance.Type attributeType = centralRepoDb.getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);
            String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(attributeType);

            String selectClause = " value, COUNT(value) FROM "
                    + "(SELECT DISTINCT case_id, value FROM " + tableName
                    + " WHERE value IN ("
                    + hashes
                    + ")) AS foo GROUP BY value";

            FrequencyCallback callback = new FrequencyCallback(currentFiles);
            centralRepoDb.processSelectClause(selectClause, callback);

        } catch (CentralRepoException ex) {
            logger.log(Level.WARNING, "Error getting frequency counts from Central Repository", ex); // NON-NLS
        }

    }

    private static String createSetNameClause(List<ResultFile> files,
            int artifactTypeID, int setNameAttrID) throws FileSearchException {

        // Concatenate the object IDs in the list of files
        String objIdList = ""; // NON-NLS
        for (ResultFile file : files) {
            if (!objIdList.isEmpty()) {
                objIdList += ","; // NON-NLS
            }
            objIdList += "\'" + file.getFirstInstance().getId() + "\'"; // NON-NLS
        }

        // Get pairs of (object ID, set name) for all files in the list of files that have
        // the given artifact type.
        return "blackboard_artifacts.obj_id AS object_id, blackboard_attributes.value_text AS set_name "
                + "FROM blackboard_artifacts "
                + "INNER JOIN blackboard_attributes ON blackboard_artifacts.artifact_id=blackboard_attributes.artifact_id "
                + "WHERE blackboard_attributes.artifact_type_id=\'" + artifactTypeID + "\' "
                + "AND blackboard_attributes.attribute_type_id=\'" + setNameAttrID + "\' "
                + "AND blackboard_artifacts.obj_id IN (" + objIdList + ") "; // NON-NLS
    }

    private FileSearch() {
        // Class should not be instantiated
    }

    /**
     * Base class for the grouping attributes.
     */
    public abstract static class AttributeType {

        /**
         * For a given file, return the key for the group it belongs to for this
         * attribute type.
         *
         * @param file the result file to be grouped
         *
         * @return the key for the group this file goes in
         */
        public abstract GroupKey getGroupKey(ResultFile file);

        /**
         * Add any extra data to the ResultFile object from this attribute.
         *
         * @param files         The list of files to enhance
         * @param caseDb        The case database
         * @param centralRepoDb The central repository database. Can be null if
         *                      not needed.
         *
         * @throws FileSearchException
         */
        public void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb, CentralRepository centralRepoDb) throws FileSearchException {
            // Default is to do nothing
        }
    }

    /**
     * Attribute for grouping/sorting by file size
     */
    public static class FileSizeAttribute extends AttributeType {

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new DiscoveryKeyUtils.FileSizeGroupKey(file);
        }
    }

    /**
     * Attribute for grouping/sorting by parent path
     */
    public static class ParentPathAttribute extends AttributeType {

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new DiscoveryKeyUtils.ParentPathGroupKey(file);
        }
    }

    /**
     * Default attribute used to make one group
     */
    static class NoGroupingAttribute extends FileSearch.AttributeType {

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new DiscoveryKeyUtils.NoGroupingGroupKey();
        }
    }

    /**
     * Attribute for grouping/sorting by data source
     */
    static class DataSourceAttribute extends AttributeType {

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new DiscoveryKeyUtils.DataSourceGroupKey(file);
        }
    }

    /**
     * Attribute for grouping/sorting by file type
     */
    static class FileTypeAttribute extends AttributeType {

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new DiscoveryKeyUtils.FileTypeGroupKey(file);
        }
    }

    /**
     * Attribute for grouping/sorting by keyword lists
     */
    static class KeywordListAttribute extends AttributeType {

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new DiscoveryKeyUtils.KeywordListGroupKey(file);
        }

        @Override
        public void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws FileSearchException {

            // Get pairs of (object ID, keyword list name) for all files in the list of files that have
            // keyword list hits.
            String selectQuery = createSetNameClause(files, BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());

            SetKeywordListNamesCallback callback = new SetKeywordListNamesCallback(files);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new FileSearchException("Error looking up keyword list attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the keyword list names to the list of ResultFile
         * objects.
         */
        private static class SetKeywordListNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<ResultFile> resultFiles;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add keyword list names to
             */
            SetKeywordListNamesCallback(List<ResultFile> resultFiles) {
                this.resultFiles = resultFiles;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (ResultFile file : resultFiles) {
                        tempMap.put(file.getFirstInstance().getId(), file);
                    }

                    while (rs.next()) {
                        try {
                            Long objId = rs.getLong("object_id"); // NON-NLS
                            String keywordListName = rs.getString("set_name"); // NON-NLS

                            tempMap.get(objId).addKeywordListName(keywordListName);

                        } catch (SQLException ex) {
                            logger.log(Level.SEVERE, "Unable to get object_id or set_name from result set", ex); // NON-NLS
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to get keyword list names", ex); // NON-NLS
                }
            }
        }
    }

    /**
     * Attribute for grouping/sorting by frequency in the central repository
     */
    static class FrequencyAttribute extends AttributeType {

        static final int BATCH_SIZE = 50; // Number of hashes to look up at one time

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new DiscoveryKeyUtils.FrequencyGroupKey(file);
        }

        @Override
        public void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws FileSearchException {
            if (centralRepoDb == null) {
                for (ResultFile file : files) {
                    if (file.getFrequency() == Frequency.UNKNOWN && file.getFirstInstance().getKnown() == TskData.FileKnown.KNOWN) {
                        file.setFrequency(Frequency.KNOWN);
                    }
                }
            } else {
                processResultFilesForCR(files, centralRepoDb);
            }
        }

        /**
         * Private helper method for adding Frequency attribute when CR is
         * enabled.
         *
         * @param files         The list of ResultFiles to caluclate frequency
         *                      for.
         * @param centralRepoDb The central repository currently in use.
         */
        private void processResultFilesForCR(List<ResultFile> files,
                CentralRepository centralRepoDb) {
            List<ResultFile> currentFiles = new ArrayList<>();
            Set<String> hashesToLookUp = new HashSet<>();
            for (ResultFile file : files) {
                if (file.getFirstInstance().getKnown() == TskData.FileKnown.KNOWN) {
                    file.setFrequency(Frequency.KNOWN);
                }
                if (file.getFrequency() == Frequency.UNKNOWN
                        && file.getFirstInstance().getMd5Hash() != null
                        && !file.getFirstInstance().getMd5Hash().isEmpty()) {
                    hashesToLookUp.add(file.getFirstInstance().getMd5Hash());
                    currentFiles.add(file);
                }
                if (hashesToLookUp.size() >= BATCH_SIZE) {
                    computeFrequency(hashesToLookUp, currentFiles, centralRepoDb);

                    hashesToLookUp.clear();
                    currentFiles.clear();
                }
            }
            computeFrequency(hashesToLookUp, currentFiles, centralRepoDb);
        }
    }

    /**
     * Callback to use with findInterCaseValuesByCount which generates a list of
     * values for common property search
     */
    private static class FrequencyCallback implements InstanceTableCallback {

        private final List<ResultFile> files;

        private FrequencyCallback(List<ResultFile> files) {
            this.files = new ArrayList<>(files);
        }

        @Override
        public void process(ResultSet resultSet) {
            try {

                while (resultSet.next()) {
                    String hash = resultSet.getString(1);
                    int count = resultSet.getInt(2);
                    for (Iterator<ResultFile> iterator = files.iterator(); iterator.hasNext();) {
                        ResultFile file = iterator.next();
                        if (file.getFirstInstance().getMd5Hash().equalsIgnoreCase(hash)) {
                            file.setFrequency(Frequency.fromCount(count));
                            iterator.remove();
                        }
                    }
                }

                // The files left had no matching entries in the CR, so mark them as unique
                for (ResultFile file : files) {
                    file.setFrequency(Frequency.UNIQUE);
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Error getting frequency counts from Central Repository", ex); // NON-NLS
            }
        }
    }

    /**
     * Attribute for grouping/sorting by hash set lists
     */
    static class HashHitsAttribute extends AttributeType {

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new DiscoveryKeyUtils.HashHitsGroupKey(file);
        }

        @Override
        public void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws FileSearchException {

            // Get pairs of (object ID, hash set name) for all files in the list of files that have
            // hash set hits.
            String selectQuery = createSetNameClause(files, BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());

            HashSetNamesCallback callback = new HashSetNamesCallback(files);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new FileSearchException("Error looking up hash set attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the hash set names to the list of ResultFile objects.
         */
        private static class HashSetNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<ResultFile> resultFiles;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add hash set names to
             */
            HashSetNamesCallback(List<ResultFile> resultFiles) {
                this.resultFiles = resultFiles;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (ResultFile file : resultFiles) {
                        tempMap.put(file.getFirstInstance().getId(), file);
                    }

                    while (rs.next()) {
                        try {
                            Long objId = rs.getLong("object_id"); // NON-NLS
                            String hashSetName = rs.getString("set_name"); // NON-NLS

                            tempMap.get(objId).addHashSetName(hashSetName);

                        } catch (SQLException ex) {
                            logger.log(Level.SEVERE, "Unable to get object_id or set_name from result set", ex); // NON-NLS
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to get hash set names", ex); // NON-NLS
                }
            }
        }
    }

    /**
     * Attribute for grouping/sorting by interesting item set lists
     */
    static class InterestingItemAttribute extends AttributeType {

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new DiscoveryKeyUtils.InterestingItemGroupKey(file);
        }

        @Override
        public void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws FileSearchException {

            // Get pairs of (object ID, interesting item set name) for all files in the list of files that have
            // interesting file set hits.
            String selectQuery = createSetNameClause(files, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());

            InterestingFileSetNamesCallback callback = new InterestingFileSetNamesCallback(files);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new FileSearchException("Error looking up interesting file set attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the interesting file set names to the list of
         * ResultFile objects.
         */
        private static class InterestingFileSetNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<ResultFile> resultFiles;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add interesting file set
             *                    names to
             */
            InterestingFileSetNamesCallback(List<ResultFile> resultFiles) {
                this.resultFiles = resultFiles;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (ResultFile file : resultFiles) {
                        tempMap.put(file.getFirstInstance().getId(), file);
                    }

                    while (rs.next()) {
                        try {
                            Long objId = rs.getLong("object_id"); // NON-NLS
                            String setName = rs.getString("set_name"); // NON-NLS

                            tempMap.get(objId).addInterestingSetName(setName);

                        } catch (SQLException ex) {
                            logger.log(Level.SEVERE, "Unable to get object_id or set_name from result set", ex); // NON-NLS
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to get interesting file set names", ex); // NON-NLS
                }
            }
        }
    }

    /**
     * Attribute for grouping/sorting by objects detected
     */
    static class ObjectDetectedAttribute extends AttributeType {

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new DiscoveryKeyUtils.ObjectDetectedGroupKey(file);
        }

        @Override
        public void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws FileSearchException {

            // Get pairs of (object ID, object type name) for all files in the list of files that have
            // objects detected
            String selectQuery = createSetNameClause(files, BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID());

            ObjectDetectedNamesCallback callback = new ObjectDetectedNamesCallback(files);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new FileSearchException("Error looking up object detected attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the object type names to the list of ResultFile
         * objects.
         */
        private static class ObjectDetectedNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<ResultFile> resultFiles;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add object detected names to
             */
            ObjectDetectedNamesCallback(List<ResultFile> resultFiles) {
                this.resultFiles = resultFiles;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (ResultFile file : resultFiles) {
                        tempMap.put(file.getFirstInstance().getId(), file);
                    }

                    while (rs.next()) {
                        try {
                            Long objId = rs.getLong("object_id"); // NON-NLS
                            String setName = rs.getString("set_name"); // NON-NLS

                            tempMap.get(objId).addObjectDetectedName(setName);

                        } catch (SQLException ex) {
                            logger.log(Level.SEVERE, "Unable to get object_id or set_name from result set", ex); // NON-NLS
                        }
                    }
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to get object detected names", ex); // NON-NLS
                }
            }
        }
    }

    /**
     * Attribute for grouping/sorting by tag name
     */
    static class FileTagAttribute extends AttributeType {

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new DiscoveryKeyUtils.FileTagGroupKey(file);
        }

        @Override
        public void addAttributeToResultFiles(List<ResultFile> files, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws FileSearchException {

            try {
                for (ResultFile resultFile : files) {
                    List<ContentTag> contentTags = caseDb.getContentTagsByContent(resultFile.getFirstInstance());

                    for (ContentTag tag : contentTags) {
                        resultFile.addTagName(tag.getName().getDisplayName());
                    }
                }
            } catch (TskCoreException ex) {
                throw new FileSearchException("Error looking up file tag attributes", ex); // NON-NLS
            }
        }
    }

    /**
     * Enum for the attribute types that can be used for grouping.
     */
    @NbBundle.Messages({
        "FileSearch.GroupingAttributeType.fileType.displayName=File Type",
        "FileSearch.GroupingAttributeType.frequency.displayName=Past Occurrences",
        "FileSearch.GroupingAttributeType.keywordList.displayName=Keyword",
        "FileSearch.GroupingAttributeType.size.displayName=File Size",
        "FileSearch.GroupingAttributeType.datasource.displayName=Data Source",
        "FileSearch.GroupingAttributeType.parent.displayName=Parent Folder",
        "FileSearch.GroupingAttributeType.hash.displayName=Hash Set",
        "FileSearch.GroupingAttributeType.interestingItem.displayName=Interesting Item",
        "FileSearch.GroupingAttributeType.tag.displayName=Tag",
        "FileSearch.GroupingAttributeType.object.displayName=Object Detected",
        "FileSearch.GroupingAttributeType.none.displayName=None"})
    public enum GroupingAttributeType {
        FILE_SIZE(new FileSizeAttribute(), Bundle.FileSearch_GroupingAttributeType_size_displayName()),
        FREQUENCY(new FrequencyAttribute(), Bundle.FileSearch_GroupingAttributeType_frequency_displayName()),
        KEYWORD_LIST_NAME(new KeywordListAttribute(), Bundle.FileSearch_GroupingAttributeType_keywordList_displayName()),
        DATA_SOURCE(new DataSourceAttribute(), Bundle.FileSearch_GroupingAttributeType_datasource_displayName()),
        PARENT_PATH(new ParentPathAttribute(), Bundle.FileSearch_GroupingAttributeType_parent_displayName()),
        HASH_LIST_NAME(new HashHitsAttribute(), Bundle.FileSearch_GroupingAttributeType_hash_displayName()),
        INTERESTING_ITEM_SET(new InterestingItemAttribute(), Bundle.FileSearch_GroupingAttributeType_interestingItem_displayName()),
        FILE_TAG(new FileTagAttribute(), Bundle.FileSearch_GroupingAttributeType_tag_displayName()),
        OBJECT_DETECTED(new ObjectDetectedAttribute(), Bundle.FileSearch_GroupingAttributeType_object_displayName()),
        NO_GROUPING(new DiscoveryKeyUtils.NoGroupingAttribute(), Bundle.FileSearch_GroupingAttributeType_none_displayName());

        private final AttributeType attributeType;
        private final String displayName;

        GroupingAttributeType(AttributeType attributeType, String displayName) {
            this.attributeType = attributeType;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        public AttributeType getAttributeType() {
            return attributeType;
        }

        /**
         * Get the list of enums that are valid for grouping images.
         *
         * @return enums that can be used to group images
         */
        public static List<GroupingAttributeType> getOptionsForGrouping() {
            return Arrays.asList(FILE_SIZE, FREQUENCY, PARENT_PATH, OBJECT_DETECTED, HASH_LIST_NAME, INTERESTING_ITEM_SET);
        }
    }
}
