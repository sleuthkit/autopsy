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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class to hold the results of the filtering/grouping/sorting operations
 */
class SearchResults {
    
    private final FileGroup.GroupSortingAlgorithm groupSortingType;
    private final FileSearch.AttributeType attrType;
    private final FileSorter fileSorter;
    
    private final Map<FileSearch.GroupKey, FileGroup> groupMap = new HashMap<>();
    private List<FileGroup> groupList = new ArrayList<>();
    
    private final long MAX_OUTPUT_FILES = 2000; // For debug UI - maximum number of lines to print
    
    /**
     * Create an empty SearchResults object
     * 
     * @param groupSortingType  The method that should be used to sortGroupsAndFiles the groups
     * @param attrType          The attribute type to use for grouping
     * @param fileSortingMethod The method that should be used to sortGroupsAndFiles the files in each group
     */
    SearchResults(FileGroup.GroupSortingAlgorithm groupSortingType, FileSearch.AttributeType attrType, 
            FileSorter.SortingMethod fileSortingMethod) {
        this.groupSortingType = groupSortingType;
        this.attrType = attrType;
        this.fileSorter = new FileSorter(fileSortingMethod);
    }
    
    /**
     * Create an dummy SearchResults object that can be used in the UI before the search is finished.
     */
    SearchResults() {
        this.groupSortingType = FileGroup.GroupSortingAlgorithm.BY_GROUP_KEY;
        this.attrType = new FileSearch.FileSizeAttribute();
        this.fileSorter = new FileSorter(FileSorter.SortingMethod.BY_FILE_NAME);
    }   
    
    /**
     * Add a list of ResultFile to the results
     * 
     * @param files the ResultFiles
     */
    void add(List<ResultFile> files) {
        for (ResultFile file : files) {
            // Add the file to the appropriate group, creating it if necessary
            FileSearch.GroupKey groupKey = attrType.getGroupKey(file);

            if ( ! groupMap.containsKey(groupKey)) {
                groupMap.put(groupKey, new FileGroup(groupSortingType, groupKey));            
            }
            groupMap.get(groupKey).addFile(file);
        }
    }
    
    /**
     * Run after all files have been added to sortGroupsAndFiles the groups and files.
     */
    void sortGroupsAndFiles() {
        
        // First sortGroupsAndFiles the files
        for (FileGroup group : groupMap.values()) {
            group.sortFiles(fileSorter);
        }
        
        // Now put the groups in a list and sortGroupsAndFiles them
        groupList = new ArrayList<>(groupMap.values());
        Collections.sort(groupList);
    }
    
    @Override
    public String toString() {
        String result = "";
        if (groupList == null) {
            return result;
        }
        
        long count = 0;
        for (FileGroup group : groupList) {
            result += group.getDisplayName() + "\n";
            
            for (ResultFile file : group.getResultFiles()) {
                result += "    " + file.toString() + "\n";
                count++;
                if (count > MAX_OUTPUT_FILES) {
                    result += "(truncated)";
                    return result;
                }
            }
        }
        return result;
    }
    
    /**
     * Get the names of the groups with counts
     * 
     * @return the group names
     */
    List<String> getGroupNamesWithCounts() {
        return groupList.stream().map(p -> p.getDisplayName() + " (" + p.getResultFiles().size() + ")").collect(Collectors.toList());
    }
    
    /**
     * Get the abstract files for the selected group
     * 
     * @param groupName The name of the group. Can have the size appended.
     * @return the list of abstract files
     */
    List<ResultFile> getAbstractFilesInGroup(String groupName) {
        if (groupName != null) {
            final String modifiedGroupName = groupName.replaceAll(" \\([0-9]+\\)$", "");

            java.util.Optional<FileGroup> fileGroup = groupList.stream().filter(p -> p.getDisplayName().equals(modifiedGroupName)).findFirst();
            if (fileGroup.isPresent()) {
                return fileGroup.get().getAbstractFiles();
            }
        }
        return new ArrayList<>();       
    }
    
    /**
     * Transform the results into a LinkedHashMap with abstract files.
     * 
     * @return the grouped and sorted results
     */
    Map<String, List<ResultFile>> toLinkedHashMap() throws FileSearchException {
        Map<String, List<ResultFile>> map = new LinkedHashMap<>();
        
        // Sort the groups and files
        sortGroupsAndFiles();
        
        // groupList is sorted and a LinkedHashMap will preserve that order.
        for (FileGroup group : groupList) {
            map.put(group.getDisplayName(), group.getAbstractFiles());
        }
        
        return map;
    }
}
