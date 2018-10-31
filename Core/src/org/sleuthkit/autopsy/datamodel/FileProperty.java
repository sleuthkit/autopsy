/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

/**
 *
 * @author dsmyda
 */

//No modifier means its is only package usable (not part of public api)
abstract class FileProperty {
    
    private String description = "";
    private final String PROPERTY_NAME;
    
    public FileProperty(String propertyName) {
        PROPERTY_NAME = propertyName;
    }
    
    /**
     * 
     * @param content
     * @return 
     */
    public abstract Object getPropertyValue();
    
    /**
     * 
     * @return 
     */
    public String getPropertyName(){
        return PROPERTY_NAME;
    }
    /**
     * 
     * @return 
     */
    public boolean isEnabled() {
        return true;
    }
    
    /*
     * 
     */
    protected void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * 
     * @param content
     * @return 
     */
    public String getDescription() {
        return description;
    }
}
