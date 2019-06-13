/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications.relationships;

import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 * @author kelly
 */
final class ThreadNode extends AbstractNode{
    
    final private MessageNode messageNode;
    
    ThreadNode(BlackboardArtifact artifact, String threadID) {
        super(Children.LEAF);
        messageNode = new MessageNode(artifact, threadID, null);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/communications/images/threaded.png" );
    }
    
    @Override
    protected Sheet createSheet() {
        return messageNode.createSheet();
    }
    
    String getThreadID() {
        return messageNode.getThreadID();
    }
    
    @Override
    public Action getPreferredAction() {
        return messageNode.getPreferredAction();
    }
    
    @Override
    public String getDisplayName() {
        return messageNode.getDisplayName();
    }
}
