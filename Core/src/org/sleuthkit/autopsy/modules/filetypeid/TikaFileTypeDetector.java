/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.filetypeid;

import java.util.SortedSet;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;

import org.sleuthkit.datamodel.AbstractFile;

/**
 * @deprecated Use org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector
 * instead.
 */
@Deprecated
public class TikaFileTypeDetector {

    private static final Tika tikaInst = new Tika(); //calling detect() with this should be thread-safe
    private final int BUFFER_SIZE = 64 * 1024; //how many bytes to pass in
    private final byte buffer[] = new byte[BUFFER_SIZE];


    /**
     * Detect the mime type of the passed in file
     *
     * @param abstractFile
     *
     * @return mime type of detected format or null
     *
     * @deprecated Use FileTypeDetector.detect(AbstractFile file) instead.
     */
    @Deprecated
    public synchronized String detect(AbstractFile abstractFile) {
        try {
            byte buf[];
            int len = abstractFile.read(buffer, 0, BUFFER_SIZE);
            if (len < BUFFER_SIZE) {
                buf = new byte[len];
                System.arraycopy(buffer, 0, buf, 0, len);
            } else {
                buf = buffer;
            }

            String mimetype = tikaInst.detect(buf, abstractFile.getName());
            // Remove tika's name out of the general types like msoffice and ooxml
            return mimetype.replace("tika-", ""); //NON-NLS
        } catch (Exception ex) {
            //do nothing
        }
        return null;
    }

    /**
     * Validate if a given mime type is in the registry. For Tika, we remove the
     * string "tika" from all MIME names, e.g. use "application/x-msoffice" NOT
     * "application/x-tika-msoffice"
     *
     * @deprecated Use FileTypeDetector.isDetectable(String mimeType) instead.
     * @param mimeType Full string of mime type, e.g. "text/html"
     *
     * @return true if detectable
     */
    @Deprecated
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
