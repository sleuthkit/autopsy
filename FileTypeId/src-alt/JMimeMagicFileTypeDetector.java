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

import org.sleuthkit.datamodel.AbstractFile;
import net.sf.jmimemagic.Magic;
import net.sf.jmimemagic.MagicMatch;
import net.sf.jmimemagic.MagicMatchNotFoundException;
import org.openide.util.Exceptions;

/**
 *
 */
public class JMimeMagicFileTypeDetector implements FileTypeDetectionInterface {

    @Override
    public FileIdInfo attemptMatch(AbstractFile abstractFile) {
        try {        
            FileIdInfo ret = new FileIdInfo();
            final int maxBytesInitial = 3000; //how many bytes to read on first pass
            byte buffer[] = new byte[maxBytesInitial];
            ///@todo decide to use max bytes or give the whole file
            int len = abstractFile.read(buffer, 0, maxBytesInitial);        
            
            try {
                MagicMatch match = Magic.getMagicMatch(buffer);
                if (match != null) {
                    String matchStr = match.getMimeType();
                    if (matchStr.equals("???")) {
                        String desc = match.getDescription();
                        if (!desc.isEmpty()) {
                            ret.type = desc;
                        }
                    } else {
                        ret.type = matchStr;
                    }
                    ret.extension = match.getExtension();
                }
            } catch (MagicMatchNotFoundException ex) {
                //do nothing
            }

            return ret;

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return new FileIdInfo();
        }        
    }

}
