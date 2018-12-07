/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.textextractors;

import java.io.Reader;

/**
 * Extracts text out of Objects and exposes it as a Reader.
 *
 * @param <T> Generic data type T
 */
public abstract class TextExtractor<T> {
    
     /**
     * Determines if the file content is supported by the extractor.
     *
     * @param file           to test if its content should be supported
     * @param detectedFormat mime-type with detected format (such as text/plain)
     *                       or null if not detected
     *
     * @return true if the file content is supported, false otherwise
     */
    abstract boolean isSupported(T file, String detectedFormat);
    
    /**
     * 
     * @return 
     */
    boolean isEnabled() {
        return true;
    }
    
    /**
     * Get a reader that will iterate over the text extracted from the given
     * source.
     *
     * @return Reader instance that contains the text of the source
     * @throws org.sleuthkit.autopsy.textextractors.InitReaderException
     *
     */
    public abstract Reader getReader() throws InitReaderException;
       
    /**
     * Determines how the extraction process will proceed given the settings 
     * stored in this context instance.
     * 
     * See the extractionconfigs package for available file configurations.
     * 
     * @param context Instance containing file config classes
     */
    void setExtractionSettings(ExtractionContext context) {
        //no-op by default
    }
}
