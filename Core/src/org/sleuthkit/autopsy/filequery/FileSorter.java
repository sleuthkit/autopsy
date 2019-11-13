/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filequery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Class used to sort ResultFiles using the supplied method.
 */
class FileSorter implements Comparator<ResultFile> {

    private final List<Comparator<ResultFile>> comparators = new ArrayList<>();

    /**
     * Set up the sorter using the supplied sorting method. The sorting is
     * defined by a list of ResultFile comparators. These comparators will be
     * run in order until one returns a non-zero result.
     *
     * @param method The method that should be used to sort the files
     */
    FileSorter(SortingMethod method) {

        // Set up the primary comparators that should applied to the files
        switch (method) {
            case BY_DATA_SOURCE:
                comparators.add(getDataSourceComparator());
                break;
            case BY_FILE_SIZE:
                comparators.add(getFileSizeComparator());
                break;
            case BY_FILE_TYPE:
                comparators.add(getFileTypeComparator());
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
            default:
                // The default comparator will be added afterward
                break;
        }

        // Add the default comparator to the end. This will ensure a consistent sort
        // order regardless of the order the files were added to the list.
        comparators.add(getDefaultComparator());
    }

    @Override
    public int compare(ResultFile file1, ResultFile file2) {

        int result = 0;
        for (Comparator<ResultFile> comp : comparators) {
            result = comp.compare(file1, file2);
            if (result != 0) {
                return result;
            }
        }

        // The files are the same
        return result;
    }

    /**
     * Compare files using data source ID. Will order smallest to largest.
     *
     * @return -1 if file1 has the lower data source ID, 0 if equal, 1 otherwise
     */
    private static Comparator<ResultFile> getDataSourceComparator() {
        return (ResultFile file1, ResultFile file2) -> Long.compare(file1.getFirstInstance().getDataSourceObjectId(), file2.getFirstInstance().getDataSourceObjectId());
    }

    /**
     * Compare files using their FileType enum. Orders based on the ranking in
     * the FileType enum.
     *
     * @return -1 if file1 has the lower FileType value, 0 if equal, 1 otherwise
     */
    private static Comparator<ResultFile> getFileTypeComparator() {
        return (ResultFile file1, ResultFile file2) -> Integer.compare(file1.getFileType().getRanking(), file2.getFileType().getRanking());
    }

    /**
     * Compare files using a concatenated version of keyword list names.
     * Alphabetical by the list names with files with no keyword list hits going
     * last.
     *
     * @return -1 if file1 has the earliest combined keyword list name, 0 if
     *         equal, 1 otherwise
     */
    private static Comparator<ResultFile> getKeywordListNameComparator() {
        return (ResultFile file1, ResultFile file2) -> {
            // Put empty lists at the bottom
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
     * @return -1 if file1's path comes first alphabetically, 0 if equal, 1
     *         otherwise
     */
    private static Comparator<ResultFile> getParentPathComparator() {

        return new Comparator<ResultFile>() {
            @Override
            public int compare(ResultFile file1, ResultFile file2) {
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
     * Compare files based on number of occurrences in the central repository.
     * Order from most rare to least rare Frequency enum.
     *
     * @return -1 if file1's rarity is lower than file2, 0 if equal, 1 otherwise
     */
    private static Comparator<ResultFile> getFrequencyComparator() {
        return (ResultFile file1, ResultFile file2) -> Integer.compare(file1.getFrequency().getRanking(), file2.getFrequency().getRanking());
    }

    /**
     * Compare files based on MIME type. Order is alphabetical.
     *
     * @return -1 if file1's MIME type comes before file2's, 0 if equal, 1
     *         otherwise
     */
    private static Comparator<ResultFile> getMIMETypeComparator() {
        return (ResultFile file1, ResultFile file2) -> compareStrings(file1.getFirstInstance().getMIMEType(), file2.getFirstInstance().getMIMEType());
    }

    /**
     * Compare files based on size. Order large to small.
     *
     * @return -1 if file1 is larger than file2, 0 if equal, 1 otherwise
     */
    private static Comparator<ResultFile> getFileSizeComparator() {
        return (ResultFile file1, ResultFile file2) -> -1 * Long.compare(file1.getFirstInstance().getSize(), file2.getFirstInstance().getSize()) // Sort large to small
                ;
    }

    /**
     * Compare files based on file name. Order alphabetically.
     *
     * @return -1 if file1 comes before file2, 0 if equal, 1 otherwise
     */
    private static Comparator<ResultFile> getFileNameComparator() {
        return (ResultFile file1, ResultFile file2) -> compareStrings(file1.getFirstInstance().getName().toLowerCase(), file2.getFirstInstance().getName().toLowerCase());
    }

    /**
     * A final default comparison between two ResultFile objects. Currently this
     * is on file name and then object ID. It can be changed but should always
     * include something like the object ID to ensure a consistent sorting when
     * the rest of the compared fields are the same.
     *
     * @return -1 if file1 comes before file2, 0 if equal, 1 otherwise
     */
    private static Comparator<ResultFile> getDefaultComparator() {
        return (ResultFile file1, ResultFile file2) -> {
            // Compare file names and then object ID (to ensure a consistent sort)
            int result = getFileNameComparator().compare(file1, file2);
            if (result == 0) {
                return Long.compare(file1.getFirstInstance().getId(), file2.getFirstInstance().getId());
            }
            return result;
        };
    }

    /**
     * Compare two strings alphabetically. Nulls are allowed.
     *
     * @param s1
     * @param s2
     *
     * @return -1 if s1 comes before s2, 0 if equal, 1 otherwise
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
        "FileSorter.SortingMethod.datasource.displayName=By data source",
        "FileSorter.SortingMethod.filename.displayName=By file name",
        "FileSorter.SortingMethod.filesize.displayName=By file size",
        "FileSorter.SortingMethod.filetype.displayName=By file type",
        "FileSorter.SortingMethod.frequency.displayName=By central repo frequency",
        "FileSorter.SortingMethod.keywordlist.displayName=By keyword list names",
        "FileSorter.SortingMethod.fullPath.displayName=By full path"})
    enum SortingMethod {
        BY_FILE_NAME(new ArrayList<>(),
                Bundle.FileSorter_SortingMethod_filename_displayName()), // Sort alphabetically by file name
        BY_DATA_SOURCE(new ArrayList<>(),
                Bundle.FileSorter_SortingMethod_datasource_displayName()), // Sort in increasing order of data source ID
        BY_FILE_SIZE(new ArrayList<>(),
                Bundle.FileSorter_SortingMethod_filesize_displayName()), // Sort in decreasing order of size
        BY_FILE_TYPE(Arrays.asList(new FileSearch.FileTypeAttribute()),
                Bundle.FileSorter_SortingMethod_filetype_displayName()), // Sort in order of file type (defined in FileType enum), with secondary sort on MIME type
        BY_FREQUENCY(Arrays.asList(new FileSearch.FrequencyAttribute()),
                Bundle.FileSorter_SortingMethod_frequency_displayName()), // Sort by decreasing rarity in the central repository
        BY_KEYWORD_LIST_NAMES(Arrays.asList(new FileSearch.KeywordListAttribute()),
                Bundle.FileSorter_SortingMethod_keywordlist_displayName()), // Sort alphabetically by list of keyword list names found
        BY_FULL_PATH(new ArrayList<>(),
                Bundle.FileSorter_SortingMethod_fullPath_displayName());       // Sort alphabetically by path

        private final String displayName;
        private final List<FileSearch.AttributeType> requiredAttributes;

        SortingMethod(List<FileSearch.AttributeType> attributes, String displayName) {
            this.requiredAttributes = attributes;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        List<FileSearch.AttributeType> getRequiredAttributes() {
            return Collections.unmodifiableList(requiredAttributes);
        }

        /**
         * Get the list of enums that are valid for ordering images.
         *
         * @return enums that can be used to ordering images.
         */
        static List<SortingMethod> getOptionsForOrdering() {
            return Arrays.asList(BY_FILE_SIZE, BY_FULL_PATH, BY_FILE_NAME, BY_DATA_SOURCE);
        }
    }
}
