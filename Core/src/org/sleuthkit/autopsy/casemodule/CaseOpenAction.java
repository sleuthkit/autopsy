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
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * An action that opens an existing case.
 */
@ServiceProvider(service = CaseOpenAction.class)
public final class CaseOpenAction extends CallableSystemAction implements ActionListener {

    private static final Logger logger = Logger.getLogger(CaseOpenAction.class.getName());
    private static final String PROP_BASECASE = "LBL_BaseCase_PATH"; //NON-NLS
    private static final long serialVersionUID = 1L;
    private final JFileChooser fileChooser = new JFileChooser();
    private final FileFilter caseMetadataFileFilter;

    /**
     * Constructs an action that opens an existing case.
     */
    public CaseOpenAction() {
        caseMetadataFileFilter = new FileNameExtensionFilter(NbBundle.getMessage(CaseOpenAction.class, "CaseOpenAction.autFilter.title", Version.getName(), CaseMetadata.getFileExtension()), CaseMetadata.getFileExtension().substring(1));
        fileChooser.setDragEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileFilter(caseMetadataFileFilter);
        if (null != ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_BASECASE)) {
            fileChooser.setCurrentDirectory(new File(ModuleSettings.getConfigSetting("Case", PROP_BASECASE))); //NON-NLS
        }
    }

    /**
     * Pops up a file chooser to allow the user to select a case metadata file
     * (.aut file) and attempts to open the case described by the file.
     *
     * @param e The action event.
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
                    logger.log(Level.SEVERE, String.format("Error closing case at %s while ingest was running", (null != currentCase ? currentCase.getCaseDirectory() : "?")), ex); //NON-NLS
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
            /*
             * Close the startup window, if it is open.
             */
            StartupWindowProvider.getInstance().close();

            /*
             * Try to open the case associated with the case metadata file the
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
                    logger.log(Level.SEVERE, String.format("Error opening case with metadata file path %s", path), ex); //NON-NLS
                    SwingUtilities.invokeLater(() -> {
                        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        JOptionPane.showMessageDialog(
                                WindowManager.getDefault().getMainWindow(),
                                ex.getMessage(), // Should be user-friendly
                                NbBundle.getMessage(this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.title"), //NON-NLS
                                JOptionPane.ERROR_MESSAGE); 
                        if (!Case.isCaseOpen()) {
                            StartupWindowProvider.getInstance().open();
                        }
                    });
                }
            }).start();
        }
    }

    @Override
    public void performAction() {
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(CaseOpenAction.class, "CTL_OpenAction");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
