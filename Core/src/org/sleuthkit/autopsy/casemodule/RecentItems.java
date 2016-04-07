/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import java.awt.Cursor;
import java.util.logging.Level;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * An action listener that opens a recent case.
 */
class RecentItems implements ActionListener {

    private static final Logger logger = Logger.getLogger(RecentItems.class.getName());
    private final String caseName;
    private final String caseMetaDataFilePath;

    /**
     * Constructs an action listener that opens a recent case.
     *
     * @param caseName             The name of the case.
     * @param caseMetaDataFilePath The path to the case metadata file.
     */
    public RecentItems(String caseName, String caseMetaDataFilePath) {
        this.caseName = caseName;
        this.caseMetaDataFilePath = caseMetaDataFilePath;
    }

    /**
     * Opens the recent case.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        /*
         * If ingest is running, do a dialog to warn the user and confirm the
         * intent to close the current case and leave the ingest process
         * incomplete.
         */
        if (IngestManager.getInstance().isIngestRunning()) {
            NotifyDescriptor descriptor = new NotifyDescriptor.Confirmation(
                    NbBundle.getMessage(this.getClass(), "CloseCaseWhileIngesting.Warning"),
                    NbBundle.getMessage(this.getClass(), "CloseCaseWhileIngesting.Warning.title"),
                    NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE);
            descriptor.setValue(NotifyDescriptor.NO_OPTION);
            Object res = DialogDisplayer.getDefault().notify(descriptor);
            if (res != null && res == DialogDescriptor.YES_OPTION) {
                Case currentCase = null;
                try {
                    currentCase = Case.getCurrentCase();
                    currentCase.closeCase();
                } catch (IllegalStateException ignored) {
                    /*
                     * No current case.
                     */
                } catch (CaseActionException ex) {
                    logger.log(Level.SEVERE, String.format("Error closing case at %s while ingest was running", (null!= currentCase ? currentCase.getCaseDirectory() : "?")),ex); //NON-NLS
                }
            } else {
                return;
            }
        }

        /*
         * Open the case. 
         */
        if (caseName.equals("") || caseMetaDataFilePath.equals("") || (!new File(caseMetaDataFilePath).exists())) {
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    NbBundle.getMessage(this.getClass(), "RecentItems.openRecentCase.msgDlg.text", caseName),
                    NbBundle.getMessage(this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.title"),
                    JOptionPane.ERROR_MESSAGE);
            RecentCases.getInstance().removeRecentCase(caseName, caseMetaDataFilePath);
            if (Case.isCaseOpen() == false) {
                EventQueue.invokeLater(() -> {
                    StartupWindowProvider.getInstance().open();
                });
            }
        } else {
            SwingUtilities.invokeLater(() -> {
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            });
            new Thread(() -> {
                try {
                    Case.open(caseMetaDataFilePath);
                } catch (CaseActionException ex) {
                    SwingUtilities.invokeLater(() -> {
                        logger.log(Level.SEVERE, String.format("Error opening case with metadata file path %s", caseMetaDataFilePath), ex); //NON-NLS
                        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        JOptionPane.showMessageDialog(
                                WindowManager.getDefault().getMainWindow(),
                                ex.getMessage(), // Should be user-friendly
                                NbBundle.getMessage(RecentItems.this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.title"), //NON-NLS
                                JOptionPane.ERROR_MESSAGE);
                        if (!Case.isCaseOpen()) {
                            StartupWindowProvider.getInstance().open();
                        }
                    });
                }
            }).start();
        }
    }
}
