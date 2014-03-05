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
package org.sleuthkit.autopsy.actions;

import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this Action allow users to delete tags applied to content.  
 */
public class DeleteContentTagAction extends TagAction {
    private static final String MENU_TEXT = NbBundle.getMessage(DeleteContentTagAction.class,
                                                                "DeleteContentTagAction.deleteTags");
        
    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static DeleteContentTagAction instance;

    public static synchronized DeleteContentTagAction getInstance() {
        if (null == instance) {
            instance = new DeleteContentTagAction();
        }
        return instance;
    }

    private DeleteContentTagAction() {
        super(MENU_TEXT);
    }    
    
    @Override
    protected void doAction(ActionEvent e) {
        Collection<? extends ContentTag> selectedTags = Utilities.actionsGlobalContext().lookupAll(ContentTag.class);
        for (ContentTag tag : selectedTags) {
            try {
                Case.getCurrentCase().getServices().getTagsManager().deleteContentTag(tag);
            }
            catch (TskCoreException ex) {                        
                Logger.getLogger(AddContentTagAction.class.getName()).log(Level.SEVERE, "Error deleting tag", ex);                
                JOptionPane.showMessageDialog(null,
                                              NbBundle.getMessage(this.getClass(),
                                                                  "DeleteContentTagAction.unableToDelTag.msg",
                                                                  tag.getName()),
                                              NbBundle.getMessage(this.getClass(), "DeleteContentTagAction.tagDelErr"),
                                              JOptionPane.ERROR_MESSAGE);
            }                    
        }                             
    }    
}
