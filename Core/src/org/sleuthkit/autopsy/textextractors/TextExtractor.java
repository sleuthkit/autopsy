/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-19 Basis Technology Corp.
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
import java.util.Collections;
import java.util.Map;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.textextractors.configs.ImageConfig;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Extracts the text out of Content instances and exposes them as a Reader.
 * Concrete implementations can be obtained from
 * {@link org.sleuthkit.autopsy.textextractors.TextExtractorFactory}
 */
public interface TextExtractor {

    /**
     * Determines if this extractor supports the given Content and
     * configurations passed into it in
     * {@link org.sleuthkit.autopsy.textextractors.TextExtractorFactory}.
     *
     * @return true if content is supported, false otherwise
     */
    boolean isSupported();

    /**
     * Get a Reader that will iterate over the text extracted from the Content
     * passed into
     * {@link org.sleuthkit.autopsy.textextractors.TextExtractorFactory}.
     *
     * @return Reader that contains the text of the underlying Content
     *
     * @throws
     * org.sleuthkit.autopsy.textextractors.TextExtractor.InitReaderException
     *
     * @see org.sleuthkit.autopsy.textextractors.TextExtractorFactory
     *
     */
    Reader getReader() throws InitReaderException;

    /**
     * Determines how the extraction process will proceed given the settings
     * stored in the context instance.
     *
     * @param context Instance containing file config classes
     */
    default void setExtractionSettings(Lookup context) {
        //no-op by default
    }

    /**
     * Retrieves content metadata, if any.
     *
     * @return Metadata as key -> value map
     */
    default Map<String, String> getMetadata() {
        return Collections.emptyMap();
    }

    /**
     * Returns true if this text extractor, based on the provided settings, will
     * perform ocr.
     *
     * @return True if will perform OCR.
     */
    default boolean willUseOCR() {
        return false;
    }

    /**
     * System level exception for reader initialization.
     */
    public class InitReaderException extends Exception {

        public InitReaderException(String msg, Throwable ex) {
            super(msg, ex);
        }

        public InitReaderException(Throwable ex) {
            super(ex);
        }

        public InitReaderException(String msg) {
            super(msg);
        }
    }
}
