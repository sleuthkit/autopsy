/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications.relationships;

import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

class SelectionSummary {

    private int attachmentCnt;
    private int messagesCnt;
    private int emailCnt;
    private int callLogCnt;
    private int contactsCnt;
    private int mediaCnt;
    private int referenceCnt;

    private final Account selectedAccount;
    private final Set<BlackboardArtifact> artifacts;

    private static final Logger logger = Logger.getLogger(SelectionSummary.class.getName());

    SelectionSummary(Account selectedAccount, Set<BlackboardArtifact> artifacts) {
        this.selectedAccount = selectedAccount;
        this.artifacts = artifacts;
        initCounts();
    }

    private void initCounts() {
        for (BlackboardArtifact artifact : artifacts) {
            BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());
            if (null != fromID) {
                switch (fromID) {
                    case TSK_EMAIL_MSG:
                        emailCnt++;
                        break;
                    case TSK_CALLLOG:
                        callLogCnt++;
                        break;
                    case TSK_MESSAGE:
                        messagesCnt++;
                        break;
                    case TSK_CONTACT:
                        if (selectedAccount.getAccountType() != Account.Type.DEVICE) {
                            String typeSpecificID = selectedAccount.getTypeSpecificID();
                     
                            String name = RelationshipsNodeUtilities.getAttributeDisplayString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME);
                            String phoneNumber = RelationshipsNodeUtilities.getAttributeDisplayString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER);
                            String email = RelationshipsNodeUtilities.getAttributeDisplayString(artifact, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL);
                            
                            if(typeSpecificID.equals(name) ||
                                    (RelationshipsNodeUtilities.normalizeEmailAddress(typeSpecificID).equals(RelationshipsNodeUtilities.normalizeEmailAddress(email))) ||
                                    (RelationshipsNodeUtilities.normalizePhoneNum(typeSpecificID).equals(RelationshipsNodeUtilities.normalizePhoneNum(phoneNumber)))) {
                                referenceCnt++;
                            } else {
                               contactsCnt++; 
                            }
                          
                        } else {
                            contactsCnt++;
                        }
                        break;
                    default:
                        break;
                }
            }
            try {
                attachmentCnt += artifact.getChildrenCount();
                for (Content childContent : artifact.getChildren()) {
                    if (ImageUtils.thumbnailSupported(childContent)) {
                        mediaCnt++;
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Exception thrown "
                        + "from getChildrenCount artifactID: %d",
                        artifact.getArtifactID()), ex); //NON-NLS
            }
        }
    }

    public int getAttachmentCnt() {
        return attachmentCnt;
    }

    public int getMessagesCnt() {
        return messagesCnt;
    }

    public int getEmailCnt() {
        return emailCnt;
    }

    public int getCallLogCnt() {
        return callLogCnt;
    }

    public int getContactsCnt() {
        return contactsCnt;
    }

    public int getThumbnailCnt() {
        return mediaCnt;
    }
    
    public int getReferenceCnt() {
        return referenceCnt;
    }
}
