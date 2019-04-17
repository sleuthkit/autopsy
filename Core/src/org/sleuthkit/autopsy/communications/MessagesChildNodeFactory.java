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
package org.sleuthkit.autopsy.communications;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * ChildFactory that creates createKeys and nodes from a given selectionInfo for
 * only emails, call logs and messages.
 *
 */
public class MessagesChildNodeFactory extends ChildFactory<BlackboardArtifact> {

    private static final Logger logger = Logger.getLogger(MessagesChildNodeFactory.class.getName());

    private CommunicationsManager communicationManager = null;
    private final SelectionInfo selectionInfo;

    /**
     * Construct a new MessageChildNodeFactory from the currently selectionInfo
     *
     * @param selectionInfo SelectionInfo object for the currently selected
     *                      accounts
     */
    MessagesChildNodeFactory(SelectionInfo selectionInfo) {
        this.selectionInfo = selectionInfo;

        try {
            communicationManager = Case.getCurrentCaseThrows().getSleuthkitCase().getCommunicationsManager();
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get communications manager from case.", ex); //NON-NLS
        }
    }

    /**
     * Creates a list of Keys (BlackboardArtifact) for only messages for the
     * currently selected accounts
     * @param list List of BlackboardArtifact to populate
     * @return True on success
     */
    @Override
    protected boolean createKeys(List<BlackboardArtifact> list) {
        if (communicationManager == null) {
            return false;
        }

        final Set<Content> relationshipSources;

        try {
            relationshipSources = communicationManager.getRelationshipSources(selectionInfo.getAccountDevicesInstances(), selectionInfo.getCommunicationsFilter());

            relationshipSources.stream().filter((content) -> (content instanceof BlackboardArtifact)).forEachOrdered((content) -> {

                BlackboardArtifact bba = (BlackboardArtifact) content;
                BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(bba.getArtifactTypeID());

                if (fromID == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG
                        || fromID == BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG
                        || fromID == BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE) {
                    list.add(bba);
                }
            });

        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get relationship sources.", ex); //NON-NLS
        }

        return true;
    }

    @Override
    protected Node createNodeForKey(BlackboardArtifact key) {
        return new MessageNode(key);
    }
}
