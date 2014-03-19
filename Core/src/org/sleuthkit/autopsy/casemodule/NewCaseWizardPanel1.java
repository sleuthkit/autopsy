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

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * The "New Case" wizard panel with a component on it. This class represents
 * data of wizard step. It defers creation and initialization of UI component of
 * wizard panel into getComponent() method.
 */
class NewCaseWizardPanel1 implements WizardDescriptor.ValidatingPanel<WizardDescriptor> {

    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private NewCaseVisualPanel1 component;
    private Boolean isFinish = false;
    private static String createdDirectory;
    private static final String PROP_BASECASE = "LBL_BaseCase_PATH";
    private static final Logger logger = Logger.getLogger(NewCaseWizardPanel1.class.getName());

    /**
     * Get the visual component for the panel. In this template, the component
     * is kept separate. This can be more efficient: if the wizard is created
     * but never displayed, or not all panels are displayed, it is better to
     * create only those which really need to be visible.
     *
     * @return component the UI component of this wizard panel
     */
    @Override
    public NewCaseVisualPanel1 getComponent() {
        if (component == null) {
            component = new NewCaseVisualPanel1(this);
        }
        return component;
    }

    /**
     * Help for this panel. When the panel is active, this is used as the help
     * for the wizard dialog.
     *
     * @return HelpCtx.DEFAULT_HELP the help for this panel
     */
    @Override
    public HelpCtx getHelp() {
        // Show no Help button for this panel:
        return HelpCtx.DEFAULT_HELP;
        // If you have context help:
        // return new HelpCtx(SampleWizardPanel1.class);
    }

    /**
     * Tests whether the panel is finished. If the panel is valid, the "Finish"
     * button will be enabled.
     *
     * @return boolean true if all the fields are correctly filled, false
     * otherwise
     */
    @Override
    public boolean isValid() {
        // If it is always OK to press Next or Finish, then:
        return isFinish;
        // If it depends on some condition (form filled out...), then:
        // return someCondition();
        // and when this condition changes (last form field filled in...) then:
        // fireChangeEvent();
        // and uncomment the complicated stuff below.
    }
    private final Set<ChangeListener> listeners = new HashSet<ChangeListener>(1); // or can use ChangeSupport in NB 6.0

    /**
     * Adds a listener to changes of the panel's validity.
     *
     * @param l the change listener to add
     */
    @Override
    public final void addChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    /**
     * Removes a listener to changes of the panel's validity.
     *
     * @param l the change listener to move
     */
    @Override
    public final void removeChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    /**
     * This method is auto-generated. It seems that this method is used to
     * listen to any change in this wizard panel.
     */
    protected final void fireChangeEvent() {
        Iterator<ChangeListener> it;
        synchronized (listeners) {
            it = new HashSet<ChangeListener>(listeners).iterator();
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

    // You can use a settings object to keep track of state. Normally the
    // settings object will be the WizardDescriptor, so you can use
    // WizardDescriptor.getProperty & putProperty to store information entered
    // by the user.
    /**
     * Provides the wizard panel with the current data--either the default data
     * or already-modified settings, if the user used the previous and/or next
     * buttons. This method can be called multiple times on one instance of
     * WizardDescriptor.Panel.
     *
     * @param settings the setting to be read from
     */
    @Override
    public void readSettings(WizardDescriptor settings) {
        NewCaseVisualPanel1 component = getComponent();
        try {
            String lastBaseDirectory = ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_BASECASE);
            component.getCaseParentDirTextField().setText(lastBaseDirectory);
            createdDirectory = (String) settings.getProperty("createdDirectory");
            if (createdDirectory != null && !createdDirectory.equals("")) {
                logger.log(Level.INFO, "Deleting a case dir in readSettings(): " + createdDirectory);
                Case.deleteCaseDirectory(new File(createdDirectory));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not read wizard settings in NewCaseWizardPanel1, ", e);
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
        settings.putProperty("caseName", getComponent().getCaseName());
        settings.putProperty("caseParentDir", getComponent().getCaseParentDir());
        settings.putProperty("createdDirectory", createdDirectory);
        ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_BASECASE, getComponent().getCaseParentDir());
    }

    @Override
    public void validate() throws WizardValidationException {
        String caseName = getComponent().getCaseName();
        String caseParentDir = getComponent().getCaseParentDir();
        String caseDirPath = caseParentDir + File.separator + caseName;

        // check if case Name contain one of this following symbol:
        //  \ / : * ? " < > |
        if (!Case.isValidName(caseName)) {
            String errorMsg = NbBundle
                    .getMessage(this.getClass(), "NewCaseWizardPanel1.validate.errMsg.invalidSymbols");
            validationError(errorMsg);
        } else {
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
                            // if user say yes
                            try {
                                createDirectory(caseDirPath);
                            } catch (Exception ex) {
                                String errorMsg = NbBundle.getMessage(this.getClass(),
                                                                      "NewCaseWizardPanel1.validate.errMsg.cantCreateParDir.msg",
                                                                      caseParentDir);
                                logger.log(Level.WARNING, errorMsg, ex);
                                validationError(errorMsg);
                            }
                        }
                        if (res2 != null && res2 == DialogDescriptor.NO_OPTION) {
                            // if user say no
                            validationError(NbBundle.getMessage(this.getClass(),
                                                                "NewCaseWizardPanel1.validate.errMsg.prevCreateBaseDir.msg",
                                                                caseDirPath) );
                        }
                    } else {
                        try {
                            createDirectory(caseDirPath);
                        } catch (Exception ex) {
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
    private void createDirectory(final String caseDirPath) throws WizardValidationException {
        // try to create the directory with the case name in the choosen parent directory
        boolean success = false;
        try {
            Case.createCaseDirectory(caseDirPath);
            success = true;
        } catch (CaseActionException ex) {
            logger.log(Level.SEVERE, "Could not createDirectory for the case, ", ex);
        }

        // check if the directory is successfully created
        if (!success) {

            // delete the folder if we already created the folder and the error shows up
            if (new File(caseDirPath).exists()) {
                Case.deleteCaseDirectory(new File(caseDirPath));
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
                logger.log(Level.WARNING, "Startup window didn't close as expected.", ex);

            }

        }
    }
}
