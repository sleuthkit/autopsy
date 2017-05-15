/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Action for navigating the tree to view the artifact this tag was 
 * generated off of.
 */
class ViewTaggedArtifactAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final BlackboardArtifact artifact;

    ViewTaggedArtifactAction(String title, final BlackboardArtifact artifact) {
        super(title);
        this.artifact = artifact;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final DirectoryTreeTopComponent dirTree = DirectoryTreeTopComponent.findInstance();
        dirTree.viewArtifact(artifact);

    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
