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

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;

/**
 * The action associated with the Case/Case Properties menu item. It invokes the
 * Case Properties dialog.
 */
final class CasePropertiesAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;
    private static JDialog casePropertiesDialog;

    CasePropertiesAction() {
        putValue(Action.NAME, NbBundle.getMessage(CasePropertiesAction.class, "CTL_CasePropertiesAction"));
        this.setEnabled(false);
        Case.addEventSubscriber(Case.Events.CURRENT_CASE.toString(), (PropertyChangeEvent evt) -> {
            setEnabled(null != evt.getNewValue());
        });
    }

    @Override
    public void performAction() {
        SwingUtilities.invokeLater(() -> {
            String title = NbBundle.getMessage(this.getClass(), "CasePropertiesAction.window.title");
            casePropertiesDialog = new JDialog(WindowManager.getDefault().getMainWindow(), title, false);
            CaseInformationPanel caseInformationPanel = new CaseInformationPanel();
            caseInformationPanel.addCloseButtonAction((ActionEvent e) -> {
                casePropertiesDialog.setVisible(false);
            });
            casePropertiesDialog.add(caseInformationPanel);
            casePropertiesDialog.setResizable(true);
            casePropertiesDialog.pack();

            Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
            double w = casePropertiesDialog.getSize().getWidth();
            double h = casePropertiesDialog.getSize().getHeight();
            casePropertiesDialog.setLocation((int) ((screenDimension.getWidth() - w) / 2), (int) ((screenDimension.getHeight() - h) / 2));
            casePropertiesDialog.setVisible(true);
            casePropertiesDialog.setVisible(true);
            casePropertiesDialog.toFront();
        });
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(CasePropertiesAction.class, "CTL_CasePropertiesAction");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
