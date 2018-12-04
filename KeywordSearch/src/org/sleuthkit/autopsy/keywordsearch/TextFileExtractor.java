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
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.Reader;
import java.util.logging.Level;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.textextractors.ContentTextExtractor;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extract text from .txt files
 */
final class TextFileExtractor extends ContentTextExtractor {

    //Set a Minimum confidence value to reject matches that may not have a valid text encoding
    //Values of valid text encodings were generally 100, xml code sometimes had a value around 50, 
    //and pictures and other files with a .txt extention were showing up with a value of 5 or less in limited testing.
    //This limited information was used to select the current value as one that would filter out clearly non-text 
    //files while hopefully working on all files with a valid text encoding
    static final private int MIN_MATCH_CONFIDENCE = 20;
    static final private Logger logger = Logger.getLogger(TextFileExtractor.class.getName());

    @Override
    public boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    public boolean isSupported(Content file, String detectedFormat) {
        return true;
    }

    @Override
    public Reader getReader(Content source) throws TextExtractorException {
        CharsetDetector detector = new CharsetDetector();
        //wrap stream in a BufferedInputStream so that it supports the mark/reset methods necessary for the CharsetDetector
        InputStream stream = new BufferedInputStream(new ReadContentInputStream(source));
        try {
            detector.setText(stream);
        } catch (IOException ex) {
            throw new TextExtractorException("Unable to get string from detected text in TextFileExtractor", ex);
        }
        CharsetMatch match = detector.detect();
        if (match.getConfidence() < MIN_MATCH_CONFIDENCE) {
            throw new TextExtractorException("Text does not match any character set with a high enough confidence for TextFileExtractor");
        }

        return match.getReader();
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
