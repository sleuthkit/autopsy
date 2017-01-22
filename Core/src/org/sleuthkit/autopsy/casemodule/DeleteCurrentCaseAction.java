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

import java.awt.event.ActionEvent;
import java.util.logging.Level;
import javax.swing.Action;
import javax.swing.JOptionPane;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * An action to delete the current case.
 */
final class DeleteCurrentCaseAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DeleteCurrentCaseAction.class.getName());

    DeleteCurrentCaseAction() { // Should pass in caller
        putValue(Action.NAME, NbBundle.getMessage(DeleteCurrentCaseAction.class, "CTL_CaseDeleteAction"));
        this.setEnabled(false);
    }

    @Override
    @Messages({
        "Case.deleteCaseConfirmationDialog.title=Delete Current Case?",
        "Case.deleteCaseConfirmationDialog.message=Are you sure you want to close and delete the current case?",
        "Case.deleteCaseFailureMessageBox.title=Failed to Delete Case",
        "# {0} - exception message", "Case.deleteCaseFailureMessageBox.message=Error deleting case: {0}",})
    public void actionPerformed(ActionEvent e) {
        try {
            Case currentCase = Case.getCurrentCase();
            String caseName = currentCase.getName();

            /*
             * Do a confirmation dialog and close the current case if the user
             * confirms he/she wants to proceed.
             */
            Object response = DialogDisplayer.getDefault().notify(new NotifyDescriptor(
                    Bundle.Case_deleteCaseConfirmationDialog_message(),
                    Bundle.Case_deleteCaseConfirmationDialog_title(),
                    NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.WARNING_MESSAGE,
                    null,
                    NotifyDescriptor.NO_OPTION));
            if (null != response && DialogDescriptor.YES_OPTION == response) {
                try {
                    Case.deleteCurrentCase(); // RJCTODO: Test this!
                } catch (CaseActionException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to delete case %s", caseName), ex);
                    JOptionPane.showMessageDialog(
                            null,
                            Bundle.Case_deleteCaseFailureMessageBox_message(ex.getMessage()),
                            Bundle.Case_deleteCaseFailureMessageBox_title(),
                            JOptionPane.ERROR_MESSAGE);
                }
                // RJCTODO: Fix this
                // because the "Delete Case" button is in the "CaseProperties" window, we have to close that window when we delete the case.
                CasePropertiesAction.closeCasePropertiesWindow();
            }
        } catch (IllegalStateException ex) {
            logger.log(Level.SEVERE, "Case delete action called with no current case", ex);
        }
    }

    @Override
    public void performAction() {
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(DeleteCurrentCaseAction.class, "CTL_CaseDeleteAction");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

}
