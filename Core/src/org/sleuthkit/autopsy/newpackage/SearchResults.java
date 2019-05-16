/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.newpackage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 */
class SearchResults {
    
    private final FileGroup.GroupSortingAlgorithm groupSortingType;
    private final FileSearch.AttributeType attrType;
    private final Comparator<ResultFile> fileSortingMethod;
    
    private final Map<Object, FileGroup> groupMap = new HashMap<>();
    
    SearchResults(FileGroup.GroupSortingAlgorithm groupSortingType, FileSearch.AttributeType attrType, 
            Comparator<ResultFile> fileSortingMethod) {
        this.groupSortingType = groupSortingType;
        this.attrType = attrType;
        this.fileSortingMethod = fileSortingMethod;
    }
    
    void add(ResultFile file) {
        Object groupID = attrType.getGroupIdentifier(file);
        
        if (groupMap.containsKey(groupID)) {
            groupMap.get(groupID).addFile(file);
        } else {
            groupMap.put(groupID, new FileGroup(attrType, groupSortingType, fileSortingMethod, file));
        }
    }
    
    void print() {
        
        System.out.println("\nSearchResults");
        for (Object key : groupMap.keySet()) {
            System.out.println("  " + groupMap.get(key).getDisplayName());
            
            for (AbstractFile file : groupMap.get(key).getAbstractFiles()) {
                System.out.println("    " + file.getName());
            }
        }
        
    }
}
