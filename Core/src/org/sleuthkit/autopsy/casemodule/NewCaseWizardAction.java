/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.awt.Cursor;
import java.awt.Dialog;
import java.io.File;
import java.text.MessageFormat;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.SystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.IngestRunningCheck;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamOrganization;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * The action associated with the Case/New Case menu item, t toolbar button, and
 * the button in the start up window that allows users to open cases action. It
 * runs first the New Case wizard, then the Add Data Source wizard.
 *
 * This action should only be invoked in the event dispatch thread (EDT).
 */
final class NewCaseWizardAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(NewCaseWizardAction.class.getName());
    private WizardDescriptor.Panel<WizardDescriptor>[] panels;

    @Override
    public void performAction() {
        String optionsDlgTitle = NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning.title");
        String optionsDlgMessage = NbBundle.getMessage(Case.class, "CloseCaseWhileIngesting.Warning");
        if (IngestRunningCheck.checkAndConfirmProceed(optionsDlgTitle, optionsDlgMessage)) {
            runNewCaseWizard();
        }
    }

    private void runNewCaseWizard() {
        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        final WizardDescriptor wizardDescriptor = new WizardDescriptor(getNewCaseWizardPanels());
        wizardDescriptor.setTitleFormat(new MessageFormat("{0}"));
        wizardDescriptor.setTitle(NbBundle.getMessage(this.getClass(), "NewCaseWizardAction.newCase.windowTitle.text"));
        Dialog dialog = DialogDisplayer.getDefault().createDialog(wizardDescriptor);
        dialog.setVisible(true);
        dialog.toFront();
        if (wizardDescriptor.getValue() == WizardDescriptor.FINISH_OPTION) {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    String caseNumber = (String) wizardDescriptor.getProperty("caseNumber"); //NON-NLS
                    String examinerName = (String) wizardDescriptor.getProperty("caseExaminerName"); //NON-NLS
                    String examinerPhone = (String) wizardDescriptor.getProperty("caseExaminerPhone"); //NON-NLS
                    String examinerEmail = (String) wizardDescriptor.getProperty("caseExaminerEmail"); //NON-NLS
                    String caseNotes = (String) wizardDescriptor.getProperty("caseNotes"); //NON-NLS
                    String organizationName = (String) wizardDescriptor.getProperty("caseOrganization"); //NON-NLS
                    final String caseName = (String) wizardDescriptor.getProperty("caseName"); //NON-NLS
                    String createdDirectory = (String) wizardDescriptor.getProperty("createdDirectory"); //NON-NLS
                    CaseType caseType = CaseType.values()[(int) wizardDescriptor.getProperty("caseType")]; //NON-NLS
                    Case.createAsCurrentCase(caseType, createdDirectory, new CaseDetails(caseName, caseNumber, examinerName, examinerPhone, examinerEmail, caseNotes));
                    if (EamDb.isEnabled()) {  //if the eam is enabled we need to save the case organization information now
                            EamDb dbManager = EamDb.getInstance();
                            if (dbManager != null) {
                                CorrelationCase cRCase = dbManager.getCase(Case.getOpenCase());
                                if (cRCase == null) {
                                    cRCase = dbManager.newCase(Case.getOpenCase());
                                }
                                if (!organizationName.isEmpty()) {
                                    for (EamOrganization org : dbManager.getOrganizations()) {
                                        if (org.getName().equals(organizationName)) {
                                            cRCase.setOrg(org);
                                            dbManager.updateCase(cRCase);
                                        }
                                    }
                                }
                            }
                        } 
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        /*
                         * Run the Add Data Source wizard by invoking the Add
                         * Data Source wizard.
                         */
                        AddImageAction addImageAction = SystemAction.get(AddImageAction.class);
                        addImageAction.actionPerformed(null);
                    } catch (InterruptedException | ExecutionException ex) {
                        if (null != ex.getCause() && !(ex.getCause() instanceof CaseActionCancelledException)) {
                            logger.log(Level.SEVERE, String.format("Error creating case %s", wizardDescriptor.getProperty("caseName")), ex); //NON-NLS                                                
                            JOptionPane.showMessageDialog(
                                    WindowManager.getDefault().getMainWindow(),
                                    (ex instanceof ExecutionException ? ex.getCause().getMessage() : ex.getMessage()),
                                    NbBundle.getMessage(this.getClass(), "CaseCreateAction.msgDlg.cantCreateCase.msg"), //NON-NLS
                                    JOptionPane.ERROR_MESSAGE);
                        }
                        doFailedCaseCleanup(wizardDescriptor);
                        StartupWindowProvider.getInstance().close();
                        StartupWindowProvider.getInstance().open();
                    } finally {
                        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    }
                }
            }.execute();
        } else {
            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            new Thread(() -> {
                doFailedCaseCleanup(wizardDescriptor);
            }).start();
        }
    }

    private void doFailedCaseCleanup(WizardDescriptor wizardDescriptor) {
        String createdDirectory = (String) wizardDescriptor.getProperty("createdDirectory"); //NON-NLS
        if (createdDirectory != null) {
            FileUtil.deleteDir(new File(createdDirectory));
        }
    }

    /**
     * Creates the new case wizard panels.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private WizardDescriptor.Panel<WizardDescriptor>[] getNewCaseWizardPanels() {
        if (panels == null) {
            panels = new WizardDescriptor.Panel[]{
                new NewCaseWizardPanel1(),
                new NewCaseWizardPanel2()
            };
            String[] steps = new String[panels.length];
            for (int i = 0; i < panels.length; i++) {
                Component c = panels[i].getComponent();
                // Default step name to component name of panel. Mainly useful
                // for getting the name of the target chooser to appear in the
                // list of steps.
                steps[i] = c.getName();
                if (c instanceof JComponent) { // assume Swing components
                    JComponent jc = (JComponent) c;
                    // Sets step number of a component
                    jc.putClientProperty("WizardPanel_contentSelectedIndex", i);
                    // Sets steps names for a panel
                    jc.putClientProperty("WizardPanel_contentData", steps);
                    // Turn on subtitle creation on each step
                    jc.putClientProperty("WizardPanel_autoWizardStyle", Boolean.TRUE);
                    // Show steps on the left side with the image on the background
                    jc.putClientProperty("WizardPanel_contentDisplayed", Boolean.TRUE);
                    // Turn on numbering of all steps
                    jc.putClientProperty("WizardPanel_contentNumbered", Boolean.TRUE);
                }
            }
        }
        return panels;
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "NewCaseWizardAction.getName.text");
    }

    /**
     * @inheritDoc
     */
    @Override
    public String iconResource() {
        return null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * @inheritDoc
     */
    @Override
    protected boolean asynchronous() {
        return false;
    }
}
