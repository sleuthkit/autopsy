/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
import java.util.Collection;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.Tags;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

public class TagBlackboardArtifactAction extends AbstractAction implements Presenter.Popup {
    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static TagBlackboardArtifactAction instance;

    public static synchronized TagBlackboardArtifactAction getInstance() {
        if (null == instance) {
            instance = new TagBlackboardArtifactAction();
        }
        return instance;
    }

    private TagBlackboardArtifactAction() {
    }
    
    @Override
    public JMenuItem getPopupPresenter() {            
        return new TagBlackboardArtifactMenu();        
    }
                
    @Override
    public void actionPerformed(ActionEvent e) {
        // Do nothing - this action should never be performed.
        // Submenu actions are invoked instead.
    }
            
    
    private static class TagBlackboardArtifactMenu extends TagMenu {
        public TagBlackboardArtifactMenu() {
            super(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class).size() > 1 ? "Tag Results" : "Tag Result");            
        }

        @Override
        protected void applyTag(String tagDisplayName, String comment) {
            Collection<? extends BlackboardArtifact> selectedArtifacts = Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class);
            for (BlackboardArtifact artifact : selectedArtifacts) {
                Tags.createTag(artifact, tagDisplayName, comment);
            }                             
        }
    }    
}
