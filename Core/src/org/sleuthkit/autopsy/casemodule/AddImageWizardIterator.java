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

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.NbBundle;

/**
 * The iterator for the add data source wizard panels.
 */
final class AddImageWizardIterator implements WizardDescriptor.Iterator<WizardDescriptor> {

    private int index = 0;
    private List<WizardDescriptor.Panel<WizardDescriptor>> panels;

    /**
     * Lazily create the panels for the add data source wizard.
     */
    private List<WizardDescriptor.Panel<WizardDescriptor>> getPanels() {
        if (null == panels) {
            panels = new ArrayList<>();

            /*
             * Create the wizard panels. The first panel is used to select a
             * data source. The second panel is used to configure the ingest
             * modules. The third panel has a progress bar that tracks progress
             * as the Sleuthkit layer adds the data source to the case database.
             */
            AddImageWizardChooseDataSourcePanel dsPanel = new AddImageWizardChooseDataSourcePanel();
            AddImageWizardAddingProgressPanel progressPanel = new AddImageWizardAddingProgressPanel();
            AddImageWizardIngestConfigPanel ingestConfigPanel = new AddImageWizardIngestConfigPanel(dsPanel, progressPanel);
            panels.add(dsPanel);
            panels.add(ingestConfigPanel);
            panels.add(progressPanel);

            /*
             * Set the appearance of the visual components of the panels.
             */
            String[] steps = new String[panels.size()];
            for (int i = 0; i < panels.size(); i++) {
                Component visualComponent = panels.get(i).getComponent();
                // Default step name to component name.
                steps[i] = visualComponent.getName();
                if (visualComponent instanceof JComponent) {
                    JComponent jc = (JComponent) visualComponent;
                    // Set step number.
                    jc.putClientProperty("WizardPanel_contentSelectedIndex", i);
                    // Sets step name.
                    jc.putClientProperty("WizardPanel_contentData", steps);
                    // Turn on subtitle creation.
                    jc.putClientProperty("WizardPanel_autoWizardStyle", Boolean.TRUE);
                    // Show steps on the left side, with image in the background.
                    jc.putClientProperty("WizardPanel_contentDisplayed", Boolean.TRUE);
                    // Turn on step numbering.
                    jc.putClientProperty("WizardPanel_contentNumbered", Boolean.TRUE);
                }
            }
        }
        return panels;
    }

    /**
     * Gets the current panel.
     *
     * @return The current panel.
     */
    @Override
    public WizardDescriptor.Panel<WizardDescriptor> current() {
        if (panels != null) {
            return panels.get(index);
        } else {
            return getPanels().get(index);
        }
    }

    /**
     * Gets the name of the current panel.
     *
     * @return The name of the current panel.
     */
    @Override
    public String name() {
        return NbBundle.getMessage(this.getClass(), "AddImageWizardIterator.stepXofN", Integer.toString(index + 1), getPanels().size());
    }

    /**
     * Tests whether there is a next panel.
     *
     * @return True or false.
     */
    @Override
    public boolean hasNext() {
        return index < getPanels().size() - 1;
    }

    /**
     * Tests whether there is a previous panel.
     *
     * @return True or false.
     */
    @Override
    public boolean hasPrevious() {
        /*
         * Disable the back buttons for the add data source wizard.
         */
        return false;
    }

    /**
     * Moves to the next panel.
     */
    @Override
    public void nextPanel() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        index++;
    }

    /**
     * Moves to the previous panel.
     */
    @Override
    public void previousPanel() {
        if (!hasPrevious()) {
            throw new NoSuchElementException();
        }
        if (index == 2) {
            index--;
        }
        index--;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void addChangeListener(ChangeListener l) {
    }

    /**
     * @inheritDoc
     */
    @Override
    public void removeChangeListener(ChangeListener l) {
    }

}
