/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.newpackage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 */
class FileGroup {
    
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
        return displayName;
    }
    
    List<AbstractFile> getAbstractFiles() {
        return files.stream().map(file -> file.getAbstractFile()).collect(Collectors.toList());
    }
    
    
    enum GroupSortingAlgorithm {
	BY_GROUP_SIZE,
	BY_ATTRIBUTE
    }

}
