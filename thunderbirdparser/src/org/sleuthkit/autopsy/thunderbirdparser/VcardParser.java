/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.thunderbirdparser;

import ezvcard.Ezvcard;
import ezvcard.VCard;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskException;

/**
 * A parser that extracts information from a vCard file.
 */
final class VcardParser {
    private static final String VCARD_HEADER = "BEGIN:VCARD";
    private static final long MIN_FILE_SIZE = 22;
    
    private static final Logger logger = Logger.getLogger(VcardParser.class.getName());
    
    /**
     * Create a VcardParser object.
     */
    VcardParser() {
    }

    /**
     * Is the supplied content a vCard file?
     * 
     * @param content The content to check.
     * 
     * @return True if the supplied content is a vCard file; otherwise false.
     */
    static boolean isVcardFile(Content content) {
        try {
            if (content.getSize() > MIN_FILE_SIZE) {
                byte[] buffer = new byte[VCARD_HEADER.length()];
                int byteRead = content.read(buffer, 0, VCARD_HEADER.length());
                if (byteRead > 0) {
                    String header = new String(buffer);
                    return header.equalsIgnoreCase(VCARD_HEADER);
                }
            }
        } catch (TskException ex) {
            logger.log(Level.WARNING, String.format("Exception while detecting if the file '%s' (id=%d) is a vCard file.",
                    content.getName(), content.getId())); //NON-NLS
            return false;
        }
        
        return false;
    }
    
    /**
     * Parse the vCard file and compile its data in a VCard object. If 
     * 
     * @param file The vCard file to be parsed.
     * 
     * @return A VCard object containing the data read from the file.
     * 
     * @throws IOException If there is an issue parsing the vCard file.
     */
    VCard parse(File file) throws IOException {
        return Ezvcard.parse(file).first();
    }
}
