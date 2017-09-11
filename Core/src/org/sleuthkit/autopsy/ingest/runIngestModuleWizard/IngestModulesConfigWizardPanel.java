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
import java.util.List;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettingsPanel;
import org.sleuthkit.datamodel.Content;

/**
 * A wizard panel for configuring an ingest job.
 */
class IngestModulesConfigWizardPanel extends ShortcutWizardDescriptorPanel {

    private final String executionContext;
    private final IngestJobSettings.IngestType ingestType;
    private IngestJobSettingsPanel ingestJobSettingsPanel;
    private final List<Content> dataSources;
    /**
     * Constructs a wizard panel for configuring an ingest job.
     *
     * @param executionContest The execution context for the wizard.
     * @param ingestType       The ingest type.
     */
    IngestModulesConfigWizardPanel(String executionContest, IngestJobSettings.IngestType ingestType, List<Content> dataSources) {
        this.executionContext = executionContest;
        this.ingestType = ingestType;
        this.dataSources = dataSources;
    }

    /**
     * Gets the ingest job settings associated with this wizard panel.
     *
     * @return The settings, will be null if the panel has not been used in the
     *         wizard.
     */
    IngestJobSettings getIngestJobSettings() {
        return ingestJobSettingsPanel.getSettings();
    }

    @NbBundle.Messages("IngestModulesConfigWizardPanel.name.text=Configure Ingest Modules")
    @Override
    public Component getComponent() {
        if (null == ingestJobSettingsPanel) {
            /*
             * Creating an ingest job settings object is expensive, so it is
             * deferred until this panel is actually used in the wizard.
             */
            ingestJobSettingsPanel = new IngestJobSettingsPanel(new IngestJobSettings(executionContext, ingestType), dataSources);
        }
        ingestJobSettingsPanel.setName(Bundle.IngestModulesConfigWizardPanel_name_text());
        return ingestJobSettingsPanel;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean isValid() {
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
        ingestJobSettingsPanel.getSettings().save();
    }

}
