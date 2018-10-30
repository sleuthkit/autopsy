/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * @author dsmyda
 */

//No modifier means its is only package usable (not part of public api)
interface FileProperty {
    /**
     * 
     * @param content
     * @return 
     */
    Object getPropertyValue(AbstractFile content);
    
    /**
     * 
     * @return 
     */
    default String getPropertyName(){
        return this.toString();
    }
    
    /**
     * 
     * @return 
     */
    default boolean isDisabled() {
        return false;
    }
    
    /**
     * 
     * @param content
     * @return 
     */
    default String getDescription(AbstractFile content) {
        return "";
    }
}
