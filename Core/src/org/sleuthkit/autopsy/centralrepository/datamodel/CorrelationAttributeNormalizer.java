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
 * Provides functions for normalizing data by attribute type before insertion or
 * querying.
 */
final public class CorrelationAttributeNormalizer {

    //common seperators that may be removed for normalizing
    private static final String SEPERATORS_REGEX = "[\\s-:]";
    private static final int MIN_PHONE_LENGTH = 5;

    /**
     * This is a utility class - no need for constructing or subclassing, etc...
     */
    private CorrelationAttributeNormalizer() {
    }

    /**
     * Normalize the data. Converts text to lower case, and ensures that the
     * data is a valid string of the format expected given the attributeType.
     *
     * @param attributeType correlation type of data
     * @param data          data to normalize
     *
     * @return normalized data
     */
    public static String normalize(CorrelationAttributeInstance.Type attributeType, String data) throws CorrelationAttributeNormalizationException {

        if (attributeType == null) {
            throw new CorrelationAttributeNormalizationException("Attribute type was null.");
        }
        if (data == null) {
            throw new CorrelationAttributeNormalizationException("Data was null.");
        }

        String trimmedData = data.trim();

        switch (attributeType.getId()) {
            case CorrelationAttributeInstance.FILES_TYPE_ID:
                return normalizeMd5(trimmedData);
            case CorrelationAttributeInstance.DOMAIN_TYPE_ID:
                return normalizeDomain(trimmedData);
            case CorrelationAttributeInstance.EMAIL_TYPE_ID:
                return normalizeEmail(trimmedData);
            case CorrelationAttributeInstance.PHONE_TYPE_ID:
                return normalizePhone(trimmedData);
            case CorrelationAttributeInstance.USBID_TYPE_ID:
                return normalizeUsbId(trimmedData);
            case CorrelationAttributeInstance.SSID_TYPE_ID:
                return verifySsid(trimmedData);
            case CorrelationAttributeInstance.MAC_TYPE_ID:
                return normalizeMac(trimmedData);
            case CorrelationAttributeInstance.IMEI_TYPE_ID:
                return normalizeImei(trimmedData);
            case CorrelationAttributeInstance.IMSI_TYPE_ID:
                return normalizeImsi(trimmedData);
            case CorrelationAttributeInstance.ICCID_TYPE_ID:
                return normalizeIccid(trimmedData);

            default:
                final String errorMessage = String.format(
                        "Validator function not found for attribute type: %s",
                        attributeType.getDisplayName());
                throw new CorrelationAttributeNormalizationException(errorMessage);
        }
    }

    /**
     * Validate the data. Converts text to lower case, and ensures that the data
     * is a valid string of the format expected given the attributeType.
     *
     * @param attributeTypeId correlation type of data
     * @param data            data to normalize
     *
     * @return normalized data
     */
    public static String normalize(int attributeTypeId, String data) throws CorrelationAttributeNormalizationException {
        try {
            List<CorrelationAttributeInstance.Type> defaultTypes = CorrelationAttributeInstance.getDefaultCorrelationTypes();
            Optional<CorrelationAttributeInstance.Type> typeOption = defaultTypes.stream().filter(attributeType -> attributeType.getId() == attributeTypeId).findAny();

            if (typeOption.isPresent()) {
                CorrelationAttributeInstance.Type type = typeOption.get();
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
        final String validMd5Regex = "^[a-f0-9]{32}$";
        final String dataLowered = data.toLowerCase();
        if (dataLowered.matches(validMd5Regex)) {
            return dataLowered;
        } else {
            throw new CorrelationAttributeNormalizationException(String.format("Data purporting to be an MD5 was found not to comform to expected format: %s", data));
        }
    }

    /**
     * Verify there are no slashes or invalid domain name characters (such as
     * '?' or \: ). Normalize to lower case.
     */
    private static String normalizeDomain(String data) throws CorrelationAttributeNormalizationException {
        DomainValidator validator = DomainValidator.getInstance(true);
        if (validator.isValid(data)) {
            return data.toLowerCase();
        } else {
            final String validIpAddressRegex = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";
            if (data.matches(validIpAddressRegex)) {
                return data;
            } else {
                throw new CorrelationAttributeNormalizationException(String.format("Data was expected to be a valid domain: %s", data));
            }
        }
    }

    /**
     * Verify that there is an '@' and no invalid characters. Should normalize
     * to lower case.
     */
    private static String normalizeEmail(String data) throws CorrelationAttributeNormalizationException {
        EmailValidator validator = EmailValidator.getInstance(true, true);
        if (validator.isValid(data)) {
            return data.toLowerCase();
        } else {
            throw new CorrelationAttributeNormalizationException(String.format("Data was expected to be a valid email address: %s", data));
        }
    }

    /**
     * Verify it is only numbers and '+'. Strip spaces, dashes, and parentheses.
     */
    private static String normalizePhone(String data) throws CorrelationAttributeNormalizationException {
        if (data.matches("\\+?[0-9()\\-\\s]+")) {
            String phoneNumber = data.replaceAll("[^0-9\\+]", "");
            if(phoneNumber.length() <= MIN_PHONE_LENGTH) {
                throw new CorrelationAttributeNormalizationException(String.format("Data was expected to be a valid phone number: %s", data));
            }
            return phoneNumber;
        } else {
            throw new CorrelationAttributeNormalizationException(String.format("Data was expected to be a valid phone number: %s", data));
        }
    }

    /**
     * Vacuous - will be replaced with something reasonable later.
     */
    private static String normalizeUsbId(String data) throws CorrelationAttributeNormalizationException {
        //TODO replace with correct usb id validation at a later date
        return data;
    }

    /**
     * Verify the wireless network name is valid
     *
     * SSIDs for wireless networks can be at most 32 characters, are case
     * sensitive, and allow special characters.
     *
     * @param data The string to normalize and validate
     *
     * @return the unmodified data if the data was a valid length to be an SSID
     *
     * @throws CorrelationAttributeNormalizationException if the data was not a
     *                                                    valid SSID
     */
    private static String verifySsid(String data) throws CorrelationAttributeNormalizationException {
        if (data.length() <= 32) {
            return data;
        } else {
            throw new CorrelationAttributeNormalizationException("Name provided was longer than the maximum valid SSID (32 characters). Name: " + data);
        }
    }

    /**
     * Verify the ICCID (Integrated Circuit Card Identifier) number and
     * normalize format.
     *
     * E.118 defines as up to 22 digits long including luhn check digit while
     * GSM Phase 1 defines it as a 20 digit operator specific structure. They
     * begin with 89 which is the ISO 7812 Major Industry Identifier for
     * telecommunication, followed by a contry code of 1-3 digits as definted by
     * ITU-T E.164, followed by issuer identifier 1-4 digits, followed by 1 luhn
     * checksum digit (sometimes omitted). The hexidecimal digit F is used as
     * filler when necessary in GSM Phase 1 specification.
     *
     * 18 digits appears to be the shortest ICCID in use.
     *
     * @param data The string to normalize and validate
     *
     * @return the data with common number seperators removed and lower cased if
     *         the data was determined to be a possible ICCID
     *
     * @throws CorrelationAttributeNormalizationException if the data was not a
     *                                                    valid ICCID
     */
    private static String normalizeIccid(String data) throws CorrelationAttributeNormalizationException {
        final String validIccidRegex = "^89[f0-9]{17,22}$";
        final String iccidWithoutSeperators = data.toLowerCase().replaceAll(SEPERATORS_REGEX, "");
        if (iccidWithoutSeperators.matches(validIccidRegex)) {
            return iccidWithoutSeperators;
        } else {
            throw new CorrelationAttributeNormalizationException("Data provided was not a valid ICCID. : " + data);
        }
    }

    /**
     * Verify the IMSI (International mobile subscriber identity) number and
     * normalize format.
     *
     * First 3 digits Mobile Country Code 2-3 digits Mobile Network Code Up to
     * 10 digits for mobile subscriber identification number MSIN
     *
     * Length will be 14 or 15 digits total
     *
     * @param data The string to normalize and validate
     *
     * @return the data with common number seperators removed if the data was
     *         determined to be a possible IMSI
     *
     * @throws CorrelationAttributeNormalizationException if the data was not a
     *                                                    valid IMSI
     */
    private static String normalizeImsi(String data) throws CorrelationAttributeNormalizationException {
        final String validImsiRegex = "^[0-9]{14,15}$";
        final String imsiWithoutSeperators = data.replaceAll(SEPERATORS_REGEX, "");
        if (imsiWithoutSeperators.matches(validImsiRegex)) {
            return imsiWithoutSeperators;
        } else {
            throw new CorrelationAttributeNormalizationException("Data provided was not a valid Imsi. : " + data);
        }
    }

    /**
     * Verify the MAC (media access control) address and normalize format.
     *
     * A 12 or 16 Hexadecimal digits long depending on standard (Possible
     * standards EUI-48, MAC-48, EUI-64)
     *
     * @param data The string to normalize and validate
     *
     * @return the data with common number seperators removed and lowercased if
     *         the data was determined to be a possible MAC
     *
     * @throws CorrelationAttributeNormalizationException if the data was not a
     *                                                    valid MAC
     */
    private static String normalizeMac(String data) throws CorrelationAttributeNormalizationException {
        final String validMacRegex = "^([a-f0-9]{12}|[a-f0-9]{16})$";
        final String macWithoutSeperators = data.toLowerCase().replaceAll(SEPERATORS_REGEX, "");
        if (macWithoutSeperators.matches(validMacRegex)) {
            return macWithoutSeperators;
        } else {
            throw new CorrelationAttributeNormalizationException("Data provided was not a valid Imsi. : " + data);
        }
    }

    /**
     * Verify the IMEI (International Mobile Equipment Identity) number and
     * normalize format.
     *
     * 14 to 16 digits digits 1 through 6 are TAC (Type Allocation Code) digits
     * 7 and 8 are also part of the TAC in phones made in 2003 or later digits 7
     * and 8 are FAC (Final Assembly Code) in phones made prior to 2003 digits 9
     * through 14 are the serial number digits 15 and 16 if present represent an
     * optional luhn checksum (or software version number when dealing with an
     * IMEI software version)
     *
     * @param data The string to normalize and validate
     *
     * @return the data with common number seperators removed if the data was
     *         determined to be a possible IMEI
     *
     * @throws CorrelationAttributeNormalizationException if the data was not a
     *                                                    valid IMEI
     */
    private static String normalizeImei(String data) throws CorrelationAttributeNormalizationException {
        final String validImeiRegex = "^[0-9]{14,16}$";
        final String imeiWithoutSeperators = data.replaceAll(SEPERATORS_REGEX, "");
        if (imeiWithoutSeperators.matches(validImeiRegex)) {
            return imeiWithoutSeperators;
        } else {
            throw new CorrelationAttributeNormalizationException("Data provided was not a valid Imsi. : " + data);
        }
    }
}