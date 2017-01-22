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
 * Action to open the subdirectory of the current case that contains the output
 * files.
 */
@ActionRegistration(displayName = "#CTL_OpenOutputFolder", iconInMenu = true, lazy = true)
@ActionReference(path = "Menu/Tools", position = 1850, separatorBefore = 1849)
@ActionID(id = "org.sleuthkit.autopsy.actions.OpenOutputFolderAction", category = "Help")
public final class OpenOutputFolderAction extends CallableSystemAction {

    private static final Logger LOGGER = Logger.getLogger(OpenOutputFolderAction.class.getName());
    private static final long serialVersionUID = 1L;

    @Override
    public void performAction() {
        File outputDir;
        try {
            Case currentCase = Case.getCurrentCase();
            outputDir = new File(currentCase.getOutputDirectory());
            if (outputDir.exists()) {
                try {
                    Desktop.getDesktop().open(outputDir);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Failed to open output folder %s", outputDir), ex); //NON-NLS
                    NotifyDescriptor descriptor = new NotifyDescriptor.Message(
                            NbBundle.getMessage(this.getClass(), "OpenOutputFolder.CouldNotOpenOutputFolder", outputDir.getAbsolutePath()), NotifyDescriptor.ERROR_MESSAGE);
                    DialogDisplayer.getDefault().notify(descriptor);
                }
            } else {
                NotifyDescriptor descriptor = new NotifyDescriptor.Message(
                        NbBundle.getMessage(this.getClass(), "OpenOutputFolder.error1", outputDir.getAbsolutePath()), NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notify(descriptor);
            }
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "OpenOutputFolderAction enabled with no current case", ex); //NON-NLS
            JOptionPane.showMessageDialog(null, NbBundle.getMessage(this.getClass(), "OpenOutputFolder.noCaseOpen"));
        }
    }

    @Override
    public boolean isEnabled() {
        try {
            Case.getCurrentCase();
            return true;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false;
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(OpenOutputFolderAction.class, "CTL_OpenOutputFolder");
    }
}
