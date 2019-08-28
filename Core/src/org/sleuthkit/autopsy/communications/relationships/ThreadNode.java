/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An AbstractNode subclass which wraps a MessageNode object.  Doing this allows
 * for the reuse of the createSheet and other function from MessageNode, but
 * also some customizing of how a ThreadNode is shown.
 */
final class ThreadNode extends AbstractNode{
    
    private static final Logger logger = Logger.getLogger(ThreadNode.class.getName());
    
    private final static int MAX_SUBJECT_LENGTH = 120;
    
    final private MessageNode messageNode;
    
    ThreadNode(BlackboardArtifact artifact, String threadID, Action preferredAction) {
        super(Children.LEAF);
            messageNode = new MessageNode(artifact, threadID, preferredAction);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/communications/images/threaded.png" );
    }
    
    @Override
    protected Sheet createSheet() {
        BlackboardArtifact artifact = messageNode.getArtifact();
        if(artifact == null) {
           return messageNode.createSheet() ;
        }
        
        Sheet sheet =  messageNode.createSheet();
        BlackboardArtifact.ARTIFACT_TYPE artifactTypeID = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());

        // If its a text message, replace the subject node which is probably 
        // an empty string with the firest 120 characters of the text message
        if(artifactTypeID != null && artifactTypeID == TSK_MESSAGE) {
            try {
                BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.fromID(TSK_TEXT.getTypeID())));
                if(attribute != null) {
                    Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
                    sheetSet.remove("Subject");

                    String msg = attribute.getDisplayString();
                    if(msg != null && msg.length() > MAX_SUBJECT_LENGTH) {
                        msg = msg.substring(0, MAX_SUBJECT_LENGTH) + "...";
                    }

                    sheetSet.put(new NodeProperty<>("Subject", Bundle.MessageNode_Node_Property_Subject(), "", msg)); //NON-NLS
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Unable to get the text message from message artifact %d", artifact.getId()), ex);
            }
        }
        
        return sheet;
    }
    
    String getThreadID() {
        return messageNode.getThreadID();
    }
    
    @Override
    public Action getPreferredAction() {
        return messageNode.getPreferredAction();
    }
    
    @Override
    public String getDisplayName() {
        return messageNode.getDisplayName();
    }
}
