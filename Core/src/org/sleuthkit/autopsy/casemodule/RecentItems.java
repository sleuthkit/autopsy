/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * This class is used to add the action to the recent case menu item. When the
 * the recent case menu is pressed, it should open that selected case.
 */
class RecentItems implements ActionListener {

    final String caseName;
    final String casePath;
    private JPanel caller; // for error handling

    /** the constructor */
    public RecentItems(String caseName, String casePath){
        this.caseName = caseName;
        this.casePath = casePath;
    }

    /**
     * Opens the recent case.
     *
     * @param e  the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        // check if the file exists
        if(caseName.equals("") || casePath.equals("") || (!new File(casePath).exists())){
            // throw an error here
            JOptionPane.showMessageDialog(caller,
                                          NbBundle.getMessage(this.getClass(), "RecentItems.openRecentCase.msgDlg.text",
                                                              caseName),
                                          NbBundle.getMessage(this.getClass(), "RecentItems.openRecentCase.msgDlg.err"),
                                          JOptionPane.ERROR_MESSAGE);
            RecentCases.getInstance().removeRecentCase(caseName, casePath); // remove the recent case if it doesn't exist anymore
            
            //if case is not opened, open the start window
            if (Case.isCaseOpen() == false) {
                EventQueue.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        StartupWindowProvider.getInstance().open();
                    }
                    
                });
                
            }
        }
        else {
            new SwingWorker<Void, Void>() {

                @Override
                protected Void doInBackground() throws Exception {
                    // Create case.
                    try{
                        Case.open(casePath);
                    } catch (CaseActionException ex) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(null, 
                                NbBundle.getMessage(this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.msg", casePath, 
                                ex.getMessage()), NbBundle.getMessage(this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.title"),
                                                      JOptionPane.ERROR_MESSAGE);
                        });
                        Logger.getLogger(RecentItems.class.getName()).log(Level.WARNING, "Error: Couldn't open recent case at " + casePath, ex); //NON-NLS
                    }    
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                    } catch (ExecutionException | InterruptedException ex) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(null, 
                                NbBundle.getMessage(this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.msg", casePath, 
                                ex.getMessage()), NbBundle.getMessage(this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.title"),
                                                      JOptionPane.ERROR_MESSAGE);
                        });
                        Logger.getLogger(RecentItems.class.getName()).log(Level.WARNING, "Error opening recent case. ", ex); //NON-NLS  
                    }
                }
            }.execute();
        }
    }
}
