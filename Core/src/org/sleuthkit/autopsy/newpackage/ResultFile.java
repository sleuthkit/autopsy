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

import org.sleuthkit.autopsy.newpackage.FileSearchData.FileType;
import org.sleuthkit.datamodel.AbstractFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Container for files that holds all necessary data for grouping and sorting
 */
class ResultFile {
    
    private final AbstractFile abstractFile;
    private FileSearchData.Frequency frequency;
    private final List<String> keywordListNames;
    private FileType fileType;
    
    ResultFile (AbstractFile abstractFile) {
        this.abstractFile = abstractFile;
        this.frequency = FileSearchData.Frequency.UNKNOWN;
        keywordListNames = new ArrayList<>();
        fileType = FileType.OTHER;
    }
    
    /**
     * Get the frequency of this file in the central repository
     * 
     * @return The Frequency enum
     */
    FileSearchData.Frequency getFrequency() {
        return frequency;
    }
    
    /**
     * Set the frequency of this file from the central repository
     * 
     * @param frequency The frequency of the file as an enum
     */
    void setFrequency (FileSearchData.Frequency frequency) {
        this.frequency = frequency;
    }
    
    /**
     * Get the file type
     * 
     * @return The FileType enum
     */
    FileType getFileType() {
        return fileType;
    }
    
    /**
     * Set the file type
     * 
     * @param fileType the type
     */
    void setFileType(FileType fileType) {
        this.fileType = fileType;
    }
    
    /**
     * Add a keyword list name that matched this file.
     * 
     * @param keywordListName 
     */
    void addKeywordListName (String keywordListName) {
        if (! keywordListNames.contains(keywordListName)) {
            keywordListNames.add(keywordListName);
        }
        
        // Sort the list so the getKeywordListNames() will be consistent regardless of the order added
        Collections.sort(keywordListNames);
    }
    
    /**
     * Get the keyword list names for this file
     * 
     * @return the keyword lists that matched this file.
     */
    List<String> getKeywordListNames() {
        return keywordListNames;
    }
    
    /**
     * Get the AbstractFile
     * 
     * @return the AbstractFile object
     */
    AbstractFile getAbstractFile() {
        return abstractFile;
    }
    
    /**
     * For debugging - print out the file name and object ID along with all the fields
     * we can group and sort on.
     * 
     * @param prefix String to print before the file data (for indenting)
     */
    void print(String prefix) {
        System.out.println(prefix + abstractFile.getName() + "(" + abstractFile.getId() + ") - "
                + abstractFile.getSize() + ", " + abstractFile.getParentPath() + ", " 
                + abstractFile.getDataSourceObjectId() + ", " + frequency.toString() + ", "
                + String.join(",", keywordListNames) + ", " + abstractFile.getMIMEType());
        
    }
}
