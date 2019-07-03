/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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

        BlackboardAttribute attribute1 = null;
        BlackboardAttribute attribute2 = null;
        // Inializing to Long.MAX_VALUE so that if a BlackboardArtifact of 
        // any unexpected type is passed in, it will bubble to the top of 
        // the list.
        long dateTime1 = Long.MAX_VALUE;
        long dateTime2 = Long.MAX_VALUE;

        if (bba1 != null) {
            BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(bba1.getArtifactTypeID());
            if (fromID != null) {
                try {
                    switch (fromID) {
                        case TSK_EMAIL_MSG:
                            attribute1 = bba1.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_SENT));
                            break;
                        case TSK_MESSAGE:
                            attribute1 = bba1.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME));
                            break;
                        case TSK_CALLLOG:
                            attribute1 = bba1.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START));
                            break;
                        default:
                            attribute1 = null;
                            break;
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, String.format("Unable to compare attributes for artifact %d", bba1.getArtifactID()), ex);
                }
            }
        }

        if (bba2 != null) {
            BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(bba2.getArtifactTypeID());
            if (fromID != null) {
                try {
                    switch (fromID) {
                        case TSK_EMAIL_MSG:
                            attribute2 = bba2.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_SENT));
                            break;
                        case TSK_MESSAGE:
                            attribute2 = bba2.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME));
                            break;
                        case TSK_CALLLOG:
                            attribute2 = bba2.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START));
                            break;
                        default:
                            attribute2 = null;
                            break;
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, String.format("Unable to compare attributes for artifact %d", bba2.getArtifactID()), ex);
                }
            }
        }

        if (attribute1 != null) {
            dateTime1 = attribute1.getValueLong();
        }

        if (attribute2 != null) {
            dateTime2 = attribute2.getValueLong();
        }

        return Long.compare(dateTime1, dateTime2) * direction;
    }
}
