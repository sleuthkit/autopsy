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

import java.util.Collection;
import java.util.logging.Level;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.Tags;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this Action allow users to apply tags to content.  
 */
public class TagAbstractFileAction extends TagSleuthKitDataModelObjectAction { 
    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static TagAbstractFileAction instance;

    public static synchronized TagAbstractFileAction getInstance() {
        if (null == instance) {
            instance = new TagAbstractFileAction();
        }
        return instance;
    }

    private TagAbstractFileAction() {
    }
    
    @Override
    protected JMenuItem getContextMenu() {
        return new TagAbstractFileMenu();                
    }
                
            
    private class TagAbstractFileMenu extends TagMenu {
        public TagAbstractFileMenu() {
            super(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).size() > 1 ? "Tag Files" : "Tag File");
        }

        @Override
        protected void applyTag(String tagDisplayName, String comment) {
            TagName tagName = getTagName(tagDisplayName, comment);
            if (tagName != null) {
                TagsManager tagsManager = Case.getCurrentCase().getServices().getTagsManager();
                Collection<? extends AbstractFile> selectedFiles = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class);
                for (AbstractFile file : selectedFiles) {
                    Tags.createTag(file, tagDisplayName, comment);
                    try {
                        tagsManager.addContentTag(file, tagName);            
                    }
                    catch (TskCoreException ex) {                        
                        Logger.getLogger(TagAbstractFileMenu.class.getName()).log(Level.SEVERE, "Error tagging result", ex);                
                        JOptionPane.showMessageDialog(null, "Unable to tag " + file.getName() + ".", "Tagging Error", JOptionPane.ERROR_MESSAGE);
                    }                    
                }                             
            }
        }
    }        
}