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
import javax.swing.JOptionPane;

import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this Action allow users to apply tags to content.  
 */
public class AddContentTagAction extends AddTagAction { 
    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static AddContentTagAction instance;

    public static synchronized AddContentTagAction getInstance() {
        if (null == instance) {
            instance = new AddContentTagAction();
        }
        return instance;
    }

    private AddContentTagAction() {
        super("");
    }
                       
    @Override
    protected String getActionDisplayName() {
        String singularTagFile = NbBundle.getMessage(this.getClass(), "AddContentTagAction.singularTagFile");
        String pluralTagFile = NbBundle.getMessage(this.getClass(), "AddContentTagAction.pluralTagFile");
        return Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).size() > 1 ? pluralTagFile : singularTagFile;
    }

    @Override
    protected void addTag(TagName tagName, String comment) {
        Collection<? extends AbstractFile> selectedFiles = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class);
        for (AbstractFile file : selectedFiles) {
            try {
                // Handle the special cases of current (".") and parent ("..") directory entries.
                if (file.getName().equals(".")) {
                    Content parentFile = file.getParent();                   
                    if (parentFile instanceof AbstractFile) {
                        file = (AbstractFile)parentFile;
                    }
                    else {
                        JOptionPane.showMessageDialog(null,
                                                      NbBundle.getMessage(this.getClass(),
                                                                          "AddContentTagAction.unableToTag.msg",
                                                                          parentFile.getName()),
                                                      NbBundle.getMessage(this.getClass(),
                                                                          "AddContentTagAction.cannotApplyTagErr"),
                                                      JOptionPane.WARNING_MESSAGE);
                        continue;
                    }
                }
                else if (file.getName().equals("..")) {
                    Content parentFile = file.getParent();                   
                    if (parentFile instanceof AbstractFile) {
                        parentFile = (AbstractFile)((AbstractFile)parentFile).getParent();
                        if (parentFile instanceof AbstractFile) {
                            file = (AbstractFile)parentFile;
                        }
                        else {
                            JOptionPane.showMessageDialog(null,
                                                          NbBundle.getMessage(this.getClass(),
                                                                              "AddContentTagAction.unableToTag.msg",
                                                                              parentFile.getName()),
                                                          NbBundle.getMessage(this.getClass(),
                                                                              "AddContentTagAction.cannotApplyTagErr"),
                                                          JOptionPane.WARNING_MESSAGE);
                            continue;
                        }
                    }
                    else {
                        JOptionPane.showMessageDialog(null,
                                                      NbBundle.getMessage(this.getClass(),
                                                                          "AddContentTagAction.unableToTag.msg",
                                                                          parentFile.getName()),
                                                      NbBundle.getMessage(this.getClass(),
                                                                          "AddContentTagAction.cannotApplyTagErr"),
                                                      JOptionPane.WARNING_MESSAGE);
                        continue;
                    }                    
                }
                
                Case.getCurrentCase().getServices().getTagsManager().addContentTag(file, tagName, comment);            
            }
            catch (TskCoreException ex) {                        
                Logger.getLogger(AddContentTagAction.class.getName()).log(Level.SEVERE, "Error tagging result", ex);                
                JOptionPane.showMessageDialog(null,
                                              NbBundle.getMessage(this.getClass(),
                                                                  "AddContentTagAction.unableToTag.msg2",
                                                                  file.getName()),
                                              NbBundle.getMessage(this.getClass(), "AddContentTagAction.taggingErr"),
                                              JOptionPane.ERROR_MESSAGE);
            }                    
        }                             
    }
}