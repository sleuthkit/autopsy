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
package org.sleuthkit.autopsy.actions;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.actions.OpenPythonModulesFolderAction")
@ActionRegistration(displayName = "#CTL_OpenPythonModulesFolderAction", lazy = false)
@ActionReference(path = "Menu/Tools", position = 1400)
public final class OpenPythonModulesFolderAction extends SystemAction implements ActionListener {

    private static final Logger logger = Logger.getLogger(OpenPythonModulesFolderAction.class.getName());
    private static final String ACTION_NAME = NbBundle.getMessage(OpenPythonModulesFolderAction.class, "OpenPythonModulesFolderAction.actionName.text");

    @Override
    public void actionPerformed(ActionEvent e) {
        String pythonModulesDirPath = PlatformUtil.getUserPythonModulesPath();
        try {
            File directory = new File(pythonModulesDirPath);
            if (directory.exists()) {
                Desktop.getDesktop().open(directory);
            } else {
                throw new IOException(String.format("%s does not exist", pythonModulesDirPath));  //NON-NLS
            }
        } catch (IOException ex) {
            String msg = String.format("Error creating File object for %s", pythonModulesDirPath); //NON-NLS
            logger.log(Level.SEVERE, msg, ex);
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    NbBundle.getMessage(OpenPythonModulesFolderAction.class, "OpenPythonModulesFolderAction.errorMsg.folderNotFound", pythonModulesDirPath),
                    NotifyDescriptor.ERROR_MESSAGE));
        }
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
