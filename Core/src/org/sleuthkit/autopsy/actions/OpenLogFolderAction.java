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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "Help",
        id = "org.sleuthkit.autopsy.actions.OpenLogFolder")
@ActionRegistration(
        displayName = "#CTL_OpenLogFolder")
@ActionReference(path = "Menu/Help", position = 1750)
// Move to Bundle for I18N
//@Messages("CTL_OpenLogFolder=Open Log Folder")
/**
 * Action in menu to open the folder containing the log files
 */
public final class OpenLogFolderAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            File logDir = new File(Places.getUserDirectory().getAbsolutePath() + File.separator + "var" + File.separator + "log");
            if (logDir.exists() == false) {
                NotifyDescriptor d =
                        new NotifyDescriptor.Message(
                                NbBundle.getMessage(this.getClass(), "OpenLogFolder.error1", logDir.getAbsolutePath()),
                                NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notify(d);
            } else {
                Desktop.getDesktop().open(logDir);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
