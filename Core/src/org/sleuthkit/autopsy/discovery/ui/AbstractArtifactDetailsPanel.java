/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.discovery.ui;

import javax.swing.JPanel;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 * @author wschaefer
 */
abstract class AbstractArtifactDetailsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Called to display the contents of the given artifact.
     *
     * @param artifact the artifact to display.
     */
    abstract void setArtifact(BlackboardArtifact artifact);

}
