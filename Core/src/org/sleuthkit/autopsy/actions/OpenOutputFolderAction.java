/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2015 Basis Technology Corp.
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
import javax.swing.JOptionPane;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Action in menu to open the folder containing the output files
 */
@ActionRegistration(
        displayName = "#CTL_OpenOutputFolder", iconInMenu = true)
@ActionReference(path = "Menu/Help", position = 1850)
@ActionID(id = "org.sleuthkit.autopsy.actions.OpenOutputFolderAction", category = "Help")
public final class OpenOutputFolderAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {

        try {
            File outputDir;
            if (Case.isCaseOpen()) {
                outputDir = new File(Case.getCurrentCase().getHostDirectory());
                if (outputDir.exists() == false) {
                    NotifyDescriptor d
                            = new NotifyDescriptor.Message(NbBundle.getMessage(this.getClass(),
                                    "OpenOutputFolder.error1", outputDir.getAbsolutePath()),
                                    NotifyDescriptor.ERROR_MESSAGE);
                    DialogDisplayer.getDefault().notify(d);
                } else {
                    Desktop.getDesktop().open(outputDir);
                }
            } else {
                JOptionPane.showMessageDialog(null, NbBundle.getMessage(this.getClass(), "OpenOutputFolder.noCaseOpen"));
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
