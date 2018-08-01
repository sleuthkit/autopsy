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
import org.apache.commons.validator.routines.EmailValidator;

/**
 * Provides functions for normalizing data by attribute type before insertion or querying.
 */
final public class CentralRepoIONormalizer {
    
    private static final Logger LOGGER = Logger.getLogger(CentralRepoIONormalizer.class.getName());
    private static final String EMPTY_STRING = "";
    
    /**
     * This is a utility class - no need for constructing or subclassing, etc...
     */
    private CentralRepoIONormalizer() { }
    
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
    public static String normalize(CorrelationAttribute.Type attributeType, String data) throws CentralRepoValidationException {
        
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
                throw new CentralRepoValidationException("Normalizer function not found for attribute type: " + attributeType.getDisplayName());   
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
    public static String normalize(int attributeTypeId, String data) throws CentralRepoValidationException {
        try {
            List<CorrelationAttribute.Type> defaultTypes = CorrelationAttribute.getDefaultCorrelationTypes();
            Optional<CorrelationAttribute.Type> typeOption = defaultTypes.stream().filter(attributeType -> attributeType.getId() == attributeTypeId).findAny();
            
            if(typeOption.isPresent()){
                CorrelationAttribute.Type type = typeOption.get();
                return CentralRepoIONormalizer.normalize(type, data);
            } else {
                throw new CentralRepoValidationException(String.format("Given attributeTypeId did not correspond to any known Attribute: %s", attributeTypeId));
            }
        } catch (EamDbException ex) {
            throw new CentralRepoValidationException(ex);
        }
    }

    private static String normalizeMd5(String data) throws CentralRepoValidationException {
        final String validMd5Regex = "^[a-fA-F0-9]{32}$";
        final String dataLowered = data.toLowerCase();
        if(dataLowered.matches(validMd5Regex)){
            return dataLowered;
        } else {
            throw new CentralRepoValidationException(String.format("Data purporting to be an MD5 was found not to comform to expected format: %s", data));
        }
    }

    private static String normalizeDomain(String data) throws CentralRepoValidationException {
        DomainValidator validator = DomainValidator.getInstance(true);
        if(validator.isValid(data)){
            return data.toLowerCase();
        } else {
            throw new CentralRepoValidationException(String.format("Data was expected to be a valid domain: %s", data));
        }
    }

    private static String normalizeEmail(String data) throws CentralRepoValidationException {
        EmailValidator validator = EmailValidator.getInstance(true, true);
        if(validator.isValid(data)){
            return data.toLowerCase();
        } else {
            throw new CentralRepoValidationException(String.format("Data was expected to be a valid email address: %s", data));
        }
    }

    @SuppressWarnings("DeadBranch")
    private static String normalizePhone(String data) throws CentralRepoValidationException {
        //TODO implement for real and get rid of suppression
        if(true){
            return data;
        } else {
            throw new CentralRepoValidationException(String.format("Data was expected to be a valid phone number: %s", data));
        }
    }

    private static String normalizeUsbId(String data) throws CentralRepoValidationException {
        //usbId is of the form: hhhh:hhhh where h is a hex digit
        String validUsbIdRegex = "^(0[Xx])?[A-Fa-f0-9]{4}[:\\s-\\.](0[Xx])?[A-Fa-f0-9]{4}$";
        if(data.matches(validUsbIdRegex)){
            return data.toLowerCase();
        } else {
            throw new CentralRepoValidationException(String.format("Data was expected to be a valid USB device ID: %s", data));
        }
    }
}
