/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestProfiles;
import org.sleuthkit.datamodel.Content;

/**
 * A wizard that allows a user to configure an ingest job.
 */
final class RunIngestModulesWizardIterator implements WizardDescriptor.Iterator<WizardDescriptor> {

    private final static String PROP_LASTPROFILE_NAME = "RIMW_LASTPROFILE_NAME"; //NON-NLS
    private final List<ShortcutWizardDescriptorPanel> panels;
    private int currentPanelIndex;

    /**
     * Constructs a wizard that allows a user to configure an ingest job.
     *
     * @param executionContext The execution context for this wizard. Ingest job
     *                         settings can differ by execution context.
     * @param ingestType       The type of ingest to be configured.
     */
    RunIngestModulesWizardIterator(String executionContext, IngestJobSettings.IngestType ingestType, List<Content> dataSources) {
        panels = new ArrayList<>();
        List<IngestProfiles.IngestProfile> profiles = IngestProfiles.getIngestProfiles();
        if (!profiles.isEmpty() && IngestJobSettings.IngestType.FILES_ONLY != ingestType) {
            panels.add(new IngestProfileSelectionWizardPanel(executionContext, PROP_LASTPROFILE_NAME));
        }

        panels.add(new IngestModulesConfigWizardPanel(executionContext, ingestType, dataSources));
        String[] steps = new String[panels.size()];
        for (int i = 0; i < panels.size(); i++) {
            Component c = panels.get(i).getComponent();
            steps[i] = c.getName();
            if (c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                jc.putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, i);
                jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, steps);
                jc.putClientProperty(WizardDescriptor.PROP_AUTO_WIZARD_STYLE, true);
                jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DISPLAYED, true);
                jc.putClientProperty(WizardDescriptor.PROP_CONTENT_NUMBERED, true);
            }
        }
    }

    IngestJobSettings getIngestJobSettings() {
        ShortcutWizardDescriptorPanel panel = current();
        if (panel instanceof IngestProfileSelectionWizardPanel) {
            return ((IngestProfileSelectionWizardPanel) panel).getIngestJobSettings();
        } else {
            return ((IngestModulesConfigWizardPanel) panel).getIngestJobSettings();
        }
    }

    @Override
    public ShortcutWizardDescriptorPanel current() {
        return panels.get(currentPanelIndex);
    }

    @Override
    public String name() {
        return currentPanelIndex + 1 + ". from " + panels.size();
    }

    @Override
    public boolean hasNext() {
        return (currentPanelIndex < panels.size() - 1
                && !(current().panelEnablesSkipping() && current().skipNextPanel()));
    }

    @Override
    public boolean hasPrevious() {
        return currentPanelIndex > 0;
    }

    @Override
    public void nextPanel() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        currentPanelIndex++;
    }

    @Override
    public void previousPanel() {
        if (!hasPrevious()) {
            throw new NoSuchElementException();
        }
        currentPanelIndex--;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }

}
