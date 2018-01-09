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

import java.awt.Component;
import java.util.HashSet;
import java.util.Set;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;

/**
 * The first wizard panel of the run ingest modules wizard. Displays the profile
 * selection panel and is only created when profiles exist.
 *
 */
public class IngestProfileSelectionWizardPanel extends ShortcutWizardDescriptorPanel {

    @Messages("IngestProfileWizardPanel.panelName=Ingest Profile Selection")

    private final static String LAST_PROFILE_PROPERTIES_FILE = "IngestProfileSelectionPanel"; //NON-NLS
    private final String executionContext;
    private final String lastProfilePropertyName;
    private final Set<ChangeListener> listeners = new HashSet<>(1);
    private IngestProfileSelectionPanel ingestProfileSelectionPanel;
    private String lastProfileUsed;

    public IngestProfileSelectionWizardPanel(String executionContext, String lastProfilePropertyName) {
        this.lastProfilePropertyName = lastProfilePropertyName;
        this.executionContext = executionContext;
    }

    /**
     * Gets the ingest job settings associated with this wizard panel.
     *
     * @return The settings, will be null if the panel has not been used in the
     *         wizard.
     */
    IngestJobSettings getIngestJobSettings() {
        return new IngestJobSettings(lastProfileUsed);
    }
    
    /**
     * @return the defaultContext
     */
    String getDefaultContext() {
        return executionContext;
    }

    /**
     * Gets the name of the file which stores the last profile used properties.
     *
     * @return the LAST_PROFILE_PROPERTIES_FILE
     */
    public static String getLastProfilePropertiesFile() {
        return LAST_PROFILE_PROPERTIES_FILE;
    }

    @Override
    public Component getComponent() {
        if (ingestProfileSelectionPanel == null) {
            if (!(ModuleSettings.getConfigSetting(LAST_PROFILE_PROPERTIES_FILE, lastProfilePropertyName) == null)
                    && !ModuleSettings.getConfigSetting(LAST_PROFILE_PROPERTIES_FILE, lastProfilePropertyName).isEmpty()) {
                lastProfileUsed = ModuleSettings.getConfigSetting(LAST_PROFILE_PROPERTIES_FILE, lastProfilePropertyName);
            } else {
                lastProfileUsed = getDefaultContext();
            }
            ingestProfileSelectionPanel = new IngestProfileSelectionPanel(this, lastProfileUsed);
            ingestProfileSelectionPanel.setName(Bundle.IngestProfileWizardPanel_panelName());
        }
        return ingestProfileSelectionPanel;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean isValid() {
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
        lastProfileUsed = ingestProfileSelectionPanel.getLastSelectedProfile();
        ModuleSettings.setConfigSetting(LAST_PROFILE_PROPERTIES_FILE, lastProfilePropertyName, lastProfileUsed);
    }

    @Override
    public boolean skipNextPanel() {
        return ingestProfileSelectionPanel.isLastPanel;
    }

    @Override
    public boolean panelEnablesSkipping() {
        return true;
    }
}
