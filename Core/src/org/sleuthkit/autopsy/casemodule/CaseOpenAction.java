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
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;

/**
 * The action to open a existing case. This class is always enabled.
 */
@ServiceProvider(service = CaseOpenAction.class)
public final class CaseOpenAction implements ActionListener {

    private static final Logger logger = Logger.getLogger(CaseOpenAction.class.getName());
    private static final String PROP_BASECASE = "LBL_BaseCase_PATH";
    private final JFileChooser fc = new JFileChooser();
    private FileFilter autFilter;

    /**
     * The constructor
     */
    public CaseOpenAction() {
        autFilter = new FileNameExtensionFilter(
                NbBundle.getMessage(CaseOpenAction.class, "CaseOpenAction.autFilter.title", Version.getName(),
                                    Case.CASE_DOT_EXTENSION),
                Case.CASE_EXTENSION);
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        fc.setFileFilter(autFilter);
        try {
            if (ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_BASECASE) != null) {
                fc.setCurrentDirectory(new File(ModuleSettings.getConfigSetting("Case", PROP_BASECASE)));
            }
        } catch (Exception e) {
        }
    }

    /**
     * Pop-up the File Chooser to open the existing case (.aut file)
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Logger.noteAction(this.getClass());


        int retval = fc.showOpenDialog((Component) e.getSource());

        if (retval == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            String dirPath = fc.getSelectedFile().getParent();
            ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_BASECASE, dirPath.substring(0, dirPath.lastIndexOf(File.separator)));
            // check if the file exists
            if (!new File(path).exists()) {
                JOptionPane.showMessageDialog(null,
                                              NbBundle.getMessage(this.getClass(),
                                                                  "CaseOpenAction.msgDlg.fileNotExist.msg"),
                                              NbBundle.getMessage(this.getClass(),
                                                                  "CaseOpenAction.msgDlg.fileNotExist.title"),
                                              JOptionPane.ERROR_MESSAGE);
                this.actionPerformed(e); // show the dialog box again
            } else {
                // try to close Startup window if there's one
                try {
                    StartupWindowProvider.getInstance().close();
                } catch (Exception ex) {
                    // no need to show the error message to the user.
                    logger.log(Level.WARNING, "Error closing startup window.", ex);
                }
                try {
                    Case.open(path); // open the case
                } catch (CaseActionException ex) {
                    JOptionPane.showMessageDialog(null,
                                                  NbBundle.getMessage(this.getClass(),
                                                                      "CaseOpenAction.msgDlg.cantOpenCase.msg", path,
                                                                      ex.getMessage()),
                                                  NbBundle.getMessage(this.getClass(),
                                                                      "CaseOpenAction.msgDlg.cantOpenCase.title"),
                                                  JOptionPane.ERROR_MESSAGE);
                    logger.log(Level.WARNING, "Error opening case in folder " + path, ex);

                    StartupWindowProvider.getInstance().open();
                }
            }
        }
    }
}
