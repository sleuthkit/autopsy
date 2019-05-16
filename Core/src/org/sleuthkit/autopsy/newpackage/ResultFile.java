/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.newpackage;

import org.sleuthkit.datamodel.AbstractFile;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
class ResultFile {
    
    private final AbstractFile abstractFile;
    private FileSearchData.Frequency frequency;
    private final Set<String> keywordListNames;
    
    ResultFile (AbstractFile abstractFile) {
        this.abstractFile = abstractFile;
        this.frequency = FileSearchData.Frequency.UNKNOWN;
        keywordListNames = new HashSet<>();
    }
    
    FileSearchData.Frequency getFrequency() {
        return frequency;
    }
    
    void setFrequency (FileSearchData.Frequency frequency) {
        this.frequency = frequency;
    }
    
    void addKeywordListName (String keywordListName) {
        keywordListNames.add(keywordListName);
    }
    
    AbstractFile getAbstractFile() {
        return abstractFile;
    }
    
    void print() {
        System.out.println("Obj ID: " + abstractFile.getId());
        System.out.println("Name: " + abstractFile.getName());
        System.out.println("Size: " + abstractFile.getSize());
        System.out.println("Parent: " + abstractFile.getParentPath());
        System.out.println("Data Source: " + abstractFile.getDataSourceObjectId());
        System.out.println("Frequency: " + frequency.toString());
        System.out.print("Keyword lists: ");
        for (String name:keywordListNames) {
            System.out.print(name + " ");
        }
        System.out.println("\n");
    }
}
