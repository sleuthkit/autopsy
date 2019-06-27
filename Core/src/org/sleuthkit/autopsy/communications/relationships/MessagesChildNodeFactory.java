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

import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A ChildFactory subclass for creating MessageNodes from a set of 
 * BlackboardArtifact objects.
 *
 */
public class MessagesChildNodeFactory extends ChildFactory<BlackboardArtifact>{

    private static final Logger logger = Logger.getLogger(MessagesChildNodeFactory.class.getName());

    private SelectionInfo selectionInfo;
    
    private List<String> threadIDs;
    
    MessagesChildNodeFactory(SelectionInfo selectionInfo, List<String> threadIDs) {
        this.selectionInfo = selectionInfo;
        this.threadIDs = threadIDs;
    }
    
    MessagesChildNodeFactory() {
        this(null, null);
    }
    
    /**
     * Updates the current instance of selectionInfo and calls the refresh method.
     * 
     * @param selectionInfo New instance of the currently selected accounts
     * @param threadIDs A list of threadIDs to filter the keys by, null will 
     *                  return all keys for the selected accounts.
     */
    public void refresh(SelectionInfo selectionInfo, List<String> threadIDs) {
        this.threadIDs = threadIDs;
        this.selectionInfo = selectionInfo;        
        refresh(true);

    }
    
    @Override
    protected boolean createKeys(List<BlackboardArtifact> list) {
        
        if(selectionInfo == null) {
            return true;
        }
        
        final Set<Content> relationshipSources;
        try {
            relationshipSources = selectionInfo.getRelationshipSources();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to load relationship sources.", ex); //NON-NLS
            return false;
        }

        try {
            for(Content content: relationshipSources) {
                if( !(content instanceof BlackboardArtifact)){
                    continue;
                }
                
                BlackboardArtifact bba = (BlackboardArtifact) content;
                BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(bba.getArtifactTypeID());

                if (fromID != BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG
                        && fromID != BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG
                        && fromID != BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE) {
                    continue;
                }

                // We want email and message artifacts that do not have "threadIDs" to appear as one thread in the UI
                // To achive this assign any artifact that does not have a threadID
                // the "UNTHREADED_ID"
                // All call logs will default to a single call logs thread
                String artifactThreadID;
                if (fromID == BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG) {
                    artifactThreadID = MessageNode.CALL_LOG_ID;
                } else {
                    artifactThreadID = MessageNode.UNTHREADED_ID;
                }
                BlackboardAttribute attribute = bba.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_THREAD_ID));

                if(attribute != null) {
                    artifactThreadID = attribute.getValueString();
                }

                if(threadIDs == null || threadIDs.contains(artifactThreadID)) {
                    list.add(bba);
                }
                
            }

        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to load artifacts for relationship sources.", ex); //NON-NLS
        }

        return true;
    }
    
    @Override
    protected Node createNodeForKey(BlackboardArtifact key) {
        return new MessageNode(key, null, null);
    }
    
}
