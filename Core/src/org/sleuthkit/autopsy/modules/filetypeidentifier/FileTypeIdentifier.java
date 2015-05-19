/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.filetypeidentifier;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

public class FileTypeIdentifier {

    private static final Logger logger = Logger.getLogger(FileTypeIdentifier.class.getName());
    private static FileTypeDetector fileTypeDetector = null;

    public FileTypeIdentifier() {
        try {
            if(fileTypeDetector == null)
                fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            logger.log(Level.WARNING, "Unable to instantiate FileTypeDetector.", ex);
        }
    }

    /**
     * Identifies the mimetype of the given abstract file. First, it looks up
     * the blackboard for the file mimetype. If the mimetype is not found, it
     * detects the mimetype using Apache Tika. This detected mimetype is posted
     * to the blackboard. This method detects user defined mimetypes as well
     * along with the standard mimetypes supported by Apache Tika.
     *
     * @param abstractFile
     * @return returns the mimetype of the given abstractFile. Returns null if
     * the mimetype cannot be determined.
     */
    public synchronized String identify(AbstractFile abstractFile) {

        String identifiedFileType = null;

        // check BB
        ArrayList<BlackboardAttribute> attributes = null;
        try {
            attributes = abstractFile.getGenInfoAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get generalInfoAttributes.", ex);
        }
        for (BlackboardAttribute attribute : attributes) {
            identifiedFileType = attribute.getValueString();
            break;
        }
        if (identifiedFileType != null) {
            return identifiedFileType;
        }

        // check user defined formats and Tika defined formats
        try {
            identifiedFileType = fileTypeDetector.detectAndPostToBlackboard(abstractFile);
            if (identifiedFileType != null) {
                return identifiedFileType;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to detect and post to Blackboard", ex);
        }

        logger.log(Level.WARNING, "Unable to determine the file type ", abstractFile.getName());
        return null;
    }

    /**
     * Checks if the header signature matches to that of the abstractFile.
     * @param abstractFile
     * @param readHeaderSize size of the file header to be matched.
     * @param HEX_SIGNATURE signature used for file mimetype detection.
     * @return returns true if the the signature matches to that of the abstractFile
     */
    public static boolean matchHeader(AbstractFile abstractFile, int readHeaderSize, int HEX_SIGNATURE) {

        byte[] fileHeaderBuffer = new byte[readHeaderSize];

        if (abstractFile.getSize() < readHeaderSize) {
            return false;
        }

        try {
            int bytesRead = abstractFile.read(fileHeaderBuffer, 0, readHeaderSize);
            if (bytesRead != readHeaderSize) {
                return false;
            }
        } catch (TskCoreException ex) {
            return false;
        }

        ByteBuffer bytes = ByteBuffer.wrap(fileHeaderBuffer);
        if(readHeaderSize < 4) {
            short signature = bytes.asShortBuffer().get();
            return signature == (short)HEX_SIGNATURE;
        } else {
            int signature = bytes.getInt();
            return signature == HEX_SIGNATURE;
        }
    }

}
