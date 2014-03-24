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

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.NbBundle;

/**
 * The iterator class for the "Add Image" wizard panel. This class is used to
 * iterate on the sequence of panels of the "Add Image" wizard panel.
 */
class AddImageWizardIterator implements WizardDescriptor.Iterator<WizardDescriptor> {

    private int index = 0;
    private List<WizardDescriptor.Panel<WizardDescriptor>> panels;
    private AddImageAction action;

    AddImageWizardIterator(AddImageAction action) {
        this.action = action;
    }

    /**
     * Initialize panels representing individual wizard's steps and sets
     * various properties for them influencing wizard appearance.
     */
    private List<WizardDescriptor.Panel<WizardDescriptor>> getPanels() {
        if (panels == null) {
            panels = new ArrayList<WizardDescriptor.Panel<WizardDescriptor>>();
            
            AddImageWizardAddingProgressPanel progressPanel = new AddImageWizardAddingProgressPanel();
            
            AddImageWizardChooseDataSourcePanel dsPanel = new AddImageWizardChooseDataSourcePanel(progressPanel);
            AddImageWizardIngestConfigPanel ingestConfigPanel = new AddImageWizardIngestConfigPanel(dsPanel, action, progressPanel);
            
            panels.add(dsPanel);
            panels.add(ingestConfigPanel);
            panels.add(progressPanel);

            String[] steps = new String[panels.size()];
            for (int i = 0; i < panels.size(); i++) {
                Component c = panels.get(i).getComponent();
                // Default step name to component name of panel.
                steps[i] = c.getName();
                if (c instanceof JComponent) { // assume Swing components
                    JComponent jc = (JComponent) c;
                    // Sets step number of a component
                    jc.putClientProperty("WizardPanel_contentSelectedIndex", new Integer(i));
                    // Sets steps names for a panel
                    jc.putClientProperty("WizardPanel_contentData", steps);
                    // Turn on subtitle creation on each step
                    jc.putClientProperty("WizardPanel_autoWizardStyle", Boolean.TRUE);
                    // Show steps on the left side with the image on the background
                    jc.putClientProperty("WizardPanel_contentDisplayed", Boolean.TRUE);
                    // Turn on numbering of all steps
                    jc.putClientProperty("WizardPanel_contentNumbered", Boolean.TRUE);
                }
            }
        }
        return panels;
    }

    /**
     * Returns the index of the current panel.
     * Note: 0 = panel 1, 1 = panel 2, etc
     *
     * @return index  the current panel index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Gets the current panel.
     *
     * @return panel  the current panel
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
     * @return name  the name of the current panel
     */
    @Override
    public String name() {
        return NbBundle.getMessage(this.getClass(), "AddImageWizardIterator.stepXofN", Integer.toString(index + 1),
                                   getPanels().size());
    }

    /**
     * Tests whether there is a next panel.
     * 
     * @return boolean  true if it has next panel, false if not
     */
    @Override
    public boolean hasNext() {
        return index < getPanels().size() - 1;
    }

    /**
     * Tests whether there is a previous panel.
     *
     * @return boolean  true if it has previous panel, false if not
     */
    @Override
    // disable the previous button on all panels
    public boolean hasPrevious() {
        return false;
    }

    /**
     * Moves to the next panel. I.e. increment its index, need not actually
     * change any GUI itself.
     */
    @Override
    public void nextPanel() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        index++;
    }

    /**
     * Moves to the previous panel. I.e. decrement its index, need not actually
     * change any GUI itself.
     */
    @Override
    public void previousPanel() {
        if (!hasPrevious()) {
            throw new NoSuchElementException();
        }
        if(index == 2)
            index--;
        index--;
    }

    // If nothing unusual changes in the middle of the wizard, simply:
    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }
}
