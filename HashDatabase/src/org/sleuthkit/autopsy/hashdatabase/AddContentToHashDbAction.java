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
package org.sleuthkit.autopsy.hashdatabase;

import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import org.openide.util.Utilities;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestConfigurator;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this Action allow users to content to a hash database.  
 */
public class AddContentToHashDbAction extends AbstractAction { 
    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static AddContentToHashDbAction instance;
    private static String SINGLE_SELECTION_NAME = "Add file to hash database"; 
    private static String MULTIPLE_SELECTION_NAME = "Add file) to hash database"; 

    public static synchronized AddContentToHashDbAction getInstance() {
        if (null == instance) {
            instance = new AddContentToHashDbAction();
        }
        
        instance.setEnabled(true);
        instance.putValue(Action.NAME, SINGLE_SELECTION_NAME);
        
        // Disable the action if file ingest is in progress.
        IngestConfigurator ingestConfigurator = Lookup.getDefault().lookup(IngestConfigurator.class);
        if (null != ingestConfigurator && ingestConfigurator.isIngestRunning()) {
            instance.setEnabled(false);
        }
        
        // Set the name of the action based on the selected content and disable the action if there is
        // selected content without an MD5 hash.
        Collection<? extends AbstractFile> selectedFiles = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class);
        if (selectedFiles.size() > 1) {
            instance.putValue(Action.NAME, MULTIPLE_SELECTION_NAME);
        }
        if (selectedFiles.isEmpty()) {
            instance.setEnabled(false);
        }
        else {
            for (AbstractFile file : selectedFiles) {
                if (null == file.getMd5Hash()) {
                    instance.setEnabled(false);
                    break;
                }
            }
        }
                
        return instance;
    }

    private AddContentToHashDbAction() {
        super(SINGLE_SELECTION_NAME);
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        Collection<? extends AbstractFile> selectedFiles = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class);
        for (AbstractFile file : selectedFiles) {
            try {
                // RJCTODO: Complete this method.
                String md5Hash = file.getMd5Hash();
                if (null != md5Hash) {
                    throw new TskCoreException("RJCTODO");
                }                
            }
            catch (TskCoreException ex) {                        
                Logger.getLogger(AddContentToHashDbAction.class.getName()).log(Level.SEVERE, "Error tagging result", ex);                
                JOptionPane.showMessageDialog(null, "Unable to add " + file.getName() + "to hash database.", "Add to Hash Database Error", JOptionPane.ERROR_MESSAGE);
            }                    
        }                             
    }        
}
