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
package org.sleuthkit.autopsy.keywordsearch;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extract text from .txt files
 */
final class TextFileExtractor {

    //Set a Minimum confidence value to reject matches that may not have a valid text encoding
    //Values of valid text encodings were generally 100, xml code sometimes had a value around 50, 
    //and pictures and other files with a .txt extention were showing up with a value of 5 or less in limited testing.
    //This limited information was used to select the current value as one that would filter out clearly non-text 
    //files while hopefully working on all files with a valid text encoding
    static final private int MIN_MATCH_CONFIDENCE = 20;

    private final Charset detectedCharset;

    TextFileExtractor(Charset detectedCharset) {
        this.detectedCharset = detectedCharset;
    }

    public Reader getReader(AbstractFile source) throws TextFileExtractorException {
        String mimeType = source.getMIMEType();
        if (mimeType.equals(MimeTypes.PLAIN_TEXT)) {
            if (detectedCharset != null) {
                return new InputStreamReader(new BufferedInputStream(new ReadContentInputStream(source)), detectedCharset);
            }
        }

        CharsetDetector detector = new CharsetDetector();
        //wrap stream in a BufferedInputStream so that it supports the mark/reset methods necessary for the CharsetDetector
        InputStream stream = new BufferedInputStream(new ReadContentInputStream(source));
        try {
            detector.setText(stream);
        } catch (IOException ex) {
            throw new TextFileExtractorException("Unable to get string from detected text in TextFileExtractor", ex);
        }
        CharsetMatch match = detector.detect();
        if (match == null) {
            throw new TextFileExtractorException("Unable to detect any matches using TextFileExtractor");
        } else if (match.getConfidence() < MIN_MATCH_CONFIDENCE) {
            throw new TextFileExtractorException("Text does not match any character set with a high enough confidence for TextFileExtractor");
        }

        return match.getReader();
    }
    
    public class TextFileExtractorException extends Exception {
        public TextFileExtractorException(String msg, Throwable ex) {
            super(msg, ex);
        }
        public TextFileExtractorException(String msg) {
            super(msg);
        }
    }
}
