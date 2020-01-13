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
package org.sleuthkit.autopsy.datasourceprocessors.xry;

/**
 * Instantiates XRYFileParsers by report type.
 */
final class XRYFileParserFactory {

    /**
     * Creates the correct implementation of a XRYFileParser for the specified
     * report type.
     *
     * It is assumed that the report type is supported, which means the client
     * needs to have tested with supports beforehand. Otherwise, an
     * IllegalArgumentException is thrown.
     *
     * @param reportType A supported XRY report type.
     * @return A XRYFileParser with defined behavior for the report type.
     * @throws IllegalArgumentException if the report type is not supported or
     * is null. This is a misuse of the API. It is assumed that the report type
     * has been tested with the supports method.
     */
    static XRYFileParser get(String reportType) {
        if (reportType == null) {
            throw new IllegalArgumentException("Report type cannot be null");
        }

        switch (reportType.trim().toLowerCase()) {
            case "calls":
                return new XRYCallsFileParser();
            case "contacts/contacts":
            case "contacts":
                return new XRYContactsFileParser();
            case "device/general information":
                return new XRYDeviceGenInfoFileParser();
            case "messages/sms":
                return new XRYMessagesFileParser();
            case "web/bookmarks":
                return new XRYWebBookmarksFileParser();
            default:
                throw new IllegalArgumentException(reportType + " not recognized.");
        }
    }

    /**
     * Tests if a XRYFileParser implementation exists for the report type.
     *
     * @param reportType Report type to test.
     * @return Indication if the report type can be parsed.
     */
    static boolean supports(String reportType) {
        try {
            //Attempt a get.
            get(reportType);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    //Prevent direct instantiation
    private XRYFileParserFactory() {
    }
}
