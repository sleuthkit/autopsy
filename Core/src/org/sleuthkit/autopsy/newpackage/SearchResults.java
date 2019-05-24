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
package org.sleuthkit.autopsy.newpackage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Class to hold the results of the filtering/grouping/sorting operations
 */
class SearchResults {
    
    private final FileGroup.GroupSortingAlgorithm groupSortingType;
    private final FileSearch.AttributeType attrType;
    private final Comparator<ResultFile> fileSortingMethod;
    
    private final Map<Object, FileGroup> groupMap = new HashMap<>();
    private List<FileGroup> groupList = null;
    
    /**
     * Create an empty SearchResults object
     * 
     * @param groupSortingType  The method that should be used to sortGroupsAndFiles the groups
     * @param attrType          The attribute type to use for grouping
     * @param fileSortingMethod The method that should be used to sortGroupsAndFiles the files in each group
     */
    SearchResults(FileGroup.GroupSortingAlgorithm groupSortingType, FileSearch.AttributeType attrType, 
            Comparator<ResultFile> fileSortingMethod) {
        this.groupSortingType = groupSortingType;
        this.attrType = attrType;
        this.fileSortingMethod = fileSortingMethod;
    }
    
    /**
     * Add a ResultFile to the results
     * 
     * @param file the ResultFile
     */
    void add(ResultFile file) {
        FileSearch.GroupKey groupKey = attrType.getGroupKey(file);
        
        if ( ! groupMap.containsKey(groupKey)) {
            groupMap.put(groupKey, new FileGroup(groupSortingType, fileSortingMethod, groupKey));            
        }
        groupMap.get(groupKey).addFile(file);
    }
    
    /**
     * Run after all files have been added to sortGroupsAndFiles the groups and files.
     */
    private void sortGroupsAndFiles() {
        
        // First sortGroupsAndFiles the files
        for (FileGroup group : groupMap.values()) {
            group.sortFiles();
        }
        
        // Now put the groups in a list and sortGroupsAndFiles them
        groupList = new ArrayList<>(groupMap.values());
        Collections.sort(groupList);
        
        // Debugging - print the results here
        System.out.println("\nSearchResults");
        for (FileGroup group : groupList) {
            System.out.println("  " + group.getDisplayName());
            
            for (ResultFile file : group.getResultFiles()) {
                file.print("    ");
            }
        }
    }
    
    /**
     * Transform the results into a LinkedHashMap with abstract files.
     * 
     * @return the grouped and sorted results
     */
    LinkedHashMap<String, List<AbstractFile>> toLinkedHashMap() throws FileSearchException {
        LinkedHashMap<String, List<AbstractFile>> map = new LinkedHashMap<>();
        
        // Sort the groups and files
        sortGroupsAndFiles();
        
        // groupList is sorted and a LinkedHashMap will preserve that order.
        for (FileGroup group : groupList) {
            map.put(group.getDisplayName(), group.getAbstractFiles());
        }
        
        return map;
    }
}
