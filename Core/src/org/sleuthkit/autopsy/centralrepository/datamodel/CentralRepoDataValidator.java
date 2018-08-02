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
final public class CentralRepoDataValidator {
    
    /**
     * This is a utility class - no need for constructing or subclassing, etc...
     */
    private CentralRepoDataValidator() { }
    
    /**
     * Validate the data.  Converts text to lower case, and ensures that the
     * data is a valid string of the format expected given the attributeType.
     *  
     * @param attributeType correlation type of data
     * @param data data to validate
     * 
     * @return normalized data
     */
    public static String validate(CorrelationAttribute.Type attributeType, String data) throws CentralRepoValidationException {
        
        switch(attributeType.getId()){
            case CorrelationAttribute.FILES_TYPE_ID:
                return validateMd5(data);
            case CorrelationAttribute.DOMAIN_TYPE_ID:
                return validateDomain(data);
            case CorrelationAttribute.EMAIL_TYPE_ID:
                return validateEmail(data);
            case CorrelationAttribute.PHONE_TYPE_ID:
                return validatePhone(data);
            case CorrelationAttribute.USBID_TYPE_ID:
                return validateUsbId(data);
            default:
                throw new CentralRepoValidationException("Normalizer function not found for attribute type: " + attributeType.getDisplayName());   
        }
    }
    
    /**
     * Validate the data.  Converts text to lower case, and ensures that the
     * data is a valid string of the format expected given the attributeType.
     *  
     * @param attributeTypeId correlation type of data
     * @param data data to validate
     * 
     * @return normalized data
     */
    public static String validate(int attributeTypeId, String data) throws CentralRepoValidationException {
        try {
            List<CorrelationAttribute.Type> defaultTypes = CorrelationAttribute.getDefaultCorrelationTypes();
            Optional<CorrelationAttribute.Type> typeOption = defaultTypes.stream().filter(attributeType -> attributeType.getId() == attributeTypeId).findAny();
            
            if(typeOption.isPresent()){
                CorrelationAttribute.Type type = typeOption.get();
                return CentralRepoDataValidator.validate(type, data);
            } else {
                throw new CentralRepoValidationException(String.format("Given attributeTypeId did not correspond to any known Attribute: %s", attributeTypeId));
            }
        } catch (EamDbException ex) {
            throw new CentralRepoValidationException(ex);
        }
    }

    private static String validateMd5(String data) throws CentralRepoValidationException {
        final String errorMessage = "Data purporting to be an MD5 was found not to comform to expected format: %s";
        if(data == null){
            throw new CentralRepoValidationException(String.format(errorMessage, data));
        }
        final String validMd5Regex = "^[a-fA-F0-9]{32}$";
        final String dataLowered = data.toLowerCase();
        if(dataLowered.matches(validMd5Regex)){
            return dataLowered;
        } else {
            throw new CentralRepoValidationException(String.format(errorMessage, data));
        }
    }

    private static String validateDomain(String data) throws CentralRepoValidationException {
        DomainValidator validator = DomainValidator.getInstance(true);
        if(validator.isValid(data)){
            return data.toLowerCase();
        } else {
            throw new CentralRepoValidationException(String.format("Data was expected to be a valid domain: %s", data));
        }
    }

    private static String validateEmail(String data) throws CentralRepoValidationException {
        EmailValidator validator = EmailValidator.getInstance(true, true);
        if(validator.isValid(data)){
            return data.toLowerCase();
        } else {
            throw new CentralRepoValidationException(String.format("Data was expected to be a valid email address: %s", data));
        }
    }

    @SuppressWarnings("DeadBranch")
    private static String validatePhone(String data) throws CentralRepoValidationException {
        //TODO implement for real and get rid of suppression
        if(true){
            return data;
        } else {
            throw new CentralRepoValidationException(String.format("Data was expected to be a valid phone number: %s", data));
        }
    }

    private static String validateUsbId(String data) throws CentralRepoValidationException {
        final String errorMessage = "Data was expected to be a valid USB device ID: %s";
        if(data == null){
            throw new CentralRepoValidationException(String.format(errorMessage, data));
        }
        //usbId is of the form: hhhh:hhhh where h is a hex digit
        String validUsbIdRegex = "^(0[Xx])?[A-Fa-f0-9]{4}[:\\\\\\ \\-.]?(0[Xx])?[A-Fa-f0-9]{4}$";
        if(data.matches(validUsbIdRegex)){
            return data.toLowerCase();
        } else {
            throw new CentralRepoValidationException(String.format(errorMessage, data));
        }
    }
}
