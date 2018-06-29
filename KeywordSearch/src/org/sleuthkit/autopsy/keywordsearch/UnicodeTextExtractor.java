/*
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
package org.sleuthkit.autopsy.keywordsearch;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.logging.Level;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extract text from unicode files
 */
final class UnicodeTextExtractor extends ContentTextExtractor {

    //Set an upper limit on the amount of data in a single file to index
    static final private int MAX_DATA_SIZE_BYTES = 100000000;
    //Set a Minimum confidence value to reject matches that may not have a valid text encoding
    //Values of valid text encodings were generally 100, xml code sometimes had a value around 50, 
    //and pictures and other files with a .txt extention were showing up with a value of 5 or less in limited testing.
    //This limited information was used to select the current value as one that would filter out clearly non-text 
    //files while hopefully working on all files with a valid text encoding
    static final private int MIN_MATCH_CONFIDENCE = 20;
    static final private Logger logger = Logger.getLogger(UnicodeTextExtractor.class.getName());

    @Override
    boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    boolean isSupported(Content file, String detectedFormat) {
        return true;
    }

    @Override
    public Reader getReader(Content source) throws TextExtractorException {
        CharsetDetector detector = new CharsetDetector();
        ReadContentInputStream stream = new ReadContentInputStream(source);
        int size = (int) source.getSize();
        if (size > MAX_DATA_SIZE_BYTES) {
            size = MAX_DATA_SIZE_BYTES;
            logger.log(Level.WARNING, "Text file size exceeded 100 mb, ony the first 100 mb has been indexed");
        }
        byte[] byteData = new byte[size];
        try {
            stream.read(byteData, 0, size);
            detector.setText(byteData);
            CharsetMatch match = detector.detect();
            if (match.getConfidence() < MIN_MATCH_CONFIDENCE) {
                throw new TextExtractorException("Text does not match any character set with a high enough confidence for UnicodeTextExtractor");
            }
            try {
                return new StringReader(match.getString());

            } catch (IOException ex) {
                throw new TextExtractorException("Unable to get string from detected text in UnicodeTextExtractor", ex);
            }
        } catch (ReadContentInputStream.ReadContentInputStreamException ex) {
            throw new TextExtractorException("Unable to read text stream in UnicodeTextExtractor", ex);
        }
    }

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public void logWarning(String msg, Exception ex) {
        logger.log(Level.WARNING, msg, ex);
    }

}
