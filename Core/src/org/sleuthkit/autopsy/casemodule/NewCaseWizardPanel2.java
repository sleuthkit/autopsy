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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/**
 * The "New Case" wizard panel with a component on it. This class represents 
 * data of wizard step. It defers creation and initialization of UI component
 * of wizard panel into getComponent() method.
 *
 * @author jantonius
 */
class NewCaseWizardPanel2 implements WizardDescriptor.ValidatingPanel<WizardDescriptor> {

    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private NewCaseVisualPanel2 component;
    private Boolean isFinish = true;
    private String caseName;
    private String caseDir;
    private String createdDirectory;

    /**
     * Get the visual component for the panel. In this template, the component
     * is kept separate. This can be more efficient: if the wizard is created
     * but never displayed, or not all panels are displayed, it is better to
     * create only those which really need to be visible.
     *
     * @return component  the UI component of this wizard panel
     */
    @Override
    public NewCaseVisualPanel2 getComponent() {
        if (component == null) {
            component = new NewCaseVisualPanel2();
        }
        return component;
    }

    /**
     * Help for this panel. When the panel is active, this is used as the help
     * for the wizard dialog.
     * 
     * @return HelpCtx.DEFAULT_HELP  the help for this panel
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
     * @return boolean  true if all the fields are correctly filled, false otherwise
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
     * @param l  the change listener to add
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
     * @param l  the change listener to move
     */
    @Override
    public final void removeChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    /**
     * This method is auto-generated. It seems that this method is used to listen
     * to any change in this wizard panel.
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
     * @param settings  the setting to be read from
     */
    @Override
    public void readSettings(WizardDescriptor settings) {
        caseName = (String) settings.getProperty("caseName");
        caseDir = (String) settings.getProperty("caseParentDir");
        createdDirectory = (String) settings.getProperty("createdDirectory");
    }

    /**
     * Provides the wizard panel with the opportunity to update the settings 
     * with its current customized state. Rather than updating its settings
     * with every change in the GUI, it should collect them, and then only save
     * them when requested to by this method. This method can be called multiple
     * times on one instance of WizardDescriptor.Panel.
     *
     * @param settings  the setting to be stored to
     */
    @Override
    public void storeSettings(WizardDescriptor settings) {
    }

    @Override
    public void validate() throws WizardValidationException {
        
        NewCaseVisualPanel2 currentComponent = getComponent();
        final String caseNumber = currentComponent.getCaseNumber();
        final String examiner = currentComponent.getExaminer();
        try {
            SwingUtilities.invokeLater(new Runnable(){

                @Override
                public void run() {
                    try {
                        Case.create(createdDirectory, caseName, caseNumber, examiner);
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            
        });
        
            //Case.create(createdDirectory, caseName, caseNumber, examiner);
        } catch(Exception ex) {
            throw new WizardValidationException(this.getComponent(),
                                                NbBundle.getMessage(this.getClass(),
                                                                    "NewCaseWizardPanel2.validate.errCreateCase.msg"),
                                                null);
        }
    }
}
