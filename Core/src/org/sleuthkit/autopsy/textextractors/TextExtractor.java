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
import org.openide.util.Lookup;
import org.sleuthkit.datamodel.Content;

/**
 * Extracts the text out of {@link org.sleuthkit.datamodel.Content} instances
 * and exposes them as a {@link java.io.Reader}. Concrete implementations can be
 * obtained from
 * {@link org.sleuthkit.autopsy.textextractors.TextExtractorFactory#getExtractor(org.sleuthkit.datamodel.Content)}
 * or
 * {@link org.sleuthkit.autopsy.textextractors.TextExtractorFactory#getExtractor(org.sleuthkit.datamodel.Content, org.openide.util.Lookup)}.
 *
 * @see org.sleuthkit.autopsy.textextractors.TextExtractorFactory
 */
public abstract class TextExtractor {

    /**
     * Determines if the file content is supported by the extractor.
     *
     * @param file           to test if its content should be supported
     * @param detectedFormat mime-type with detected format (such as text/plain)
     *                       or null if not detected
     *
     * @return true if the file content is supported, false otherwise
     */
    abstract boolean isSupported(Content file, String detectedFormat);

    /**
     * Determines if the TextExtractor instance is enabled to read content.
     *
     * @return
     */
    boolean isEnabled() {
        return true;
    }

    /**
     * Get a {@link java.io.Reader} that will iterate over the text extracted
     * from the {@link org.sleuthkit.datamodel.Content} passed into
     * {@link org.sleuthkit.autopsy.textextractors.TextExtractorFactory}.
     *
     * @return {@link java.io.Reader} that contains the text of the underlying
     *         {@link org.sleuthkit.datamodel.Content}
     *
     * @throws
     * org.sleuthkit.autopsy.textextractors.TextExtractor.ExtractionException
     *
     * @see org.sleuthkit.autopsy.textextractors.TextExtractorFactory
     *
     */
    public abstract Reader getReader() throws ExtractionException;

    /**
     * Determines how the extraction process will proceed given the settings
     * stored in the context instance.
     *
     * @param context Instance containing file config classes
     */
    void setExtractionSettings(Lookup context) {
        //no-op by default
    }

    /**
     * Exception encountered during
     * {@link org.sleuthkit.autopsy.textextractors.TextExtractor#getReader()}.
     * This indicates that there was an internal parsing error that occurred
     * during the reading of Content text.
     */
    public class ExtractionException extends Exception {

        public ExtractionException(String msg, Throwable ex) {
            super(msg, ex);
        }

        public ExtractionException(Throwable ex) {
            super(ex);
        }

        public ExtractionException(String msg) {
            super(msg);
        }
    }
}
