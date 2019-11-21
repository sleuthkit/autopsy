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
import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extract text from text files
 */
public final class TextFileExtractor implements TextExtractor {
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

    private final AbstractFile file;

    public TextFileExtractor(AbstractFile file) {
        this.file = file;
    }

    public Reader getReader() {
        Charset encoding = getEncoding(file);
        if (encoding == UNKNOWN_CHARSET) {
            encoding = StandardCharsets.UTF_8;
        }
        return getReader(encoding);
    }

    public Reader getReader(Charset encoding) {
        return new InputStreamReader(new BufferedInputStream(new ReadContentInputStream(file)), encoding);
    }

    @Override
    public boolean isSupported() {
        return file.getMIMEType().equals("text/plain");
    }

    public class TextFileExtractorException extends Exception {
        public TextFileExtractorException(String msg, Throwable ex) {
            super(msg, ex);
        }
        public TextFileExtractorException(String msg) {
            super(msg);
        }
    }

    public static Charset getEncoding(Content content) {
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
