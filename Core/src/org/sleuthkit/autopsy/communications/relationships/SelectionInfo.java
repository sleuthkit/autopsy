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

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Class to wrap the details of the current selection from the AccountBrowser or
 * VisualizationPane
 */
public final class SelectionInfo {
    
    private static final Logger logger = Logger.getLogger(SelectionInfo.class.getName());

    private final Set<AccountDeviceInstance> selectedNodes;
    private final Set<GraphEdge> selectedEdges;
    private final CommunicationsFilter communicationFilter;
    private final Set<Account> accounts;
    
    private Set<BlackboardArtifact> accountArtifacts = null;
    private SelectionSummary summary = null;

    /**
     * Wraps the details of the currently selected accounts.
     *
     * @param selectedNodes Selected accountDecivedInstances
     * @param selectedEdges Selected pairs of accountDecivedInstances
     * @param communicationFilter    Currently selected communications filters
     */
    public SelectionInfo(Set<AccountDeviceInstance> selectedNodes, Set<GraphEdge> selectedEdges, 
            CommunicationsFilter communicationFilter) {
        this.selectedNodes = selectedNodes;
        this.selectedEdges = selectedEdges;
        this.communicationFilter = communicationFilter;
        
        accounts = new HashSet<>();
        selectedNodes.forEach((instance) -> {
            accounts.add(instance.getAccount());
        });
    }

    /**
     * Returns the currently selected nodes
     *
     * @return Set of AccountDeviceInstance
     */
    public Set<AccountDeviceInstance> getSelectedNodes() {
        return selectedNodes;
    }
    
    /**
     * Returns the currently selected edges
     * 
     * @return Set of GraphEdge objects
     */
    public Set<GraphEdge> getSelectedEdges() {
        return selectedEdges;
    }

    /**
     * Returns the currently selected communications filters.
     *
     * @return Instance of CommunicationsFilter
     */
    public CommunicationsFilter getCommunicationsFilter() {
        return communicationFilter;
    }
    
    public Set<Account> getAccounts() {
        return accounts;
    }
    
    /**
     * Get the set of relationship sources from the case database
     * 
     * @return the relationship sources (may be empty)
     * @throws TskCoreException 
     */
    Set<Content> getRelationshipSources() throws TskCoreException {

        Set<Content> relationshipSources = new HashSet<>();
        
        CommunicationsManager communicationManager;
        try {
            communicationManager = Case.getCurrentCaseThrows().getSleuthkitCase().getCommunicationsManager();
        } catch (NoCurrentCaseException ex) {
            throw new TskCoreException("Failed to get current case", ex);
        }
        
        try {
            relationshipSources.addAll(communicationManager.getRelationshipSources(getSelectedNodes(), getCommunicationsFilter()));
            for (SelectionInfo.GraphEdge edge : getSelectedEdges()) {
                relationshipSources.addAll(communicationManager.getRelationshipSources(edge.getStartNode(), 
                        edge.getEndNode(), getCommunicationsFilter()));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get relationships from case database.", ex); //NON-NLS
            
        }
        return relationshipSources;
    }
    
    public Set<BlackboardArtifact> getArtifacts() {
        if(accountArtifacts == null) {
            accountArtifacts = new HashSet<>();
            
            try {
                final Set<Content> relationshipSources = getRelationshipSources();
                relationshipSources.stream().filter((content) -> (content instanceof BlackboardArtifact)).forEachOrdered((content) -> {
                    accountArtifacts.add((BlackboardArtifact) content);
                });
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to load relationship sources.", ex); //NON-NLS
                return accountArtifacts;
            }
        }
        
        return accountArtifacts;
    }
    
    public SelectionSummary getSummary() {
        if(summary == null) {
            summary = new SelectionSummary();
        }
        
        return summary;
    }
    
    final class SelectionSummary{
        int attachmentCnt;
        int messagesCnt;
        int emailCnt;
        int callLogCnt;
        int contactsCnt;
        
        SelectionSummary() {
            getCounts();
        }
        
        private void getCounts(){
            for(BlackboardArtifact artifact: getArtifacts()) {
                BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());
                if(null != fromID) switch (fromID) {
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
                        contactsCnt++;
                        break;
                    default:
                        break;
                }
                try{
                    attachmentCnt+= artifact.getChildrenCount();
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
    }

    public static class GraphEdge {
        AccountDeviceInstance startNode;
        AccountDeviceInstance endNode;
        
        public GraphEdge(AccountDeviceInstance startNode, AccountDeviceInstance endNode) {
            this.startNode = startNode;
            this.endNode = endNode;
        }
        
        public AccountDeviceInstance getStartNode() {
            return startNode;
        }
        
        public AccountDeviceInstance getEndNode() {
            return endNode;
        }
    }
}
