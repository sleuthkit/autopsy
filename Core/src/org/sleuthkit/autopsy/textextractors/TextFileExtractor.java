/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2020 Basis Technology Corp.
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.textutils.EncodingUtils;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A TextExtractor that is used to extract text from a text file.
 */
public final class TextFileExtractor implements TextExtractor {

    private static final Logger logger = Logger.getLogger(TextFileExtractor.class.getName());
    private final AbstractFile file;
    private static final String PLAIN_TEXT_MIME_TYPE = "text/plain";

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
    public Reader getReader() throws InitReaderException {
        if(encoding == null) {
            try {
                encoding = EncodingUtils.getEncoding(file);
                if(encoding == EncodingUtils.UNKNOWN_CHARSET) {
                    encoding = StandardCharsets.UTF_8;
                }
            } catch (TskCoreException | IOException ex) {
                logger.log(Level.WARNING, String.format("Error detecting the "
                        + "encoding for %s (objID=%d)", file.getName(), file.getId()), ex);
                encoding = StandardCharsets.UTF_8;
            }
        }
        
        return getReader(encoding);
    }

    private Reader getReader(Charset encoding) {
        return new InputStreamReader(new BufferedInputStream(new ReadContentInputStream(file)), encoding);
    }

    @Override
    public boolean isSupported() {        
        // get the MIME type
        String mimeType = file.getMIMEType();
        
        // if it is not present, attempt to use the FileTypeDetector to determine
        if (StringUtils.isEmpty(mimeType)) {
            FileTypeDetector fileTypeDetector = null;
            try {
                fileTypeDetector = new FileTypeDetector();
            } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                logger.log(Level.SEVERE, "Unable to create file type detector for determining MIME type", ex);
                return false;
            }
            mimeType = fileTypeDetector.getMIMEType(file);
        }
        
        return PLAIN_TEXT_MIME_TYPE.equals(mimeType);
    }
}
