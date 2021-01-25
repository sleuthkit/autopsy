/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils.textutils;

import com.ethteck.decodetect.core.Decodetect;
import com.ethteck.decodetect.core.DecodetectResult;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.List;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for working with text file encodings.
 */
public class EncodingUtils {
    
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
    
    /*
     * The char set returned if the algorithm fails to detect the
     * encoding of the file.
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
    
    /**
     * Returns the encoding of the file.
     *
     * @return Detected encoding or UNKNOWN_CHARSET.
     */
    public static Charset getEncoding(AbstractFile file) throws TskCoreException, IOException {
        // Encoding detection is hard. We use several libraries since the data passed in is often messy.
        // First try CharsetDetector (from Tika / ICU4J).
        // It is a rule-based detection approach.
        try (InputStream stream = new BufferedInputStream(new ReadContentInputStream(file))) {
            CharsetDetector detector = new CharsetDetector();
            detector.setText(stream);
            
            CharsetMatch[] tikaResults = detector.detectAll();
            // Get all guesses by Tika. These matches are ordered
            // by descending confidence (largest first).
            if (tikaResults.length > 0) {
                CharsetMatch topPick = tikaResults[0];
                
                if (topPick.getName().equalsIgnoreCase("IBM500") && tikaResults.length > 1) {
                    // Legacy encoding, let's discard this one in favor
                    // of the second pick. Tika has some problems with 
                    // mistakenly identifying text as IBM500. See JIRA-6600 
                    // and https://issues.apache.org/jira/browse/TIKA-2771 for 
                    // more details.
                    topPick = tikaResults[1];
                }
                
                if (!topPick.getName().equalsIgnoreCase("IBM500") && 
                        topPick.getConfidence() >= MIN_CHARSETDETECT_MATCH_CONFIDENCE &&
                        Charset.isSupported(topPick.getName())) {
                    // Choose this charset since it's supported and has high 
                    // enough confidence
                    return Charset.forName(topPick.getName());
                }
            }
        }

        // If that did not work, then use DecoDetect, which is statistical 
        // We needed this for some Japanese text files that were incorrectly detected by CharsetDetector (with low confidence)
        // This will not always work with messy data that combines some binary and some ASCII.
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
                return topResult.getEncoding();
            }
        }

        return UNKNOWN_CHARSET;
    }
}
