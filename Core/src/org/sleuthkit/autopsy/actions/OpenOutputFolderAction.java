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
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * The action associated with the Tools/Open Output Folder menu item. It opens a
 * file explorer window for the root output directory for the currently open
 * case. If the case is a single-user case, this is the case directory. If the
 * case is a multi-user case, this is a subdirectory of the case directory
 * specific to the host machine.
 *
 * This action should only be invoked in the event dispatch thread (EDT).
 */
@ActionRegistration(displayName = "#CTL_OpenOutputFolder", iconInMenu = true, lazy = false)
@ActionReference(path = "Menu/Tools", position = 1850, separatorBefore = 1849)
@ActionID(id = "org.sleuthkit.autopsy.actions.OpenOutputFolderAction", category = "Help")
public final class OpenOutputFolderAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(OpenOutputFolderAction.class.getName());
    
    @Override
    public void performAction() {
        File outputDir;
        try {
            Case currentCase = Case.getOpenCase();
            outputDir = new File(currentCase.getOutputDirectory());
            if (outputDir.exists()) {
                try {
                    Desktop.getDesktop().open(outputDir);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to open output folder %s", outputDir), ex); //NON-NLS
                    NotifyDescriptor descriptor = new NotifyDescriptor.Message(
                            NbBundle.getMessage(this.getClass(), "OpenOutputFolder.CouldNotOpenOutputFolder", outputDir.getAbsolutePath()), NotifyDescriptor.ERROR_MESSAGE);
                    DialogDisplayer.getDefault().notify(descriptor);
                }
            } else {
                NotifyDescriptor descriptor = new NotifyDescriptor.Message(
                        NbBundle.getMessage(this.getClass(), "OpenOutputFolder.error1", outputDir.getAbsolutePath()), NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notify(descriptor);
            }
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "OpenOutputFolderAction enabled with no current case", ex); //NON-NLS
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), NbBundle.getMessage(this.getClass(), "OpenOutputFolder.noCaseOpen"));
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
        return false;
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(OpenOutputFolderAction.class, "CTL_OpenOutputFolder");
    }
}
