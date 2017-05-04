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
import java.awt.event.ActionListener;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.IngestRunningCheck;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * An action listener for a specific case, associated with a Recent Cases menu
 * item for the case by a DynamicMenuContent content JMenuItem.
 *
 * This action should only be invoked in the event dispatch thread (EDT).
 */
class RecentItems implements ActionListener {

    private static final Logger logger = Logger.getLogger(RecentItems.class.getName());
    private final String caseMetaDataFilePath;

    /**
     * Constructs an action listener for a specific case, associated with a
     * Recent Cases menu item for the case by a DynamicMenuContent content
     * JMenuItem.
     *
     * @param caseName             The name of the case.
     * @param caseMetaDataFilePath The path to the case metadata file.
     */
    RecentItems(String caseName, String caseMetaDataFilePath) {
        this.caseMetaDataFilePath = caseMetaDataFilePath;
    }

    /**
     * Opens the case associated with the action.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        String optionsDlgTitle = NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning.title");
        String optionsDlgMessage = NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning");
        if (IngestRunningCheck.checkAndConfirmProceed(optionsDlgTitle, optionsDlgMessage)) {
            new Thread(() -> {
                try {
                    Case.openAsCurrentCase(caseMetaDataFilePath);
                    StartupWindowProvider.getInstance().close();
                } catch (CaseActionException ex) {
                    SwingUtilities.invokeLater(() -> {
                        if (!(ex instanceof CaseActionCancelledException)) {
                            logger.log(Level.SEVERE, String.format("Error opening case with metadata file path %s", caseMetaDataFilePath), ex); //NON-NLS
                            JOptionPane.showMessageDialog(
                                    WindowManager.getDefault().getMainWindow(),
                                    ex.getMessage(),
                                    NbBundle.getMessage(RecentItems.this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.title"), //NON-NLS
                                    JOptionPane.ERROR_MESSAGE);
                        }
                        StartupWindowProvider.getInstance().open();
                    });
                }
            }).start();
        }
    }
}
