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

import org.sleuthkit.autopsy.report.modules.portablecase.PortableCaseReportModule;
import org.sleuthkit.autopsy.report.modules.html.HTMLReport;
import org.sleuthkit.autopsy.report.ReportModule;
import org.sleuthkit.autopsy.report.ReportModuleSettings;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import static java.util.Collections.swap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.python.FactoryClassNameNormalizer;

/**
 * Display reports modules.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class ReportVisualPanel1 extends JPanel implements ListSelectionListener {

    private static final Logger logger = Logger.getLogger(ReportVisualPanel1.class.getName());
    private final ReportWizardPanel1 wizPanel;
    private final List<ReportModule> modules = new ArrayList<>();
    private List<GeneralReportModule> generalModules = new ArrayList<>();
    private List<TableReportModule> tableModules = new ArrayList<>();
    private List<FileReportModule> fileModules = new ArrayList<>();
    private PortableCaseReportModule portableCaseModule;
    private Map<String, ReportModuleConfig> moduleConfigs;
    private Integer selectedIndex;
    private final boolean displayCaseSpecificData;

    /**
     * Creates new form ReportVisualPanel1
     */
    ReportVisualPanel1(ReportWizardPanel1 wizPanel, Map<String, ReportModuleConfig> moduleConfigs, boolean displayCaseSpecificData) {
        this.displayCaseSpecificData = displayCaseSpecificData;
        this.wizPanel = wizPanel;
        this.moduleConfigs = moduleConfigs;
        initComponents();
        configurationPanel.setLayout(new BorderLayout());
        descriptionTextPane.setEditable(false);
        initModules();
    }

    // Initialize the list of ReportModules
    private void initModules() {

        tableModules = ReportModuleLoader.getTableReportModules();
        generalModules = ReportModuleLoader.getGeneralReportModules();
        fileModules = ReportModuleLoader.getFileReportModules();

        for (TableReportModule module : tableModules) {
            if (!moduleIsValid(module)) {
                popupWarning(module);
                tableModules.remove(module);
            }
        }

        for (GeneralReportModule module : generalModules) {
            if (!moduleIsValid(module)) {
                popupWarning(module);
                generalModules.remove(module);
            }
        }

        for (FileReportModule module : fileModules) {
            if (!moduleIsValid(module)) {
                popupWarning(module);
                fileModules.remove(module);
            }
        }

        // our theory is that the report table modules are more common, so they go on top
        modules.addAll(tableModules);
        modules.addAll(fileModules);
        modules.addAll(generalModules);

        portableCaseModule = new PortableCaseReportModule();
        if (moduleIsValid(portableCaseModule)) {
            modules.add(portableCaseModule);
        } else {
            popupWarning(portableCaseModule);
        }
        
        // Results-HTML should always be first in the list of Report Modules.
        int indexOfHTMLReportModule = 0;
        for (ReportModule module : modules) {
            if (module instanceof HTMLReport) {
                break;
            }
            indexOfHTMLReportModule++;
        }
        swap(modules, indexOfHTMLReportModule, 0);
        
        // set module configurations
        selectedIndex = 0;
        int indx = 0;
        for (ReportModule module : modules) {
            ReportModuleSettings settings = null;
            if (moduleConfigs != null) {
                // get configuration for this module
                ReportModuleConfig config = moduleConfigs.get(FactoryClassNameNormalizer.normalize(module.getClass().getCanonicalName()));                
                if (config != null) {
                    // there is an existing configuration for this module
                    settings = config.getModuleSettings();
                    
                    // check if this module is enabled
                    if (config.isEnabled()) {
                        // make sure this module is the selected module in the UI panel
                        selectedIndex = indx;
                    }
                }
            }
            if (settings == null) {
                // get default module configuration
                settings = module.getDefaultConfiguration();
            }
            // set module configuration
            module.setConfiguration(settings);
            indx++;
        }

        modulesJList.getSelectionModel().addListSelectionListener(this);
        modulesJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modulesJList.setCellRenderer(new ModuleCellRenderer());
        modulesJList.setListData(modules.toArray(new ReportModule[modules.size()]));
        modulesJList.setSelectedIndex(selectedIndex);
    }

    // Make sure that the report module has a valid non-null name.
    private boolean moduleIsValid(ReportModule module) {
        return module.getName() != null && !module.getName().isEmpty();
    }

    private void popupWarning(ReportModule module) {
        String moduleClassName = module.getClass().getSimpleName();
        logger.log(Level.WARNING, "Invalid ReportModule: {0}", moduleClassName); // NON_NLS NON-NLS
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                NbBundle.getMessage(ReportVisualPanel1.class, "ReportVisualPanel1.invalidModuleWarning", moduleClassName),
                NotifyDescriptor.ERROR_MESSAGE));
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "ReportVisualPanel1.getName.text");
    }

    public ReportModule getSelectedModule() {
        return modules.get(selectedIndex);
    }

    /**
     * Get the Selection status of the TableModules.
     *
     * @return
     */
    TableReportModule getTableModule() {
        ReportModule mod = getSelectedModule();
        if (tableModules.contains(mod)) {
            return (TableReportModule) mod;
        }
        return null;
    }

    /**
     * Get the selection status of the GeneralReportModules.
     *
     * @return
     */
    GeneralReportModule getGeneralModule() {
        ReportModule mod = getSelectedModule();
        if (generalModules.contains(mod)) {
            return (GeneralReportModule) mod;
        }
        return null;
    }

    /**
     * Get the selection status of the FileReportModules.
     *
     * @return
     */
    FileReportModule getFileModule() {
        ReportModule mod = getSelectedModule();
        if (fileModules.contains(mod)) {
            return (FileReportModule) mod;
        }
        return null;
    }

    /**
     * Get the selection status of the Portable Case report module.
     *
     * @return
     */
    PortableCaseReportModule getPortableCaseModule() {
        ReportModule mod = getSelectedModule();
        if (portableCaseModule.equals(mod)) {
            return (PortableCaseReportModule) mod;
        }
        return null;
    }

    /**
     * Get updated configuration for all report modules.
     *
     * @return
     */
    Map<String, ReportModuleConfig> getUpdatedModuleConfigs() {
        moduleConfigs = new HashMap<>();
        for (ReportModule module : modules) {
            // get updated module configuration
            ReportModuleSettings settings = module.getConfiguration();
            moduleConfigs.put(FactoryClassNameNormalizer.normalize(module.getClass().getCanonicalName()), new ReportModuleConfig(module, false, settings));
        }
        
        // set "enabled" flag for the selected module
        ReportModule mod = getSelectedModule();
        ReportModuleConfig config = moduleConfigs.get(FactoryClassNameNormalizer.normalize(mod.getClass().getCanonicalName()));
        config.setEnabled(true);
        
        return moduleConfigs;
    }
    
    Map<String, ReportModule> getReportModules() {
        Map<String, ReportModule> modulesMap = new HashMap<>();
        for (ReportModule module : modules) {
            modulesMap.put(FactoryClassNameNormalizer.normalize(module.getClass().getCanonicalName()), module);
        }
        return modulesMap;
    } 

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        reportModulesLabel = new javax.swing.JLabel();
        javax.swing.JSplitPane modulesSplitPane = new javax.swing.JSplitPane();
        javax.swing.JPanel detailsPanel = new javax.swing.JPanel();
        configurationPanel = new javax.swing.JPanel();
        descriptionScrollPane = new javax.swing.JScrollPane();
        descriptionTextPane = new javax.swing.JTextPane();
        modulesScrollPane = new javax.swing.JScrollPane();
        modulesJList = new javax.swing.JList<>();

        setPreferredSize(new java.awt.Dimension(834, 374));

        org.openide.awt.Mnemonics.setLocalizedText(reportModulesLabel, org.openide.util.NbBundle.getMessage(ReportVisualPanel1.class, "ReportVisualPanel1.reportModulesLabel.text")); // NOI18N

        //Make border on split pane invisible to maintain previous style
        modulesSplitPane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                javax.swing.plaf.basic.BasicSplitPaneDivider divider = new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void setBorder(Border border){
                        //do nothing so border is not visible
                    }
                };
                return divider;
            }
        });
        modulesSplitPane.setBorder(null);
        modulesSplitPane.setDividerSize(8);
        modulesSplitPane.setResizeWeight(0.5);

        configurationPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(125, 125, 125)));
        configurationPanel.setOpaque(false);

        javax.swing.GroupLayout configurationPanelLayout = new javax.swing.GroupLayout(configurationPanel);
        configurationPanel.setLayout(configurationPanelLayout);
        configurationPanelLayout.setHorizontalGroup(
            configurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 546, Short.MAX_VALUE)
        );
        configurationPanelLayout.setVerticalGroup(
            configurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 290, Short.MAX_VALUE)
        );

        descriptionScrollPane.setBorder(null);

        descriptionTextPane.setBackground(new java.awt.Color(240, 240, 240));
        descriptionTextPane.setBorder(null);
        descriptionTextPane.setOpaque(false);
        descriptionScrollPane.setViewportView(descriptionTextPane);

        javax.swing.GroupLayout detailsPanelLayout = new javax.swing.GroupLayout(detailsPanel);
        detailsPanel.setLayout(detailsPanelLayout);
        detailsPanelLayout.setHorizontalGroup(
            detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(detailsPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(descriptionScrollPane)
                    .addComponent(configurationPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        detailsPanelLayout.setVerticalGroup(
            detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(detailsPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(descriptionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(configurationPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        modulesSplitPane.setRightComponent(detailsPanel);

        modulesJList.setBackground(new java.awt.Color(240, 240, 240));
        modulesJList.setModel(new javax.swing.AbstractListModel<ReportModule>() {
            ReportModule[] modules = {};
            public int getSize() { return modules.length; }
            public ReportModule getElementAt(int i) { return modules[i]; }
        });
        modulesJList.setOpaque(false);
        modulesScrollPane.setViewportView(modulesJList);

        modulesSplitPane.setLeftComponent(modulesScrollPane);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(reportModulesLabel)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(modulesSplitPane))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(reportModulesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(modulesSplitPane)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel configurationPanel;
    private javax.swing.JScrollPane descriptionScrollPane;
    private javax.swing.JTextPane descriptionTextPane;
    private javax.swing.JList<ReportModule> modulesJList;
    private javax.swing.JScrollPane modulesScrollPane;
    private javax.swing.JLabel reportModulesLabel;
    // End of variables declaration//GEN-END:variables

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        configurationPanel.removeAll();
        ListSelectionModel m = (ListSelectionModel) e.getSource();
        // single selection, so max selection index is the only one selected.
        selectedIndex = m.getMaxSelectionIndex();

        ReportModule module = modules.get(selectedIndex);
        JPanel panel = module.getConfigurationPanel();
        if (panel == null) {
            panel = new JPanel();
        }

        descriptionTextPane.setText(module.getDescription());
        configurationPanel.add(panel, BorderLayout.CENTER);
        configurationPanel.revalidate();
        configurationPanel.repaint();

        // General modules that support data source selection will be presented
        // a data source selection panel, so they should not be finished immediately.
        boolean generalModuleSelected = (module instanceof GeneralReportModule) && (!((GeneralReportModule)module).supportsDataSourceSelection() || !displayCaseSpecificData);
        
        wizPanel.setNext(!generalModuleSelected);
        wizPanel.setFinish(generalModuleSelected);
    }

    private class ModuleCellRenderer extends JRadioButton implements ListCellRenderer<ReportModule> {

        @Override
        public Component getListCellRendererComponent(JList<? extends ReportModule> list, ReportModule value, int index, boolean isSelected, boolean cellHasFocus) {
            this.setText(value.getName());
            this.setEnabled(true);
            this.setSelected(isSelected);
            return this;
        }

    }
}
