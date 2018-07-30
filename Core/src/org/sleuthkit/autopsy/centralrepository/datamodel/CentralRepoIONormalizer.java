/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides functions for normalizing data by type before insertion and querying.
 */
final public class CentralRepoIONormalizer {
    
    private static final Logger LOGGER = Logger.getLogger(CentralRepoIONormalizer.class.getName());
    private static final String EMPTY_STRING = "";
    
    /**
     * Normalize the data.
     * 
     * @param attributeType type of data
     * @param data data to normalize.
     * 
     * @return normalized data
     */
    static String normalize(CorrelationAttribute.Type attributeType, String data){
        
        switch(attributeType.getId()){
            case CorrelationAttribute.FILES_TYPE_ID:
                return normalizeMd5(data);
            case CorrelationAttribute.DOMAIN_TYPE_ID:
                return normalizeDomain(data);
            case CorrelationAttribute.EMAIL_TYPE_ID:
                return normalizeEmail(data);
            case CorrelationAttribute.PHONE_TYPE_ID:
                return normalizePhone(data);
            case CorrelationAttribute.USBID_TYPE_ID:
                return normalizeUsbId(data);
            default:
                throw new IllegalArgumentException("Normalizer not found for attribute type: " + attributeType.getDisplayName());            
        }        
    }

    private static String normalizeMd5(String data) {
        final String validMd5Regex = "/^[a-f0-9]{32}$/";
        final String dataLowered = data.toLowerCase();
        if(dataLowered.matches(validMd5Regex)){
            return dataLowered;
        } else {
            LOGGER.log(Level.WARNING, "Data purporting to be an MD5 was found not to comform to expected format."); //non-nls
            return EMPTY_STRING;
        }
    }

    private static String normalizeDomain(String data) {
        //commons or guava
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static String normalizeEmail(String data) {
        //commons
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static String normalizePhone(String data) {
        //TODO implement for real
        return data;
    }

    private static String normalizeUsbId(String data) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private CentralRepoIONormalizer() {
    }
    
    
}
