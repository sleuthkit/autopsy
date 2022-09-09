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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.discovery.search.SearchData.PageViews;
import org.sleuthkit.autopsy.discovery.ui.MonthAbbreviation;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility class for constructing keys for groups and searches.
 */
public class DiscoveryKeyUtils {

    private final static Logger logger = Logger.getLogger(DiscoveryKeyUtils.class.getName());

    /**
     * Represents a key for a specific search for a specific user.
     */
    static class SearchKey implements Comparable<SearchKey> {

        private final String keyString;
        private final Group.GroupSortingAlgorithm groupSortingType;
        private final DiscoveryAttributes.AttributeType groupAttributeType;
        private final ResultsSorter.SortingMethod sortingMethod;
        private final List<AbstractFilter> filters;
        private final SleuthkitCase sleuthkitCase;
        private final CentralRepository centralRepository;
        private final SearchContext context;

        /**
         * Construct a new SearchKey with all information that defines a search.
         *
         * @param userName           The name of the user performing the search.
         * @param filters            The Filters being used for the search.
         * @param groupAttributeType The AttributeType to group by.
         * @param groupSortingType   The algorithm to sort the groups by.
         * @param sortingMethod      The method to sort the results by.
         * @param sleuthkitCase      The SleuthkitCase being searched.
         * @param centralRepository  The Central Repository being searched.
         * @param context            The SearchContext which reflects the search
         *                           being performed to get results for this
         *                           key.
         */
        SearchKey(String userName, List<AbstractFilter> filters,
                DiscoveryAttributes.AttributeType groupAttributeType,
                Group.GroupSortingAlgorithm groupSortingType,
                ResultsSorter.SortingMethod sortingMethod,
                SleuthkitCase sleuthkitCase, CentralRepository centralRepository, SearchContext context) {
            this.groupAttributeType = groupAttributeType;
            this.groupSortingType = groupSortingType;
            this.sortingMethod = sortingMethod;
            this.filters = filters;
            this.context = context;

            StringBuilder searchStringBuilder = new StringBuilder();
            searchStringBuilder.append(userName);
            for (AbstractFilter filter : filters) {
                searchStringBuilder.append(filter.toString());
            }
            searchStringBuilder.append(groupAttributeType).append(groupSortingType).append(sortingMethod);
            keyString = searchStringBuilder.toString();
            this.sleuthkitCase = sleuthkitCase;
            this.centralRepository = centralRepository;
        }

        /**
         * Construct a SearchKey without a SearchContext, SleuthkitCase or
         * CentralRepositry instance.
         *
         * @param userName           The name of the user performing the search.
         * @param filters            The Filters being used for the search.
         * @param groupAttributeType The AttributeType to group by.
         * @param groupSortingType   The algorithm to sort the groups by.
         * @param sortingMethod      The method to sort the results by.
         */
        SearchKey(String userName, List<AbstractFilter> filters,
                DiscoveryAttributes.AttributeType groupAttributeType,
                Group.GroupSortingAlgorithm groupSortingType,
                ResultsSorter.SortingMethod sortingMethod) {
            this(userName, filters, groupAttributeType, groupSortingType,
                    sortingMethod, null, null, null);
            //this constructor should only be used putting things directly into a map or getting if present since casedb, cr, and search context will be null
        }

        @Override
        public int compareTo(SearchKey otherSearchKey) {
            return getKeyString().compareTo(otherSearchKey.getKeyString());
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof SearchKey)) {
                return false;
            }

            SearchKey otherSearchKey = (SearchKey) otherKey;
            if (this.sleuthkitCase != otherSearchKey.getSleuthkitCase()
                    || this.centralRepository != otherSearchKey.getCentralRepository()) {
                return false;
            }

            return getKeyString().equals(otherSearchKey.getKeyString());
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 79 * hash + Objects.hashCode(getKeyString());
            return hash;
        }

        /**
         * Get the SearchContext for the search this key is being used in.
         *
         * @return The SearchContext the search key is being used in.
         *
         * @throws DiscoveryException Thrown when the key being used has a null
         *                            context indicating it was not created with
         *                            knowledge of the case or central
         *                            repository databases.
         */
        SearchContext getContext() throws DiscoveryException {
            if (context == null) {
                throw new DiscoveryException("The key in use was created without a context and does not support retrieving information from the databases.");
            }
            return context;
        }

        /**
         * Get the String representation of this key.
         *
         * @return The String representation of this key.
         */
        String getKeyString() {
            return keyString;
        }

        /**
         * Get the list of filters associated with this key.
         *
         * @return The list of filters associated with this key.
         */
        List<AbstractFilter> getFilters() {
            return Collections.unmodifiableList(this.filters);
        }

        /**
         * Get the group sorting type for this key.
         *
         * @return The group sorting type for this key.
         */
        Group.GroupSortingAlgorithm getGroupSortingType() {
            return groupSortingType;
        }

        /**
         * Get the grouping attribute for this key.
         *
         * @return The grouping attribute for this key.
         */
        DiscoveryAttributes.AttributeType getGroupAttributeType() {
            return groupAttributeType;
        }

        /**
         * Get the SortingMethod for this key.
         *
         * @return The SortingMethod for this key.
         */
        ResultsSorter.SortingMethod getFileSortingMethod() {
            return sortingMethod;
        }

        /**
         * Get the case database for this key.
         *
         * @return The case database for this key.
         */
        SleuthkitCase getSleuthkitCase() {
            return this.sleuthkitCase;
        }

        /**
         * Get the central repository for this key.
         *
         * @return The central repository for this key.
         */
        CentralRepository getCentralRepository() {
            return this.centralRepository;
        }
    }

    /**
     * The key used for grouping for each attribute type.
     */
    public abstract static class GroupKey implements Comparable<GroupKey> {

        /**
         * Get the string version of the group key for display. Each display
         * name should correspond to a unique GroupKey object.
         *
         * @return The display name for this key
         */
        abstract String getDisplayName();

        /**
         * Subclasses must implement equals().
         *
         * @param otherKey The GroupKey to compare to this key.
         *
         * @return true if the keys are equal, false otherwise
         */
        @Override
        abstract public boolean equals(Object otherKey);

        /**
         * Subclasses must implement hashCode().
         *
         * @return The hash code for the GroupKey.
         */
        @Override
        abstract public int hashCode();

        /**
         * It should not happen with the current setup, but we need to cover the
         * case where two different GroupKey subclasses are compared against
         * each other. Use a lexicographic comparison on the class names.
         *
         * @param otherGroupKey The other group key.
         *
         * @return Result of alphabetical comparison on the class name.
         */
        int compareClassNames(GroupKey otherGroupKey) {
            return this.getClass().getName().compareTo(otherGroupKey.getClass().getName());
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    /**
     * Key representing a file size group.
     */
    static class FileSizeGroupKey extends GroupKey {

        private final SearchData.FileSize fileSize;

        /**
         * Construct a new FileSizeGroupKey.
         *
         * @param file The file to create the group key for.
         */
        FileSizeGroupKey(Result file) {
            ResultFile resultFile = (ResultFile) file;
            if (resultFile.getFileType() == SearchData.Type.VIDEO) {
                fileSize = SearchData.FileSize.fromVideoSize(resultFile.getFirstInstance().getSize());
            } else {
                fileSize = SearchData.FileSize.fromImageSize(resultFile.getFirstInstance().getSize());
            }
        }

        @Override
        String getDisplayName() {
            return getFileSize().toString();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FileSizeGroupKey) {
                FileSizeGroupKey otherFileSizeGroupKey = (FileSizeGroupKey) otherGroupKey;
                return Integer.compare(getFileSize().getRanking(), otherFileSizeGroupKey.getFileSize().getRanking());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof FileSizeGroupKey)) {
                return false;
            }

            FileSizeGroupKey otherFileSizeGroupKey = (FileSizeGroupKey) otherKey;
            return getFileSize().equals(otherFileSizeGroupKey.getFileSize());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getFileSize().getRanking());
        }

        /**
         * The size of the file.
         *
         * @return The size of the file.
         */
        SearchData.FileSize getFileSize() {
            return fileSize;
        }
    }

    /**
     * Key representing a file type group.
     */
    static class FileTypeGroupKey extends GroupKey {

        private final SearchData.Type fileType;

        /**
         * Construct a new FileTypeGroupKey.
         *
         * @param file The file to create the group key for.
         */
        FileTypeGroupKey(Result file) {
            fileType = ((ResultFile) file).getFileType();
        }

        @Override
        String getDisplayName() {
            return getFileType().toString();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FileTypeGroupKey) {
                FileTypeGroupKey otherFileTypeGroupKey = (FileTypeGroupKey) otherGroupKey;
                return Integer.compare(getFileType().getRanking(), otherFileTypeGroupKey.getFileType().getRanking());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof FileTypeGroupKey)) {
                return false;
            }

            FileTypeGroupKey otherFileTypeGroupKey = (FileTypeGroupKey) otherKey;
            return getFileType().equals(otherFileTypeGroupKey.getFileType());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getFileType().getRanking());
        }

        /**
         * Get the type of file the group exists for.
         *
         * @return The type of file the group exists for.
         */
        SearchData.Type getFileType() {
            return fileType;
        }
    }

    /**
     * Key representing a keyword list group.
     */
    static class KeywordListGroupKey extends GroupKey {

        private final List<String> keywordListNames;
        private final String keywordListNamesString;

        /**
         * Construct a new KeywordListGroupKey.
         *
         * @param file The file to create the group key for.
         */
        @NbBundle.Messages({
            "DiscoveryKeyUtils.KeywordListGroupKey.noKeywords=None"})
        KeywordListGroupKey(ResultFile file) {
            keywordListNames = file.getKeywordListNames();
            if (keywordListNames.isEmpty()) {
                keywordListNamesString = Bundle.DiscoveryKeyUtils_KeywordListGroupKey_noKeywords();
            } else {
                keywordListNamesString = String.join(",", keywordListNames); // NON-NLS
            }
        }

        @Override
        String getDisplayName() {
            return getKeywordListNamesString();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof KeywordListGroupKey) {
                KeywordListGroupKey otherKeywordListNamesGroupKey = (KeywordListGroupKey) otherGroupKey;

                // Put the empty list at the end
                if (getKeywordListNames().isEmpty()) {
                    if (otherKeywordListNamesGroupKey.getKeywordListNames().isEmpty()) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else if (otherKeywordListNamesGroupKey.getKeywordListNames().isEmpty()) {
                    return -1;
                }

                return getKeywordListNamesString().compareTo(otherKeywordListNamesGroupKey.getKeywordListNamesString());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof KeywordListGroupKey)) {
                return false;
            }

            KeywordListGroupKey otherKeywordListGroupKey = (KeywordListGroupKey) otherKey;
            return getKeywordListNamesString().equals(otherKeywordListGroupKey.getKeywordListNamesString());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getKeywordListNamesString());
        }

        /**
         * Get the list of keywords this group is for.
         *
         * @return The list of keywords this group is for.
         */
        List<String> getKeywordListNames() {
            return Collections.unmodifiableList(keywordListNames);
        }

        /**
         * Get the string which represents the keyword names represented by this
         * group key.
         *
         * @return The string which represents the keyword names represented by
         *         this group key.
         */
        String getKeywordListNamesString() {
            return keywordListNamesString;
        }
    }

    /**
     * Key representing a file tag group.
     */
    static class FileTagGroupKey extends GroupKey {

        private final List<String> tagNames;
        private final String tagNamesString;

        /**
         * Construct a new FileTagGroupKey.
         *
         * @param file The file to create the group key for.
         */
        @NbBundle.Messages({
            "DiscoveryKeyUtils.FileTagGroupKey.noSets=None"})
        FileTagGroupKey(ResultFile file) {
            tagNames = file.getTagNames();

            if (tagNames.isEmpty()) {
                tagNamesString = Bundle.DiscoveryKeyUtils_FileTagGroupKey_noSets();
            } else {
                tagNamesString = String.join(",", tagNames); // NON-NLS
            }
        }

        @Override
        String getDisplayName() {
            return getTagNamesString();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FileTagGroupKey) {
                FileTagGroupKey otherFileTagGroupKey = (FileTagGroupKey) otherGroupKey;

                // Put the empty list at the end
                if (getTagNames().isEmpty()) {
                    if (otherFileTagGroupKey.getTagNames().isEmpty()) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else if (otherFileTagGroupKey.getTagNames().isEmpty()) {
                    return -1;
                }

                return getTagNamesString().compareTo(otherFileTagGroupKey.getTagNamesString());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }
            if (!(otherKey instanceof FileTagGroupKey)) {
                return false;
            }
            FileTagGroupKey otherFileTagGroupKey = (FileTagGroupKey) otherKey;
            return getTagNamesString().equals(otherFileTagGroupKey.getTagNamesString());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getTagNamesString());
        }

        /**
         * Get the list of tag names which are represented by this group.
         *
         * @return The list of tag names which are represented by this group.
         */
        List<String> getTagNames() {
            return Collections.unmodifiableList(tagNames);
        }

        /**
         * Get the String representation of the tags which are represented by
         * this group.
         *
         * @return The String representation of the tags which are represented
         *         by this group.
         */
        String getTagNamesString() {
            return tagNamesString;
        }
    }

    /**
     * Key representing a parent path group.
     */
    static class ParentPathGroupKey extends GroupKey {

        private String parentPath;
        private Long parentID;

        /**
         * Construct a new ParentPathGroupKey.
         *
         * @param file The file to create the group key for.
         */
        ParentPathGroupKey(ResultFile file) {
            Content parent;
            try {
                parent = file.getFirstInstance().getParent();
            } catch (TskCoreException ignored) {
                parent = null;
            }
            //Find the directory this file is in if it is an embedded file
            while (parent != null && parent instanceof AbstractFile && ((AbstractFile) parent).isFile()) {
                try {
                    parent = parent.getParent();
                } catch (TskCoreException ignored) {
                    parent = null;
                }
            }
            setParentPathAndID(parent, file);
        }

        /**
         * Helper method to set the parent path and parent ID.
         *
         * @param parent The parent content object.
         * @param file   The ResultFile object.
         */
        private void setParentPathAndID(Content parent, ResultFile file) {
            if (parent != null) {
                try {
                    parentPath = parent.getUniquePath();
                    parentID = parent.getId();
                } catch (TskCoreException ignored) {
                    //catch block left blank purposefully next if statement will handle case when exception takes place as well as when parent is null
                }

            }
            if (parentPath == null) {
                if (file.getFirstInstance().getParentPath() != null) {
                    parentPath = file.getFirstInstance().getParentPath();
                } else {
                    parentPath = ""; // NON-NLS
                }
                parentID = -1L;
            }
        }

        @Override
        String getDisplayName() {
            return getParentPath();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof ParentPathGroupKey) {
                ParentPathGroupKey otherParentPathGroupKey = (ParentPathGroupKey) otherGroupKey;
                int comparisonResult = getParentPath().compareTo(otherParentPathGroupKey.getParentPath());
                if (comparisonResult == 0) {
                    comparisonResult = getParentID().compareTo(otherParentPathGroupKey.getParentID());
                }
                return comparisonResult;
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof ParentPathGroupKey)) {
                return false;
            }

            ParentPathGroupKey otherParentPathGroupKey = (ParentPathGroupKey) otherKey;
            return getParentPath().equals(otherParentPathGroupKey.getParentPath()) && getParentID().equals(otherParentPathGroupKey.getParentID());
        }

        @Override
        public int hashCode() {
            int hashCode = 11;
            hashCode = 61 * hashCode + Objects.hash(getParentPath());
            hashCode = 61 * hashCode + Objects.hash(getParentID());
            return hashCode;
        }

        /**
         * Get the parent path this group is for.
         *
         * @return The parent path this group is for as a String.
         */
        String getParentPath() {
            return parentPath;
        }

        /**
         * Get the object ID of the parent object.
         *
         * @return The object ID of the parent object.
         */
        Long getParentID() {
            return parentID;
        }
    }

    /**
     * Key representing a data source group.
     */
    static class DataSourceGroupKey extends GroupKey {

        private final long dataSourceID;
        private String displayName;

        /**
         * Construct a new DataSourceGroupKey.
         *
         * @param result The Result to create the group key for.
         */
        @NbBundle.Messages({
            "# {0} - Data source name",
            "# {1} - Data source ID",
            "DiscoveryKeyUtils.DataSourceGroupKey.datasourceAndID={0}(ID: {1})",
            "# {0} - Data source ID",
            "DiscoveryKeyUtils.DataSourceGroupKey.idOnly=Data source (ID: {0})"})
        DataSourceGroupKey(Result result) {
            //get the id first so that it can be used when logging if necessary
            dataSourceID = result.getDataSourceObjectId();
            try {
                // The data source should be cached so this won't actually be a database query.
                Content ds = result.getDataSource();
                displayName = Bundle.DiscoveryKeyUtils_DataSourceGroupKey_datasourceAndID(ds.getName(), ds.getId());
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error looking up data source with ID " + dataSourceID, ex); // NON-NLS
                displayName = Bundle.DiscoveryKeyUtils_DataSourceGroupKey_idOnly(dataSourceID);
            }
        }

        @Override
        String getDisplayName() {
            return displayName;
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof DataSourceGroupKey) {
                DataSourceGroupKey otherDataSourceGroupKey = (DataSourceGroupKey) otherGroupKey;
                return Long.compare(getDataSourceID(), otherDataSourceGroupKey.getDataSourceID());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof DataSourceGroupKey)) {
                return false;
            }

            DataSourceGroupKey otherDataSourceGroupKey = (DataSourceGroupKey) otherKey;
            return getDataSourceID() == otherDataSourceGroupKey.getDataSourceID();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getDataSourceID());
        }

        /**
         * Get the object ID of the data source.
         *
         * @return The object ID of the data source.
         */
        long getDataSourceID() {
            return dataSourceID;
        }
    }

    /**
     * Dummy key for when there is no grouping. All files will have the same
     * key.
     */
    static class NoGroupingGroupKey extends GroupKey {

        /**
         * Constructor for dummy group which puts all files together.
         */
        NoGroupingGroupKey() {
            // Nothing to save - all files will get the same GroupKey
        }

        @NbBundle.Messages({
            "DiscoveryKeyUtils.NoGroupingGroupKey.allFiles=All Files"})
        @Override
        String getDisplayName() {
            return Bundle.DiscoveryKeyUtils_NoGroupingGroupKey_allFiles();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            // As long as the other key is the same type, they are equal
            if (otherGroupKey instanceof NoGroupingGroupKey) {
                return 0;
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }
            // As long as the other key is the same type, they are equal
            return otherKey instanceof NoGroupingGroupKey;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    /**
     * Group key representing a domain category (TSK_WEB_CATEGORY artifact).
     */
    static class DomainCategoryGroupKey extends GroupKey {

        private final Set<String> webCategories = new HashSet<>();
        private final String displayName;

        DomainCategoryGroupKey(Result result) {
            if (result instanceof ResultDomain) {
                ResultDomain domain = (ResultDomain) result;
                this.webCategories.addAll(domain.getWebCategories());
                displayName = String.join(",", webCategories);
            } else {
                throw new IllegalArgumentException("Input result should be of type ResultDomain");
            }
        }

        @Override
        String getDisplayName() {

            return this.displayName;
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey instanceof GroupKey) {
                return compareTo((GroupKey) otherKey) == 0;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(webCategories);
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof DomainCategoryGroupKey) {
                if (webCategories.size() != ((DomainCategoryGroupKey) otherGroupKey).getWebCategories().size()) {
                    return 1;
                }
                if (webCategories.containsAll(((DomainCategoryGroupKey) otherGroupKey).getWebCategories())) {
                    return 0;
                } else {
                    return -1;
                }
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        Set<String> getWebCategories() {
            return Collections.unmodifiableSet(webCategories);
        }
    }

    /**
     * Key representing a central repository notable status.
     */
    static class PreviouslyNotableGroupKey extends GroupKey {

        private final SearchData.PreviouslyNotable notableStatus;

        PreviouslyNotableGroupKey(Result result) {
            this.notableStatus = result.getPreviouslyNotableInCR();
        }

        @Override
        String getDisplayName() {
            return this.notableStatus.toString();
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey instanceof GroupKey) {
                return compareTo((GroupKey) otherKey) == 0;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getStatus().getRanking());
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof PreviouslyNotableGroupKey) {
                PreviouslyNotableGroupKey otherFrequencyGroupKey = (PreviouslyNotableGroupKey) otherGroupKey;
                return Integer.compare(getStatus().getRanking(), otherFrequencyGroupKey.getStatus().getRanking());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        SearchData.PreviouslyNotable getStatus() {
            return notableStatus;
        }
    }

    /**
     * Key representing a central repository frequency group.
     */
    static class FrequencyGroupKey extends GroupKey {

        private final SearchData.Frequency frequency;

        /**
         * Construct a new FrequencyGroupKey.
         *
         * @param result The Result to create the group key for.
         */
        FrequencyGroupKey(Result result) {
            frequency = result.getFrequency();
        }

        @Override
        String getDisplayName() {
            return getFrequency().toString();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FrequencyGroupKey) {
                FrequencyGroupKey otherFrequencyGroupKey = (FrequencyGroupKey) otherGroupKey;
                return Integer.compare(getFrequency().getRanking(), otherFrequencyGroupKey.getFrequency().getRanking());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof FrequencyGroupKey)) {
                return false;
            }

            FrequencyGroupKey otherFrequencyGroupKey = (FrequencyGroupKey) otherKey;
            return getFrequency().equals(otherFrequencyGroupKey.getFrequency());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getFrequency().getRanking());
        }

        /**
         * Get the frequency which the group is for.
         *
         * @return The frequency which the group is for.
         */
        SearchData.Frequency getFrequency() {
            return frequency;
        }
    }

    /**
     * Key representing a hash hits group.
     */
    static class HashHitsGroupKey extends GroupKey {

        private final List<String> hashSetNames;
        private final String hashSetNamesString;

        /**
         * Construct a new HashHitsGroupKey.
         *
         * @param file The file to create the group key for.
         */
        @NbBundle.Messages({
            "DiscoveryKeyUtils.HashHitsGroupKey.noHashHits=None"})
        HashHitsGroupKey(ResultFile file) {
            hashSetNames = file.getHashSetNames();

            if (hashSetNames.isEmpty()) {
                hashSetNamesString = Bundle.DiscoveryKeyUtils_HashHitsGroupKey_noHashHits();
            } else {
                hashSetNamesString = String.join(",", hashSetNames); // NON-NLS
            }
        }

        @Override
        String getDisplayName() {
            return getHashSetNamesString();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof HashHitsGroupKey) {
                HashHitsGroupKey otherHashHitsGroupKey = (HashHitsGroupKey) otherGroupKey;

                // Put the empty list at the end
                if (getHashSetNames().isEmpty()) {
                    if (otherHashHitsGroupKey.getHashSetNames().isEmpty()) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else if (otherHashHitsGroupKey.getHashSetNames().isEmpty()) {
                    return -1;
                }

                return getHashSetNamesString().compareTo(otherHashHitsGroupKey.getHashSetNamesString());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof HashHitsGroupKey)) {
                return false;
            }

            HashHitsGroupKey otherHashHitsGroupKey = (HashHitsGroupKey) otherKey;
            return getHashSetNamesString().equals(otherHashHitsGroupKey.getHashSetNamesString());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getHashSetNamesString());
        }

        /**
         * Get the list of hash set names the group is for.
         *
         * @return The list of hash set names the group is for.
         */
        List<String> getHashSetNames() {
            return Collections.unmodifiableList(hashSetNames);
        }

        /**
         * Get the String representation of the list of hash set names.
         *
         * @return The String representation of the list of hash set names.
         */
        String getHashSetNamesString() {
            return hashSetNamesString;
        }
    }

    /**
     * Key representing a interesting item set group.
     */
    static class InterestingItemGroupKey extends GroupKey {

        private final List<String> interestingItemSetNames;
        private final String interestingItemSetNamesString;

        /**
         * Construct a new InterestingItemGroupKey.
         *
         * @param file The file to create the group key for.
         */
        @NbBundle.Messages({
            "DiscoveryKeyUtils.InterestingItemGroupKey.noSets=None"})
        InterestingItemGroupKey(ResultFile file) {
            interestingItemSetNames = file.getInterestingSetNames();

            if (interestingItemSetNames.isEmpty()) {
                interestingItemSetNamesString = Bundle.DiscoveryKeyUtils_InterestingItemGroupKey_noSets();
            } else {
                interestingItemSetNamesString = String.join(",", interestingItemSetNames); // NON-NLS
            }
        }

        @Override
        String getDisplayName() {
            return getInterestingItemSetNamesString();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof InterestingItemGroupKey) {
                InterestingItemGroupKey otherInterestingItemGroupKey = (InterestingItemGroupKey) otherGroupKey;

                // Put the empty list at the end
                if (this.getInterestingItemSetNames().isEmpty()) {
                    if (otherInterestingItemGroupKey.getInterestingItemSetNames().isEmpty()) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else if (otherInterestingItemGroupKey.getInterestingItemSetNames().isEmpty()) {
                    return -1;
                }

                return getInterestingItemSetNamesString().compareTo(otherInterestingItemGroupKey.getInterestingItemSetNamesString());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof InterestingItemGroupKey)) {
                return false;
            }

            InterestingItemGroupKey otherInterestingItemGroupKey = (InterestingItemGroupKey) otherKey;
            return getInterestingItemSetNamesString().equals(otherInterestingItemGroupKey.getInterestingItemSetNamesString());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getInterestingItemSetNamesString());
        }

        /**
         * Get the list of interesting item set names the group is for.
         *
         * @return The list of interesting item set names the group is for.
         */
        List<String> getInterestingItemSetNames() {
            return Collections.unmodifiableList(interestingItemSetNames);
        }

        /**
         * Get the String representation of the interesting item set names the
         * group is for.
         *
         * @return The String representation of the interesting item set names
         *         the group is for.
         */
        String getInterestingItemSetNamesString() {
            return interestingItemSetNamesString;
        }
    }

    /**
     * Key representing a date of last activity.
     */
    static class LastActivityDateGroupKey extends GroupKey {

        private ZonedDateTime currentWeekCutOff;

        /**
         * Construct a new LastActivityDateGroupKey.
         *
         * @param result The Result to create the group key for.
         */
        LastActivityDateGroupKey(Result result) {
            if (result instanceof ResultDomain) {
                ResultDomain domainResult = ((ResultDomain) result);
                currentWeekCutOff = getCurrentWeekCutOff(domainResult.getActivityEnd(), domainResult);
            } else {
                throw new IllegalArgumentException("Expected a domain result only.");
            }
        }

        @NbBundle.Messages({
            "# {0} - month abbreviation",
            "# {1} - day of month",
            "# {2} - year",
            "DiscoveryAttributes.ActivityDateGroupKey.getDisplayNameTemplate=Week of {0} {1}, {2}"
        })
        @Override
        String getDisplayName() {
            MonthAbbreviation currentCutOffMonth = MonthAbbreviation.fromMonthValue(currentWeekCutOff.getMonthValue());
            return Bundle.DiscoveryAttributes_ActivityDateGroupKey_getDisplayNameTemplate(
                    currentCutOffMonth.toString(), Integer.toString(currentWeekCutOff.getDayOfMonth()),
                    Integer.toString(currentWeekCutOff.getYear()));
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof LastActivityDateGroupKey)) {
                return false;
            }

            LastActivityDateGroupKey dateGroupKey = (LastActivityDateGroupKey) otherKey;
            return getDisplayName().equals(dateGroupKey.getDisplayName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getDisplayName());
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof LastActivityDateGroupKey) {
                LastActivityDateGroupKey otherDateGroupKey = (LastActivityDateGroupKey) otherGroupKey;
                return Long.compare(otherDateGroupKey.currentWeekCutOff.toEpochSecond(), currentWeekCutOff.toEpochSecond());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }
    }

    /**
     * Get the next closed Sunday given an epoch time and timezone. Dates for
     * grouping are managed on a weekly basis. Each Sunday acts as the boundary
     * and representative for the week.
     */
    private static ZonedDateTime getCurrentWeekCutOff(long epochSeconds, ResultDomain domainResult) {
        Instant startActivityAsInsant = Instant.ofEpochSecond(epochSeconds);
        // Determines the timezone using the settings panel or value parsed from the
        // parent data source
        TimeZone currentTimeZone = TimeZoneUtils.getTimeZone();
        // Convert to a datetime using epoch and timezone.
        ZonedDateTime startActivityAsDateTime = ZonedDateTime.ofInstant(startActivityAsInsant, currentTimeZone.toZoneId());
        // Get the closest Sunday, which is the cut off for the current week.
        // Use this cut off to perform grouping and comparing.
        return startActivityAsDateTime.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    /**
     * Key representing a date of first activity.
     */
    static class FirstActivityDateGroupKey extends GroupKey {

        private ZonedDateTime currentWeekCutOff;

        /**
         * Construct a new FirstActivityDateGroupKey.
         *
         * @param result The Result to create the group key for.
         */
        FirstActivityDateGroupKey(Result result) {
            if (result instanceof ResultDomain) {
                ResultDomain domainResult = ((ResultDomain) result);
                currentWeekCutOff = getCurrentWeekCutOff(domainResult.getActivityStart(), domainResult);
            } else {
                throw new IllegalArgumentException("Expected a domain result only.");
            }
        }

        @Override
        String getDisplayName() {
            MonthAbbreviation currentCutOffMonth = MonthAbbreviation.fromMonthValue(currentWeekCutOff.getMonthValue());
            return Bundle.DiscoveryAttributes_ActivityDateGroupKey_getDisplayNameTemplate(
                    currentCutOffMonth.toString(), Integer.toString(currentWeekCutOff.getDayOfMonth()),
                    Integer.toString(currentWeekCutOff.getYear()));
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof FirstActivityDateGroupKey)) {
                return false;
            }

            FirstActivityDateGroupKey dateGroupKey = (FirstActivityDateGroupKey) otherKey;
            return getDisplayName().equals(dateGroupKey.getDisplayName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getDisplayName());
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof FirstActivityDateGroupKey) {
                FirstActivityDateGroupKey otherDateGroupKey = (FirstActivityDateGroupKey) otherGroupKey;
                return Long.compare(otherDateGroupKey.currentWeekCutOff.toEpochSecond(), currentWeekCutOff.toEpochSecond());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }
    }

    /**
     * Key representing the number of page views. Page views are defined as the
     * number of TSK_WEB_HISTORY artifacts that match a domain value.
     */
    static class PageViewsGroupKey extends GroupKey {

        private final String displayName;
        private final PageViews pageViews;

        /**
         * Construct a new NumberOfVisitsGroupKey.
         *
         * @param result The Result to create the group key for.
         */
        PageViewsGroupKey(Result result) {
            if (result instanceof ResultDomain) {
                Long totalPageViews = ((ResultDomain) result).getTotalPageViews();
                if (totalPageViews == null) {
                    totalPageViews = 0L;
                }
                pageViews = PageViews.fromPageViewCount(totalPageViews);
                displayName = pageViews.toString();
            } else {
                throw new IllegalArgumentException("Expected a domain instance only.");
            }
        }

        @Override
        String getDisplayName() {
            return displayName;
        }

        @Override
        public int hashCode() {
            return Objects.hash(displayName);
        }

        /**
         * Get the number of page views this group is for.
         *
         * @return The number of page views this group is for.
         */
        PageViews getPageViews() {
            return pageViews;
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof PageViewsGroupKey)) {
                return false;
            }

            PageViewsGroupKey pageViewsKey = (PageViewsGroupKey) otherKey;
            return pageViews.equals(pageViewsKey.getPageViews());
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof PageViewsGroupKey) {
                PageViewsGroupKey pageViewsKey = (PageViewsGroupKey) otherGroupKey;
                return getPageViews().compareTo(pageViewsKey.getPageViews());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }
    }

    /**
     * Key representing an object detected group.
     */
    static class ObjectDetectedGroupKey extends GroupKey {

        private final List<String> objectDetectedNames;
        private final String objectDetectedNamesString;

        /**
         * Construct a new ObjectDetectedGroupKey.
         *
         * @param file The file to create the group key for.
         */
        @NbBundle.Messages({
            "DiscoveryKeyUtils.ObjectDetectedGroupKey.noSets=None"})
        ObjectDetectedGroupKey(ResultFile file) {
            objectDetectedNames = file.getObjectDetectedNames();
            if (objectDetectedNames.isEmpty()) {
                objectDetectedNamesString = Bundle.DiscoveryKeyUtils_ObjectDetectedGroupKey_noSets();
            } else {
                objectDetectedNamesString = String.join(",", objectDetectedNames); // NON-NLS
            }
        }

        @Override
        String getDisplayName() {
            return getObjectDetectedNamesString();
        }

        @Override
        public int compareTo(GroupKey otherGroupKey) {
            if (otherGroupKey instanceof ObjectDetectedGroupKey) {
                ObjectDetectedGroupKey otherObjectDetectedGroupKey = (ObjectDetectedGroupKey) otherGroupKey;

                // Put the empty list at the end
                if (this.getObjectDetectedNames().isEmpty()) {
                    if (otherObjectDetectedGroupKey.getObjectDetectedNames().isEmpty()) {
                        return 0;
                    } else {
                        return 1;
                    }
                } else if (otherObjectDetectedGroupKey.getObjectDetectedNames().isEmpty()) {
                    return -1;
                }

                return getObjectDetectedNamesString().compareTo(otherObjectDetectedGroupKey.getObjectDetectedNamesString());
            } else {
                return compareClassNames(otherGroupKey);
            }
        }

        @Override
        public boolean equals(Object otherKey) {
            if (otherKey == this) {
                return true;
            }

            if (!(otherKey instanceof ObjectDetectedGroupKey)) {
                return false;
            }

            ObjectDetectedGroupKey otherObjectDetectedGroupKey = (ObjectDetectedGroupKey) otherKey;
            return getObjectDetectedNamesString().equals(otherObjectDetectedGroupKey.getObjectDetectedNamesString());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getObjectDetectedNamesString());
        }

        /**
         * Get the list of object detected names for this group.
         *
         * @return The list of object detected names for this group.
         */
        List<String> getObjectDetectedNames() {
            return Collections.unmodifiableList(objectDetectedNames);
        }

        /**
         * Get the String representation of the object detected names for this
         * group.
         *
         * @return The String representation of the object detected names for
         *         this group.
         */
        String getObjectDetectedNamesString() {
            return objectDetectedNamesString;
        }
    }

    /**
     * Private constructor for GroupKeyUtils utility class.
     */
    private DiscoveryKeyUtils() {
        //private constructor in a utility class intentionally left blank
    }
}
