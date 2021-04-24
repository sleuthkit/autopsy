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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.util.HelpCtx;
import org.openide.windows.WindowManager;
import java.awt.Cursor;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * The second panel of the New Case wizard.
 */
class NewCaseWizardPanel2 implements WizardDescriptor.ValidatingPanel<WizardDescriptor> {

    private static final Logger logger = Logger.getLogger(NewCaseWizardPanel2.class.getName());
    private static final String PROP_EXAMINER_NAME = "LBL_EXAMINER_NAME"; //NON-NLS
    private static final String PROP_EXAMINER_PHONE = "LBL_EXAMINER_PHONE"; //NON-NLS
    private static final String PROP_EXAMINER_EMAIL = "LBL_EXAMINER_EMAIL"; //NON-NLS
    private static final String PROP_ORGANIZATION_NAME = "LBL_ORGANIZATION_NAME"; //NON-NLS
    private NewCaseVisualPanel2 component;
    private final Set<ChangeListener> listeners = new HashSet<>(1);

    /**
     * Get the visual component for the panel.
     *
     * @return component The UI component of this wizard panel.
     */
    @Override
    public NewCaseVisualPanel2 getComponent() {
        if (component == null) {
            component = new NewCaseVisualPanel2();
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
     * @return boolean true if all the fields are correctly filled, false
     *         otherwise
     */
    @Override
    public boolean isValid() {
        return true;
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
     * Provides the wizard panel with the current data--either the default data
     * or already-modified settings, if the user used the previous and/or next
     * buttons. This method can be called multiple times on one instance of
     * WizardDescriptor.Panel.
     *
     * @param settings the setting to be read from
     */
    @Override
    public void readSettings(WizardDescriptor settings) {
        NewCaseVisualPanel2 panel = getComponent();
        panel.refreshCaseDetailsFields();
        try {
            String lastExaminerName = ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_EXAMINER_NAME);
            String lastExaminerPhone = ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_EXAMINER_PHONE);
            String lastExaminerEmail = ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_EXAMINER_EMAIL);
            String lastOrganizationName = ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_ORGANIZATION_NAME);
            panel.setExaminerName(lastExaminerName);
            panel.setExaminerPhone(lastExaminerPhone);
            panel.setExaminerEmail(lastExaminerEmail);
            panel.setOrganization(CentralRepository.isEnabled() ? lastOrganizationName : "");
            panel.setCaseNumber("");  //clear the number field 
            panel.setCaseNotes(""); //clear the notes field
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not read wizard settings in NewCaseWizardPanel2, ", e); //NON-NLS
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
        settings.putProperty("caseNumber", component.getCaseNumber()); //NON-NLS
        settings.putProperty("caseExaminerName", component.getExaminerName()); //NON-NLS
        ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_EXAMINER_NAME, component.getExaminerName());
        settings.putProperty("caseExaminerPhone", component.getExaminerPhone()); //NON-NLS
        ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_EXAMINER_PHONE, component.getExaminerPhone());
        settings.putProperty("caseExaminerEmail", component.getExaminerEmail()); //NON-NLS
        ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_EXAMINER_EMAIL, component.getExaminerEmail());
        settings.putProperty("caseNotes", component.getCaseNotes()); //NON-NLS
        settings.putProperty("caseOrganization", component.getOrganization()); //NON-NLS
        ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, PROP_ORGANIZATION_NAME, component.getOrganization());
    }

    @Override
    public void validate() throws WizardValidationException {
        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }
}
