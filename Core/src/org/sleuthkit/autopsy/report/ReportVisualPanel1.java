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

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;

public final class ReportVisualPanel1 extends JPanel {
    private static final Logger logger = Logger.getLogger(ReportVisualPanel1.class.getName());
    private ReportWizardPanel1 wizPanel;
    
    private Map<GeneralReportModule, Boolean> generalModuleStates = new LinkedHashMap<GeneralReportModule, Boolean>();
    private Map<FileReportModule, Boolean> fileListModuleStates = new LinkedHashMap<FileReportModule, Boolean>();
    private List<ReportModule> modules = new ArrayList<>();
    private Map<ReportModule, Boolean> moduleStates;
    
    private ModulesTableModel modulesModel;
    private ModuleSelectionListener modulesListener;

    /**
     * Creates new form ReportVisualPanel1
     */
    public ReportVisualPanel1(ReportWizardPanel1 wizPanel) {
        moduleStates = new LinkedHashMap<>();
        initComponents();
        initModules();
        this.wizPanel = wizPanel;
        configurationPanel.setLayout(new BorderLayout());
        descriptionTextPane.setEditable(false);
        modulesTable.setRowSelectionInterval(0, 0);
    }
    
    // Initialize the list of ReportModules
    private void initModules() {
        for (ReportModule module : Lookup.getDefault().lookupAll(TableReportModule.class)) {
            if (module.getName().equals("Results - HTML")) {
                moduleStates.put(module, Boolean.TRUE);
            } else {
                moduleStates.put(module, Boolean.FALSE);
            }
        }
        for (ReportModule module : Lookup.getDefault().lookupAll(GeneralReportModule.class)) {
            moduleStates.put(module, Boolean.FALSE);
        }
        for (ReportModule module : Lookup.getDefault().lookupAll(FileReportModule.class)) {
            moduleStates.put(module, Boolean.FALSE);
        }
        
        modules.addAll(moduleStates.keySet());
        Collections.sort(modules, new Comparator<ReportModule>() {
            @Override
            public int compare(ReportModule rm1, ReportModule rm2) {
                return rm1.getName().compareTo(rm2.getName());
            }
            
        });
        
        modulesModel = new ModulesTableModel();
        modulesListener = new ModuleSelectionListener();
        modulesTable.setModel(modulesModel);
        modulesTable.getSelectionModel().addListSelectionListener(modulesListener);
        modulesTable.setTableHeader(null);
        modulesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modulesTable.setRowHeight(modulesTable.getRowHeight() + 5);
        
        int width = modulesScrollPane.getPreferredSize().width;
        for (int i = 0; i < modulesTable.getColumnCount(); i++) {
            TableColumn column = modulesTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (width * 0.15)));
            } else {
                column.setPreferredWidth(((int) (width * 0.84)));
            }
        }
    }

    @Override
    public String getName() {
        return "Select and Configure Report Modules";
    }
    
    /**
     * @return the enabled/disabled states of all TableReportModules
     */
    Map<TableReportModule, Boolean> getTableModuleStates() {
        Map<TableReportModule, Boolean> tableModuleStates = new LinkedHashMap<>();
        for (Entry<ReportModule, Boolean> module : moduleStates.entrySet()) {
            if (module.getKey() instanceof TableReportModule) {
                tableModuleStates.put((TableReportModule) module.getKey(), module.getValue());
            }
        }
        return tableModuleStates;
    }
    
    /**
     * @return the enabled/disabled states of all GeneralReportModules
     */
    Map<GeneralReportModule, Boolean> getGeneralModuleStates() {
        Map<GeneralReportModule, Boolean> generalModuleStates = new LinkedHashMap<>();
        for (Entry<ReportModule, Boolean> module : moduleStates.entrySet()) {
            if (module.getKey() instanceof GeneralReportModule) {
                generalModuleStates.put((GeneralReportModule) module.getKey(), module.getValue());
            }
        }
        return generalModuleStates;
    }
    
    /**
     * @return the enabled/disabled states of all FileListReportModules
     */
    Map<FileReportModule, Boolean> getFileListModuleStates() {
        Map<FileReportModule, Boolean> fileModuleStates = new LinkedHashMap<>();
        for (Entry<ReportModule, Boolean> module : moduleStates.entrySet()) {
            if (module.getKey() instanceof FileReportModule) {
                fileModuleStates.put((FileReportModule) module.getKey(), module.getValue());
            }
        }
        return fileModuleStates;
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        modulesScrollPane = new javax.swing.JScrollPane();
        modulesTable = new javax.swing.JTable();
        reportModulesLabel = new javax.swing.JLabel();
        configurationPanel = new javax.swing.JPanel();
        descriptionScrollPane = new javax.swing.JScrollPane();
        descriptionTextPane = new javax.swing.JTextPane();

        setPreferredSize(new java.awt.Dimension(650, 250));

        modulesTable.setBackground(new java.awt.Color(240, 240, 240));
        modulesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        modulesTable.setShowHorizontalLines(false);
        modulesTable.setShowVerticalLines(false);
        modulesScrollPane.setViewportView(modulesTable);

        org.openide.awt.Mnemonics.setLocalizedText(reportModulesLabel, org.openide.util.NbBundle.getMessage(ReportVisualPanel1.class, "ReportVisualPanel1.reportModulesLabel.text")); // NOI18N

        configurationPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(125, 125, 125)));

        javax.swing.GroupLayout configurationPanelLayout = new javax.swing.GroupLayout(configurationPanel);
        configurationPanel.setLayout(configurationPanelLayout);
        configurationPanelLayout.setHorizontalGroup(
            configurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 432, Short.MAX_VALUE)
        );
        configurationPanelLayout.setVerticalGroup(
            configurationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        descriptionScrollPane.setBorder(null);

        descriptionTextPane.setBackground(new java.awt.Color(240, 240, 240));
        descriptionTextPane.setBorder(null);
        descriptionScrollPane.setViewportView(descriptionTextPane);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(reportModulesLabel)
                    .addComponent(modulesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 186, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(10, 10, 10)
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
                    .addComponent(modulesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 208, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(configurationPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(descriptionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel configurationPanel;
    private javax.swing.JScrollPane descriptionScrollPane;
    private javax.swing.JTextPane descriptionTextPane;
    private javax.swing.JScrollPane modulesScrollPane;
    private javax.swing.JTable modulesTable;
    private javax.swing.JLabel reportModulesLabel;
    // End of variables declaration//GEN-END:variables

    private class ModulesTableModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return moduleStates.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex >= moduleStates.size()) {
                return "";
            }
            if (columnIndex == 0) {
                // selection status
                return moduleStates.get(modules.get(rowIndex));
            } else {
                // module name
                return modules.get(rowIndex).getName();
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                moduleStates.put(modules.get(rowIndex), (Boolean) aValue);
            }
            
            // Check if there are any TableReportModules enabled
            boolean moduleEnabled = false;
            boolean moreConfig = false;
            for (Entry<ReportModule, Boolean> module : moduleStates.entrySet()) {
                if (module.getValue()) {
                    if (module.getKey() instanceof TableReportModule
                            || module.getKey() instanceof FileReportModule) {
                        moreConfig = true;
                    } 
                    moduleEnabled = true;
                } 
            }
            
            if(moreConfig) {
                wizPanel.setNext(true);
                wizPanel.setFinish(false);
            } else if (moduleEnabled) {
                wizPanel.setFinish(true);
                wizPanel.setNext(false);
            } else {
                wizPanel.setFinish(false);
                wizPanel.setNext(false);
            }
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
    }
    
    private class ModuleSelectionListener implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent e) {
            configurationPanel.removeAll();
            int rowIndex = modulesTable.getSelectedRow();
            
            JPanel panel = new DefaultReportConfigurationPanel();
            ReportModule module = modules.get(rowIndex);
            if (module instanceof GeneralReportModule) {
                JPanel generalPanel = ((GeneralReportModule) module).getConfigurationPanel();
                panel = (generalPanel == null) ? panel : generalPanel;
            }
            
            descriptionTextPane.setText(module.getDescription());
            configurationPanel.add(panel, BorderLayout.CENTER);
            configurationPanel.revalidate();
            configurationPanel.repaint();
        }
        
    }

}
