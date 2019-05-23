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
import java.util.List;
import java.util.stream.Collectors;

import org.sleuthkit.datamodel.AbstractFile;

/**
 * Class for storing files that belong to a particular group.
 */
class FileGroup implements Comparable<FileGroup> {
    
    private final FileGroup.GroupSortingAlgorithm groupSortingType;
    private final FileSearch.AttributeType attrType;
    private final Comparator<ResultFile> fileSortingMethod;
    private final List<ResultFile> files;
    private final String displayName;
    
    /**
     * Create a FileGroup object with its first file.
     * 
     * @param attrType          The type of attribute being used for grouping
     * @param groupSortingType  The method for sorting the group
     * @param fileSortingMethod The method for sorting files within the group
     * @param resultFile        The first file to add to this group
     */
    FileGroup(FileSearch.AttributeType attrType, FileGroup.GroupSortingAlgorithm groupSortingType, 
            Comparator<ResultFile> fileSortingMethod, ResultFile resultFile) {
        this.groupSortingType = groupSortingType;
        this.attrType = attrType;
        this.fileSortingMethod = fileSortingMethod;
        files = new ArrayList<>();
        files.add(resultFile);
        this.displayName = attrType.getGroupName(resultFile);
    }
    
    /**
     * Add a ResultFile to the group.
     * Will not be sorted at this time.
     * 
     * @param file The ResultFile to add to the FileGroup
     */
    void addFile(ResultFile file) {
        files.add(file);
    }
    
    /**
     * Get the display name for this group, including the size of the group.
     * This must be unique for each group.
     * 
     * @return the display name
     */
    String getDisplayName() {
        return displayName + " (" + files.size() + ")"; // NON-NLS
    }
    
    /**
     * Pull the AbstractFile objects out of the ResultFile objects.
     * 
     * @return List of abstract files
     */
    List<AbstractFile> getAbstractFiles() {
        return files.stream().map(file -> file.getAbstractFile()).collect(Collectors.toList());
    }
    
    /**
     * Sort all the files in the group
     */
    void sortFiles() {
        Collections.sort(files, fileSortingMethod);
    }
    
    /**
     * Get the list of ResultFile objects in the group
     * 
     * @return List of ResultFile objects
     */
    List<ResultFile> getResultFiles() {
        return files;
    }
    
    /**
     * Compare this group to another group for sorting.
     * Uses the algorithm specified in groupSortingType.
     * 
     * @param otherGroup the group to compare this one to
     * 
     * @return -1 if this group should be displayed before the other group, 1 otherwise
     */
    @Override
    public int compareTo(FileGroup otherGroup) {
        
        if (groupSortingType == FileGroup.GroupSortingAlgorithm.BY_GROUP_SIZE) {
            
            if (files.size() != otherGroup.files.size()) {
                return -1 * Long.compare(files.size(), otherGroup.files.size()); // High to low
            }
            // If the groups have the same size, fall through to BY_ATTRIBUTE
        }
        
        // Compare the first two files using the default sorting of the grouping attribute.
        // File groups are never empty.
        Comparator<ResultFile> comparator = attrType.getDefaultFileComparator();
        return comparator.compare(files.get(0), otherGroup.files.get(0));
        // TODO make separate methods
    }   
    
    /**
     * Enum to specify how to sort the group.
     */
    enum GroupSortingAlgorithm {
	BY_GROUP_SIZE,
	BY_ATTRIBUTE
    }

}
