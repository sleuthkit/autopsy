/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obt ain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.communications.relationships;

import java.util.Comparator;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

 /**
* A comparator class for comparing BlackboardArtifacts of type
* TSK_EMAIL_MSG, TSK_MESSAGE, and TSK_CALLLOG by their respective creation
* date-time.
*/
class BlackboardArtifactDateComparator implements Comparator<BlackboardArtifact> {
    static final int ACCENDING = 1;
    static final int DECENDING = -1;
    
    private static final Logger logger = Logger.getLogger(BlackboardArtifactDateComparator.class.getName());
    
    private final int direction;
    
    BlackboardArtifactDateComparator(int direction) {
        this.direction = direction;
    }
    
    @Override
    public int compare(BlackboardArtifact bba1, BlackboardArtifact bba2) {

        BlackboardAttribute attribute1 = getTimeAttributeForArtifact(bba1);
        BlackboardAttribute attribute2 = getTimeAttributeForArtifact(bba2);
        // Inializing to Long.MAX_VALUE so that if a BlackboardArtifact of 
        // any unexpected type is passed in, it will bubble to the top of 
        // the list.
        long dateTime1 = Long.MAX_VALUE;
        long dateTime2 = Long.MAX_VALUE;

        if (attribute1 != null) {
            dateTime1 = attribute1.getValueLong();
        }

        if (attribute2 != null) {
            dateTime2 = attribute2.getValueLong();
        }

        return Long.compare(dateTime1, dateTime2) * direction;
    }
    
    private BlackboardAttribute getTimeAttributeForArtifact(BlackboardArtifact artifact) {
        if(artifact == null) {
            return null;
        }
        
        BlackboardAttribute attribute = null;
        
        BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());
        if (fromID != null) {
            try {
                switch (fromID) {
                    case TSK_EMAIL_MSG:
                        attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_SENT));
                        break;
                    case TSK_MESSAGE:
                        attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME));
                        break;
                    case TSK_CALLLOG:
                        attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START));
                        break;
                    default:
                        attribute = null;
                        break;
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Unable to compare attributes for artifact %d", artifact.getArtifactID()), ex);
            }
        }
        
        return attribute;
    }
}
