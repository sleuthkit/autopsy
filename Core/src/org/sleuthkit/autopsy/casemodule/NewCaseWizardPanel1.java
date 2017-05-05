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

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * The first panel of the New Case wizard.
 */
class NewCaseWizardPanel1 implements WizardDescriptor.ValidatingPanel<WizardDescriptor> {

    private static final Logger logger = Logger.getLogger(NewCaseWizardPanel1.class.getName());
    private static final String PROP_BASECASE = "LBL_BaseCase_PATH"; //NON-NLS
    private static String createdDirectory;
    private final Set<ChangeListener> listeners = new HashSet<>(1);
    private NewCaseVisualPanel1 component;
    private boolean isFinish;

    /**
     * Get the visual component for the panel.
     *
     * @return The UI component of this wizard panel
     */
    @Override
    public NewCaseVisualPanel1 getComponent() {
        if (component == null) {
            component = new NewCaseVisualPanel1(this);
        }
        return component;
    }

    /**
     * Gets the help object for this panel. When the panel is active, this is
     * used as the help for the wizard dialog.
     *
     * @return The help for this panel.
     */
    @Override
    public HelpCtx getHelp() {
        /*
         * Currently, no help is provided for this panel.
         */
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * Tests whether the panel is finished. If the panel is valid, the "Finish"
     * button will be enabled.
     *
     * @return boolean True if all the fields are correctly filled, false
     *         otherwise.
     */
    @Override
    public boolean isValid() {
        return isFinish;
    }

    /**
     * Adds a change listener to this panel.
     *
     * @param listener The change listener to add.
     */
    @Override
    public final void addChangeListener(ChangeListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a change listener from this panel.
     *
     * @param listener The change listener to remove.
     */
    @Override
    public final void removeChangeListener(ChangeListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Notifies any registerd change listeners of a change in the panel.
     */
    protected final void fireChangeEvent() {
        Iterator<ChangeListener> it;
        synchronized (listeners) {
            it = new HashSet<>(listeners).iterator();
        }
        ChangeEvent ev = new ChangeEvent(this);
        while (it.hasNext()) {
            it.next().stateChanged(ev);
        }
    }

    /**
     * Sets the isFinish variable in this class. isFinish variable is used to
     * determine whether the Finish button should be disabled or not.
     *
     * @param isFinish the given parameter (boolean)
     */
    public void setIsFinish(Boolean isFinish) {
        this.isFinish = isFinish;
        fireChangeEvent();
    }

    /**
     * Provides the wizard panel with the current data - either the default data
     * or already-modified settings, if the user used the previous and/or next
     * buttons. This method can be called multiple times on one instance of
     * WizardDescriptor.Panel.
     *
     * @param settings the setting to be read from
     */
    @Override
    public void readSettings(WizardDescriptor settings) {
        NewCaseVisualPanel1 panel = getComponent();
        try {
            String lastBaseDirectory = ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_BASECASE);
            panel.setCaseParentDir(lastBaseDirectory);
            panel.readSettings();
            createdDirectory = (String) settings.getProperty("createdDirectory"); //NON-NLS
            if (createdDirectory != null && !createdDirectory.isEmpty()) {
                logger.log(Level.INFO, "Deleting a case dir in readSettings(): {0}", createdDirectory); //NON-NLS
                FileUtil.deleteDir(new File(createdDirectory));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not read wizard settings in NewCaseWizardPanel1, ", e); //NON-NLS
        }
    }

    /**
     * Provides the wizard panel with the opportunity to update the settings
     * with its current customized state. Rather than updating its settings with
     * every change in the GUI, it should collect them, and then only save them
     * when requested to by this method. This method can be called multiple
     * times on one instance of WizardDescriptor.Panel.
     *
     * @param settings the setting to be stored to
     */
    @Override
    public void storeSettings(WizardDescriptor settings) {
        CaseType caseType = getComponent().getCaseType();
        settings.putProperty("caseName", getComponent().getCaseName()); //NON-NLS
        settings.putProperty("caseParentDir", getComponent().getCaseParentDir()); //NON-NLS
        settings.putProperty("createdDirectory", createdDirectory); //NON-NLS
        settings.putProperty("caseType", caseType.ordinal()); //NON-NLS
        ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, ModuleSettings.CURRENT_CASE_TYPE, caseType.toString());
        ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_BASECASE, getComponent().getCaseParentDir());
    }

    @Override
    public void validate() throws WizardValidationException {
        /*
         * Check whether or not the case name is valid. To be valid, the case
         * name must not contain any characters that are not allowed in file
         * names, since it will be used as the name of the case directory.
         */
        String caseName = getComponent().getCaseName();
        if (!Case.isValidName(caseName)) {
            String errorMsg = NbBundle
                    .getMessage(this.getClass(), "NewCaseWizardPanel1.validate.errMsg.invalidSymbols");
            validationError(errorMsg);
        } else {

            String caseParentDir = getComponent().getCaseParentDir();
            String caseDirPath = caseParentDir + caseName;

            // check if the directory exist
            if (new File(caseDirPath).exists()) {
                // throw a warning to enter new data or delete the existing directory
                String errorMsg = NbBundle
                        .getMessage(this.getClass(), "NewCaseWizardPanel1.validate.errMsg.dirExists", caseDirPath);
                validationError(errorMsg);
            } else {
                // check if the "base" directory path is absolute
                File baseDir = new File(caseParentDir);
                if (baseDir.isAbsolute()) {
                    // when the base directory doesn't exist
                    if (!baseDir.exists()) {
                        // get confirmation to create directory
                        String confMsg = NbBundle
                                .getMessage(this.getClass(), "NewCaseWizardPanel1.validate.confMsg.createDir.msg",
                                        caseParentDir);
                        NotifyDescriptor d2 = new NotifyDescriptor.Confirmation(confMsg,
                                NbBundle.getMessage(this.getClass(),
                                        "NewCaseWizardPanel1.validate.confMsg.createDir.title"),
                                NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE);
                        d2.setValue(NotifyDescriptor.NO_OPTION);

                        Object res2 = DialogDisplayer.getDefault().notify(d2);
                        if (res2 != null && res2 == DialogDescriptor.YES_OPTION) {
                            // if user says yes
                            try {
                                createDirectory(caseDirPath, getComponent().getCaseType());
                            } catch (WizardValidationException ex) {
                                String errorMsg = NbBundle.getMessage(this.getClass(),
                                        "NewCaseWizardPanel1.validate.errMsg.cantCreateParDir.msg",
                                        caseParentDir);
                                logger.log(Level.WARNING, errorMsg, ex);
                                validationError(errorMsg);
                            }
                        }
                        if (res2 != null && res2 == DialogDescriptor.NO_OPTION) {
                            // if user says no
                            validationError(NbBundle.getMessage(this.getClass(),
                                    "NewCaseWizardPanel1.validate.errMsg.prevCreateBaseDir.msg",
                                    caseDirPath));
                        }
                    } else {
                        try {
                            createDirectory(caseDirPath, getComponent().getCaseType());
                        } catch (WizardValidationException ex) {
                            String errorMsg = NbBundle
                                    .getMessage(this.getClass(), "NewCaseWizardPanel1.validate.errMsg.cantCreateDir");
                            logger.log(Level.WARNING, errorMsg, ex);
                            validationError(errorMsg);
                        }
                    }
                } else {
                    // throw a notification
                    String errorMsg = NbBundle
                            .getMessage(this.getClass(), "NewCaseWizardPanel1.validate.errMsg.invalidBaseDir.msg");
                    validationError(errorMsg);
                }
            }
        }
    }

    private void validationError(String errorMsg) throws WizardValidationException {
        throw new WizardValidationException(this.getComponent(), errorMsg, null);
    }

    /*
     * create the directory and create a new case
     */
    private void createDirectory(final String caseDirPath, CaseType caseType) throws WizardValidationException {
        // try to create the directory with the case name in the chosen parent directory
        boolean success = false;
        try {
            Case.createCaseDirectory(caseDirPath, caseType);
            success = true;
        } catch (CaseActionException ex) {
            logger.log(Level.SEVERE, "Could not createDirectory for the case, ", ex); //NON-NLS
        }

        // check if the directory is successfully created
        if (!success) {

            // delete the folder if we already created the folder and the error shows up
            if (new File(caseDirPath).exists()) {
                FileUtil.deleteDir(new File(caseDirPath));
            }

            String errorMsg = NbBundle.getMessage(this.getClass(),
                    "NewCaseWizardPanel1.createDir.errMsg.cantCreateDir.msg");

            validationError(errorMsg);

        } // the new case directory is successfully created
        else {
            createdDirectory = caseDirPath;
            // try to close Startup window if there's one
            try {
                StartupWindowProvider.getInstance().close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Startup window didn't close as expected.", ex); //NON-NLS

            }
        }
    }
    
}
