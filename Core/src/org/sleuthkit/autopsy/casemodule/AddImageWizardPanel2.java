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

import java.awt.Color;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

/**
 * The "Add Image" wizard panel2. Handles processing the image in a worker
 * thread, and any errors that may occur during the add process.
 */
class AddImageWizardPanel2 implements WizardDescriptor.Panel<WizardDescriptor> {
    private boolean imgAdded;
    
    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private AddImageVisualPanel2 component;

    /**
     * Get the visual component for the panel. In this template, the component
     * is kept separate. This can be more efficient: if the wizard is created
     * but never displayed, or not all panels are displayed, it is better to
     * create only those which really need to be visible.
     *
     * @return component the UI component of this wizard panel
     */
    @Override
    public AddImageVisualPanel2 getComponent() {
        if (component == null) {
            component = new AddImageVisualPanel2();
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
    }

    /**
     * Tests whether the panel is finished and it is safe to proceed to the next
     * one. If the panel is valid, the "Next" button will be enabled.
     *
     * @return boolean true if can proceed to the next one, false otherwise
     */
    @Override
    public boolean isValid() {
        // set the focus to the next button of the wizard dialog if it's enabled
        if (imgAdded) {
            Lookup.getDefault().lookup(AddImageAction.class).requestFocusButton("Next >");
        }

        return imgAdded;
    }

    /**
     * Updates the UI to display the add image process has begun.
     */
    void setStateStarted() {
        component.getCrDbProgressBar().setIndeterminate(true);
        component.changeProgressBarTextAndColor("*Adding the image may take some time for large images.", 0, Color.black);
    }

    /**
     * Updates the UI to display the add image process is over.
     */
    void setStateFinished() {
        imgAdded = true;
        fireChangeEvent();
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
     * Load the image locations from the WizardDescriptor settings object, and
     * the
     *
     * @param settings the setting to be read from
     */
    @Override
    public void readSettings(WizardDescriptor settings) {
        settings.setOptions(new Object[] {WizardDescriptor.PREVIOUS_OPTION, WizardDescriptor.NEXT_OPTION, WizardDescriptor.FINISH_OPTION, WizardDescriptor.CANCEL_OPTION});
    }

    /**
     *
     * @param settings the setting to be stored to
     */
    @Override
    public void storeSettings(WizardDescriptor settings) {
        getComponent().resetInfoPanel();
    }


 

}
