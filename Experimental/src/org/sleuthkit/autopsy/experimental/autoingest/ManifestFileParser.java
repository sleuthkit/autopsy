/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.nio.file.Path;

/**
 * Responsible for parsing the manifest files that
 * describe cases, devices, and data sources.
 * These are used by autoingest to create cases and add
 * data sources to the correct case.
 */
public interface ManifestFileParser {
    
    /**
     * Checks if a file is this type of manifest file
     * @param filePath Path to potential manifest file
     * @return True if the file is a manifest that this parser supports
     */ 
    boolean fileIsManifest(Path filePath);
    
    /**
     * Parses the given file.  Will only be called if 
     * fileIsManifest() previously returned true. 
     * @param filePath Path to manifest file
     * @return Parsed results
     * @throws org.sleuthkit.autopsy.experimental.autoingest.ManifestFileParser.ManifestFileParserException 
     */
    Manifest parse(Path filePath) throws ManifestFileParserException;
    
    public final static class ManifestFileParserException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception to throw if a manifest file cannot be parsed.
         *
         * @param message The exception message.
         */
        public ManifestFileParserException(String message) {
            super(message);
        }

        /**
         * Constructs an exception to throw if a manifest file cannot be parsed.
         *
         * @param message The exception message.
         * @param cause   The exception cause, if it was a Throwable.
         */
        public ManifestFileParserException(String message, Throwable cause) {
            super(message, cause);
        }
    }
        
}
