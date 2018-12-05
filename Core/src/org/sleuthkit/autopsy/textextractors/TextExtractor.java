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
import org.sleuthkit.datamodel.SleuthkitVisitableItem;

/**
 * Extracts text out of a SleuthkitVisitableItem, and exposes it is a Reader.
 * This Reader is given to the Ingester to chunk and index in Solr.
 *
 * @param <T> The subtype of SleuthkitVisitableItem an implementation is able to
 *            process.
 */
public interface TextExtractor<T extends SleuthkitVisitableItem> {

    /**
     * Is this extractor configured such that no extraction will/should be done?
     *
     * @return True if this extractor will/should not perform any extraction.
     */
    boolean isDisabled();

    /**
     * Log the given message and exception as a warning.
     *
     * @param msg Log message
     * @param ex  Exception associated with the incoming message
     */
    void logWarning(String msg, Exception ex);

    /**
     * Get a reader that will iterate over the text extracted from the given
     * source.
     *
     * @param source source content of type T
     *
     * @return Reader instance that contains the text of the source
     *
     * @throws TextExtractorException
     */
    Reader getReader(T source) throws TextExtractorException;

    /**
     * Get the 'object' id of the given source.
     *
     * @param source Source content of type T
     *
     * @return Object id of the source content
     */
    long getID(T source);

    /**
     * Get a human readable name for the given source.
     *
     * @param source Source content of type T
     *
     * @return Name of the content source
     */
    String getName(T source);
    
       
    /**
     * Determines how the extraction process will proceed given the settings 
     * stored in this context instance.
     * 
     * See the extractionconfigs package for available file configurations.
     * 
     * @param context Instance containing file config classes
     */
    void setExtractionSettings(ExtractionContext context);

    /**
     * System exception for dealing with errors encountered during extraction.
     */
    class TextExtractorException extends Exception {

        public TextExtractorException(String message) {
            super(message);
        }

        public TextExtractorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
