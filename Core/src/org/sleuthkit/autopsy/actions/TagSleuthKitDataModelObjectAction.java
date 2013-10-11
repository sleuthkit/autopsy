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
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

abstract class TagSleuthKitDataModelObjectAction extends AbstractAction implements Presenter.Popup {    
    abstract protected JMenuItem getContextMenu();
    
    @Override
    public JMenuItem getPopupPresenter() {            
        return getContextMenu();        
    }
                
    @Override
    public void actionPerformed(ActionEvent e) {
        // Do nothing - this action should never be performed.
        // Context actions are invoked instead.
    }
            
    protected TagName getTagName(String tagDisplayName, String comment) {
        TagName tagName = null;
        try {
            TagsManager tagsManager = Case.getCurrentCase().getServices().getTagsManager();
            if (tagsManager.tagNameExists(tagDisplayName)) {
                tagName = tagsManager.getTagName(tagDisplayName);
            }
            else {
                try {
                    tagName = tagsManager.addTagName(tagDisplayName);
                }
                catch (TskCoreException ex) {
                    Logger.getLogger(TagSleuthKitDataModelObjectAction.class.getName()).log(Level.SEVERE, "Error adding " + tagDisplayName + " tag name", ex);
                    JOptionPane.showMessageDialog(null, "Unable to add the " + tagDisplayName + " tag name to the case.", "Tagging Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            }
        }
        catch (TagsManager.TagNameAlreadyExistsException ex) {
            JOptionPane.showMessageDialog(null, "A " + tagDisplayName + " tag name has already been defined.", "Duplicate Tag Error", JOptionPane.ERROR_MESSAGE);
        }
        return tagName;        
    }
}