/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2019 Basis Technology Corp.
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
import java.io.File;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * An action that opens a multi-user case and hides the open multi-user case
 * dialog given the coordination service node data for the case.
 */
final class OpenMultiUserCaseAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(OpenMultiUserCaseAction.class.getName());
    private final CaseNodeData caseNodeData;

    /**
     * Constructs an action that opens a multi-user case and hides the open
     * multi-user case dialog given the coordination service node data for the
     * case.
     *
     * @param caseNodeData The coordination service node data for the case
     *                     associated with this action.
     */
    @NbBundle.Messages({
        "OpenMultiUserCaseAction.menuItemText=Open Case"
    })
    OpenMultiUserCaseAction(CaseNodeData caseNodeData) {
        super(Bundle.OpenMultiUserCaseAction_menuItemText());
        this.caseNodeData = caseNodeData;
    }

    @NbBundle.Messages({
        "# {0} - caseErrorMessage", "OpenMultiUserCaseAction.caseOpeningErrorErrorMsg=Failed to open case: {0}"
    })
    @Override
    public void actionPerformed(ActionEvent event) {
        StartupWindowProvider.getInstance().close();
        OpenMultiUserCaseDialog.getInstance().setVisible(false);
        new Thread(() -> {
            String caseMetadataFilePath = null;
            File caseDirectory = caseNodeData.getDirectory().toFile();
            File[] filesInDirectory = caseDirectory.listFiles();
            if (filesInDirectory != null) {
                for (File file : filesInDirectory) {
                    if (file.getName().toLowerCase().endsWith(CaseMetadata.getFileExtension()) && file.isFile()) {
                        caseMetadataFilePath = file.getPath();
                    }
                }
            }
            if (caseMetadataFilePath != null) {
                try {
                    Case.openAsCurrentCase(caseMetadataFilePath);
                } catch (CaseActionException ex) {
                    if (null != ex.getCause() && !(ex.getCause() instanceof CaseActionCancelledException)) {
                        logger.log(Level.SEVERE, String.format("Error opening case with metadata file path %s", caseMetadataFilePath), ex); //NON-NLS
                    }
                    SwingUtilities.invokeLater(() -> {
                        MessageNotifyUtil.Message.error(Bundle.OpenMultiUserCaseAction_caseOpeningErrorErrorMsg(ex.getLocalizedMessage()));
                        StartupWindowProvider.getInstance().open();
                        OpenMultiUserCaseDialog.getInstance().setVisible(true);
                    });
                }
            } else {
                SwingUtilities.invokeLater(() -> {
                    MessageNotifyUtil.Message.error(Bundle.OpenMultiUserCaseAction_caseOpeningErrorErrorMsg("Could not locate case metadata file."));
                });
            }
        }).start();
    }

    @Override
    public OpenMultiUserCaseAction clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

}
