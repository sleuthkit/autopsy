/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.logging.Level;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A TextExtractor that is used to extract text from a text file.
 */
public final class TextFileExtractor implements TextExtractor {

    /*
     * The char set returned if a text file extractor fails to detect the
     * encoding of the file from which it is extracting text.
     */
    public static final Charset UNKNOWN_CHARSET = new Charset("unknown", null) {
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

    // This value will be used as a threshold for determining which encoding
    // detection library to use. If CharsetDetector's own confidence is at least
    // MIN_MATCH_CONFIDENCE, CharsetDetector's result will be used for decoding.
    // Otherwise, Decodetect will be used.
    // 
    // Note: We initially used a confidence of 35, but it was causing some 
    // Chrome Cache files to get flagged as UTF-16 with confidence 40. 
    // These files had a small amount of binary data and then ASCII. 
    static final private int MIN_CHARSETDETECT_MATCH_CONFIDENCE = 41;

    // This value determines whether we will consider Decodetect's top-scoring
    // result a legitimate match or if we will disregard its findings.
    //
    // Possible values are 0 to 1, inclusive.
    static final private double MIN_DECODETECT_MATCH_CONFIDENCE = 0.4;

    private static final Logger logger = Logger.getLogger(SqliteTextExtractor.class.getName());
    private final AbstractFile file;

    private Charset encoding = null;

    /**
     * Constructs a TextExtractor that is used to extract text from a text file.
     *
     * @param file The file.
     */
    public TextFileExtractor(AbstractFile file) {
        this.file = file;
    }

    @Override
    public Reader getReader() {
        Charset enc = getEncoding();
        if (enc.equals(UNKNOWN_CHARSET)) {
            enc = StandardCharsets.UTF_8;
        }
        return getReader(enc);
    }

    private Reader getReader(Charset encoding) {
        return new InputStreamReader(new BufferedInputStream(new ReadContentInputStream(file)), encoding);
    }

    @Override
    public boolean isSupported() {
        return file.getMIMEType().equals("text/plain");
    }

    /**
     * Returns the encoding of the file.
     *
     * @return Detected encoding or UNKNOWN_CHARSET.
     */
    public Charset getEncoding() {
        if (encoding != null) {
            return encoding;
        }

        // Encoding detection is hard. We use several libraries since the data passed in is often messy.
        // First try CharsetDetector (from Tika / ICU4J).
        // It is a rule-based detection approach.
        try (InputStream stream = new BufferedInputStream(new ReadContentInputStream(file))) {
            CharsetDetector detector = new CharsetDetector();
            detector.setText(stream);
            CharsetMatch tikaResult = detector.detect();
            if (tikaResult != null && tikaResult.getConfidence() >= MIN_CHARSETDETECT_MATCH_CONFIDENCE) {
                try {
                    encoding = Charset.forName(tikaResult.getName());
                    return encoding;
                } catch (UnsupportedCharsetException ex) {
                    logger.log(Level.WARNING, String.format("Error converting CharsetDetector result for %s (objID=%d)", file.getName(), file.getId()), ex);
                }
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, String.format("Error setting CharsetDetector stream for %s (objID=%d)", file.getName(), file.getId()), ex);
        }

        // If that did not work, then use DecoDetect, which is stastical 
        // We needed this for some Japanese text files that were incorrectly detected by CharsetDetector (with low confidence)
        // This will not always work with messy data that combines some binary and some ASCII.
        try {
            int maxBytes = 100000;
            int numBytes = maxBytes;
            if (file.getSize() < maxBytes) {
                numBytes = (int) file.getSize();
            }

            byte[] targetArray = new byte[numBytes];
            file.read(targetArray, 0, numBytes);
            List<DecodetectResult> results = Decodetect.DECODETECT.getResults(targetArray);
            if (!results.isEmpty()) {
                DecodetectResult topResult = results.get(0);
                if (topResult.getConfidence() >= MIN_DECODETECT_MATCH_CONFIDENCE) {
                    encoding = topResult.getEncoding();
                    return encoding;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Error reading content from %s (objID=%d)", file.getName(), file.getId()), ex);
        }

        encoding = UNKNOWN_CHARSET;
        return encoding;
    }
}
