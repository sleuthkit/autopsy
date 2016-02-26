/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.interestingitems;

import java.io.Serializable;
import java.util.Map;

/**
 *
 * @author oliver
 */
class InterestingItemsFilesSetSettings implements Serializable {
    private static final long serialVersionUID = 1L;
    private Map<String, FilesSet> filesSets;
    InterestingItemsFilesSetSettings(Map<String, FilesSet> filesSets) {
        this.filesSets = filesSets;
    }

    /**
     * @return the filesSets
     */
    Map<String, FilesSet> getFilesSets() {
        return filesSets;
    }
    
    
    
}
