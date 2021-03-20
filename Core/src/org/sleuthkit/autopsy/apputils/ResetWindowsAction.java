/*
 * Autopsy
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.apputils;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.commons.io.FileUtils;
import org.openide.LifecycleManager;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Class to open the Discovery dialog. Allows the user to run searches and see
 * results in the DiscoveryTopComponent.
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.apputils.ResetWindowsAction")
@ActionReferences(value = {
    @ActionReference(path = "Menu/Window", position = 105)})
@ActionRegistration(displayName = "#CTL_ResetWindowsAction", lazy = false)
@NbBundle.Messages({"CTL_ResetWindowsAction=Reset Windows"})
public final class ResetWindowsAction extends CallableSystemAction {

    private static final String DISPLAY_NAME = Bundle.CTL_ResetWindowsAction();
    private static final long serialVersionUID = 1L;
    private final static Logger logger = Logger.getLogger(ResetWindowsAction.class.getName());

    @Override
    public boolean isEnabled() {
        return !Case.isCaseOpen();
    }

    @NbBundle.Messages({"ResetWindowAction.confirm.title=Reset Windows",
        "ResetWindowAction.confirm.text=The program will close and restart to perform the resetting of window locations.\n\nAre you sure you want to reset all window locations?"})

    @Override
    public void performAction() {
        SwingUtilities.invokeLater(() -> {
            int response = JOptionPane.showConfirmDialog(
                    WindowManager.getDefault().getMainWindow(),
                    Bundle.ResetWindowAction_confirm_text(),
                    Bundle.ResetWindowAction_confirm_title(),
                    JOptionPane.YES_NO_OPTION);
            if (response == JOptionPane.YES_OPTION) {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        try {
                            FileUtils.deleteDirectory(new File(PlatformUtil.getUserConfigDirectory() + File.separator + "Windows2Local"));
                        } catch (IOException ex) {
                            logger.log(Level.WARNING, "Unable to delete config directory, window locations will not be reset.", ex);
                        }
                    }
                });
                LifecycleManager.getDefault().markForRestart();
                LifecycleManager.getDefault().exit();
            }
        });
    }

    /**
     * Set this action to be enabled/disabled
     *
     * @param value whether to enable this action or not
     */
    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
    }

    @Override
    public String getName() {
        return DISPLAY_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }
}
