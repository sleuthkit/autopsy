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

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
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
                        && fromID != BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE) {
                    continue;
                }

                // We want email and message artifacts that do not have "threadIDs" to appear as one thread in the UI
                // To achive this assign any artifact that does not have a threadID
                // the "UNTHREADED_ID"
                // All call logs will default to a single call logs thread
                String artifactThreadID = MessageNode.UNTHREADED_ID;
                
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
        
        list.sort(new DateComparator());

        return true;
    }
    
    @Override
    protected Node createNodeForKey(BlackboardArtifact key) {
        return new MessageNode(key, null, null);
    }
    
    /**
     * A comparator class for comparing BlackboardArtifacts of type
     * TSK_EMAIL_MSG, TSK_MESSAGE, and TSK_CALLLOG by their respective creation
     * date-time.
     */
    class DateComparator implements Comparator<BlackboardArtifact> {
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

            return Long.compare(dateTime1, dateTime2);
        }
    }
}
