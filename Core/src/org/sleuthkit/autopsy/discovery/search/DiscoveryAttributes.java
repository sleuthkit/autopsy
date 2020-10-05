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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.InstanceTableCallback;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import java.util.StringJoiner;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizer;

/**
 * Class which contains the search attributes which can be specified for
 * Discovery.
 */
public class DiscoveryAttributes {

    private final static Logger logger = Logger.getLogger(DiscoveryAttributes.class.getName());

    /**
     * Base class for the grouping attributes.
     */
    public abstract static class AttributeType {

        /**
         * For a given Result, return the key for the group it belongs to for
         * this attribute type.
         *
         * @param result The result to be grouped.
         *
         * @return The key for the group this result goes in.
         */
        public abstract DiscoveryKeyUtils.GroupKey getGroupKey(Result result);

        /**
         * Add any extra data to the ResultFile object from this attribute.
         *
         * @param results       The list of results to enhance.
         * @param caseDb        The case database.
         * @param centralRepoDb The central repository database. Can be null if
         *                      not needed.
         *
         * @throws DiscoveryException
         */
        public void addAttributeToResults(List<Result> results, SleuthkitCase caseDb, CentralRepository centralRepoDb) throws DiscoveryException {
            // Default is to do nothing
        }
    }

    /**
     * Attribute for grouping/sorting by file size.
     */
    public static class FileSizeAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result result) {
            return new DiscoveryKeyUtils.FileSizeGroupKey(result);
        }
    }

    /**
     * Attribute for grouping/sorting by parent path.
     */
    public static class ParentPathAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result file) {
            return new DiscoveryKeyUtils.ParentPathGroupKey((ResultFile) file);
        }
    }

    /**
     * Default attribute used to make one group.
     */
    static class NoGroupingAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result result) {
            return new DiscoveryKeyUtils.NoGroupingGroupKey();
        }
    }

    /**
     * Attribute for grouping/sorting by data source.
     */
    static class DataSourceAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result result) {
            return new DiscoveryKeyUtils.DataSourceGroupKey(result);
        }
    }

    /**
     * Attribute for grouping/sorting by file type.
     */
    static class FileTypeAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result file) {
            return new DiscoveryKeyUtils.FileTypeGroupKey(file);
        }
    }

    /**
     * Attribute for grouping/sorting by keyword lists.
     */
    static class KeywordListAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result file) {
            return new DiscoveryKeyUtils.KeywordListGroupKey((ResultFile) file);
        }

        @Override
        public void addAttributeToResults(List<Result> results, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws DiscoveryException {

            // Get pairs of (object ID, keyword list name) for all files in the list of files that have
            // keyword list hits.
            String selectQuery = createSetNameClause(results, BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());

            SetKeywordListNamesCallback callback = new SetKeywordListNamesCallback(results);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new DiscoveryException("Error looking up keyword list attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the keyword list names to the list of ResultFile
         * objects.
         */
        private static class SetKeywordListNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<Result> resultFiles;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add keyword list names to.
             */
            SetKeywordListNamesCallback(List<Result> resultFiles) {
                this.resultFiles = resultFiles;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (Result result : resultFiles) {
                        if (result.getType() == SearchData.Type.DOMAIN) {
                            break;
                        }
                        ResultFile file = (ResultFile) result;
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
     * Attribute for grouping/sorting by frequency in the central repository.
     */
    static class FrequencyAttribute extends AttributeType {

        static final int BATCH_SIZE = 50; // Number of hashes to look up at one time

        static final int DOMAIN_BATCH_SIZE = 500; // Number of domains to look up at one time

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result file) {
            return new DiscoveryKeyUtils.FrequencyGroupKey(file);
        }

        @Override
        public void addAttributeToResults(List<Result> results, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws DiscoveryException {
            if (centralRepoDb == null) {
                for (Result result : results) {
                    if (result.getFrequency() == SearchData.Frequency.UNKNOWN && result.getKnown() == TskData.FileKnown.KNOWN) {
                        result.setFrequency(SearchData.Frequency.KNOWN);
                    }
                }
            } else {
                processResultFilesForCR(results, centralRepoDb);
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
        private void processResultFilesForCR(List<Result> results,
                CentralRepository centralRepoDb) throws DiscoveryException {
            List<ResultFile> currentFiles = new ArrayList<>();
            Set<String> hashesToLookUp = new HashSet<>();
            List<ResultDomain> domainsToQuery = new ArrayList<>();
            for (Result result : results) {
                // If frequency was already calculated, skip...
                if (result.getFrequency() == SearchData.Frequency.UNKNOWN) {
                    if (result.getKnown() == TskData.FileKnown.KNOWN) {
                        result.setFrequency(SearchData.Frequency.KNOWN);
                    }

                    if (result.getType() != SearchData.Type.DOMAIN) {
                        ResultFile file = (ResultFile) result;
                        if (file.getFirstInstance().getMd5Hash() != null
                                && !file.getFirstInstance().getMd5Hash().isEmpty()) {
                            hashesToLookUp.add(file.getFirstInstance().getMd5Hash());
                            currentFiles.add(file);
                        }

                        if (hashesToLookUp.size() >= BATCH_SIZE) {
                            computeFrequency(hashesToLookUp, currentFiles, centralRepoDb);

                            hashesToLookUp.clear();
                            currentFiles.clear();
                        }
                    } else {
                        domainsToQuery.add((ResultDomain) result);
                        if (domainsToQuery.size() == DOMAIN_BATCH_SIZE) {
                            queryDomainFrequency(domainsToQuery, centralRepoDb);

                            domainsToQuery.clear();
                        }
                    }
                }
            }

            queryDomainFrequency(domainsToQuery, centralRepoDb);
            computeFrequency(hashesToLookUp, currentFiles, centralRepoDb);
        }
    }

    /**
     * Query to get the frequency of a domain.
     *
     * @param domainsToQuery    List of domains to check the frequency of.
     * @param centralRepository The central repository to query.
     *
     * @throws DiscoveryException
     */
    private static void queryDomainFrequency(List<ResultDomain> domainsToQuery, CentralRepository centralRepository) throws DiscoveryException {
        if (domainsToQuery.isEmpty()) {
            return;
        }
        try {
            final Map<String, List<ResultDomain>> resultDomainTable = new HashMap<>();
            final StringJoiner joiner = new StringJoiner(", ");

            final CorrelationAttributeInstance.Type attributeType = centralRepository.getCorrelationTypeById(CorrelationAttributeInstance.DOMAIN_TYPE_ID);
            for (ResultDomain domainInstance : domainsToQuery) {
                try {
                    final String domainValue = domainInstance.getDomain();
                    final String normalizedDomain = CorrelationAttributeNormalizer.normalize(attributeType, domainValue);
                    final List<ResultDomain> bucket = resultDomainTable.getOrDefault(normalizedDomain, new ArrayList<>());
                    bucket.add(domainInstance);
                    resultDomainTable.put(normalizedDomain, bucket);
                    joiner.add("'" + normalizedDomain + "'");
                } catch (CorrelationAttributeNormalizationException ex) {
                    logger.log(Level.INFO, String.format("Domain [%s] failed normalization, skipping...", domainInstance.getDomain()));
                }
            }

            final String tableName = CentralRepoDbUtil.correlationTypeToInstanceTableName(attributeType);
            final String domainFrequencyQuery = " value AS domain_name, COUNT(*) AS frequency "
                    + "FROM " + tableName + " "
                    + "WHERE value IN (" + joiner + ") "
                    + "GROUP BY value";

            final DomainFrequencyCallback frequencyCallback = new DomainFrequencyCallback(resultDomainTable);
            centralRepository.processSelectClause(domainFrequencyQuery, frequencyCallback);

            if (frequencyCallback.getCause() != null) {
                throw frequencyCallback.getCause();
            }
        } catch (CentralRepoException | SQLException ex) {
            throw new DiscoveryException("Fatal exception encountered querying the CR.", ex);
        }
    }

    /**
     * Callback to get the frequency of domain.
     */
    private static class DomainFrequencyCallback implements InstanceTableCallback {

        private final Map<String, List<ResultDomain>> domainLookup;
        private SQLException sqlCause;

        /**
         * Construct a new DomainFrequencyCallback.
         *
         * @param domainLookup The map to get domain from.
         */
        private DomainFrequencyCallback(Map<String, List<ResultDomain>> domainLookup) {
            this.domainLookup = domainLookup;
        }

        @Override
        public void process(ResultSet resultSet) {
            try {
                while (resultSet.next()) {
                    String domain = resultSet.getString("domain_name");
                    Long frequency = resultSet.getLong("frequency");

                    List<ResultDomain> domainInstances = domainLookup.get(domain);
                    for (ResultDomain domainInstance : domainInstances) {
                        domainInstance.setFrequency(SearchData.Frequency.fromCount(frequency));
                    }
                }
            } catch (SQLException ex) {
                this.sqlCause = ex;
            }
        }

        /**
         * Get the SQL exception if one occurred during this callback.
         *
         * @return
         */
        SQLException getCause() {
            return this.sqlCause;
        }
    }

    /**
     * Callback to use with findInterCaseValuesByCount which generates a list of
     * values for common property search
     */
    private static class FrequencyCallback implements InstanceTableCallback {

        private final List<ResultFile> files;

        /**
         * Construct a new FrequencyCallback.
         *
         * @param files List of files to add hash set names to.
         */
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
                            file.setFrequency(SearchData.Frequency.fromCount(count));
                            iterator.remove();
                        }
                    }
                }

                // The files left had no matching entries in the CR, so mark them as unique
                for (ResultFile file : files) {
                    file.setFrequency(SearchData.Frequency.UNIQUE);
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Error getting frequency counts from Central Repository", ex); // NON-NLS
            }
        }
    }

    /**
     * Attribute for grouping/sorting by hash set lists.
     */
    static class HashHitsAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result result) {
            if (result.getType() == SearchData.Type.DOMAIN) {
                return null;
            }
            return new DiscoveryKeyUtils.HashHitsGroupKey((ResultFile) result);
        }

        @Override
        public void addAttributeToResults(List<Result> results, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws DiscoveryException {

            // Get pairs of (object ID, hash set name) for all files in the list of files that have
            // hash set hits.
            String selectQuery = createSetNameClause(results, BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());

            HashSetNamesCallback callback = new HashSetNamesCallback(results);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new DiscoveryException("Error looking up hash set attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the hash set names to the list of ResultFile objects.
         */
        private static class HashSetNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<Result> results;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add hash set names to.
             */
            HashSetNamesCallback(List<Result> results) {
                this.results = results;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (Result result : results) {
                        if (result.getType() == SearchData.Type.DOMAIN) {
                            return;
                        }
                        ResultFile file = (ResultFile) result;
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
     * Attribute for grouping/sorting by interesting item set lists.
     */
    static class InterestingItemAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result file) {
            return new DiscoveryKeyUtils.InterestingItemGroupKey((ResultFile) file);
        }

        @Override
        public void addAttributeToResults(List<Result> results, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws DiscoveryException {

            // Get pairs of (object ID, interesting item set name) for all files in the list of files that have
            // interesting file set hits.
            String selectQuery = createSetNameClause(results, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());

            InterestingFileSetNamesCallback callback = new InterestingFileSetNamesCallback(results);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new DiscoveryException("Error looking up interesting file set attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the interesting file set names to the list of
         * ResultFile objects.
         */
        private static class InterestingFileSetNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<Result> results;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add interesting file set
             *                    names to.
             */
            InterestingFileSetNamesCallback(List<Result> results) {
                this.results = results;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (Result result : results) {
                        if (result.getType() == SearchData.Type.DOMAIN) {
                            return;
                        }
                        ResultFile file = (ResultFile) result;
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
     * Attribute for grouping/sorting by date of most recent activity.
     */
    static class MostRecentActivityDateAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result result) {
            return new DiscoveryKeyUtils.MostRecentActivityDateGroupKey(result);
        }

    }

    /**
     * Attribute for grouping/sorting by date of first activity.
     */
    static class FirstActivityDateAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result result) {
            return new DiscoveryKeyUtils.FirstActivityDateGroupKey(result);
        }

    }

    /**
     * Attribute for grouping/sorting by number of visits.
     */
    static class NumberOfVisitsAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result result) {
            return new DiscoveryKeyUtils.NumberOfVisitsGroupKey(result);
        }
    }

    /**
     * Attribute for grouping/sorting by objects detected.
     */
    static class ObjectDetectedAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result file) {
            return new DiscoveryKeyUtils.ObjectDetectedGroupKey((ResultFile) file);
        }

        @Override
        public void addAttributeToResults(List<Result> results, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws DiscoveryException {

            // Get pairs of (object ID, object type name) for all files in the list of files that have
            // objects detected
            String selectQuery = createSetNameClause(results, BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED.getTypeID(),
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID());

            ObjectDetectedNamesCallback callback = new ObjectDetectedNamesCallback(results);
            try {
                caseDb.getCaseDbAccessManager().select(selectQuery, callback);
            } catch (TskCoreException ex) {
                throw new DiscoveryException("Error looking up object detected attributes", ex); // NON-NLS
            }
        }

        /**
         * Callback to process the results of the CaseDbAccessManager select
         * query. Will add the object type names to the list of ResultFile
         * objects.
         */
        private static class ObjectDetectedNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

            List<Result> results;

            /**
             * Create the callback.
             *
             * @param resultFiles List of files to add object detected names to.
             */
            ObjectDetectedNamesCallback(List<Result> results) {
                this.results = results;
            }

            @Override
            public void process(ResultSet rs) {
                try {
                    // Create a temporary map of object ID to ResultFile
                    Map<Long, ResultFile> tempMap = new HashMap<>();
                    for (Result result : results) {
                        if (result.getType() == SearchData.Type.DOMAIN) {
                            return;
                        }
                        ResultFile file = (ResultFile) result;
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
     * Attribute for grouping/sorting by tag name.
     */
    static class FileTagAttribute extends AttributeType {

        @Override
        public DiscoveryKeyUtils.GroupKey getGroupKey(Result file) {
            return new DiscoveryKeyUtils.FileTagGroupKey((ResultFile) file);
        }

        @Override
        public void addAttributeToResults(List<Result> results, SleuthkitCase caseDb,
                CentralRepository centralRepoDb) throws DiscoveryException {

            try {
                for (Result result : results) {
                    if (result.getType() == SearchData.Type.DOMAIN) {
                        return;
                    }
                    ResultFile file = (ResultFile) result;
                    List<ContentTag> contentTags = caseDb.getContentTagsByContent(file.getFirstInstance());

                    for (ContentTag tag : contentTags) {
                        result.addTagName(tag.getName().getDisplayName());
                    }
                }
            } catch (TskCoreException ex) {
                throw new DiscoveryException("Error looking up file tag attributes", ex); // NON-NLS
            }
        }
    }

    /**
     * Enum for the attribute types that can be used for grouping.
     */
    @NbBundle.Messages({
        "DiscoveryAttributes.GroupingAttributeType.fileType.displayName=File Type",
        "DiscoveryAttributes.GroupingAttributeType.frequency.displayName=Past Occurrences",
        "DiscoveryAttributes.GroupingAttributeType.keywordList.displayName=Keyword",
        "DiscoveryAttributes.GroupingAttributeType.size.displayName=File Size",
        "DiscoveryAttributes.GroupingAttributeType.datasource.displayName=Data Source",
        "DiscoveryAttributes.GroupingAttributeType.parent.displayName=Parent Folder",
        "DiscoveryAttributes.GroupingAttributeType.hash.displayName=Hash Set",
        "DiscoveryAttributes.GroupingAttributeType.interestingItem.displayName=Interesting Item",
        "DiscoveryAttributes.GroupingAttributeType.tag.displayName=Tag",
        "DiscoveryAttributes.GroupingAttributeType.object.displayName=Object Detected",
        "DiscoveryAttributes.GroupingAttributeType.mostRecentDate.displayName=Most Recent Activity Date",
        "DiscoveryAttributes.GroupingAttributeType.firstDate.displayName=First Activity Date",
        "DiscoveryAttributes.GroupingAttributeType.numberOfVisits.displayName=Number of Visits",
        "DiscoveryAttributes.GroupingAttributeType.none.displayName=None"})
    public enum GroupingAttributeType {
        FILE_SIZE(new FileSizeAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_size_displayName()),
        FREQUENCY(new FrequencyAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_frequency_displayName()),
        KEYWORD_LIST_NAME(new KeywordListAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_keywordList_displayName()),
        DATA_SOURCE(new DataSourceAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_datasource_displayName()),
        PARENT_PATH(new ParentPathAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_parent_displayName()),
        HASH_LIST_NAME(new HashHitsAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_hash_displayName()),
        INTERESTING_ITEM_SET(new InterestingItemAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_interestingItem_displayName()),
        FILE_TAG(new FileTagAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_tag_displayName()),
        OBJECT_DETECTED(new ObjectDetectedAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_object_displayName()),
        MOST_RECENT_DATE(new MostRecentActivityDateAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_mostRecentDate_displayName()),
        FIRST_DATE(new FirstActivityDateAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_firstDate_displayName()),
        NUMBER_OF_VISITS(new NumberOfVisitsAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_numberOfVisits_displayName()),
        NO_GROUPING(new NoGroupingAttribute(), Bundle.DiscoveryAttributes_GroupingAttributeType_none_displayName());

        private final AttributeType attributeType;
        private final String displayName;

        /**
         * Construct a new GroupingAttributeType enum value.
         *
         * @param attributeType The type of attribute this enum value was
         *                      constructed for.
         * @param displayName   The display name for this grouping attribute
         *                      type.
         */
        GroupingAttributeType(AttributeType attributeType, String displayName) {
            this.attributeType = attributeType;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        /**
         * Get the type of attribute this enum value was constructed for.
         *
         * @return The type of attribute this enum value was constructed for.
         */
        public AttributeType getAttributeType() {
            return attributeType;
        }

        /**
         * Get the list of enums that are valid for grouping files.
         *
         * @return Enums that can be used to group files.
         */
        public static List<GroupingAttributeType> getOptionsForGroupingForFiles() {
            return Arrays.asList(FILE_SIZE, FREQUENCY, PARENT_PATH, OBJECT_DETECTED, HASH_LIST_NAME, INTERESTING_ITEM_SET);
        }

        /**
         * Get the list of enums that are valid for grouping files.
         *
         * @return Enums that can be used to group files.
         */
        public static List<GroupingAttributeType> getOptionsForGroupingForDomains() {
            return Arrays.asList(FREQUENCY, MOST_RECENT_DATE, FIRST_DATE, NUMBER_OF_VISITS);
        }
    }

    /**
     * Computes the CR frequency of all the given hashes and updates the list of
     * files.
     *
     * @param hashesToLookUp Hashes to find the frequency of.
     * @param currentFiles   List of files to update with frequencies.
     * @param centralRepoDb  The central repository being used.
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

    /**
     * Private helper method to create a set name clause to be used in queries.
     *
     * @param results        The list of results to create the set name clause
     *                       for.
     * @param artifactTypeID The Blackboard Artifact type ID for the artifact
     *                       type.
     * @param setNameAttrID  The set name attribute id.
     *
     * @return The String to use as a set name clause in queries.
     *
     * @throws DiscoveryException
     */
    private static String createSetNameClause(List<Result> results,
            int artifactTypeID, int setNameAttrID) throws DiscoveryException {

        // Concatenate the object IDs in the list of files
        String objIdList = ""; // NON-NLS
        for (Result result : results) {
            if (result.getType() == SearchData.Type.DOMAIN) {
                break;
            }
            ResultFile file = (ResultFile) result;
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
                + "AND blackboard_artifacts.obj_id IN (" + objIdList
                + ") "; // NON-NLS
    }

    /**
     * Private constructor for DiscoveryAttributes class.
     */
    private DiscoveryAttributes() {
        // Class should not be instantiated
    }
}
