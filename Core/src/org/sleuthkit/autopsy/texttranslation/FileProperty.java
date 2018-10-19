/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.texttranslation;

import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * @author dsmyda
 */
public interface FileProperty {
    /**
     * 
     * @param content
     * @return 
     */
    public Object getPropertyValue(AbstractFile content);
    
    /**
     * 
     * @return 
     */
    public default String getPropertyName(){
        return this.toString();
    }
    
    /**
     * 
     * @return 
     */
    public default boolean isDisabled() {
        return false;
    }
    
    /**
     * 
     * @param content
     * @return 
     */
    public default String getDescription(AbstractFile content) {
        return "";
    }
}
