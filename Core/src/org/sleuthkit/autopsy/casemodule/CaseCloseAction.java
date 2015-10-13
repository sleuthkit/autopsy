/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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
import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingWorker;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;

/**
 * The action to close the current Case. This class should be disabled on
 * creation and it will be enabled on new case creation or case opened.
 */
public final class CaseCloseAction extends CallableSystemAction implements Presenter.Toolbar {

    JButton toolbarButton = new JButton();

    /**
     * The constructor for this class
     */
    public CaseCloseAction() {
        putValue("iconBase", "org/sleuthkit/autopsy/images/close-icon.png"); // put the icon NON-NLS
        putValue(Action.NAME, NbBundle.getMessage(CaseCloseAction.class, "CTL_CaseCloseAct")); // put the action Name

        // set action of the toolbar button
        toolbarButton.addActionListener(CaseCloseAction.this::actionPerformed);

        this.setEnabled(false);
    }

    /**
     * Closes the current opened case.
     *
     * @param e the action event for this method
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (Case.existsCurrentCase() == false) {
            return;
        }

        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    Case result = Case.getCurrentCase();
                    result.closeCase();
                } catch (CaseActionException | IllegalStateException unused) {
                    // Already logged.
                }
                return null;
            }

            @Override
            protected void done() {
                StartupWindowProvider.getInstance().open();
            }
        }.execute();
    }

    /**
     * This method does nothing. Use the "actionPerformed(ActionEvent e)"
     * instead of this method.
     */
    @Override
    public void performAction() {
    }

    /**
     * Gets the name of this action. This may be presented as an item in a menu.
     *
     * @return actionName
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(CaseCloseAction.class, "CTL_CaseCloseAct");
    }

    /**
     * Gets the HelpCtx associated with implementing object
     *
     * @return HelpCtx or HelpCtx.DEFAULT_HELP
     */
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * Returns the toolbar component of this action
     *
     * @return component the toolbar button
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
