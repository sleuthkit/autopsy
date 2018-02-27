/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import org.openide.modules.Places;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * The action associated with the Help/Open Log Folder menu item. It opens a
 * file explorer window for either the log subdirectory for the currently open
 * case, or the log subdirectory of the user directory, if there is no current
 * case.
 * 
 * This action should only be invoked in the event dispatch thread (EDT).
 */
@ActionRegistration(displayName = "#CTL_OpenLogFolder", iconInMenu = true)
@ActionReference(path = "Menu/Help", position = 1750)
@ActionID(id = "org.sleuthkit.autopsy.actions.OpenLogFolderAction", category = "Help")
public final class OpenLogFolderAction implements ActionListener {

    private static final Logger logger = Logger.getLogger(OpenLogFolderAction.class.getName());
    
    @Override
    public void actionPerformed(ActionEvent e) {
        File logDir;
        if (Case.isCaseOpen()) {
            try {
                /*
                 * Open the log directory for the case.
                 */
                Case currentCase = Case.getOpenCase();
                logDir = new File(currentCase.getLogDirectoryPath());
            } catch (NoCurrentCaseException ex) {
                /*
                 * There is no open case, open the application level log
                 * directory.
                 */
                logDir = new File(Places.getUserDirectory().getAbsolutePath() + File.separator + "var" + File.separator + "log");
            }
        } else {
            logDir = new File(Places.getUserDirectory().getAbsolutePath() + File.separator + "var" + File.separator + "log");
        }

        try {
            if (logDir.exists()) {
                Desktop.getDesktop().open(logDir);
            } else {
                logger.log(Level.SEVERE, String.format("The log directory %s does not exist", logDir));
                NotifyDescriptor notifyDescriptor = new NotifyDescriptor.Message(
                        NbBundle.getMessage(this.getClass(), "OpenLogFolder.error1", logDir.getAbsolutePath()),
                        NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notify(notifyDescriptor);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Could not open log directory %s", logDir), ex);
            NotifyDescriptor notifyDescriptor = new NotifyDescriptor.Message(
                    NbBundle.getMessage(this.getClass(), "OpenLogFolder.CouldNotOpenLogFolder", logDir.getAbsolutePath()),
                    NotifyDescriptor.ERROR_MESSAGE);
            DialogDisplayer.getDefault().notify(notifyDescriptor);
        }
    }

}
