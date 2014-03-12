/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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


package org.sleuthkit.autopsy.fileextmismatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.ContextMenuActionsProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestConfigurator;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * This creates a single context menu item for adding a new filename extension to 
 * the mismatch list for the MIME type of the selected node.
 */
@ServiceProvider(service = ContextMenuActionsProvider.class)
public class FileExtMismatchContextMenuActionsProvider implements ContextMenuActionsProvider {
    @Override
    public List<Action> getActions() {
        ArrayList<Action> actions = new ArrayList<>();

        // Ignore if file ingest is in progress.
        IngestConfigurator ingestConfigurator = Lookup.getDefault().lookup(IngestConfigurator.class);
        if (ingestConfigurator != null && !ingestConfigurator.isIngestRunning()) {
            
            final Collection<? extends BlackboardArtifact> selectedArts = Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class);

            // Prevent multiselect
            if (selectedArts.size() == 1) {                
          
                for (BlackboardArtifact nodeArt : selectedArts) {    
                
                    // Only for mismatch results
                    if (nodeArt.getArtifactTypeName().equals("TSK_EXT_MISMATCH_DETECTED")) {
                        String mimeTypeStr = "";                    
                        String extStr = "";
          
                        AbstractFile af = null;
                        try {
                            af = nodeArt.getSleuthkitCase().getAbstractFileById(nodeArt.getObjectID());
                        } catch (TskCoreException ex) {
                            Logger.getLogger(FileExtMismatchContextMenuActionsProvider.class.getName()).log(Level.SEVERE, "Error getting file by id", ex);
                        }
                            
                        if (af != null) {
                            try {
                                int i = af.getName().lastIndexOf(".");
                                if ((i > -1) && ((i + 1) < af.getName().length())) {
                                    extStr = af.getName().substring(i + 1).toLowerCase();
                                }                    

                                ArrayList<BlackboardArtifact> artList = af.getAllArtifacts();
                                for (BlackboardArtifact art : artList) {
                                    List<BlackboardAttribute> atrList = art.getAttributes();
                                    for (BlackboardAttribute att : atrList) {
                                        if (att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG.getTypeID()) {                        
                                            mimeTypeStr = att.getValueString();
                                            break;
                                        }
                                    }
                                    if (!mimeTypeStr.isEmpty()) {
                                        break;
                                    }
                                }
                            } catch (TskCoreException ex) {
                                Logger.getLogger(FileExtMismatchContextMenuActionsProvider.class.getName()).log(Level.SEVERE, "Error looking up blackboard attributes", ex);
                            }

                            if (!extStr.isEmpty() && !mimeTypeStr.isEmpty()) {
                                // Limit max size so the context window doesn't get ridiculously wide
                                if (extStr.length() > 10) {
                                    extStr = extStr.substring(0, 9);
                                }
                                if (mimeTypeStr.length() > 40) {
                                    mimeTypeStr = mimeTypeStr.substring(0, 39);
                                }                            
                                String menuItemStr = NbBundle.getMessage(this.getClass(),
                                                                         "FileExtMismatchContextMenuActionsProvider.menuItemStr",
                                                                         extStr, mimeTypeStr);
                                actions.add(new AddFileExtensionAction(menuItemStr, extStr, mimeTypeStr));

                                // Check if already added
                                HashMap<String, String[]> editableMap = FileExtMismatchXML.getDefault().load();
                                ArrayList<String> editedExtensions = new ArrayList<>(Arrays.asList(editableMap.get(mimeTypeStr)));               
                                if (editedExtensions.contains(extStr)) {                            
                                    // Informs the user that they have already added this extension to this MIME type
                                    actions.get(0).setEnabled(false);                                
                                }

                            }
                        }
                    }
                }
            }
        }
        
        return actions;
    }
}
