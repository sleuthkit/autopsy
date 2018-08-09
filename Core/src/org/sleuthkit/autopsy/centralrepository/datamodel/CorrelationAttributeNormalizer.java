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
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.EmailValidator;

/**
 * Provides functions for normalizing data by attribute type before insertion or querying.
 */
final public class CorrelationAttributeNormalizer {
    
    /**
     * This is a utility class - no need for constructing or subclassing, etc...
     */
    private CorrelationAttributeNormalizer() { }
    
    /**
     * Normalize the data.  Converts text to lower case, and ensures that the
     * data is a valid string of the format expected given the attributeType.
     *  
     * @param attributeType correlation type of data
     * @param data data to normalize
     * 
     * @return normalized data
     */
    public static String normalize(CorrelationAttribute.Type attributeType, String data) throws CorrelationAttributeNormalizationException {
        
        final String errorMessage = "Validator function not found for attribute type: %s";
        
        if(attributeType == null){
            throw new CorrelationAttributeNormalizationException(String.format(errorMessage, "null"));
        }
        
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
                throw new CorrelationAttributeNormalizationException(String.format(errorMessage, attributeType.getDisplayName()));   
        }
    }
    
    /**
     * Validate the data.  Converts text to lower case, and ensures that the
     * data is a valid string of the format expected given the attributeType.
     *  
     * @param attributeTypeId correlation type of data
     * @param data data to normalize
     * 
     * @return normalized data
     */
    public static String normalize(int attributeTypeId, String data) throws CorrelationAttributeNormalizationException {
        try {
            List<CorrelationAttribute.Type> defaultTypes = CorrelationAttribute.getDefaultCorrelationTypes();
            Optional<CorrelationAttribute.Type> typeOption = defaultTypes.stream().filter(attributeType -> attributeType.getId() == attributeTypeId).findAny();
            
            if(typeOption.isPresent()){
                CorrelationAttribute.Type type = typeOption.get();
                return CorrelationAttributeNormalizer.normalize(type, data);
            } else {
                throw new CorrelationAttributeNormalizationException(String.format("Given attributeTypeId did not correspond to any known Attribute: %s", attributeTypeId));
            }
        } catch (EamDbException ex) {
            throw new CorrelationAttributeNormalizationException(ex);
        }
    }

    /**
     * Verify MD5 is the correct length and values. Make lower case.
     */
    private static String normalizeMd5(String data) throws CorrelationAttributeNormalizationException {
        final String errorMessage = "Data purporting to be an MD5 was found not to comform to expected format: %s";
        if(data == null){
            throw new CorrelationAttributeNormalizationException(String.format(errorMessage, data));
        }
        final String validMd5Regex = "^[a-fA-F0-9]{32}$";
        final String dataLowered = data.toLowerCase();
        if(dataLowered.matches(validMd5Regex)){
            return dataLowered;
        } else {
            throw new CorrelationAttributeNormalizationException(String.format(errorMessage, data));
        }
    }

    /**
     * Verify there are no slashes or invalid domain name characters (such as '?' or \: ). Normalize to lower case.
     */
    private static String normalizeDomain(String data) throws CorrelationAttributeNormalizationException {
        DomainValidator validator = DomainValidator.getInstance(true);
        if(validator.isValid(data)){
            return data.toLowerCase();
        } else {
            throw new CorrelationAttributeNormalizationException(String.format("Data was expected to be a valid domain: %s", data));
        }
    }

    /**
     *  Verify that there is an '@' and no invalid characters. Should normalize to lower case.
     */
    private static String normalizeEmail(String data) throws CorrelationAttributeNormalizationException {
        EmailValidator validator = EmailValidator.getInstance(true, true);
        if(validator.isValid(data)){
            return data.toLowerCase();
        } else {
            throw new CorrelationAttributeNormalizationException(String.format("Data was expected to be a valid email address: %s", data));
        }
    }

    /**
     * Verify it is only numbers and '+'. Strip spaces, dashes, and parentheses.
     */
    private static String normalizePhone(String data) throws CorrelationAttributeNormalizationException {
        String phoneNumber = data.replaceAll("[^0-9\\+]", "");
        if(phoneNumber.matches("\\+?[0-9]+")){
            return phoneNumber;
        } else {
            throw new CorrelationAttributeNormalizationException(String.format("Data was expected to be a valid phone number: %s", data));
        }
    }

    /**
     * USB ID is of the form: hhhh:hhhh where h is a hex digit.  Convert to lower case.
     */
    private static String normalizeUsbId(String data) throws CorrelationAttributeNormalizationException {
        final String errorMessage = "Data was expected to be a valid USB device ID: %s";
        if(data == null){
            throw new CorrelationAttributeNormalizationException(String.format(errorMessage, data));
        }
        
        String validUsbIdRegex = "^(0[Xx])?[A-Fa-f0-9]{4}[:\\\\\\ \\-.]?(0[Xx])?[A-Fa-f0-9]{4}$";
        if(data.matches(validUsbIdRegex)){
            return data.toLowerCase();
        } else {
            throw new CorrelationAttributeNormalizationException(String.format(errorMessage, data));
        }
    }
}
