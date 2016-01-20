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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.openide.util.NbBundle;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.windows.WindowManager;
import java.awt.Cursor;
import org.openide.util.ChangeSupport;
import org.openide.util.actions.CallableSystemAction;

/**
 * The first panel of the add data source wizard. The visual component of this
 * panel allows a user to select data sources to add to the current case.
 */
final class AddImageWizardChooseDataSourcePanel implements WizardDescriptor.Panel<WizardDescriptor>, PropertyChangeListener {

    private AddImageWizardChooseDataSourceVisual component;
    private final ChangeSupport changeSupport;
    private boolean nextButtonIsEnabled;

    /**
     * Constructs an instance of the first panel of the add data source wizard.
     */
    AddImageWizardChooseDataSourcePanel() {
        changeSupport = new ChangeSupport(this);
    }

    /**
     * Gets the visual component for the panel. The component is kept separate.
     * This can be more efficient: if the wizard is created but never displayed,
     * or not all panels are displayed, it is better to create only those which
     * really need to be visible.
     *
     * @return The UI component of this wizard panel.
     */
    @Override
    public AddImageWizardChooseDataSourceVisual getComponent() {
        if (null == component) {
            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            component = new AddImageWizardChooseDataSourceVisual(this);
        }
        component.addPropertyChangeListener(this);
        return component;
    }

    /**
     * Gets the help for this panel. When the panel is active, this is used as
     * the help for the wizard dialog.
     *
     * @return The help for this panel
     */
    @Override
    public HelpCtx getHelp() {
        // Show no Help button for this panel:
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     *
     * Tests whether or not the panel is finished. If the panel is valid, the
     * "Finish" button will be enabled.
     *
     * @return True or false.
     */
    @Override
    public boolean isValid() {
        // If it is always OK to press Next or Finish, then:
        // return true;
        // If it depends on some condition (form filled out...), then:
        // return someCondition();
        // and when this condition changes (last form field filled in...) then:
        // fireChangeEvent();
        /*
         * When it is valid, the visual component calls enableNextButton to set
         * this flag.
         */
        return nextButtonIsEnabled;
    }

    /**
     * Moves the keyboard focus to the next button of the wizard.
     */
    void moveFocusToNext() {
        if (nextButtonIsEnabled) {
            CallableSystemAction.get(AddImageAction.class).requestFocusForWizardButton(
                    NbBundle.getMessage(this.getClass(), "AddImageWizardChooseDataSourcePanel.moveFocusNext"));
        }
    }

    /**
     * Enables the "Next" button and fires a change event to update the UI.
     *
     * @param isEnabled True if next button should be enabled, false otherwise.
     */
    void enableNextButton(boolean isEnabled) {
        nextButtonIsEnabled = isEnabled;
        fireChangeEvent();
    }

    /**
     * @inheritDoc
     */
    @Override
    public final void addChangeListener(ChangeListener listener) {
        changeSupport.addChangeListener(listener);
    }

    /**
     * @inheritDoc
     */
    @Override
    public final void removeChangeListener(ChangeListener listener) {
        changeSupport.removeChangeListener(listener);
    }

    /**
     * @inheritDoc
     */
    protected final void fireChangeEvent() {
        changeSupport.fireChange();
    }

    /**
     * Provides the wizard panel with the current data--either the default data
     * or already-modified settings, if the user used the previous and/or next
     * buttons. This method can be called multiple times on one instance of
     * WizardDescriptor.Panel.
     *
     * @param settings The settings.
     */
    @Override
    public void readSettings(WizardDescriptor settings) {
    }

    /**
     * @inheritDoc
     */
    @Override
    public void storeSettings(WizardDescriptor settings) {
    }

    /**
     * @inheritDoc
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        fireChangeEvent();
    }
}
