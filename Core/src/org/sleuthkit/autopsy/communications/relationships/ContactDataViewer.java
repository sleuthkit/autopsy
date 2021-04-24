/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.contentviewers.artifactviewers.ContactArtifactViewer;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 * Wrapper around ContactArtifactViewer to add Node support and an
 * ExplorerManager.
 */
public class ContactDataViewer extends ContactArtifactViewer implements DataContent, ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;

    final private ExplorerManager explorerManager = new ExplorerManager();

    @Override
    public void setNode(Node selectedNode) {
        BlackboardArtifact artifact = null;
        if (selectedNode != null) {
            artifact = selectedNode.getLookup().lookup(BlackboardArtifact.class);
        }
        this.setArtifact(artifact);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

}
