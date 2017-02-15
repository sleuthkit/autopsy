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
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettingsPanel;


class IngestModulesConfigWizardPanel extends ShortcutWizardDescriptorPanel {
    
    @NbBundle.Messages("IngestModulesConfigWizardPanel.name.text=Configure Ingest Modules")

    /**
     * The visual ingestJobSettingsPanel that displays this panel. If you need
     * to access the ingestJobSettingsPanel from this class, just use
     * getComponent().
     */
    private IngestJobSettingsPanel ingestJobSettingsPanel;

    // Get the visual ingestJobSettingsPanel for the panel. In this template, the ingestJobSettingsPanel
    // is kept separate. This can be more efficient: if the wizard is created
    // but never displayed, or not all panels are displayed, it is better to
    // create only those which really need to be visible.
    @Override
    public Component getComponent() {
        if (ingestJobSettingsPanel == null) {
            ingestJobSettingsPanel = new IngestJobSettingsPanel(new IngestJobSettings(RunIngestModulesAction.getDefaultContext()));
        }
        ingestJobSettingsPanel.setName(Bundle.IngestModulesConfigWizardPanel_name_text());
        return ingestJobSettingsPanel;
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

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }

    @Override
    public void readSettings(WizardDescriptor wiz) {
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        IngestJobSettings ingestJobSettings = this.ingestJobSettingsPanel.getSettings();
        ingestJobSettings.save();
        wiz.putProperty("executionContext", RunIngestModulesAction.getDefaultContext()); //NON-NLS
    }

}
