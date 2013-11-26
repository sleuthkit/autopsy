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

import eu.medsea.mimeutil.MimeException;
import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.detector.MagicMimeMimeDetector;
import java.util.Iterator;
import java.util.LinkedHashSet;
import org.openide.util.Exceptions;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 */
public class MimeUtilFileTypeDetector implements FileTypeDetectionInterface {
    private static MagicMimeMimeDetector mimeUtil = new MagicMimeMimeDetector();
    
    
    @Override
    public FileIdInfo attemptMatch(AbstractFile abstractFile) {
        try {        
            FileIdInfo ret = new FileIdInfo();
            final int maxBytesInitial = 3000; //how many bytes to read on first pass
            byte buffer[] = new byte[maxBytesInitial];
            int len = abstractFile.read(buffer, 0, maxBytesInitial);        
            
            try {
                LinkedHashSet mimeSet = (LinkedHashSet)mimeUtil.getMimeTypesByteArray(buffer);

                Iterator it =  mimeSet.iterator(); 
                while (it.hasNext()) {
                    MimeType mt = (MimeType)it.next();
                    ret.type = mt.getMediaType() + "/" + mt.getSubType();
                    break; //just take the first one for now
                }

            } catch (MimeException ex) {
                //do nothing
            }

            return ret;

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return new FileIdInfo();
        }        
    }    
    
}
