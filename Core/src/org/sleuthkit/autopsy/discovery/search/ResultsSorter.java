/*
 * Autopsy Forensic Browser
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Class used to sort Results using the supplied method.
 */
public class ResultsSorter implements Comparator<Result> {

    private final List<Comparator<Result>> comparators = new ArrayList<>();

    /**
     * Set up the sorter using the supplied sorting method. The sorting is
     * defined by a list of Result comparators. These comparators will be run in
     * order until one returns a non-zero result.
     *
     * @param method The method that should be used to sort the results.
     */
    public ResultsSorter(SortingMethod method) {

        // Set up the primary comparators that should applied to the results
        switch (method) {
            case BY_DATA_SOURCE:
                comparators.add(getDataSourceComparator());
                break;
            case BY_FILE_SIZE:
                comparators.add(getFileSizeComparator());
                break;
            case BY_FILE_TYPE:
                comparators.add(getTypeComparator());
                comparators.add(getMIMETypeComparator());
                break;
            case BY_FREQUENCY:
                comparators.add(getFrequencyComparator());
                break;
            case BY_KEYWORD_LIST_NAMES:
                comparators.add(getKeywordListNameComparator());
                break;
            case BY_FULL_PATH:
                comparators.add(getParentPathComparator());
                break;
            case BY_FILE_NAME:
                comparators.add(getFileNameComparator());
                break;
            case BY_DOMAIN_NAME:
                comparators.add(getDomainNameComparator());
                break;
            case BY_PAGE_VIEWS:
                comparators.add(getPageViewComparator());
                break;
            case BY_LAST_ACTIVITY:
                comparators.add(getLastActivityDateTimeComparator());
                break;
            case BY_DOWNLOADS:
                comparators.add(getWebDownloadsComparator());
                break;
            default:
                // The default comparator will be added afterward
                break;
        }

        // Add the default comparator to the end. This will ensure a consistent sort
        // order regardless of the order the results were added to the list.
        comparators.add(getDefaultComparator());
    }

    @Override
    public int compare(Result result1, Result result2) {

        int result = 0;
        for (Comparator<Result> comp : comparators) {
            result = comp.compare(result1, result2);
            if (result != 0) {
                return result;
            }
        }

        // The results are the same
        return result;
    }

    /**
     * Compare results using data source ID. Will order smallest to largest.
     *
     * @return -1 if result1 has the lower data source ID, 0 if equal, 1
     *         otherwise.
     */
    private static Comparator<Result> getDataSourceComparator() {
        return (Result result1, Result result2) -> Long.compare(result1.getDataSourceObjectId(), result2.getDataSourceObjectId());
    }

    /**
     * Compare results using their Type enum. Orders based on the ranking in the
     * Type enum.
     *
     * @return -1 if result1 has the lower Type value, 0 if equal, 1 otherwise.
     */
    private static Comparator<Result> getTypeComparator() {
        return (Result result1, Result result2) -> Integer.compare(result1.getType().getRanking(), result2.getType().getRanking());
    }

    /**
     * Compare files using a concatenated version of keyword list names.
     * Alphabetical by the list names with files with no keyword list hits going
     * last.
     *
     * @return -1 if result1 has the earliest combined keyword list name, 0 if
     *         equal, 1 otherwise.
     */
    private static Comparator<Result> getKeywordListNameComparator() {
        return (Result result1, Result result2) -> {
            // Put empty lists at the bottom
            if (result1.getType() == SearchData.Type.DOMAIN) {
                return 0;
            }
            ResultFile file1 = (ResultFile) result1;
            ResultFile file2 = (ResultFile) result2;
            if (file1.getKeywordListNames().isEmpty()) {
                if (file2.getKeywordListNames().isEmpty()) {
                    return 0;
                }
                return 1;
            } else if (file2.getKeywordListNames().isEmpty()) {
                return -1;
            }

            String list1 = String.join(",", file1.getKeywordListNames());
            String list2 = String.join(",", file2.getKeywordListNames());
            return compareStrings(list1, list2);
        };
    }

    /**
     * Compare files based on parent path. Order alphabetically.
     *
     * @return -1 if result1's path comes first alphabetically, 0 if equal, 1
     *         otherwise.
     */
    private static Comparator<Result> getParentPathComparator() {

        return new Comparator<Result>() {
            @Override
            public int compare(Result result1, Result result2) {
                if (result1.getType() == SearchData.Type.DOMAIN) {
                    return 0;
                }
                ResultFile file1 = (ResultFile) result1;
                ResultFile file2 = (ResultFile) result2;
                String file1ParentPath;
                try {
                    file1ParentPath = file1.getFirstInstance().getParent().getUniquePath();
                } catch (TskCoreException ingored) {
                    file1ParentPath = file1.getFirstInstance().getParentPath();
                }
                String file2ParentPath;
                try {
                    file2ParentPath = file2.getFirstInstance().getParent().getUniquePath();
                } catch (TskCoreException ingored) {
                    file2ParentPath = file2.getFirstInstance().getParentPath();
                }
                return compareStrings(file1ParentPath.toLowerCase(), file2ParentPath.toLowerCase());
            }
        };
    }

    /**
     * Compare results based on number of occurrences in the central repository.
     * Order from most rare to least rare Frequency enum.
     *
     * @return -1 if result1's rarity is lower than result2, 0 if equal, 1
     *         otherwise.
     */
    private static Comparator<Result> getFrequencyComparator() {
        return (Result result1, Result result2) -> Integer.compare(result1.getFrequency().getRanking(), result2.getFrequency().getRanking());
    }

    /**
     * Compare files based on MIME type. Order is alphabetical.
     *
     * @return -1 if result1's MIME type comes before result2's, 0 if equal, 1
     *         otherwise.
     */
    private static Comparator<Result> getMIMETypeComparator() {
        return (Result result1, Result result2) -> {
            if (result1.getType() == SearchData.Type.DOMAIN) {
                return 0;
            }
            return compareStrings(((ResultFile) result1).getFirstInstance().getMIMEType(), ((ResultFile) result2).getFirstInstance().getMIMEType());
        };
    }

    /**
     * Compare files based on size. Order large to small.
     *
     * @return -1 if result1 is larger than result2, 0 if equal, 1 otherwise.
     */
    private static Comparator<Result> getFileSizeComparator() {
        return (Result result1, Result result2) -> {
            if (result1.getType() == SearchData.Type.DOMAIN) {
                return 0;
            }
            return -1 * Long.compare(((ResultFile) result1).getFirstInstance().getSize(), ((ResultFile) result2).getFirstInstance().getSize()); // Sort large to small
        };
    }

    /**
     * Compare files based on file name. Order alphabetically.
     *
     * @return -1 if result1 comes before result2, 0 if equal, 1 otherwise.
     */
    private static Comparator<Result> getFileNameComparator() {
        return (Result result1, Result result2) -> {
            if (result1.getType() == SearchData.Type.DOMAIN) {
                return 0;
            }
            return compareStrings(((ResultFile) result1).getFirstInstance().getName().toLowerCase(), (((ResultFile) result2).getFirstInstance().getName().toLowerCase()));
        };
    }

    /**
     * Sorts domain names in lexographical order, ignoring case.
     *
     * @return -1 if domain1 comes before domain2, 0 if equal, 1 otherwise.
     */
    private static Comparator<Result> getDomainNameComparator() {
        return (Result domain1, Result domain2) -> {
            if (domain1.getType() != SearchData.Type.DOMAIN) {
                return 0;
            }

            ResultDomain first = (ResultDomain) domain1;
            ResultDomain second = (ResultDomain) domain2;
            return compareStrings(first.getDomain().toLowerCase(), second.getDomain().toLowerCase());
        };
    }

    /**
     * Sorts domains by page view count.
     *
     * This comparator sorts results in descending order (largest -> smallest).
     */
    private static Comparator<Result> getPageViewComparator() {
        return (Result domain1, Result domain2) -> {
            if (domain1.getType() != SearchData.Type.DOMAIN
                    || domain2.getType() != SearchData.Type.DOMAIN) {
                return 0;
            }

            ResultDomain first = (ResultDomain) domain1;
            ResultDomain second = (ResultDomain) domain2;

            long firstPageViews = first.getTotalPageViews();
            long secondPageViews = second.getTotalPageViews();
            return Long.compare(secondPageViews, firstPageViews);
        };
    }

    /**
     * Sorts result domains by last activity date time. The results will be in
     * descending order.
     */
    private static Comparator<Result> getLastActivityDateTimeComparator() {
        return (Result domain1, Result domain2) -> {
            if (domain1.getType() != SearchData.Type.DOMAIN
                    || domain2.getType() != SearchData.Type.DOMAIN) {
                return 0;
            }
            ResultDomain first = (ResultDomain) domain1;
            ResultDomain second = (ResultDomain) domain2;

            long firstActivityEnd = first.getActivityEnd();
            long secondActivityEnd = second.getActivityEnd();
            return Long.compare(secondActivityEnd, firstActivityEnd);
        };
    }

    /**
     * Sorts result domains by most file downloads. The results will be in
     * descending order.
     */
    private static Comparator<Result> getWebDownloadsComparator() {
        return (Result domain1, Result domain2) -> {
            if (domain1.getType() != SearchData.Type.DOMAIN
                    || domain2.getType() != SearchData.Type.DOMAIN) {
                return 0;
            }
            ResultDomain first = (ResultDomain) domain1;
            ResultDomain second = (ResultDomain) domain2;

            long firstFilesDownloaded = first.getFilesDownloaded();
            long secondFilesDownloaded = second.getFilesDownloaded();
            return Long.compare(secondFilesDownloaded, firstFilesDownloaded);
        };
    }

    /**
     * A final default comparison between two ResultFile objects. Currently this
     * is on file name and then object ID. It can be changed but should always
     * include something like the object ID to ensure a consistent sorting when
     * the rest of the compared fields are the same.
     *
     * @return -1 if file1 comes before file2, 0 if equal, 1 otherwise.
     */
    private static Comparator<Result> getDefaultComparator() {
        return (Result result1, Result result2) -> {
            // Compare file names and then object ID (to ensure a consistent sort)
            if (result1.getType() == SearchData.Type.DOMAIN) {
                return getFrequencyComparator().compare(result1, result2);
            } else {
                ResultFile file1 = (ResultFile) result1;
                ResultFile file2 = (ResultFile) result2;
                int result = getFileNameComparator().compare(file1, file2);
                if (result == 0) {
                    return Long.compare(file1.getFirstInstance().getId(), file2.getFirstInstance().getId());
                }
                return result;
            }

        };
    }

    /**
     * Compare two strings alphabetically. Nulls are allowed.
     *
     * @param s1
     * @param s2
     *
     * @return -1 if s1 comes before s2, 0 if equal, 1 otherwise.
     */
    private static int compareStrings(String s1, String s2) {
        String string1 = s1 == null ? "" : s1;
        String string2 = s2 == null ? "" : s2;
        return string1.compareTo(string2);

    }

    /**
     * Enum for selecting the primary method for sorting result files.
     */
    @NbBundle.Messages({
        "FileSorter.SortingMethod.datasource.displayName=Data Source",
        "FileSorter.SortingMethod.filename.displayName=File Name",
        "FileSorter.SortingMethod.filesize.displayName=File Size",
        "FileSorter.SortingMethod.filetype.displayName=File Type",
        "FileSorter.SortingMethod.frequency.displayName=Central Repo Frequency",
        "FileSorter.SortingMethod.keywordlist.displayName=Keyword List Names",
        "FileSorter.SortingMethod.fullPath.displayName=Full Path",
        "FileSorter.SortingMethod.domain.displayName=Domain Name",
        "FileSorter.SortingMethod.pageViews.displayName=Page Views",
        "FileSorter.SortingMethod.downloads.displayName=File Downloads",
        "FileSorter.SortingMethod.activity.displayName=Last Activity Date"})
    public enum SortingMethod {
        BY_FILE_NAME(new ArrayList<>(),
                Bundle.FileSorter_SortingMethod_filename_displayName()), // Sort alphabetically by file name
        BY_DATA_SOURCE(new ArrayList<>(),
                Bundle.FileSorter_SortingMethod_datasource_displayName()), // Sort in increasing order of data source ID
        BY_FILE_SIZE(new ArrayList<>(),
                Bundle.FileSorter_SortingMethod_filesize_displayName()), // Sort in decreasing order of size
        BY_FILE_TYPE(Arrays.asList(new DiscoveryAttributes.FileTypeAttribute()),
                Bundle.FileSorter_SortingMethod_filetype_displayName()), // Sort in order of file type (defined in FileType enum), with secondary sort on MIME type
        BY_FREQUENCY(Arrays.asList(new DiscoveryAttributes.FrequencyAttribute()),
                Bundle.FileSorter_SortingMethod_frequency_displayName()), // Sort by decreasing rarity in the central repository
        BY_KEYWORD_LIST_NAMES(Arrays.asList(new DiscoveryAttributes.KeywordListAttribute()),
                Bundle.FileSorter_SortingMethod_keywordlist_displayName()), // Sort alphabetically by list of keyword list names found
        BY_FULL_PATH(new ArrayList<>(),
                Bundle.FileSorter_SortingMethod_fullPath_displayName()), // Sort alphabetically by path
        BY_DOMAIN_NAME(Arrays.asList(new DiscoveryAttributes.DomainCategoryAttribute(), new DiscoveryAttributes.PreviouslyNotableAttribute()), Bundle.FileSorter_SortingMethod_domain_displayName()),
        BY_PAGE_VIEWS(Arrays.asList(new DiscoveryAttributes.DomainCategoryAttribute(), new DiscoveryAttributes.PreviouslyNotableAttribute()), Bundle.FileSorter_SortingMethod_pageViews_displayName()),
        BY_DOWNLOADS(Arrays.asList(new DiscoveryAttributes.DomainCategoryAttribute(), new DiscoveryAttributes.PreviouslyNotableAttribute()), Bundle.FileSorter_SortingMethod_downloads_displayName()),
        BY_LAST_ACTIVITY(Arrays.asList(new DiscoveryAttributes.DomainCategoryAttribute(), new DiscoveryAttributes.PreviouslyNotableAttribute()), Bundle.FileSorter_SortingMethod_activity_displayName());

        private final String displayName;
        private final List<DiscoveryAttributes.AttributeType> requiredAttributes;

        /**
         * Construct a new SortingMethod enum value.
         *
         * @param attributes  The list of DiscoveryAttributes required by this
         *                    enum value.
         * @param displayName The display name for this enum value.
         */
        SortingMethod(List<DiscoveryAttributes.AttributeType> attributes, String displayName) {
            this.requiredAttributes = attributes;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        /**
         * Get the list of DiscoveryAttributes required by this enum value.
         *
         * @return The list of DiscoveryAttributes required by this enum value.
         */
        public List<DiscoveryAttributes.AttributeType> getRequiredAttributes() {
            return Collections.unmodifiableList(requiredAttributes);
        }

        /**
         * Get the list of enum values that are valid for ordering files.
         *
         * @return Enum values that can be used to ordering files.
         */
        public static List<SortingMethod> getOptionsForOrderingFiles() {
            return Arrays.asList(BY_FILE_SIZE, BY_FULL_PATH, BY_FILE_NAME, BY_DATA_SOURCE);
        }

        /**
         * Get the list of enum values that are valid for ordering files.
         *
         * @return Enum values that can be used to ordering files.
         */
        public static List<SortingMethod> getOptionsForOrderingDomains() {
            return Arrays.asList(BY_PAGE_VIEWS, BY_DOWNLOADS, BY_LAST_ACTIVITY, BY_DOMAIN_NAME);
        }

    }
}
