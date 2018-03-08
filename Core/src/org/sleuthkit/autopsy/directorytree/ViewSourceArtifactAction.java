/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action for navigating the tree to view the artifact this artifact was
 * generated off of.
 */
class ViewSourceArtifactAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ViewSourceArtifactAction.class.getName());
    private final BlackboardArtifact artifact;

    ViewSourceArtifactAction(String title, final BlackboardArtifact artifact) {
        super(title);
        this.artifact = artifact;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final DirectoryTreeTopComponent dirTree = DirectoryTreeTopComponent.findInstance();
        try {
            for (BlackboardAttribute attribute : artifact.getAttributes()) {
                if (attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()) {
                    BlackboardArtifact associatedArtifact = Case.getOpenCase().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                    if (associatedArtifact != null) {
                        dirTree.viewArtifact(associatedArtifact);
                        break;
                    }

                }
            }
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to perform view artifact on an associated artifact of " + artifact.getDisplayName(), ex);  //NON-NLS
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
