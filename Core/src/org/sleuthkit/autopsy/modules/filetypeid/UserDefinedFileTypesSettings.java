/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.filetypeid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author oliver
 */
class UserDefinedFileTypesSettings implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<FileType> userDefinedFileTypes;
    
    UserDefinedFileTypesSettings(List<FileType> userDefinedFileTypes) {
        this.userDefinedFileTypes = userDefinedFileTypes;
    }

    /**
     * @return the userDefinedFileTypes
     */
    public List<FileType> getUserDefinedFileTypes() {
        return userDefinedFileTypes;
    }
    
    
}
