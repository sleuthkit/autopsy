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

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.validator.routines.DomainValidator;

/**
 * Provides functions for normalizing data by attribute type before insertion or querying.
 */
final public class CentralRepoIONormalizer {
    
    private static final Logger LOGGER = Logger.getLogger(CentralRepoIONormalizer.class.getName());
    private static final String EMPTY_STRING = "";
    
    /**
     * Normalize the data.  To lower, in addition to various domain specific 
     * checks and transformations:
     * 
     * //TODO other specifics here...
     * 
     * @param attributeType correlation type of data
     * @param data data to normalize
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
                Exception exception = new IllegalArgumentException("Normalizer not found for attribute type: " + attributeType.getDisplayName());    
                log(exception);
                return data;
        }        
    }
    
    /**
     * Normalize the data.  To lower, in addition to various domain specific 
     * checks and transformations:
     * 
     * //TODO other specifics here...
     * 
     * @param attributeTypeId correlation type of data
     * @param data data to normalize
     * 
     * @return normalized data
     */
    static String normalize(int attributeTypeId, String data){
        try {
            List<CorrelationAttribute.Type> defaultTypes = CorrelationAttribute.getDefaultCorrelationTypes();
            Optional<CorrelationAttribute.Type> typeOption = defaultTypes.stream().filter(attributeType -> attributeType.getId() == attributeTypeId).findAny();
            
            if(typeOption.isPresent()){
                CorrelationAttribute.Type type = typeOption.get();
                return CentralRepoIONormalizer.normalize(type, data);
            } else {
                Exception exception = new IllegalArgumentException(String.format("Given attributeTypeId did not correspond to any known Attribute: %s", attributeTypeId));
                log(exception);
                return data;
            }
        } catch (EamDbException ex) {
            log(ex);
            return data;
        }
    }
    
    private static void log(Throwable throwable){
        LOGGER.log(Level.WARNING, "Data not normalized - using original data.", throwable);
    }

    private static String normalizeMd5(String data) {
        final String validMd5Regex = "/^[a-f0-9]{32}$/";
        final String dataLowered = data.toLowerCase();
        if(dataLowered.matches(validMd5Regex)){
            return dataLowered;
        } else {
            LOGGER.log(Level.WARNING, String.format("Data purporting to be an MD5 was found not to comform to expected format: %s", data)); //non-nls
            return EMPTY_STRING;
        }
    }

    private static String normalizeDomain(String data) {
        DomainValidator validator = DomainValidator.getInstance(true);
        if(validator.isValid(data)){
            return data.toLowerCase();
        } else {
            LOGGER.log(Level.WARNING, String.format("Data was expected to be a valid domain: %s", data)); //non-nls
            return EMPTY_STRING;
        }
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
