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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.sleuthkit.autopsy.ingest.IngestProfileMap;

/**
 * Iterator class for creating a wizard for run ingest modules.
 *
 */
final class RunIngestModulesWizardIterator implements WizardDescriptor.Iterator<WizardDescriptor> {

    private int index;
    private int profilePanelIndex;

    private List<WizardDescriptor.Panel<WizardDescriptor>> panels;

    /**
     * Gets the list of panels used by this wizard for iterating over. 
     * Constructing it when it is null.
     * 
     * @return panels - the list of of WizardDescriptor panels
     */
    private List<WizardDescriptor.Panel<WizardDescriptor>> getPanels() {
        if (panels == null) {
            panels = new ArrayList<>();
            IngestProfileSelectionWizardPanel profilePanel = new IngestProfileSelectionWizardPanel(RunIngestModulesAction.getDefaultContext());
            TreeMap<String, IngestProfileMap.IngestProfile> profileMap = new IngestProfileMap().getIngestProfileMap();
            if (!profileMap.isEmpty()) {
                panels.add(profilePanel);
            }

            panels.add(new IngestModulesConfigWizardPanel());
            profilePanelIndex=panels.indexOf(profilePanel);
            String[] steps = new String[panels.size()];
            for (int i = 0; i < panels.size(); i++) {
                Component c = panels.get(i).getComponent();
                // Default step name to component name of panel.
                steps[i] = c.getName();
                if (c instanceof JComponent) { // assume Swing components
                    JComponent jc = (JComponent) c;
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, i);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, steps);
                    jc.putClientProperty(WizardDescriptor.PROP_AUTO_WIZARD_STYLE, true);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DISPLAYED, true);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_NUMBERED, true);
                }
            }
        }
        return panels;
    }

    @Override
    public WizardDescriptor.Panel<WizardDescriptor> current() {
        return getPanels().get(index);
    }

    @Override
    public String name() {
        return index + 1 + ". from " + getPanels().size();
    }

   
    @Override
    public boolean hasNext() {
        return (index < getPanels().size() - 1 && 
                !(index == profilePanelIndex && ((IngestProfileSelectionWizardPanel)current()).isFinishPanel()));
    }

    @Override
    public boolean hasPrevious() {
        return index > 0;
    }

    @Override
    public void nextPanel() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        index++;
    }

    @Override
    public void previousPanel() {
        if (!hasPrevious()) {
            throw new NoSuchElementException();
        }
        index--;
    }

    // If nothing unusual changes in the middle of the wizard, simply:
    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }
    // If something changes dynamically (besides moving between panels), e.g.
    // the number of panels changes in response to user input, then use
    // ChangeSupport to implement add/removeChangeListener and call fireChange
    // when needed

}
