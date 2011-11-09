/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.logging.Log;

/**
 * This class is used to add the action to the recent case menu item. When the
 * the recent case menu is pressed, it should open that selected case.
 */
class RecentItems implements ActionListener {

    String caseName;
    String casePath;
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
        Log.noteAction(this.getClass());
        
        // check if the file exists
        if(caseName.equals("") || casePath.equals("") || (!new File(casePath).exists())){
            // throw an error here
            JOptionPane.showMessageDialog(caller, "Error: Case doesn't exist.", "Error", JOptionPane.ERROR_MESSAGE);
            RecentCases.getInstance().removeRecentCase(caseName, casePath); // remove the recent case if it doesn't exist anymore
        }
        else {
            try {
                Case.open(casePath); // open the case
            } catch (Exception ex) {
                Logger.getLogger(RecentItems.class.getName()).log(Level.WARNING, "Error: Couldn't open recent case.", ex);
            }
        }
    }
}
