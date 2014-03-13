/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import java.awt.Component;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.NbPreferences;

 final class ReportWizardIterator implements WizardDescriptor.Iterator<WizardDescriptor> {
    private int index;
    
    private ReportWizardPanel1 firstPanel;
    private ReportWizardPanel2 tableConfigPanel;
    private ReportWizardFileOptionsPanel fileConfigPanel;
    
    private List<WizardDescriptor.Panel<WizardDescriptor>> panels;
    
    // Panels that should be shown if both Table and File report modules should
    // be configured.
    private WizardDescriptor.Panel<WizardDescriptor>[] allConfigPanels;
    private String[] allConfigIndex;
    // Panels that should be shown if only Table report modules should
    // be configured.
    private WizardDescriptor.Panel<WizardDescriptor>[] tableConfigPanels;
    private String[] tableConfigIndex;
    // Panels that should be shown if only File report modules should
    // be configured.
    private WizardDescriptor.Panel<WizardDescriptor>[] fileConfigPanels;
    private String[] fileConfigIndex;
    
    @SuppressWarnings({"rawtypes", "unchecked"})
    ReportWizardIterator() {
        firstPanel = new ReportWizardPanel1();
        tableConfigPanel = new ReportWizardPanel2();
        fileConfigPanel = new ReportWizardFileOptionsPanel();
        
        allConfigPanels = new WizardDescriptor.Panel[]{firstPanel, tableConfigPanel, fileConfigPanel};
        tableConfigPanels = new WizardDescriptor.Panel[]{firstPanel, tableConfigPanel};
        fileConfigPanels = new WizardDescriptor.Panel[]{firstPanel, fileConfigPanel};
    }

    private List<WizardDescriptor.Panel<WizardDescriptor>> getPanels() {
        if (panels == null) {
            panels = Arrays.asList(allConfigPanels);
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
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DISPLAYED, false);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_NUMBERED, true);
                }
            }
            
            allConfigIndex = steps;
            tableConfigIndex = new String[] {steps[0], steps[1]};
            fileConfigIndex = new String[] {steps[0], steps[2]};
        }
        return panels;
    }
    
    /**
     * Change which panels will be shown based on the selection of reporting modules.
     * @param moreConfig true if a GeneralReportModule was selected
     * @param tableConfig true if a TReportModule was selected
     */
    private void enableConfigPanels(boolean generalModule, boolean tableModule) {
        if (generalModule) {
            // General Module selected, no additional panels
        } else if (tableModule) {
            // Table Module selected, need Artifact Configuration Panel
            // (ReportWizardPanel2)
            panels = Arrays.asList(tableConfigPanels);
        } else {
            // File Module selected, need File Report Configuration Panel
            // (ReportWizardFileOptionsPanel)
            panels = Arrays.asList(fileConfigPanels);
        }
    }

    @Override
    public WizardDescriptor.Panel<WizardDescriptor> current() {
        return getPanels().get(index);
    }

    @Override
    public String name() {
        return "";
    }

    @Override
    public boolean hasNext() {
        return index < getPanels().size() - 1;
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
        
        if(index == 0) {
            // Update path through configuration panels
            boolean generalModule, tableModule;
            // These preferences are set in ReportWizardPanel1.storeSettings()
            generalModule = NbPreferences.forModule(ReportWizardPanel1.class).getBoolean("generalModule", true);
            tableModule = NbPreferences.forModule(ReportWizardPanel1.class).getBoolean("tableModule", true);
            enableConfigPanels(generalModule, tableModule);
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

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }
}
