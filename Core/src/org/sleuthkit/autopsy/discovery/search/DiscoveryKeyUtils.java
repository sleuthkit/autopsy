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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
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

        /**
         * Construct a new SearchKey with all information that defines a search.
         *
         * @param userName           The name of the user performing the search.
         * @param filters            The FileFilters being used for the search.
         * @param groupAttributeType The AttributeType to group by.
         * @param groupSortingType   The algorithm to sort the groups by.
         * @param fileSortingMethod  The method to sort the files by.
         */
        SearchKey(String userName, List<AbstractFilter> filters,
                FileSearch.AttributeType groupAttributeType,
                FileGroup.GroupSortingAlgorithm groupSortingType,
                FileSorter.SortingMethod fileSortingMethod) {
            StringBuilder searchStringBuilder = new StringBuilder();
            searchStringBuilder.append(userName);
            for (AbstractFilter filter : filters) {
                searchStringBuilder.append(filter.toString());
            }
            searchStringBuilder.append(groupAttributeType).append(groupSortingType).append(fileSortingMethod);
            keyString = searchStringBuilder.toString();
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
            return getKeyString().equals(otherSearchKey.getKeyString());
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 79 * hash + Objects.hashCode(getKeyString());
            return hash;
        }

        /**
         * @return the keyString
         */
        String getKeyString() {
            return keyString;
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
         * @param otherKey
         *
         * @return true if the keys are equal, false otherwise
         */
        @Override
        abstract public boolean equals(Object otherKey);

        /**
         * Subclasses must implement hashCode().
         *
         * @return the hash code
         */
        @Override
        abstract public int hashCode();

        /**
         * It should not happen with the current setup, but we need to cover the
         * case where two different GroupKey subclasses are compared against
         * each other. Use a lexicographic comparison on the class names.
         *
         * @param otherGroupKey The other group key
         *
         * @return result of alphabetical comparison on the class name
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
     * Key representing a file size group
     */
    static class FileSizeGroupKey extends GroupKey {

        private final FileSearchData.FileSize fileSize;

        FileSizeGroupKey(ResultFile file) {
            if (file.getFileType() == FileSearchData.FileType.VIDEO) {
                fileSize = FileSearchData.FileSize.fromVideoSize(file.getFirstInstance().getSize());
            } else {
                fileSize = FileSearchData.FileSize.fromImageSize(file.getFirstInstance().getSize());
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
         * @return the fileSize
         */
        FileSearchData.FileSize getFileSize() {
            return fileSize;
        }
    }

    /**
     * Key representing a file type group
     */
    static class FileTypeGroupKey extends GroupKey {

        private final FileSearchData.FileType fileType;

        FileTypeGroupKey(ResultFile file) {
            fileType = file.getFileType();
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
         * @return the fileType
         */
        FileSearchData.FileType getFileType() {
            return fileType;
        }
    }

    /**
     * Key representing a keyword list group
     */
    static class KeywordListGroupKey extends GroupKey {

        private final List<String> keywordListNames;
        private final String keywordListNamesString;

        @NbBundle.Messages({
            "FileSearch.KeywordListGroupKey.noKeywords=None"})
        KeywordListGroupKey(ResultFile file) {
            keywordListNames = file.getKeywordListNames();

            if (keywordListNames.isEmpty()) {
                keywordListNamesString = Bundle.FileSearch_KeywordListGroupKey_noKeywords();
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
         * @return the keywordListNames
         */
        List<String> getKeywordListNames() {
            return Collections.unmodifiableList(keywordListNames);
        }

        /**
         * @return the keywordListNamesString
         */
        String getKeywordListNamesString() {
            return keywordListNamesString;
        }
    }

    /**
     * Key representing a file tag group
     */
    static class FileTagGroupKey extends GroupKey {

        private final List<String> tagNames;
        private final String tagNamesString;

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
         * @return the tagNames
         */
        List<String> getTagNames() {
            return Collections.unmodifiableList(tagNames);
        }

        /**
         * @return the tagNamesString
         */
        String getTagNamesString() {
            return tagNamesString;
        }
    }

    /**
     * Key representing a parent path group
     */
    static class ParentPathGroupKey extends GroupKey {

        private String parentPath;
        private Long parentID;

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
         * @return the parentPath
         */
        String getParentPath() {
            return parentPath;
        }

        /**
         * @return the parentID
         */
        Long getParentID() {
            return parentID;
        }
    }

    /**
     * Key representing a data source group
     */
    static class DataSourceGroupKey extends GroupKey {

        private final long dataSourceID;
        private String displayName;

        @NbBundle.Messages({
            "# {0} - Data source name",
            "# {1} - Data source ID",
            "FileSearch.DataSourceGroupKey.datasourceAndID={0}(ID: {1})",
            "# {0} - Data source ID",
            "FileSearch.DataSourceGroupKey.idOnly=Data source (ID: {0})"})
        DataSourceGroupKey(ResultFile file) {
            dataSourceID = file.getFirstInstance().getDataSourceObjectId();

            try {
                // The data source should be cached so this won't actually be a database query.
                Content ds = file.getFirstInstance().getDataSource();
                displayName = Bundle.FileSearch_DataSourceGroupKey_datasourceAndID(ds.getName(), ds.getId());
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error looking up data source with ID " + dataSourceID, ex); // NON-NLS
                displayName = Bundle.FileSearch_DataSourceGroupKey_idOnly(dataSourceID);
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
         * @return the dataSourceID
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
     * Key representing a central repository frequency group
     */
    static class FrequencyGroupKey extends GroupKey {

        private final FileSearchData.Frequency frequency;

        FrequencyGroupKey(ResultFile file) {
            frequency = file.getFrequency();
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
         * @return the frequency
         */
        FileSearchData.Frequency getFrequency() {
            return frequency;
        }
    }

    /**
     * Key representing a hash hits group
     */
    static class HashHitsGroupKey extends GroupKey {

        private final List<String> hashSetNames;
        private final String hashSetNamesString;

        @NbBundle.Messages({
            "FileSearch.HashHitsGroupKey.noHashHits=None"})
        HashHitsGroupKey(ResultFile file) {
            hashSetNames = file.getHashSetNames();

            if (hashSetNames.isEmpty()) {
                hashSetNamesString = Bundle.FileSearch_HashHitsGroupKey_noHashHits();
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
         * @return the hashSetNames
         */
        List<String> getHashSetNames() {
            return Collections.unmodifiableList(hashSetNames);
        }

        /**
         * @return the hashSetNamesString
         */
        String getHashSetNamesString() {
            return hashSetNamesString;
        }
    }

    /**
     * Key representing a interesting item set group
     */
    static class InterestingItemGroupKey extends GroupKey {

        private final List<String> interestingItemSetNames;
        private final String interestingItemSetNamesString;

        @NbBundle.Messages({
            "FileSearch.InterestingItemGroupKey.noSets=None"})
        InterestingItemGroupKey(ResultFile file) {
            interestingItemSetNames = file.getInterestingSetNames();

            if (interestingItemSetNames.isEmpty()) {
                interestingItemSetNamesString = Bundle.FileSearch_InterestingItemGroupKey_noSets();
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
         * @return the interestingItemSetNames
         */
        List<String> getInterestingItemSetNames() {
            return Collections.unmodifiableList(interestingItemSetNames);
        }

        /**
         * @return the interestingItemSetNamesString
         */
        String getInterestingItemSetNamesString() {
            return interestingItemSetNamesString;
        }
    }

    /**
     * Key representing an object detected group
     */
    static class ObjectDetectedGroupKey extends GroupKey {

        private final List<String> objectDetectedNames;
        private final String objectDetectedNamesString;

        @NbBundle.Messages({
            "FileSearch.ObjectDetectedGroupKey.noSets=None"})
        ObjectDetectedGroupKey(ResultFile file) {
            objectDetectedNames = file.getObjectDetectedNames();

            if (objectDetectedNames.isEmpty()) {
                objectDetectedNamesString = Bundle.FileSearch_ObjectDetectedGroupKey_noSets();
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
         * @return the objectDetectedNames
         */
        List<String> getObjectDetectedNames() {
            return Collections.unmodifiableList(objectDetectedNames);
        }

        /**
         * @return the objectDetectedNamesString
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
