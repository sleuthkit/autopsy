/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications.relationships;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 *
 * Class to format and get information from a phone number
 */
public final class PhoneNumUtil {

    private static final Logger logger = Logger.getLogger(PhoneNumUtil.class.getName());
 
    private PhoneNumUtil() {
    }
    
    /**
     * Get the country code from a phone number
     * 
     * @param phoneNumber 
     * @return country code if can determine otherwise it will return ""
     */ 
    public static String getCountryCode(String phoneNumber) {
        String regionCode = null;
        try {
            PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber phoneNum = phoneNumberUtil.parse(phoneNumber, "");
            regionCode = phoneNumberUtil.getRegionCodeForNumber(phoneNum);

            if (regionCode == null) {
                return "";
            } else {
                return regionCode;
            }
        } catch (NumberParseException ex) {
            logger.log(Level.WARNING, "Error getting country code, for phone number: {0}", phoneNumber);
            return "";
        }
    }

    /**
     * Convert a phone number to the E164 format
     *
     * @param phoneNumber
     *
     * @return formated phone number if successful or original phone number if
     *         unsuccessful
     */
    public static String convertToE164(String phoneNumber) {
        PhoneNumberUtil phone_util = PhoneNumberUtil.getInstance();
        try {
            Phonenumber.PhoneNumber phoneProto = phone_util.parse(phoneNumber, getCountryCode(phoneNumber));
            if (phone_util.isValidNumber(phoneProto)) {
                return phone_util.format(phoneProto, PhoneNumberUtil.PhoneNumberFormat.E164);
            } else {
                logger.log(Level.WARNING, "Invalid phone number: {0}", phoneNumber);
                return phoneNumber;
            }
        } catch (NumberParseException e) {
            logger.log(Level.WARNING, "Error parsing phone number: {0}", phoneNumber);
            return phoneNumber;
        }
    }

    /**
     * Convert a phone number to the International format
     *
     * @param phoneNumber
     *
     * @return formated phone number if successful or original phone number if
     *         unsuccessful
     */
    public static String convertToInternational(String phoneNumber) {
        PhoneNumberUtil phone_util = PhoneNumberUtil.getInstance();
        try {
            Phonenumber.PhoneNumber phoneProto = phone_util.parse(phoneNumber, getCountryCode(phoneNumber));
            if (phone_util.isValidNumber(phoneProto)) {
                return phone_util.format(phoneProto, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
            } else {
                logger.log(Level.WARNING, "Invalid phone number: {0}", phoneNumber);
                return phoneNumber;
            }
        } catch (NumberParseException e) {
            logger.log(Level.WARNING, "Error parsing phone number: {0}", phoneNumber);
            return phoneNumber;
        }
    }

}
