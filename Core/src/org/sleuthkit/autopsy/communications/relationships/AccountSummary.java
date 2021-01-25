/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications.relationships;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments.FileAttachment;
import org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments;
import org.sleuthkit.datamodel.CommunicationsUtils;
import org.sleuthkit.datamodel.InvalidAccountIDException;
import org.sleuthkit.datamodel.blackboardutils.attributes.BlackboardJsonAttrUtil;

/**
 * 
 * Class representing the Summary data for a given account.
 */
class AccountSummary {

    private int attachmentCnt;
    private int messagesCnt;
    private int emailCnt;
    private int callLogCnt;
    private int contactsCnt;
    private int mediaCnt;
    private int referenceCnt;

    private final Account selectedAccount;
    private final Set<BlackboardArtifact> artifacts;

    private static final Logger logger = Logger.getLogger(AccountSummary.class.getName());

    /**
     * Summary constructor.
     * 
     * @param selectedAccount Selected account object
     * @param artifacts List of relationship source artifacts
     */
    AccountSummary(Account selectedAccount, Set<BlackboardArtifact> artifacts) {
        this.selectedAccount = selectedAccount;
        this.artifacts = artifacts;
        initCounts();
    }

    /**
     * Initialize the counts based on the selected account and the given artifacts.
     */
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
                            
                            List<BlackboardAttribute> attributes = null;
                            
                            try{
                                attributes = artifact.getAttributes();
                            } catch(TskCoreException ex) {
                                logger.log(Level.WARNING, String.format("Unable to getAttributes for artifact: %d", artifact.getArtifactID()), ex);
                                break;
                            }
                            
                            boolean isReference = false;
                            
                            for (BlackboardAttribute attribute : attributes) {

                                String attributeTypeName = attribute.getAttributeType().getTypeName();
                                String attributeValue = attribute.getValueString();
                                try {
                                    if (attributeTypeName.contains("PHONE")) {
                                        attributeValue = CommunicationsUtils.normalizePhoneNum(attributeValue);
                                    } else if (attributeTypeName.contains("EMAIL")) {
                                        attributeValue = CommunicationsUtils.normalizeEmailAddress(attributeValue);
                                    }
                                    
                                    if (typeSpecificID.equals(attributeValue)) {
                                        isReference = true;
                                        break;
                                    }
                                } catch (InvalidAccountIDException ex) {
                                    logger.log(Level.WARNING, String.format("Exception thrown "
                                            + "in trying to normalize attribute value: %s",
                                            attributeValue), ex); //NON-NLS
                                }

                            }
                            if (isReference) {
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
                // count the attachments from the TSK_ATTACHMENTS attribute.
                BlackboardAttribute attachmentsAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ATTACHMENTS));
                if (attachmentsAttr != null) {
                    try {
                        MessageAttachments msgAttachments = BlackboardJsonAttrUtil.fromAttribute(attachmentsAttr, MessageAttachments.class);
                        Collection<FileAttachment> fileAttachments = msgAttachments.getFileAttachments();
                        for (FileAttachment fileAttachment : fileAttachments) {
                            attachmentCnt++;
                            long attachedFileObjId = fileAttachment.getObjectId();
                            if (attachedFileObjId >= 0) {
                                AbstractFile attachedFile = artifact.getSleuthkitCase().getAbstractFileById(attachedFileObjId);
                                if (ImageUtils.thumbnailSupported(attachedFile)) {
                                    mediaCnt++;
                                }  
                            }
                        }
                    } 
                    catch (BlackboardJsonAttrUtil.InvalidJsonException ex) {
                        logger.log(Level.WARNING, String.format("Unable to parse json for MessageAttachments object in artifact: %s", artifact.getName()), ex);
                    }                    
                } else {  // backward compatibility - email message attachments are derived files, children of the message.
                    attachmentCnt += artifact.getChildrenCount();
                    for (Content childContent : artifact.getChildren()) {
                        if (ImageUtils.thumbnailSupported(childContent)) {
                            mediaCnt++;
                        }
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Exception thrown "
                        + "from getChildrenCount artifactID: %d",
                        artifact.getArtifactID()), ex); //NON-NLS
            }
        }
    }

    /**
     * Total number of attachments that this account is referenced.
     * 
     * @return Attachment count
     */
    public int getAttachmentCnt() {
        return attachmentCnt;
    }

    /**
     * Total number of messages that this account is referenced.
     * 
     * @return Message count
     */
    public int getMessagesCnt() {
        return messagesCnt;
    }

    /**
     * Total number of Emails that this account is referenced.
     * 
     * @return Email count
     */
    public int getEmailCnt() {
        return emailCnt;
    }

    /**
     * Total number of call logs that this account is referenced.
     * 
     * @return call log count
     */
    public int getCallLogCnt() {
        return callLogCnt;
    }

    /**
     * Total number of contacts in this accounts contact book.
     * 
     * @return contact count
     */
    public int getContactsCnt() {
        return contactsCnt;
    }

    /**
     * Total number of thumbnail\media attachments that this account is referenced.
     * 
     * @return Thumbnail count
     */
    public int getThumbnailCnt() {
        return mediaCnt;
    }
    
    /**
     * Total number of contacts that this account is referenced.
     * 
     * @return Contact count
     */
    public int getReferenceCnt() {
        return referenceCnt;
    }
}
