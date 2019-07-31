/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2019 Basis Technology Corp.
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

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import static java.util.Collections.swap;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.python.JythonModuleLoader;

/**
 * Display reports modules.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class ReportVisualPanel1 extends JPanel implements ListSelectionListener {

    private static final Logger logger = Logger.getLogger(ReportVisualPanel1.class.getName());
    private final ReportWizardPanel1 wizPanel;
    private final List<ReportModule> modules = new ArrayList<>();
    private final List<GeneralReportModule> generalModules = new ArrayList<>();
    private final List<TableReportModule> tableModules = new ArrayList<>();
    private final List<FileReportModule> fileModules = new ArrayList<>();
    private PortableCaseReportModule portableCaseModule;
    private Integer selectedIndex;

    /**
     * Creates new form ReportVisualPanel1
     */
    public ReportVisualPanel1(ReportWizardPanel1 wizPanel) {
        this.wizPanel = wizPanel;
        initComponents();
        configurationPanel.setLayout(new BorderLayout());
        descriptionTextPane.setEditable(false);
        initModules();
    }

    // Initialize the list of ReportModules
    private void initModules() {
        for (TableReportModule module : Lookup.getDefault().lookupAll(TableReportModule.class)) {
            if (moduleIsValid(module)) {
                tableModules.add(module);
                modules.add(module);
            } else {
                popupWarning(module);
            }
        }

        for (GeneralReportModule module : Lookup.getDefault().lookupAll(GeneralReportModule.class)) {
            if (moduleIsValid(module)) {
                generalModules.add(module);
                modules.add(module);
            } else {
                popupWarning(module);
            }
        }

        for (GeneralReportModule module : JythonModuleLoader.getGeneralReportModules()) {
            if (moduleIsValid(module)) {
                generalModules.add(module);
                modules.add(module);
            } else {
                popupWarning(module);
            }
        }

        for (FileReportModule module : Lookup.getDefault().lookupAll(FileReportModule.class)) {
            if (moduleIsValid(module)) {
                fileModules.add(module);
                modules.add(module);
            } else {
                popupWarning(module);
            }
        }
        
        portableCaseModule = new PortableCaseReportModule();
        if (moduleIsValid(portableCaseModule)) {
            modules.add(portableCaseModule);
        } else {
            popupWarning(portableCaseModule);
        }

        Collections.sort(modules, new Comparator<ReportModule>() {
            @Override
            public int compare(ReportModule rm1, ReportModule rm2) {
                // our theory is that the report table modules are more common, so they go on top
                boolean rm1isTable = (rm1 instanceof TableReportModule);
                boolean rm2isTable = (rm2 instanceof TableReportModule);
                if (rm1isTable && !rm2isTable) {
                    return -1;
                }
                if (!rm1isTable && rm2isTable) {
                    return 1;
                }

                return rm1.getName().compareTo(rm2.getName());
            }
        });

        // Results-HTML should always be first in the list of Report Modules.
        int indexOfHTMLReportModule = 0;
        for (ReportModule module : modules) {
            if (module instanceof ReportHTML) {
                break;
            }
            indexOfHTMLReportModule++;
        }
        swap(modules, indexOfHTMLReportModule, 0);

        modulesJList.getSelectionModel().addListSelectionListener(this);
        modulesJList.setCellRenderer(new ModuleCellRenderer());
        modulesJList.setListData(modules.toArray(new ReportModule[modules.size()]));
        selectedIndex = 0;
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
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        reportModulesLabel = new javax.swing.JLabel();
        configurationPanel = new javax.swing.JPanel();
        descriptionScrollPane = new javax.swing.JScrollPane();
        descriptionTextPane = new javax.swing.JTextPane();
        modulesScrollPane = new javax.swing.JScrollPane();
        modulesJList = new javax.swing.JList<>();

        setPreferredSize(new java.awt.Dimension(650, 250));

        org.openide.awt.Mnemonics.setLocalizedText(reportModulesLabel, org.openide.util.NbBundle.getMessage(ReportVisualPanel1.class, "ReportVisualPanel1.reportModulesLabel.text")); // NOI18N

        configurationPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(125, 125, 125)));
        configurationPanel.setOpaque(false);

        javax.swing.GroupLayout configurationPanelLayout = new javax.swing.GroupLayout(configurationPanel);
        configurationPanel.setLayout(configurationPanelLayout);
        configurationPanelLayout.setHorizontalGroup(
            configurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 432, Short.MAX_VALUE)
        );
        configurationPanelLayout.setVerticalGroup(
            configurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 168, Short.MAX_VALUE)
        );

        descriptionScrollPane.setBorder(null);

        descriptionTextPane.setBackground(new java.awt.Color(240, 240, 240));
        descriptionTextPane.setBorder(null);
        descriptionTextPane.setOpaque(false);
        descriptionScrollPane.setViewportView(descriptionTextPane);

        modulesJList.setBackground(new java.awt.Color(240, 240, 240));
        modulesJList.setModel(new javax.swing.AbstractListModel<ReportModule>() {
            ReportModule[] modules = {};
            public int getSize() { return modules.length; }
            public ReportModule getElementAt(int i) { return modules[i]; }
        });
        modulesJList.setOpaque(false);
        modulesScrollPane.setViewportView(modulesJList);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(reportModulesLabel)
                    .addComponent(modulesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(configurationPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(descriptionScrollPane))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(reportModulesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(descriptionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(configurationPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(modulesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 208, Short.MAX_VALUE))
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
        
        boolean generalModuleSelected = (module instanceof GeneralReportModule);

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
