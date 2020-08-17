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
import org.openide.util.NbBundle;

/**
 * Utility class for constructing keys for groups and searches.
 */
public class DiscoveryKeyUtils {

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
     * Default attribute used to make one group
     */
    static class NoGroupingAttribute extends FileSearch.AttributeType {

        @Override
        public GroupKey getGroupKey(ResultFile file) {
            return new NoGroupingGroupKey();
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
     * Private constructor for GroupKeyUtils utility class.
     */
    private DiscoveryKeyUtils() {
        //private constructor in a utility class intentionally left blank
    }
}
