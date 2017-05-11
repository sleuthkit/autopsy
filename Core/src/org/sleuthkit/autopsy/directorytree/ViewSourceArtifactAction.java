/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author wschaefer
 */
public class ViewSourceArtifactAction extends AbstractAction {

    private final BlackboardArtifact artifact;

    public ViewSourceArtifactAction(String title, final BlackboardArtifact artifact) {
        super(title);
        this.artifact = artifact;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final DirectoryTreeTopComponent dirTree = DirectoryTreeTopComponent.findInstance();
        try {
            for (BlackboardAttribute attribute : artifact.getAttributes()) {
                System.out.println(attribute.getAttributeType().getDisplayName());
                if (attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()) {
                    BlackboardArtifact associatedArtifact = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                    if (associatedArtifact != null) {
                        dirTree.viewArtifact(associatedArtifact);
                        break;
                    }
                    
                }
            }
        } catch (TskCoreException ex) {
        }
    }
}
