/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingWorker;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.IngestRunningCheck;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * The action associated with the Case/Close Case menu item and the Close Case
 * toolbar button. It closes the current case and pops up the start up window
 * that allows a user to open another case.
 *
 * This action should only be invoked in the event dispatch thread (EDT).
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.casemodule.CaseCloseAction")
@ActionRegistration(displayName = "#CTL_CaseCloseAct", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Toolbars/Case", position = 107)})
public final class CaseCloseAction extends CallableSystemAction implements Presenter.Toolbar {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(CaseCloseAction.class.getName());
    private final JButton toolbarButton = new JButton();

    /**
     * Constructs the action associated with the Case/Close Case menu item and
     * the Close Case toolbar button.
     */
    public CaseCloseAction() {
        putValue("iconBase", "org/sleuthkit/autopsy/images/close-icon.png"); //NON-NLS
        putValue(Action.NAME, NbBundle.getMessage(CaseCloseAction.class, "CTL_CaseCloseAct")); //NON-NLS
        toolbarButton.addActionListener(CaseCloseAction.this::actionPerformed);
        this.setEnabled(false);
    }

    /**
     * Closes the current case.
     *
     * @param e The action event.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        String optionsDlgTitle = NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning.title");
        String optionsDlgMessage = NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning");
        if (IngestRunningCheck.checkAndConfirmProceed(optionsDlgTitle, optionsDlgMessage)) {
            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new SwingWorker<Void, Void>() {

                @Override
                protected Void doInBackground() throws Exception {
                    Case.closeCurrentCase();
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                    } catch (InterruptedException ex) {
                        logger.log(Level.SEVERE, "Unexpected interrupt closing the current case", ex);
                    } catch (ExecutionException ex) {
                        logger.log(Level.SEVERE, "Error closing the current case", ex);
                        MessageNotifyUtil.Message.error(Bundle.Case_closeException_couldNotCloseCase(ex.getMessage()));
                    }
                    WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    StartupWindowProvider.getInstance().open();
                }
            }.execute();
        }
    }

    /**
     * Closes the current case.
     */
    @Override
    public void performAction() {
        actionPerformed(null);
    }

    /**
     * Gets the action name.
     *
     * @return The action name.
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(CaseCloseAction.class, "CTL_CaseCloseAct");
    }

    /**
     * Gets the help context.
     *
     * @return The help context.
     */
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * Returns the toolbar component of this action.
     *
     * @return The toolbar button
     */
    @Override
    public Component getToolbarPresenter() {
        ImageIcon icon = new ImageIcon(getClass().getResource("btn_icon_close_case.png")); //NON-NLS
        toolbarButton.setIcon(icon);
        toolbarButton.setText(this.getName());
        return toolbarButton;
    }

    /**
     * Set this action to be enabled/disabled
     *
     * @param value whether to enable this action or not
     */
    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        toolbarButton.setEnabled(value);
    }
}
