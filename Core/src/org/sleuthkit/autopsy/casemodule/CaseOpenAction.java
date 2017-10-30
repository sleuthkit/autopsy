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

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.IngestRunningCheck;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.Version;

/**
 * The action associated with the Case/Open Case menu item via the layer.xml
 * file, a toolbar button, and the Create New Case button of the start up window
 * that allows a user to open a case. It opens an existing case.
 *
 * This action should only be invoked in the event dispatch thread (EDT).
 */
@ServiceProvider(service = CaseOpenAction.class)
public final class CaseOpenAction extends CallableSystemAction implements ActionListener {

    private static final long serialVersionUID = 1L;
    private static final String PROP_BASECASE = "LBL_BaseCase_PATH"; //NON-NLS
    private static final Logger logger = Logger.getLogger(CaseOpenAction.class.getName());
    private final JFileChooser fileChooser = new JFileChooser();
    private final FileFilter caseMetadataFileFilter;

    /**
     * Constructs the action associated with the Case/Open Case menu item via
     * the layer.xml file, a toolbar button, and the Create New Case button of
     * the start up window that allows a user to open a case. It opens an
     * existing case.
     *
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
        String optionsDlgTitle = NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning.title");
        String optionsDlgMessage = NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning");
        if (IngestRunningCheck.checkAndConfirmProceed(optionsDlgTitle, optionsDlgMessage)) {
            /**
             * Pop up a file chooser to allow the user to select a case metadata
             * file (.aut file).
             */
            int retval = fileChooser.showOpenDialog(WindowManager.getDefault().getMainWindow());
            if (retval == JFileChooser.APPROVE_OPTION) {
                /*
                 * Close the startup window, if it is open.
                 */
                StartupWindowProvider.getInstance().close();

                /*
                 * Try to open the case associated with the case metadata file
                 * the user selected.
                 */
                final String path = fileChooser.getSelectedFile().getPath();
                String dirPath = fileChooser.getSelectedFile().getParent();
                ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_BASECASE, dirPath.substring(0, dirPath.lastIndexOf(File.separator)));
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                new SwingWorker<Void, Void>() {

                    @Override
                    protected Void doInBackground() throws Exception {
                        Case.openAsCurrentCase(path);
                        return null;
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                        } catch (InterruptedException | ExecutionException ex) {
                            if (ex instanceof InterruptedException || (null != ex.getCause() && !(ex.getCause() instanceof CaseActionCancelledException))) {
                                logger.log(Level.SEVERE, String.format("Error opening case with metadata file path %s", path), ex); //NON-NLS
                                JOptionPane.showMessageDialog(
                                        WindowManager.getDefault().getMainWindow(),
                                        ex.getCause().getMessage(), //get the message of the wrapped exception
                                        NbBundle.getMessage(this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.title"), //NON-NLS
                                        JOptionPane.ERROR_MESSAGE);
                            }
                            StartupWindowProvider.getInstance().open();
                        } finally {
                            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        }
                    }
                }.execute();
            }
        }
    }

    @Override
    public void performAction() {
        actionPerformed(null);
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(CaseOpenAction.class, "CTL_CaseOpenAction");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
