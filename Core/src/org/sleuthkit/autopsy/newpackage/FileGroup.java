/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.newpackage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 */
class FileGroup implements Comparable<FileGroup> {
    
    private final FileGroup.GroupSortingAlgorithm groupSortingType;
    private final FileSearch.AttributeType attrType;
    private final Comparator<ResultFile> fileSortingMethod;
    private final List<ResultFile> files;
    private final String displayName;
    
    FileGroup(FileSearch.AttributeType attrType, FileGroup.GroupSortingAlgorithm groupSortingType, 
            Comparator<ResultFile> fileSortingMethod, ResultFile resultFile) {
        this.groupSortingType = groupSortingType;
        this.attrType = attrType;
        this.fileSortingMethod = fileSortingMethod;
        files = new ArrayList<>();
        files.add(resultFile);
        this.displayName = attrType.getGroupName(resultFile);
    }
    
    void addFile(ResultFile file) {
        files.add(file);
    }
    
    String getDisplayName() {
        return displayName + " (" + files.size() + ")";
    }
    
    List<AbstractFile> getAbstractFiles() {
        return files.stream().map(file -> file.getAbstractFile()).collect(Collectors.toList());
    }
    
    void sortFiles() {
        Collections.sort(files, fileSortingMethod);
    }
    
    // Probably only for testing
    List<ResultFile> getResultFiles() {
        return files;
    }
    
    @Override
    public int compareTo(FileGroup otherGroup) {
        
        if (groupSortingType == FileGroup.GroupSortingAlgorithm.BY_GROUP_SIZE) {
            return -1 * Long.compare(files.size(), otherGroup.files.size()); // High to low
        }
        
        // Otherwise, compare the first two files using the default sorting of the grouping attribute.
        // File groups are never empty.
        Comparator<ResultFile> comparator = attrType.getDefaultFileComparator();
        return comparator.compare(files.get(0), otherGroup.files.get(0));
    }   
    
    enum GroupSortingAlgorithm {
	BY_GROUP_SIZE,
	BY_ATTRIBUTE
    }

}
