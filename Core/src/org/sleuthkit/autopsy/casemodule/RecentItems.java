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

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import java.awt.Cursor;

/**
 * This class is used to add the action to the recent case menu item. When the
 * the recent case menu is pressed, it should open that selected case.
 */
class RecentItems implements ActionListener {

    final String caseName;
    final String casePath;
    private JPanel caller; // for error handling

    /**
     * the constructor
     */
    public RecentItems(String caseName, String casePath) {
        this.caseName = caseName;
        this.casePath = casePath;
    }

    /**
     * Opens the recent case.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        // check if the file exists
        if (caseName.equals("") || casePath.equals("") || (!new File(casePath).exists())) {
            // throw an error here
            JOptionPane.showMessageDialog(caller,
                    NbBundle.getMessage(this.getClass(), "RecentItems.openRecentCase.msgDlg.text",
                            caseName),
                    NbBundle.getMessage(this.getClass(), "RecentItems.openRecentCase.msgDlg.err"),
                    JOptionPane.ERROR_MESSAGE);
            RecentCases.getInstance().removeRecentCase(caseName, casePath); // remove the recent case if it doesn't exist anymore

            //if case is not opened, open the start window
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
                // Create case.
                try {
                    Case.open(casePath);
                } catch (CaseActionException ex) {
                    SwingUtilities.invokeLater(() -> {
                        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), ex.getMessage(),
                                NbBundle.getMessage(RecentItems.this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.title"), JOptionPane.ERROR_MESSAGE); //NON-NLS
                        if (!Case.isCaseOpen()) {
                            StartupWindowProvider.getInstance().open();
                        }
                    });
                }
                /* KDM from RC 
                finally {
                    SwingUtilities.invokeLater(() -> {
                        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    });
                }
                */
            }).start();
        }
    }
}
