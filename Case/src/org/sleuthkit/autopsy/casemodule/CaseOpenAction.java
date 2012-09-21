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
import java.util.Collections;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * The action to open a existing case. This class is always enabled.
 */
@ServiceProvider(service = CaseOpenAction.class)
public final class CaseOpenAction implements ActionListener {
    private static final Logger logger = Logger.getLogger(CaseOpenAction.class.getName());
    private static final String PROP_BASECASE = "LBL_BaseCase_PATH";
    ModuleSettings AutopsyProperties = ModuleSettings.getInstance();

    JFileChooser fc = new JFileChooser();
    GeneralFilter autFilter = new GeneralFilter(Collections.<String>singletonList(".aut"), "AUTOPSY File (*.aut)");

    /** The constructor */
    public CaseOpenAction() {
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        //fc.setAcceptAllFileFilterUsed(false);
        fc.addChoosableFileFilter(autFilter);
        //fc.addChoosableFileFilter(fc.getAcceptAllFileFilter());
        try{
        if(AutopsyProperties.getConfigSetting("Case", PROP_BASECASE) != null)
            fc.setCurrentDirectory(new File(AutopsyProperties.getConfigSetting("Case", PROP_BASECASE)));
        }
        catch(Exception e){
            
        }
    }

    /**
     * Pop-up the File Chooser to open the existing case (.aut file)
     * 
     * @param e  the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Logger.noteAction(this.getClass());


        int retval = fc.showOpenDialog((Component) e.getSource());
        if (retval == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            String dirPath = fc.getSelectedFile().getParent();
            AutopsyProperties.setConfigSetting("Case", PROP_BASECASE, dirPath.substring(0, dirPath.lastIndexOf(File.separator)));
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
                    logger.log(Level.WARNING, "Error closing startup window.", ex);
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
