/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-18 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.contentviewers.artifactviewers.MessageArtifactViewer;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extends MessageContentViewer so that it implements DataContent and can be set
 * as the only ContentViewer for a DataResultPanel. In addition it provides an
 * ExplorerManager.
 *
 * @see org.sleuthkit.autopsy.timeline.DataContentExplorerPanel for another
 * solution to a very similar problem.
 *
 */
final class MessageDataContent extends MessageArtifactViewer implements DataContent, ExplorerManager.Provider {

    private static final Logger LOGGER = Logger.getLogger(MessageDataContent.class.getName());

    private static final long serialVersionUID = 1L;
    final private ExplorerManager explorerManager = new ExplorerManager();
    
    private ArtifactFetcher worker;

    @Override
    public void propertyChange(final PropertyChangeEvent evt) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    @Override
    public void setNode(Node node) {
        if(worker != null) {
            worker.cancel(true);
            worker = null;
        }
        
        if(node == null) {
            resetComponent();
            return;
        }
        
        worker = new ArtifactFetcher(node);
        worker.execute();
    }

    /**
     * Returns the artifact represented by node.
     *
     * If the node lookup has an artifact, that artifact is returned. However,
     * if the node lookup is a file, then we look for a TSK_ASSOCIATED_OBJECT
     * artifact on the file, and if a message artifact is found associated with
     * the file, that artifact is returned.
     *
     * @param node Node to check.
     *
     * @return Blackboard artifact for the node, null if there isn't any.
     */
    private BlackboardArtifact getNodeArtifact(Node node) {
        BlackboardArtifact nodeArtifact = node.getLookup().lookup(BlackboardArtifact.class);

        if (nodeArtifact == null) {
            try {
                SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                AbstractFile file = node.getLookup().lookup(AbstractFile.class);
                if (file != null) {
                    List<BlackboardArtifact> artifactsList = tskCase.getBlackboardArtifacts(TSK_ASSOCIATED_OBJECT, file.getId());

                    for (BlackboardArtifact fileArtifact : artifactsList) {
                        BlackboardAttribute associatedArtifactAttribute = fileArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
                        if (associatedArtifactAttribute != null) {
                            BlackboardArtifact associatedArtifact = fileArtifact.getSleuthkitCase().getBlackboardArtifact(associatedArtifactAttribute.getValueLong());
                            if (isMessageArtifact(associatedArtifact)) {
                                nodeArtifact = associatedArtifact;
                            }
                        }
                    }
                }
            } catch (NoCurrentCaseException | TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Failed to get file for selected node.", ex); //NON-NLS
            }
        }

        return nodeArtifact;
    }
    
    private class ArtifactFetcher extends SwingWorker<BlackboardArtifact, Void> {
        private final Node node;
        
        ArtifactFetcher(Node node) {
            this.node = node;
        }
        
        @Override
        protected BlackboardArtifact doInBackground() throws Exception {
           return getNodeArtifact(node);
        }
        
        @Override
        public void done() {
            if(isCancelled()) {
                return;
            }
            
            try {
                setArtifact(get());
            } catch (InterruptedException | ExecutionException ex) {
                LOGGER.log(Level.SEVERE, "Failed to get node for artifact.", ex); //NON-NLS
            }
        }
    }
}
