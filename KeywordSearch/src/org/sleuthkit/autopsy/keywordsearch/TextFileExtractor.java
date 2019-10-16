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
import java.nio.charset.StandardCharsets;
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

    public Reader getReader(AbstractFile source) throws TextFileExtractorException {
        Charset encoding = TextExtractor.getEncoding(source);
        if (encoding == TextExtractor.UNKNOWN_CHARSET) {
            encoding = StandardCharsets.UTF_8;
        }
        return getReader(source, encoding);
    }

    public Reader getReader(AbstractFile source, Charset encoding) throws TextFileExtractorException {
        return new InputStreamReader(new BufferedInputStream(new ReadContentInputStream(source)), encoding);
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
