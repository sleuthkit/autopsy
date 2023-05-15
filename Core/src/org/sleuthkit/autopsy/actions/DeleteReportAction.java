/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2022 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action for deleting selected reports.
 */
@Messages({
    "DeleteReportAction.actionDisplayName.singleReport=Delete Report",
    "DeleteReportAction.actionDisplayName.multipleReports=Delete Reports"
})
public class DeleteReportAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private static DeleteReportAction instance = null;

    /**
     * @return Singleton instance of this class.
     */
    public static DeleteReportAction getInstance() {
        if (instance == null) {
            instance = new DeleteReportAction();
        }

        if (Utilities.actionsGlobalContext().lookupAll(Report.class).size() == 1) {
            instance.putValue(Action.NAME, Bundle.DeleteReportAction_actionDisplayName_singleReport());
        } else {
            instance.putValue(Action.NAME, Bundle.DeleteReportAction_actionDisplayName_multipleReports());
        }
        return instance;
    }

    /**
     * Do not instantiate directly. Use DeleteReportAction.getInstance(),
     * instead.
     */
    private DeleteReportAction() {
    }

    @NbBundle.Messages({
        "DeleteReportAction.showConfirmDialog.single.explanation=The report will remain on disk.",
        "DeleteReportAction.showConfirmDialog.multiple.explanation=The reports will remain on disk.",
        "DeleteReportAction.showConfirmDialog.errorMsg=An error occurred while deleting the reports.",
        "DeleteReportAction.actionPerformed.showConfirmDialog.title=Confirm Deletion",
        "DeleteReportAction.actionPerformed.showConfirmDialog.single.msg=Do you want to delete 1 report from the case?",
        "# {0} - reportNum",
        "DeleteReportAction.actionPerformed.showConfirmDialog.multiple.msg=Do you want to delete {0} reports from the case?"})
    @Override
    public void actionPerformed(ActionEvent e) {
        Collection<? extends Report> selectedReportsCollection = Utilities.actionsGlobalContext().lookupAll(Report.class);
        String message = selectedReportsCollection.size() > 1
                ? Bundle.DeleteReportAction_actionPerformed_showConfirmDialog_multiple_msg(selectedReportsCollection.size())
                : Bundle.DeleteReportAction_actionPerformed_showConfirmDialog_single_msg();
        String explanation = selectedReportsCollection.size() > 1
                ? Bundle.DeleteReportAction_showConfirmDialog_multiple_explanation()
                : Bundle.DeleteReportAction_showConfirmDialog_single_explanation();
        Object[] jOptionPaneContent = {message, explanation};
        if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(null, jOptionPaneContent,
                Bundle.DeleteReportAction_actionPerformed_showConfirmDialog_title(),
                JOptionPane.YES_NO_OPTION)) {
            try {
                Case.getCurrentCaseThrows().deleteReports(selectedReportsCollection);
            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(DeleteReportAction.class.getName()).log(Level.SEVERE, "Error deleting reports", ex); // NON-NLS
                MessageNotifyUtil.Message.error(Bundle.DeleteReportAction_showConfirmDialog_errorMsg());
            }
        }
    }
}
