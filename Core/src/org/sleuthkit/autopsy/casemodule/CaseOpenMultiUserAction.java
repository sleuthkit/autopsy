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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.core.UserPreferences;
import static org.sleuthkit.autopsy.core.UserPreferences.SelectedMode.REVIEW;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;

/**
 * The action associated with the Case/Open Case menu item via the layer.xml
 * file, a toolbar button, and the Create New Case button of the start up window
 * that allows a user to open a case. It opens an existing case.
 *
 * This action should only be invoked in the event dispatch thread (EDT).
 */
@ActionID(category = "Case", id = "org.sleuthkit.autopsy.casemodule.CaseOpenMultiUserAction")
@ActionReference(path = "Menu/Case", position = 102)
@ActionRegistration(displayName = "#CTL_CaseOpenMultiUserAction", lazy = false)
@NbBundle.Messages({"CTL_CaseOpenMultiUserAction=Open Multi-User Case"})
public final class CaseOpenMultiUserAction extends CallableSystemAction implements ActionListener {

    private static final long serialVersionUID = 1L;
    private static JDialog multiUserCaseWindow;
    
    private static final String DISPLAY_NAME = Bundle.CTL_CaseOpenMultiUserAction();
    private static final String LOCAL_HOST_NAME = NetworkUtils.getLocalHostName();
    private static final String REVIEW_MODE_TITLE = "Open Multi-User Case (" + LOCAL_HOST_NAME + ")";

    /**
     * Constructs the action associated with the Case/Open Case menu item via
     * the layer.xml file, a toolbar button, and the Create New Case button of
     * the start up window that allows a user to open a case. It opens an
     * existing case.
     *
     */
    public CaseOpenMultiUserAction() {
        multiUserCaseWindow = new JDialog(
                WindowManager.getDefault().getMainWindow(),
                REVIEW_MODE_TITLE,
                Dialog.ModalityType.APPLICATION_MODAL);
        multiUserCaseWindow.getRootPane().registerKeyboardAction(
                e -> {
                    multiUserCaseWindow.setVisible(false);
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        multiUserCaseWindow.add(new MultiUserCasePanel());
        multiUserCaseWindow.pack();
        multiUserCaseWindow.setResizable(false);
    }

    public static void closeMultiUserCasesWindow() {
        if (null != multiUserCaseWindow) {
            multiUserCaseWindow.setVisible(false);
        }
    }
    
    @Override
    public boolean isEnabled() {
        return UserPreferences.getIsMultiUserModeEnabled();
    }

    /**
     * Pops up a file chooser to allow the user to select a case metadata file
     * (.aut file) and attempts to open the case described by the file.
     *
     * @param e The action event.
     */
    @Override
    public void actionPerformed(ActionEvent event) {
        multiUserCaseWindow.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        multiUserCaseWindow.setVisible(true);
    }

    @Override
    public void performAction() {
        actionPerformed(null);
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
