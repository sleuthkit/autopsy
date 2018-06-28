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
        byte[] byteData = new byte[(int) source.getSize()];
        try {
            stream.read(byteData, 0, (int) source.getSize());
            detector.setText(byteData);
            CharsetMatch match = detector.detect();
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
