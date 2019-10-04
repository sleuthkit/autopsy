/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.Message;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * EML file parser. An .eml file contains a single email message.
 *
 */
class EMLParser extends MimeJ4MessageParser {

    /**
     * If the extention of the AbstractFile is eml and 'To:' is found close to
     * the beginning of the file, then its probably an eml file.
     *
     * @param abFile AbstractFile to test
     * @param buffer A byte buffer of the beginning of the file.
     *
     * @return True, if we think this is an eml file, false otherwise.
     */
    static boolean isEMLFile(AbstractFile abFile, byte[] buffer) {
        String ext = abFile.getNameExtension();
        boolean isEMLFile = ext != null && ext.equals("eml");
        if (isEMLFile) {
            isEMLFile = (new String(buffer)).contains(":"); //NON-NLS
        }
        return isEMLFile;
    }

    /**
     *
     * @param sourceFile AbstractFile source file for eml message
     * @param localPath  The local path to the eml file
     *
     * @return EmailMessage object for message in eml file
     *
     * @throws FileNotFoundException
     * @throws IOException
     * @throws MimeException
     */
    static EmailMessage parse(AbstractFile sourceFile) throws FileNotFoundException, IOException, MimeException {
        try (ReadContentInputStream fis = new ReadContentInputStream(sourceFile)) {
            EMLParser parser = new EMLParser();
            parser.setLocalPath(sourceFile.getParentPath());
            Message mimeMsg = parser.getMessageBuilder().parseMessage(fis);
            return parser.extractEmail(mimeMsg, "", sourceFile.getId());
        }
    }
}
