/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 * @author wschaefer
 */
public class ViewTaggedArtifactAction extends AbstractAction {

    private final BlackboardArtifact artifact;

    public ViewTaggedArtifactAction(String title, final BlackboardArtifact artifact) {
        super(title);
        this.artifact = artifact;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final DirectoryTreeTopComponent dirTree = DirectoryTreeTopComponent.findInstance();
        dirTree.viewArtifact(artifact);

    }
}
