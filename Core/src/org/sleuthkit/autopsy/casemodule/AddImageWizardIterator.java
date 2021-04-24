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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.ingest.IngestProfiles;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.IngestProfileSelectionWizardPanel;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.ShortcutWizardDescriptorPanel;
import org.sleuthkit.datamodel.Host;

/**
 * The iterator class for the "Add Image" wizard panel. This class is used to
 * iterate on the sequence of panels of the "Add Image" wizard panel.
 */
class AddImageWizardIterator implements WizardDescriptor.Iterator<WizardDescriptor> {

    private int index = 0;
    private List<ShortcutWizardDescriptorPanel> panels;
    private final AddImageAction action;
    private int progressPanelIndex;
    private int dsPanelIndex;
    private int hostPanelIndex;
    private int ingestPanelIndex;
    private final static String PROP_LASTPROFILE_NAME = "AIW_LASTPROFILE_NAME"; //NON-NLS
    private AddImageWizardSelectHostPanel hostPanel = null;

    AddImageWizardIterator(AddImageAction action) {
        this.action = action;
    }

    /**
     * Initialize panels representing individual wizard's steps and sets various
     * properties for them influencing wizard appearance.
     */
    private List<ShortcutWizardDescriptorPanel> getPanels() {
        if (panels == null) {
            panels = new ArrayList<>();
            hostPanel = new AddImageWizardSelectHostPanel();
            panels.add(hostPanel);
            hostPanelIndex = panels.indexOf(hostPanel);
            AddImageWizardSelectDspPanel dspSelection = new AddImageWizardSelectDspPanel();
            panels.add(dspSelection);
            AddImageWizardAddingProgressPanel progressPanel = new AddImageWizardAddingProgressPanel(action);
            AddImageWizardDataSourceSettingsPanel dsPanel = new AddImageWizardDataSourceSettingsPanel();
            AddImageWizardIngestConfigPanel ingestConfigPanel = new AddImageWizardIngestConfigPanel(progressPanel);
            panels.add(dsPanel);
            List<IngestProfiles.IngestProfile> profiles = IngestProfiles.getIngestProfiles();
            if (!profiles.isEmpty()) {
                panels.add(new IngestProfileSelectionWizardPanel(AddImageWizardIngestConfigPanel.class.getCanonicalName(), getPropLastprofileName()));
            }
            panels.add(ingestConfigPanel);
            panels.add(progressPanel);
            progressPanelIndex = panels.indexOf(progressPanel);  //Doing programatically because number of panels is variable
            dsPanelIndex = panels.indexOf(dsPanel);
            ingestPanelIndex = panels.indexOf(ingestConfigPanel);
            String[] steps = new String[panels.size()];
            for (int i = 0; i < panels.size(); i++) {
                Component c = panels.get(i).getComponent();
                // Default step name to component name of panel.
                steps[i] = c.getName();
                if (c instanceof JComponent) { // assume Swing components
                    JComponent jc = (JComponent) c;
                    // Sets step number of a component
                    jc.putClientProperty("WizardPanel_contentSelectedIndex", i);
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
     * Returns the index of the current panel. Note: 0 = panel 1, 1 = panel 2,
     * etc
     *
     * @return index the current panel index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Gets the name of the property which stores the name of the last profile
     * used by the Add Image Wizard.
     *
     * @return the PROP_LASTPROFILE_NAME
     */
    static String getPropLastprofileName() {
        return PROP_LASTPROFILE_NAME;
    }

    /**
     * @return the PROP_LASTPROFILE_NAME
     */
    static String getPROP_LASTPROFILE_NAME() {
        return PROP_LASTPROFILE_NAME;
    }

    /**
     * Gets the current panel.
     *
     * @return panel the current panel
     */
    @Override
    public ShortcutWizardDescriptorPanel current() {
        if (panels != null) {
            return panels.get(index);
        } else {
            return getPanels().get(index);
        }
    }

    /**
     * Gets the name of the current panel.
     *
     * @return name the name of the current panel
     */
    @Override
    public String name() {
        return NbBundle.getMessage(this.getClass(), "AddImageWizardIterator.stepXofN", Integer.toString(index + 1),
                getPanels().size());
    }

    /**
     * Tests whether there is a next panel.
     *
     * @return boolean true if it has next panel, false if not
     */
    @Override
    public boolean hasNext() {
        return index < getPanels().size() - 1;
    }

    /**
     * Tests whether there is a previous panel.
     *
     * @return boolean true if it has previous panel, false if not
     */
    @Override
    // disable the previous button on all panels except the data source panel
    public boolean hasPrevious() {
        return (index <= dsPanelIndex && index > 0); //Users should be able to back up to select a different DSP
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
        // Start processing the data source by handing it off to the selected DSP, 
        // so it gets going in the background while the user is still picking the Ingest modules    
        // This will occur when the next button is clicked on the panel where you have chosen your data to process
        if (index == ingestPanelIndex) {
            AddImageWizardAddingProgressPanel addingProgressPanel = (AddImageWizardAddingProgressPanel) panels.get(progressPanelIndex);
            AddImageWizardDataSourceSettingsVisual dspSettingsPanel = ((AddImageWizardDataSourceSettingsPanel) panels.get(dsPanelIndex)).getComponent();
            Host host = (hostPanel == null) ? null : hostPanel.getSelectedHost();
            addingProgressPanel.startDataSourceProcessing(dspSettingsPanel.getCurrentDSProcessor(), host);
        }
        boolean panelEnablesSkipping = current().panelEnablesSkipping();
        boolean skipNextPanel = current().skipNextPanel();
        index++;
        if (panelEnablesSkipping && skipNextPanel) {
            current().processThisPanelBeforeSkipped();
            nextPanel();
        }
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
        if (index == progressPanelIndex) {
            index--;
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
}
