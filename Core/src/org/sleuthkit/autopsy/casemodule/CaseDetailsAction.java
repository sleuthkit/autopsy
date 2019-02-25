/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.util.EnumSet;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;

/**
 * The action associated with the Case/Case Details menu item. It invokes the
 * dialog containing the CaseInformationPanel.
 */
final class CaseDetailsAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;
    private static JDialog caseDetailsDialog;

    CaseDetailsAction() {
        putValue(Action.NAME, NbBundle.getMessage(CaseDetailsAction.class, "CTL_CaseDetailsAction"));
        this.setEnabled(false);
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            setEnabled(null != evt.getNewValue());
        });
    }

    @Override
    public void performAction() {
        SwingUtilities.invokeLater(() -> {
            String title = NbBundle.getMessage(this.getClass(), "CaseDetailsAction.window.title");
            Frame mainWindow = WindowManager.getDefault().getMainWindow();
            caseDetailsDialog = new JDialog(mainWindow, title, true);
            CaseInformationPanel caseInformationPanel = new CaseInformationPanel();
            caseInformationPanel.addCloseButtonAction((ActionEvent e) -> {
                caseDetailsDialog.dispose();
            });
            caseDetailsDialog.add(caseInformationPanel);
            caseDetailsDialog.setResizable(true);
            caseDetailsDialog.pack();
            caseDetailsDialog.setLocationRelativeTo(mainWindow);
            caseDetailsDialog.setVisible(true);
            caseDetailsDialog.toFront();
        });
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(CaseDetailsAction.class, "CTL_CaseDetailsAction");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
