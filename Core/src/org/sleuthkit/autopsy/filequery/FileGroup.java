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
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.filequery.FileSearch.GroupKey;

/**
 * Class for storing files that belong to a particular group.
 */
class FileGroup implements Comparable<FileGroup> {

    private final FileGroup.GroupSortingAlgorithm groupSortingType;
    private final GroupKey groupKey;
    private final List<ResultFile> files;
    private final String displayName;

    /**
     * Create a FileGroup object with its first file.
     *
     * @param groupSortingType The method for sorting the group
     * @param groupKey         The GroupKey for this group
     */
    FileGroup(FileGroup.GroupSortingAlgorithm groupSortingType, GroupKey groupKey) {
        this.groupSortingType = groupSortingType;
        this.groupKey = groupKey;
        files = new ArrayList<>();
        this.displayName = groupKey.getDisplayName();
    }

    /**
     * Add a ResultFile to the group. Will not be sorted at this time.
     *
     * @param file The ResultFile to add to the FileGroup
     */
    void addFile(ResultFile file) {
        if (getFiles().contains(file)) {
            ResultFile existingCopy = files.get(files.indexOf(file)); //get the copy of this which exists in the list
            existingCopy.addDuplicate(file.getFirstInstance());
        } else {
            files.add(file);
        }
    }

    /**
     * Get the display name for this group.
     *
     * @return The display name of the group.
     */
    String getDisplayName() {
        return displayName; // NON-NLS
    }

    /**
     * Get the key which uniquely identifies each group.
     *
     * @return The unique key for the group.
     */
    GroupKey getGroupKey() {
        return groupKey;
    }

    /**
     * Sort all the files in the group
     */
    void sortFiles(FileSorter sorter) {
        Collections.sort(getFiles(), sorter);
    }

    /**
     * Compare this group to another group for sorting. Uses the algorithm
     * specified in groupSortingType.
     *
     * @param otherGroup the group to compare this one to
     *
     * @return -1 if this group should be displayed before the other group, 1
     *         otherwise
     */
    @Override
    public int compareTo(FileGroup otherGroup) {

        switch (groupSortingType) {
            case BY_GROUP_SIZE:
                return compareGroupsBySize(this, otherGroup);
            case BY_GROUP_KEY:
            default:
                return compareGroupsByGroupKey(this, otherGroup);
        }
    }

    /**
     * Compare two groups based on the group key
     *
     * @param group1
     * @param group2
     *
     * @return -1 if group1 should be displayed before group2, 1 otherwise
     */
    private static int compareGroupsByGroupKey(FileGroup group1, FileGroup group2) {
        return group1.getGroupKey().compareTo(group2.getGroupKey());
    }

    /**
     * Compare two groups based on the group size. Falls back on the group key
     * if the groups are the same size.
     *
     * @param group1
     * @param group2
     *
     * @return -1 if group1 should be displayed before group2, 1 otherwise
     */
    private static int compareGroupsBySize(FileGroup group1, FileGroup group2) {
        if (group1.getFiles().size() != group2.getFiles().size()) {
            return -1 * Long.compare(group1.getFiles().size(), group2.getFiles().size()); // High to low
        } else {
            // If the groups have the same size, fall through to the BY_GROUP_KEY sorting
            return compareGroupsByGroupKey(group1, group2);
        }
    }

    /**
     * Enum to specify how to sort the group.
     */
    enum GroupSortingAlgorithm {
        BY_GROUP_SIZE, // Sort from largest to smallest group
        BY_GROUP_KEY   // Sort using the group key (for example, if grouping by size sort from largest to smallest value)
    }

    /**
     * Get the list of ResultFile objects in the group
     *
     * @return List of ResultFile objects
     */
    List<ResultFile> getFiles() {
        return Collections.unmodifiableList(files);
    }

}
