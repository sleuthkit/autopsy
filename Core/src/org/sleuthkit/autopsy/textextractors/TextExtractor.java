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

import com.ethteck.decodetect.core.Decodetect;
import com.ethteck.decodetect.core.DecodetectResult;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.openide.util.Lookup;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extracts the text out of Content instances and exposes them as a Reader.
 * Concrete implementations can be obtained from
 * {@link org.sleuthkit.autopsy.textextractors.TextExtractorFactory}
 */
public interface TextExtractor {
    public static Charset UNKNOWN_CHARSET = new Charset("unknown", null) {
        @Override
        public boolean contains(Charset cs) {
            return false;
        }

        @Override
        public CharsetDecoder newDecoder() {
            return null;
        }

        @Override
        public CharsetEncoder newEncoder() {
            return null;
        }
    };

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

    static Charset getEncoding(Content content) {
        InputStream stream = new BufferedInputStream(new ReadContentInputStream(content));
        Charset detectedCharset = UNKNOWN_CHARSET;

        try {
            int maxBytes = 100000;
            int numBytes = Math.min(stream.available(), maxBytes);
            byte[] targetArray = new byte[numBytes];
            stream.read(targetArray);
            List<DecodetectResult> results = Decodetect.DECODETECT.getResults(targetArray);
            if (results.size() > 0) {
                DecodetectResult topResult = results.get(0);
                if (topResult.getConfidence() > 0.4) {
                    detectedCharset = topResult.getEncoding();
                }
            }
            stream.reset();
        } catch (IOException ignored) {
        }
        return detectedCharset;
    }
}
