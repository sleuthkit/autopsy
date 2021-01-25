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

import java.awt.event.ActionEvent;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_SENT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_TO;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT;
import org.sleuthkit.datamodel.TskCoreException;
import static org.sleuthkit.autopsy.communications.relationships.RelationshipsNodeUtilities.getAttributeDisplayString;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.datamodel.Account;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.blackboardutils.attributes.BlackboardJsonAttrUtil;
import org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments;

/**
 * Wraps a BlackboardArtifact as an AbstractNode for use in an OutlookView
 */
class MessageNode extends BlackboardArtifactNode {

    public static final String UNTHREADED_ID = "<UNTHREADED>";

    private static final Logger logger = Logger.getLogger(MessageNode.class.getName());

    private final String threadID;

    private final Action preferredAction;

    private final Action defaultNoopAction = new DefaultMessageAction();

    MessageNode(BlackboardArtifact artifact, String threadID, Action preferredAction) {
        super(artifact);

        this.preferredAction = preferredAction;

        final String stripEnd = StringUtils.stripEnd(artifact.getDisplayName(), "s"); // NON-NLS
        String removeEndIgnoreCase = StringUtils.removeEndIgnoreCase(stripEnd, "message"); // NON-NLS
        setDisplayName(removeEndIgnoreCase.isEmpty() ? stripEnd : removeEndIgnoreCase);

        this.threadID = threadID;
    }

    @Messages({
        "MessageNode_Node_Property_Type=Type",
        "MessageNode_Node_Property_From=From",
        "MessageNode_Node_Property_To=To",
        "MessageNode_Node_Property_Date=Date",
        "MessageNode_Node_Property_Subject=Subject",
        "MessageNode_Node_Property_Attms=Attachment Count"
    })

    @Override
    protected Sheet createSheet() {
        Sheet sheet = Sheet.createDefault();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>("Type", Bundle.MessageNode_Node_Property_Type(), "", getDisplayName())); //NON-NLS

        final BlackboardArtifact artifact = getArtifact();
        BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());

        if (fromID == null
                || (fromID != TSK_EMAIL_MSG
                && fromID != TSK_MESSAGE)) {
            return sheet;
        }
        if (threadID != null) {
            sheetSet.put(new NodeProperty<>("ThreadID", "ThreadID", "", threadID)); //NON-NLS
        }
        sheetSet.put(new NodeProperty<>("Subject", Bundle.MessageNode_Node_Property_Subject(), "",
                getAttributeDisplayString(artifact, TSK_SUBJECT))); //NON-NLS
        try {
            sheetSet.put(new NodeProperty<>("Attms", Bundle.MessageNode_Node_Property_Attms(), "", getAttachmentsCount())); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error loading attachment count for " + artifact, ex); //NON-NLS
        }

        String msg_from = getAttributeDisplayString(artifact, TSK_EMAIL_FROM);
        String msg_to = getAttributeDisplayString(artifact, TSK_EMAIL_TO);
        String date = getAttributeDisplayString(artifact, TSK_DATETIME_SENT);
        
        Account account_from = null;
        Account account_to = null;
        
        try {
            CommunicationsManager manager = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager();
            
            if (msg_from.isEmpty()) {
                msg_from = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_FROM);
                if(manager != null && !msg_from.isEmpty()) {
                    account_from = manager.getAccount(Account.Type.PHONE, msg_from);
                }
            } else if(manager != null) {
                // To email address sometime is in the format <name>: <email>
                String toStr = msg_to;
                String[] strSplit = msg_to.split(":");
                if(strSplit.length > 0) {
                    toStr = strSplit[strSplit.length-1].trim();
                }
                account_from = manager.getAccount(Account.Type.EMAIL, toStr);
            }  
                   
            if (msg_to.isEmpty()) {
                msg_to = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_TO);
                if(manager != null && !msg_to.isEmpty()) {
                    account_to = manager.getAccount(Account.Type.PHONE, msg_to);
                }
            } else if(manager != null) {
                account_to = manager.getAccount(Account.Type.EMAIL, msg_to);
            }
            
            if (date.isEmpty()) {
                date = getAttributeDisplayString(artifact, TSK_DATETIME);
            }
        } catch (TskCoreException ex) {
            
        }

        sheetSet.put(new AccountNodeProperty<>("From", Bundle.MessageNode_Node_Property_From(), 
                msg_from, account_from)); //NON-NLS
        sheetSet.put(new AccountNodeProperty<>("To", Bundle.MessageNode_Node_Property_To(),
                msg_to, account_to)); //NON-NLS
        sheetSet.put(new NodeProperty<>("Date", Bundle.MessageNode_Node_Property_Date(), "",
                date)); //NON-NLS

        return sheet;
    }

    /**
     * Circumvent DataResultFilterNode's slightly odd delegation to
     * BlackboardArtifactNode.getSourceName().
     *
     * @return the displayName of this Node, which is the type.
     */
    @Override
    public String getSourceName() {
        return getDisplayName();
    }

    String getThreadID() {
        return threadID;
    }

    @Override
    public Action getPreferredAction() {
        return preferredAction != null ? preferredAction : defaultNoopAction;
    }

    private int getAttachmentsCount() throws TskCoreException {
        final BlackboardArtifact artifact = getArtifact();
        int attachmentsCount;

        //  Attachments are specified in an attribute TSK_ATTACHMENTS as JSON attribute
        BlackboardAttribute attachmentsAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ATTACHMENTS));
        if (attachmentsAttr != null) {
            try {
                MessageAttachments msgAttachments = BlackboardJsonAttrUtil.fromAttribute(attachmentsAttr, MessageAttachments.class);
                return msgAttachments.getAttachmentsCount();
            } catch (BlackboardJsonAttrUtil.InvalidJsonException ex) {
                logger.log(Level.WARNING, String.format("Unable to parse json for MessageAttachments object in artifact: %s", artifact.getName()), ex);
                return 0;
            }
        } else {    // legacy attachments may be children of message artifact.
            attachmentsCount = artifact.getChildrenCount();
        }

        return attachmentsCount;
    }

    /**
     * A no op action to override the default action of BlackboardArtifactNode
     */
    private class DefaultMessageAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            // Do Nothing.
        }
    }
}
