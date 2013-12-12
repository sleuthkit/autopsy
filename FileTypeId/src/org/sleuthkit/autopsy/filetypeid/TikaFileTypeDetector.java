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
import org.openide.util.Exceptions;
import org.apache.tika.Tika;

import org.sleuthkit.datamodel.AbstractFile;

public class TikaFileTypeDetector implements FileTypeDetectionInterface {

    private static Tika tikaInst = new Tika();
    
    @Override
    public FileTypeDetectionInterface.FileIdInfo attemptMatch(AbstractFile abstractFile) {
        try {        
            FileTypeDetectionInterface.FileIdInfo ret = new FileTypeDetectionInterface.FileIdInfo();
            final int maxBytesInitial = 100; //how many bytes to read on first pass
            byte buffer[] = new byte[maxBytesInitial];
            int len = abstractFile.read(buffer, 0, maxBytesInitial);        

            try {
                String mimetype = tikaInst.detect(buffer);
                
                // Remove tika's name out of the general types like msoffice and ooxml
                ret.type = mimetype.replace("tika-", "");
            } catch (Exception ex) {
                //do nothing
            }

            return ret;

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return new FileTypeDetectionInterface.FileIdInfo();
        }        
    }

}
