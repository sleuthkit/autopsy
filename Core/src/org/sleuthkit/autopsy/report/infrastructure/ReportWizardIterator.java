/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.infrastructure;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;

final class ReportWizardIterator implements WizardDescriptor.Iterator<WizardDescriptor> {

    private static final Logger logger = Logger.getLogger(ReportWizardIterator.class.getName());
    private int index;

    private final ReportWizardPanel1 firstPanel;
    private final ReportWizardPanel2 tableConfigPanel;
    private final ReportWizardFileOptionsPanel fileConfigPanel;
    private final ReportWizardPortableCaseOptionsPanel portableCaseConfigPanel;
    private final ReportWizardDataSourceSelectionPanel dataSourceSelectionPanel;

    private List<WizardDescriptor.Panel<WizardDescriptor>> panels;

    // Panels that should be shown if both Table and File report modules should
    // be configured.
    private final WizardDescriptor.Panel<WizardDescriptor>[] allConfigPanels;

    // Panels that should be shown if only Table report modules should
    // be configured.
    private final WizardDescriptor.Panel<WizardDescriptor>[] tableConfigPanels;

    // Panels that should be shown if only File report modules should
    // be configured.
    private final WizardDescriptor.Panel<WizardDescriptor>[] fileConfigPanels;

    // Panels that should be shown if only Portable Case report modules should
    // be configured.
    private final WizardDescriptor.Panel<WizardDescriptor>[] portableCaseConfigPanels;

    @SuppressWarnings({"rawtypes", "unchecked"})
    ReportWizardIterator(String reportingConfigurationName, boolean useCaseSpecificData) {
        
        ReportingConfig config = null;
        try {
            if(ReportingConfigLoader.configExists(reportingConfigurationName)) {
                config = ReportingConfigLoader.loadConfig(reportingConfigurationName);
            }
        } catch (ReportConfigException ex) {
            logger.log(Level.SEVERE, "Unable to load reporting configuration " + reportingConfigurationName + ". Using default settings", ex);
        }
        
        if (config != null) {
            firstPanel = new ReportWizardPanel1(config.getModuleConfigs(), useCaseSpecificData);
            tableConfigPanel = new ReportWizardPanel2(useCaseSpecificData, config.getTableReportSettings());
            fileConfigPanel = new ReportWizardFileOptionsPanel(config.getFileReportSettings());
            portableCaseConfigPanel = new ReportWizardPortableCaseOptionsPanel(config.getModuleConfigs(), useCaseSpecificData);
        } else {
            firstPanel = new ReportWizardPanel1(null, useCaseSpecificData);
            tableConfigPanel = new ReportWizardPanel2(useCaseSpecificData, null);
            fileConfigPanel = new ReportWizardFileOptionsPanel(null);
            portableCaseConfigPanel = new ReportWizardPortableCaseOptionsPanel(null, useCaseSpecificData);
        }
        
        if (useCaseSpecificData) {
            dataSourceSelectionPanel = new ReportWizardDataSourceSelectionPanel();
            allConfigPanels = new WizardDescriptor.Panel[]{firstPanel, dataSourceSelectionPanel, tableConfigPanel, fileConfigPanel, portableCaseConfigPanel};
        }
        else {
            dataSourceSelectionPanel = null;
            allConfigPanels = new WizardDescriptor.Panel[]{firstPanel, tableConfigPanel, fileConfigPanel, portableCaseConfigPanel};
        }

        tableConfigPanels = new WizardDescriptor.Panel[]{firstPanel, tableConfigPanel};
        fileConfigPanels = new WizardDescriptor.Panel[]{firstPanel, fileConfigPanel};
        portableCaseConfigPanels = new WizardDescriptor.Panel[]{firstPanel, portableCaseConfigPanel};
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
        }
        return panels;
    }

    /**
     * Change which panels will be shown based on the selection of reporting
     * modules.
     *
     * @param moreConfig  true if a GeneralReportModule was selected
     * @param tableConfig true if a TReportModule was selected
     */
    private void enableConfigPanels(boolean generalModule, boolean tableModule, boolean portableCaseModule, boolean showDataSourceSelectionPanel) {
        boolean includedDataSourceSelection = showDataSourceSelectionPanel && dataSourceSelectionPanel != null;
        if (generalModule) {
            if(includedDataSourceSelection) {
                panels = Arrays.asList(firstPanel, dataSourceSelectionPanel);
            }
        } else if (tableModule) {
            // Table Module selected, need Artifact Configuration Panel
            // (ReportWizardPanel2)
            panels = Arrays.asList(tableConfigPanels);
            if(includedDataSourceSelection) {
                panels = new ArrayList<>(panels);
                panels.add(1, dataSourceSelectionPanel);
            }
        } else if (portableCaseModule) {
            // Portable Case Module selected, need Portable Case Configuration Panel
            // (ReportWizardPortableCaseOptionsPanel)
            panels = Arrays.asList(portableCaseConfigPanels);
        } else {
            // File Module selected, need File Report Configuration Panel
            // (ReportWizardFileOptionsPanel)
            panels = Arrays.asList(fileConfigPanels);
            if(includedDataSourceSelection) {
                panels = new ArrayList<>(panels);
                panels.add(1, dataSourceSelectionPanel);
            }
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

        if (index == 0) {
            // Update path through configuration panels
            boolean generalModule, tableModule, portableModule;
            // These preferences are set in ReportWizardPanel1.storeSettings()
            generalModule = NbPreferences.forModule(ReportWizardPanel1.class).getBoolean("generalModule", true); //NON-NLS
            tableModule = NbPreferences.forModule(ReportWizardPanel1.class).getBoolean("tableModule", true); //NON-NLS
            portableModule = NbPreferences.forModule(ReportWizardPanel1.class).getBoolean("portableCaseModule", true); //NON-NLS
            boolean showDataSourceSelectionPanel = NbPreferences.forModule(ReportWizardPanel1.class).getBoolean("showDataSourceSelectionPanel", false); // NON-NLS
            enableConfigPanels(generalModule, tableModule, portableModule, showDataSourceSelectionPanel);
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
