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

import java.util.logging.Level;
import javax.swing.Action;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
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
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE;
import org.sleuthkit.datamodel.BlackboardAttribute;
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

    MessageNode(BlackboardArtifact artifact, String threadID,  Action preferredAction) {
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
        "MessageNode_Node_Property_Attms=Attachments"
    })
    
    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }
        
        // This property is needed to make the threading code work property in the UI.
        if(threadID != null) {
            sheetSet.put(new NodeProperty<>("ThreadID", "ThreadID","", threadID)); //NON-NLS
        }
        
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
        return preferredAction;
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
            } 
            catch (BlackboardJsonAttrUtil.InvalidJsonException ex) {
                logger.log(Level.WARNING, String.format("Unable to parse json for MessageAttachments object in artifact: %s", artifact.getName()), ex);
                return 0;
            }
        } else {    // legacy attachments may be children of message artifact.
            attachmentsCount = artifact.getChildrenCount();
        }

        return attachmentsCount;
    }
}
