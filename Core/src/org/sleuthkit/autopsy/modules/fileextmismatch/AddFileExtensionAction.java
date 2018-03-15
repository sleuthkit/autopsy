/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Do the context menu action for adding a new filename extension to the
 * extension list for the MIME type.
 */
class AddFileExtensionAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AddFileExtensionAction.class.getName());
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
        HashMap<String, Set<String>> editableMap;
        editableMap = settings.getMimeTypeToExtsMap();
        Set<String> editedExtensions = editableMap.get(mimeTypeStr);
        editedExtensions.add(extStr);

        try {
            FileExtMismatchSettings.writeSettings(new FileExtMismatchSettings(editableMap));
        } catch (FileExtMismatchSettings.FileExtMismatchSettingsException ex) {
            //error
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    Bundle.AddFileExtensionAction_writeError_message(),
                    NbBundle.getMessage(this.getClass(), "AddFileExtensionAction.msgDlg.title"),
                    JOptionPane.ERROR_MESSAGE);
            logger.log(Level.SEVERE, "Could not write file extension settings.", ex);
        }
    }
}
