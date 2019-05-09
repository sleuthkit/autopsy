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

    private final Set<AccountDeviceInstance> accountDeviceInstances;
    private final CommunicationsFilter communicationFilter;
    private final Set<Account> accounts;
    
    private Set<BlackboardArtifact> accountArtifacts = null;
    private SelectionSummary summary = null;

    /**
     * Wraps the details of the currently selected accounts.
     *
     * @param accountDeviceInstances Selected accountDecivedInstances
     * @param communicationFilter    Currently selected communications filters
     */
    public SelectionInfo(Set<AccountDeviceInstance> accountDeviceInstances, CommunicationsFilter communicationFilter) {
        this.accountDeviceInstances = accountDeviceInstances;
        this.communicationFilter = communicationFilter;
        
        accounts = new HashSet<>();
        accountDeviceInstances.forEach((instance) -> {
            accounts.add(instance.getAccount());
        });
    }

    /**
     * Returns the currently selected accountDeviceInstances
     *
     * @return Set of AccountDeviceInstance
     */
    public Set<AccountDeviceInstance> getAccountDevicesInstances() {
        return accountDeviceInstances;
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
    
    public Set<BlackboardArtifact> getArtifacts() {
        if(accountArtifacts == null) {
            accountArtifacts = new HashSet<>();
            CommunicationsManager communicationManager;
            try {
                communicationManager = Case.getCurrentCaseThrows().getSleuthkitCase().getCommunicationsManager();
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to get communications manager from case.", ex); //NON-NLS
                return null;
            }

            final Set<Content> relationshipSources;

            try {
                relationshipSources = communicationManager.getRelationshipSources(getAccountDevicesInstances(), getCommunicationsFilter());

                relationshipSources.stream().filter((content) -> (content instanceof BlackboardArtifact)).forEachOrdered((content) -> {
                    accountArtifacts.add((BlackboardArtifact) content);
                });

            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to get relationship sources.", ex); //NON-NLS
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

}
