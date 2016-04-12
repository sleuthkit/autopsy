/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.fileextmismatch;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Do the context menu action for adding a new filename extension to the
 * extension list for the MIME type.
 */
class AddFileExtensionAction extends AbstractAction {
    private static final long serialVersionUID = 1L;

    private final String extStr;
    private final String mimeTypeStr;
    private final FileExtMismatchSettings settings;

    AddFileExtensionAction(String menuItemStr, String extStr, String mimeTypeStr, FileExtMismatchSettings settings) {
        super(menuItemStr);
        this.mimeTypeStr = mimeTypeStr;
        this.extStr = extStr;
        this.settings = settings;
    }

    @Override
    @Messages({"AddFileExtensionAction.writeError.message=Could not write file extension settings."})
    public void actionPerformed(ActionEvent event) {
        HashMap<String, String[]> editableMap;
        editableMap = settings.getMimeTypeToExtsMap();
        ArrayList<String> editedExtensions = new ArrayList<>(Arrays.asList(editableMap.get(mimeTypeStr)));
        editedExtensions.add(extStr);

        // Old array will be replaced by new array for this key
        editableMap.put(mimeTypeStr, editedExtensions.toArray(new String[0]));

        try {
            if (!FileExtMismatchSettings.writeSettings(new FileExtMismatchSettings(editableMap))) {
                //error
                JOptionPane.showMessageDialog(null,
                        Bundle.AddFileExtensionAction_writeError_message(),
                        NbBundle.getMessage(this.getClass(), "AddFileExtensionAction.msgDlg.title"),
                        JOptionPane.ERROR_MESSAGE);
                Logger.getLogger(this.getClass().getName()).log(Level.WARNING, Bundle.AddFileExtensionAction_writeError_message());
            } // else //in the future we might want to update the statusbar to give feedback to the user
        } catch (FileExtMismatchSettings.FileExtMismatchSettingsException ex) {
            JOptionPane.showMessageDialog(null,
                    Bundle.AddFileExtensionAction_writeError_message(),
                    NbBundle.getMessage(this.getClass(), "AddFileExtensionAction.msgDlg.title"),
                    JOptionPane.ERROR_MESSAGE);
            Logger.getLogger(this.getClass().getName()).log(Level.WARNING, Bundle.AddFileExtensionAction_writeError_message(), ex);
        }
    }
}
