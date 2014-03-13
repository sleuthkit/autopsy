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

import org.openide.util.NbBundle;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;

/**
 * Do the context menu action for adding a new filename extension to 
 * the mismatch list for the MIME type of the selected node.
 */
class AddFileExtensionAction extends AbstractAction { 
    private String extStr;
    private String mimeTypeStr;
    
    public AddFileExtensionAction(String menuItemStr, String extStr, String mimeTypeStr) {        
        super(menuItemStr);
        this.mimeTypeStr = mimeTypeStr;
        this.extStr = extStr;
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        HashMap<String, String[]> editableMap = FileExtMismatchXML.getDefault().load();
        ArrayList<String> editedExtensions = new ArrayList<>(Arrays.asList(editableMap.get(mimeTypeStr)));        
        editedExtensions.add(extStr);
        
        // Old array will be replaced by new array for this key
        editableMap.put(mimeTypeStr, editedExtensions.toArray(new String[0])); 
        
        if (!FileExtMismatchXML.getDefault().save(editableMap)) {            
            //error
            JOptionPane.showMessageDialog(null,
                                          NbBundle.getMessage(this.getClass(), "AddFileExtensionAction.msgDlg.msg"),
                                          NbBundle.getMessage(this.getClass(), "AddFileExtensionAction.msgDlg.title"),
                                          JOptionPane.ERROR_MESSAGE);
        } // else //in the future we might want to update the statusbar to give feedback to the user
        
    }      
    
}
