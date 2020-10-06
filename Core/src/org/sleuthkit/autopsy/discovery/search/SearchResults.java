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
package org.sleuthkit.autopsy.discovery.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;

/**
 * Class to hold the results of the filtering/grouping/sorting operations.
 */
class SearchResults {

    private final Group.GroupSortingAlgorithm groupSortingType;
    private final DiscoveryAttributes.AttributeType attrType;
    private final ResultsSorter fileSorter;

    private final Map<GroupKey, Group> groupMap = new HashMap<>();
    private List<Group> groupList = new ArrayList<>();

    private static final long MAX_OUTPUT_FILES = 2000; // For debug UI - maximum number of lines to print

    /**
     * Create an empty SearchResults object.
     *
     * @param groupSortingType  The method that should be used to
     *                          sortGroupsAndFiles the groups.
     * @param attrType          The attribute type to use for grouping.
     * @param fileSortingMethod The method that should be used to
     *                          sortGroupsAndFiles the files in each group.
     */
    SearchResults(Group.GroupSortingAlgorithm groupSortingType, DiscoveryAttributes.AttributeType attrType,
            ResultsSorter.SortingMethod fileSortingMethod) {
        this.groupSortingType = groupSortingType;
        this.attrType = attrType;
        this.fileSorter = new ResultsSorter(fileSortingMethod);
    }

    /**
     * Create an dummy SearchResults object that can be used in the UI before
     * the search is finished.
     */
    SearchResults() {
        this.groupSortingType = Group.GroupSortingAlgorithm.BY_GROUP_NAME;
        this.attrType = new DiscoveryAttributes.FileSizeAttribute();
        this.fileSorter = new ResultsSorter(ResultsSorter.SortingMethod.BY_FILE_NAME);
    }

    /**
     * Add a list of ResultFile to the results.
     *
     * @param files The list of ResultFiles to add.
     */
    void add(List<Result> results) {
        for (Result result : results) {
            // Add the file to the appropriate group, creating it if necessary
            GroupKey groupKey = attrType.getGroupKey(result);

            if (!groupMap.containsKey(groupKey)) {
                groupMap.put(groupKey, new Group(groupSortingType, groupKey));
            }
            groupMap.get(groupKey).addResult(result);
        }
    }

    /**
     * Run after all files have been added to sortGroupsAndFiles the groups and
     * files.
     */
    void sortGroupsAndFiles() {

        // First sortGroupsAndFiles the files
        for (Group group : groupMap.values()) {
            group.sortResults(fileSorter);
        }

        // Now put the groups in a list and sortGroupsAndFiles them
        groupList = new ArrayList<>(groupMap.values());
        Collections.sort(groupList);
    }

    @Override
    public String toString() {
        String resultString = "";
        if (groupList == null) {
            return resultString;
        }

        long count = 0;
        for (Group group : groupList) {
            resultString += group.getDisplayName() + "\n";

            for (Result result : group.getResults()) {
                resultString += "    " + result.toString() + "\n";
                count++;
                if (count > MAX_OUTPUT_FILES) {
                    resultString += "(truncated)";
                    return resultString;
                }
            }
        }
        return resultString;
    }

    /**
     * Get the names of the groups with counts.
     *
     * @return The list of group names.
     */
    List<String> getGroupNamesWithCounts() {
        return groupList.stream().map(p -> p.getDisplayName() + " (" + p.getResults().size() + ")").collect(Collectors.toList());
    }

    /**
     * Get the result files for the selected group.
     *
     * @param groupName The name of the group. Can have the size appended.
     *
     * @return The list of result files.
     */
    List<Result> getResultFilesInGroup(String groupName) {
        if (groupName != null) {
            final String modifiedGroupName = groupName.replaceAll(" \\([0-9]+\\)$", "");

            java.util.Optional<Group> group = groupList.stream().filter(p -> p.getDisplayName().equals(modifiedGroupName)).findFirst();
            if (group.isPresent()) {
                return group.get().getResults();
            }
        }
        return new ArrayList<>();
    }

    /**
     * Transform the results into a LinkedHashMap with result files.
     *
     * @return The grouped and sorted results.
     */
    Map<GroupKey, List<Result>> toLinkedHashMap() throws DiscoveryException {
        Map<GroupKey, List<Result>> map = new LinkedHashMap<>();

        // Sort the groups and files
        sortGroupsAndFiles();

        // groupList is sorted and a LinkedHashMap will preserve that order.
        for (Group group : groupList) {
            map.put(group.getGroupKey(), group.getResults());
        }

        return map;
    }
}
