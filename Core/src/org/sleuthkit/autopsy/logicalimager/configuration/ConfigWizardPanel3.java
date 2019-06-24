/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.logicalimager.configuration;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;

/**
 * Configuration Wizard Panel 3
 */
final class ConfigWizardPanel3 implements WizardDescriptor.Panel<WizardDescriptor> {

    private final Set<ChangeListener> listeners = new HashSet<>(1); // or can use ChangeSupport in NB 6.0
    private ConfigVisualPanel3 component;

    @Override
    public ConfigVisualPanel3 getComponent() {
        if (component == null) {
            component = new ConfigVisualPanel3();
            component.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getPropertyName().equals(ConfigVisualPanel3.getSavedEventName())) { // NON-NLS
                        fireChangeEvent();
                    }
                }
            });
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void readSettings(WizardDescriptor wiz) {
        String configFilename = (String) wiz.getProperty("configFilename"); // NON-NLS
        LogicalImagerConfig config = (LogicalImagerConfig) wiz.getProperty("config"); // NON-NLS
        component.setConfigInfoForSaving(configFilename, config);
        component.resetPanel();
    }

    @Override
    public void storeSettings(WizardDescriptor data) {
        //no settings to store
    }

    @Override
    public boolean isValid() {
        return component.isSaved();
    }

    /**
     * Fire an envent to indicate that state of the wizard panel has changed
     */
    private void fireChangeEvent() {
        Iterator<ChangeListener> it;
        synchronized (listeners) {
            it = new HashSet<>(listeners).iterator();
        }
        ChangeEvent ev = new ChangeEvent(this);
        while (it.hasNext()) {
            it.next().stateChanged(ev);
        }
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
        synchronized (listeners) {
            listeners.add(cl);
        }
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
        synchronized (listeners) {
            listeners.remove(cl);
        }
    }

}
