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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.logging.Log;

/**
 * The action to open a existing case. This class is always enabled.
 *
 * @author jantonius
 */
@ServiceProvider(service = CaseOpenAction.class)
public final class CaseOpenAction implements ActionListener {

    JFileChooser fc = new JFileChooser();
    GeneralFilter autFilter = new GeneralFilter(new String[]{".aut"}, "AUTOPSY File (*.aut)", false);

    /** The constructor */
    public CaseOpenAction() {
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        fc.addChoosableFileFilter(autFilter);
    }

    /**
     * Pop-up the File Chooser to open the existing case (.aut file)
     * 
     * @param e  the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Log.noteAction(this.getClass());


        int retval = fc.showOpenDialog((Component) e.getSource());
        if (retval == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();

            // check if the file exists
            if (!new File(path).exists()) {
                JOptionPane.showMessageDialog(null, "Error: File doesn't exist.", "Error", JOptionPane.ERROR_MESSAGE);
                this.actionPerformed(e); // show the dialog box again
            } else {
                // try to close Startup window if there's one
                try {
                    StartupWindow.getInstance().close();
                } catch (Exception ex) {
                    // no need to show the error message to the user.
                    // TODO: But maybe put the error message in the log in the future.
                }
                try {
                    Case.open(path); // open the case
                } catch (Exception ex) {
                    Logger.getLogger(CaseOpenAction.class.getName()).log(Level.SEVERE, "Error opening case.", ex);
                }
            }
        }
    }
}
