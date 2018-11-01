/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
    public void setDescription(String description) {
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
