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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.openide.util.NbBundle.Messages;

/**
 * Class for storing results that belong to a particular group.
 */
public class Group implements Comparable<Group> {

    private final Group.GroupSortingAlgorithm groupSortingType;
    private final DiscoveryKeyUtils.GroupKey groupKey;
    private final List<Result> results;
    private final String displayName;

    /**
     * Create a Group object with its first result.
     *
     * @param groupSortingType The method for sorting the group
     * @param groupKey         The GroupKey for this group
     */
    public Group(Group.GroupSortingAlgorithm groupSortingType, DiscoveryKeyUtils.GroupKey groupKey) {
        this.groupSortingType = groupSortingType;
        this.groupKey = groupKey;
        results = new ArrayList<>();
        this.displayName = groupKey.getDisplayName();
    }

    /**
     * Add a Result to the group. Will not be sorted at this time.
     *
     * @param result The Result to add to the Group.
     */
    void addResult(Result result) {
        if (result.getType() != SearchData.Type.DOMAIN && results.contains(result)) {
            //dedupe files and show instances
            ResultFile existingCopy = (ResultFile) results.get(results.indexOf(result)); //get the copy of this which exists in the list
            existingCopy.addDuplicate(((ResultFile) result).getFirstInstance());
        } else {
            //Domains and non files are not being deduped currently
            results.add(result);
        }
    }

    /**
     * Get the display name for this group.
     *
     * @return The display name of the group.
     */
    public String getDisplayName() {
        return displayName; // NON-NLS
    }

    /**
     * Get the key which uniquely identifies each group.
     *
     * @return The unique key for the group.
     */
    public DiscoveryKeyUtils.GroupKey getGroupKey() {
        return groupKey;
    }

    /**
     * Sort all the results in the group
     */
    public void sortResults(ResultsSorter sorter) {
        Collections.sort(results, sorter);
    }

    /**
     * Compare this group to another group for sorting. Uses the algorithm
     * specified in groupSortingType.
     *
     * @param otherGroup The group to compare this one to.
     *
     * @return -1 if this group should be displayed before the other group, 1
     *         otherwise.
     */
    @Override
    public int compareTo(Group otherGroup) {

        switch (groupSortingType) {
            case BY_GROUP_SIZE:
                return compareGroupsBySize(this, otherGroup);
            case BY_GROUP_NAME:
            default:
                return compareGroupsByGroupKey(this, otherGroup);
        }
    }

    /**
     * Compare two groups based on the group key.
     *
     * @param group1 The first group to be compared.
     * @param group2 The second group to be compared.
     *
     * @return -1 if group1 should be displayed before group2, 1 otherwise.
     */
    private static int compareGroupsByGroupKey(Group group1, Group group2) {
        return group1.getGroupKey().compareTo(group2.getGroupKey());
    }

    /**
     * Compare two groups based on the group size. Falls back on the group key
     * if the groups are the same size.
     *
     * @param group1 The first group to be compared.
     * @param group2 The second group to be compared.
     *
     * @return -1 if group1 should be displayed before group2, 1 otherwise.
     */
    private static int compareGroupsBySize(Group group1, Group group2) {
        if (group1.getResults().size() != group2.getResults().size()) {
            return -1 * Long.compare(group1.getResults().size(), group2.getResults().size()); // High to low
        } else {
            // If the groups have the same size, fall through to the BY_GROUP_NAME sorting
            return compareGroupsByGroupKey(group1, group2);
        }
    }

    /**
     * Enum to specify how to sort the group.
     */
    @Messages({"FileGroup.groupSortingAlgorithm.groupSize.text=Group Size",
        "FileGroup.groupSortingAlgorithm.groupName.text=Group Name"})
    public enum GroupSortingAlgorithm {
        BY_GROUP_NAME(Bundle.FileGroup_groupSortingAlgorithm_groupName_text()), // Sort using the group key (for example, if grouping by size sort from largest to smallest value)
        BY_GROUP_SIZE(Bundle.FileGroup_groupSortingAlgorithm_groupSize_text());  // Sort from largest to smallest group

        private final String displayName;

        /**
         * Construct a GroupSortingAlgorithm enum value.
         *
         * @param name The name to display to the user for the enum value.
         */
        GroupSortingAlgorithm(String name) {
            displayName = name;
        }

        @Override
        public String toString() {
            return displayName;
        }

    }

    /**
     * Get the list of Result objects in the group.
     *
     * @return The list of Result objects.
     */
    public List<Result> getResults() {
        return Collections.unmodifiableList(results);
    }

}
