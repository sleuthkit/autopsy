/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2013 Basis Technology Corp.
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

package org.sleuthkit.autopsy.filetypeid;
import java.util.SortedSet;
import org.openide.util.Exceptions;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;

import org.sleuthkit.datamodel.AbstractFile;

class TikaFileTypeDetector implements FileTypeDetectionInterface {

    private static Tika tikaInst = new Tika();
    
    @Override
    public FileTypeDetectionInterface.FileIdInfo attemptMatch(AbstractFile abstractFile) {
        try {        
            FileTypeDetectionInterface.FileIdInfo ret = new FileTypeDetectionInterface.FileIdInfo();
            final int maxBytesInitial = 100; //how many bytes to read on first pass
            byte buffer[] = new byte[maxBytesInitial];
            int len = abstractFile.read(buffer, 0, maxBytesInitial);        

            boolean found = false;
            try {
                // the xml detection in Tika tries to parse the entire file and throws exceptions
                // for files that are not complete
                try {
                    String tagHeader = new String(buffer, 0, 5);
                    if (tagHeader.equals("<?xml")) {
                        ret.type = "text/xml";
                        found = true;
                    }
                }
                catch (IndexOutOfBoundsException e) {
                    // do nothing
                }
                
                if (found == false) {
                    String mimetype = tikaInst.detect(buffer);
                    // Remove tika's name out of the general types like msoffice and ooxml
                    ret.type = mimetype.replace("tika-", "");
                }
            } catch (Exception ex) {
                //do nothing
            }

            return ret;

        } catch (Exception ex) {
            return new FileTypeDetectionInterface.FileIdInfo();
        }        
    }

    /**
     * Validate if a given mime type is in the registry.
     * For Tika, we remove the string "tika" from all MIME names, 
     * e.g. use "application/x-msoffice" NOT "application/x-tika-msoffice"
     * @param mimeType Full string of mime type, e.g. "text/html"
     * @return true if detectable
     */
    @Override
    public boolean isMimeTypeDetectable(String mimeType) {
        boolean ret = false;
        
        SortedSet<MediaType> m = MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry().getTypes();        
        String[] split = mimeType.split("/");
        
        if (split.length == 2) {
            String type = split[0];
            String subtype = split[1];
            MediaType mediaType = new MediaType(type, subtype);
            ret = m.contains(mediaType);
        }

        return ret;        
    }
}
