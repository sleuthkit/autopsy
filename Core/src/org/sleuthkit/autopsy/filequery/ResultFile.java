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

import org.sleuthkit.autopsy.filequery.FileSearchData.FileType;
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
    private final List<String> hashSetNames;
    private final List<String> tagNames;
    private final List<String> interestingSetNames;
    private final List<String> objectDetectedNames;
    private FileType fileType;
    
    /**
     * Create a ResultFile from an AbstractFile
     * 
     * @param abstractFile 
     */
    ResultFile (AbstractFile abstractFile) {
        this.abstractFile = abstractFile;
        this.frequency = FileSearchData.Frequency.UNKNOWN;
        keywordListNames = new ArrayList<>();
        hashSetNames = new ArrayList<>();
        tagNames = new ArrayList<>();
        interestingSetNames = new ArrayList<>();
        objectDetectedNames = new ArrayList<>();
        
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
     * @return the keyword list names that matched this file.
     */
    List<String> getKeywordListNames() {
        return keywordListNames;
    }
    
    /**
     * Add a hash set name that matched this file.
     * 
     * @param hashSetName 
     */
    void addHashSetName (String hashSetName) {
        if (! hashSetNames.contains(hashSetName)) {
            hashSetNames.add(hashSetName);
        }
        
        // Sort the list so the getHashHitNames() will be consistent regardless of the order added
        Collections.sort(hashSetNames);
    }
    
    /**
     * Get the hash set names for this file
     * 
     * @return the hash set names that matched this file.
     */
    List<String> getHashSetNames() {
        return hashSetNames;
    }
    
    /**
     * Add a tag name that matched this file.
     * 
     * @param tagName 
     */
    void addTagName (String tagName) {
        if (! tagNames.contains(tagName)) {
            tagNames.add(tagName);
        }
        
        // Sort the list so the getTagNames() will be consistent regardless of the order added
        Collections.sort(tagNames);
    }
    
    /**
     * Get the tag names for this file
     * 
     * @return the tag names that matched this file.
     */
    List<String> getTagNames() {
        return tagNames;
    }
    
    /**
     * Add an interesting file set name that matched this file.
     * 
     * @param interestingSetName 
     */
    void addInterestingSetName (String interestingSetName) {
        if (! interestingSetNames.contains(interestingSetName)) {
            interestingSetNames.add(interestingSetName);
        }
        
        // Sort the list so the getInterestingSetNames() will be consistent regardless of the order added
        Collections.sort(interestingSetNames);
    }
    
    /**
     * Get the interesting item set names for this file
     * 
     * @return the interesting item set names that matched this file.
     */
    List<String> getInterestingSetNames() {
        return interestingSetNames;
    }
    
    /**
     * Add an object detected in this file.
     * 
     * @param objectDetectedName 
     */
    void addObjectDetectedName (String objectDetectedName) {
        if (! objectDetectedNames.contains(objectDetectedName)) {
            objectDetectedNames.add(objectDetectedName);
        }
        
        // Sort the list so the getObjectDetectedNames() will be consistent regardless of the order added
        Collections.sort(objectDetectedNames);
    }
    
    /**
     * Get the objects detected for this file
     * 
     * @return the objects detected in this file.
     */
    List<String> getObjectDetectedNames() {
        return objectDetectedNames;
    }
    
    /**
     * Get the AbstractFile
     * 
     * @return the AbstractFile object
     */
    AbstractFile getAbstractFile() {
        return abstractFile;
    }
    
    @Override
    public String toString() {
        return abstractFile.getName() + "(" + abstractFile.getId() + ") - "
                + abstractFile.getSize() + ", " + abstractFile.getParentPath() + ", " 
                + abstractFile.getDataSourceObjectId() + ", " + frequency.toString() + ", "
                + String.join(",", keywordListNames) + ", " + abstractFile.getMIMEType();
    }
}
