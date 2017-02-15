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
package org.sleuthkit.autopsy.ingest.runIngestModuleWizard;

import java.util.HashSet;
import java.util.Set;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * The first wizard panel of the run ingest modules wizard. Displays the profile
 * selection panel and is only created when profiles exist.
 *
 */
public class IngestProfileSelectionWizardPanel extends ShortcutWizardDescriptorPanel {

    @Messages("IngestProfileWizardPanel.panelName=Ingest Profile Selection")

    private final Set<ChangeListener> listeners = new HashSet<>(1);
    private final static String LAST_PROFILE_PROPERTIES_FILE = "IngestProfileSelectionPanel"; //NON-NLS
    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private IngestProfileSelectionPanel component;
    private String lastProfileUsed;
    private final String lastProfilePropertyName;
    private final String defaultContext;

    public IngestProfileSelectionWizardPanel(String defaultContext, String lastProfilePropertyName) {
        this.lastProfilePropertyName = lastProfilePropertyName;
        this.defaultContext = defaultContext;
    }

    /**
     * @return the defaultContext
     */
    String getDefaultContext() {
        return defaultContext;
    }

    /**
     * Gets the name of the file which stores the last profile used properties.
     *
     * @return the LAST_PROFILE_PROPERTIES_FILE
     */
    public static String getLastProfilePropertiesFile() {
        return LAST_PROFILE_PROPERTIES_FILE;
    }

    // Get the visual component for the panel. In this template, the component
    // is kept separate. This can be more efficient: if the wizard is created
    // but never displayed, or not all panels are displayed, it is better to
    // create only those which really need to be visible.
    @Override
    public IngestProfileSelectionPanel getComponent() {
        if (component == null) {
            if (!(ModuleSettings.getConfigSetting(LAST_PROFILE_PROPERTIES_FILE, lastProfilePropertyName) == null)
                    && !ModuleSettings.getConfigSetting(LAST_PROFILE_PROPERTIES_FILE, lastProfilePropertyName).isEmpty()) {
                lastProfileUsed = ModuleSettings.getConfigSetting(LAST_PROFILE_PROPERTIES_FILE, lastProfilePropertyName);
            } else {
                lastProfileUsed = getDefaultContext();
            }
            component = new IngestProfileSelectionPanel(this, lastProfileUsed);
            component.setName(Bundle.IngestProfileWizardPanel_panelName());
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        // Show no Help button for this panel:
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean isValid() {
        // If it is always OK to press Next or Finish, then:
        return true;
    }

    /**
     * Fires a change event to notify listeners that changes have taken place.
     */
    protected final void fireChangeEvent() {
        Set<ChangeListener> ls;
        synchronized (listeners) {
            ls = new HashSet<>(listeners);
        }
        ChangeEvent ev = new ChangeEvent(this);
        for (ChangeListener l : ls) {
            l.stateChanged(ev);
        }
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    @Override
    public void readSettings(WizardDescriptor wiz) {
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        lastProfileUsed = component.getLastSelectedProfile();
        wiz.putProperty("executionContext", lastProfileUsed); //NON-NLS
        ModuleSettings.setConfigSetting(LAST_PROFILE_PROPERTIES_FILE, lastProfilePropertyName, lastProfileUsed);
    }

    @Override
    public boolean skipNextPanel() {
        return component.isLastPanel;
    }

    @Override
    public boolean panelEnablesSkipping() {
        return true;
    }
}
