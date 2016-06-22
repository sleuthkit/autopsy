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

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Action in menu to open the folder containing the output files
 */
@ActionRegistration(
        displayName = "#CTL_OpenOutputFolder", iconInMenu = true)
@ActionReference(path = "Menu/Tools", position = 1850, separatorBefore = 1849)
@ActionID(id = "org.sleuthkit.autopsy.actions.OpenOutputFolderAction", category = "Help")
public final class OpenOutputFolderAction extends CallableSystemAction {

    private static final Logger logger = Logger.getLogger(OpenOutputFolderAction.class.getName());

    @Override
    public void performAction() {

        try {
            File outputDir;
            if (Case.isCaseOpen()) {
                outputDir = new File(Case.getCurrentCase().getOutputDirectory());
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
            logger.log(Level.WARNING, NbBundle.getMessage(this.getClass(), "OpenOutputFolder.CouldNotOpenOutputFolder"), ex); //NON-NLS
        }
    }

    @Override
    public boolean isEnabled() {
        return Case.isCaseOpen();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }
    @Override
    public String getName() {
        return NbBundle.getMessage(OpenOutputFolderAction.class, "CTL_OpenOutputFolder");
    }
}
