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

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.Version;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.logging.Level;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * An action that opens an existing case.
 */
@ServiceProvider(service = CaseOpenAction.class)
public final class CaseOpenAction implements ActionListener {

    private static final String PROP_BASECASE = "LBL_BaseCase_PATH"; //NON-NLS
    private final JFileChooser fileChooser = new JFileChooser();
    private final FileFilter caseMetadataFileFilter;

    /**
     * Constructs an action that opens an existing case.
     */
    public CaseOpenAction() {
        caseMetadataFileFilter = new FileNameExtensionFilter(NbBundle.getMessage(CaseOpenAction.class, "CaseOpenAction.autFilter.title", Version.getName(), Case.CASE_DOT_EXTENSION), Case.CASE_EXTENSION);
        fileChooser.setDragEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileFilter(caseMetadataFileFilter);
        if (null != ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_BASECASE)) {
            fileChooser.setCurrentDirectory(new File(ModuleSettings.getConfigSetting("Case", PROP_BASECASE))); //NON-NLS
        }
    }

    /**
     * Pops up a file chooser to allow the user to select a case meta data file
     * (.aut file) and attempts to open the case described by the file.
     *
     * @param e The action event.
     */
    @Override
    public void actionPerformed(ActionEvent e) {

        /*
         * If ingest is running, do a dialog to warn the user and confirm
         * abandoning the ingest.
         */
        if (IngestManager.getInstance().isIngestRunning()) {
            String closeCurrentCase = NbBundle.getMessage(this.getClass(), "CloseCaseWhileIngesting.Warning");
            NotifyDescriptor descriptor = new NotifyDescriptor.Confirmation(closeCurrentCase,
                    NbBundle.getMessage(this.getClass(), "CloseCaseWhileIngesting.Warning.title"),
                    NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE);
            descriptor.setValue(NotifyDescriptor.NO_OPTION);
            Object res = DialogDisplayer.getDefault().notify(descriptor);
            if (res != null && res == DialogDescriptor.YES_OPTION) {
                try {
                    Case.getCurrentCase().closeCase();
                } catch (Exception ex) {
                    Logger.getLogger(NewCaseWizardAction.class.getName()).log(Level.WARNING, "Error closing case", ex); //NON-NLS
                }
            } else {
                return;
            }
        }

        /**
         * Pop up a file chooser to allow the user to select a case meta data
         * file (.aut file).
         */
        int retval = fileChooser.showOpenDialog(WindowManager.getDefault().getMainWindow());
        if (retval == JFileChooser.APPROVE_OPTION) {
            /**
             * This is a bit of a hack, but close the startup window, if it was
             * the source of the action invocation.
             */
            try {
                StartupWindowProvider.getInstance().close();
            } catch (Exception unused) {
            }

            /**
             * Try to open the case associated with the case meta data file the
             * user selected.
             */
            final String path = fileChooser.getSelectedFile().getPath();
            String dirPath = fileChooser.getSelectedFile().getParent();
            ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_BASECASE, dirPath.substring(0, dirPath.lastIndexOf(File.separator)));
            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new Thread(() -> {
                try {
                    Case.open(path);
                } catch (CaseActionException ex) {
                    SwingUtilities.invokeLater(() -> {
                        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), ex.getMessage() + " "
                                + NbBundle.getMessage(this.getClass(), "CaseExceptionWarning.CheckMultiUserOptions"),
                                NbBundle.getMessage(this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.title"), JOptionPane.ERROR_MESSAGE); //NON-NLS
                        if (!Case.isCaseOpen()) {
                            StartupWindowProvider.getInstance().open();
                        }
                    });
                }
            }).start();
        }
    }
}
