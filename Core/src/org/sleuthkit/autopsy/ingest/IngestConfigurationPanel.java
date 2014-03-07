/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import org.sleuthkit.autopsy.corecomponents.AdvancedConfigurationDialog;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * User interface component to allow a user to set ingest module options and
 * enable/disable the modules.
 */
class IngestConfigurationPanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(IngestConfigurationPanel.class.getName());
    private List<IngestModuleModel> modules = new ArrayList<>();
    private boolean processUnallocatedSpace = false;
    private IngestModuleModel selectedModule = null;

    IngestConfigurationPanel(List<IngestModuleTemplate> moduleTemplates, boolean processUnallocatedSpace) {
        for (IngestModuleTemplate moduleTemplate : moduleTemplates) {
            modules.add(new IngestModuleModel(moduleTemplate));
        }
        this.processUnallocatedSpace = processUnallocatedSpace;
        initComponents();
        customizeComponents();
    }

    List<IngestModuleTemplate> getIngestModuleTemplates() {
        List<IngestModuleTemplate> moduleTemplates = new ArrayList<>();
        for (IngestModuleModel module : modules) {
            IngestModuleTemplate moduleTemplate = module.getIngestModuleTemplate();
            if (module.hasIngestOptionsPanel()) {
                IngestModuleOptions options = module.getIngestOptionsPanel().getIngestOptions();
                moduleTemplate.setIngestOptions(options);
            }
            moduleTemplates.add(moduleTemplate);
        }
        return moduleTemplates;
    }

    boolean getProcessUnallocSpace() {
        return processUnallocCheckbox.isSelected();
    }

    private void customizeComponents() {
        modulesTable.setModel(new IngestModulesTableModel());
        modulesTable.setTableHeader(null);
        modulesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Set the column widths in the table model and add a custom cell 
        // renderer that will display module descriptions from the module models 
        // as tooltips.
        IngestModulesTableRenderer renderer = new IngestModulesTableRenderer();
        int width = modulesScrollPane.getPreferredSize().width;
        for (int i = 0; i < modulesTable.getColumnCount(); ++i) {
            TableColumn column = modulesTable.getColumnModel().getColumn(i);
            if (0 == i) {
                column.setPreferredWidth(((int) (width * 0.15)));
            } else {
                column.setCellRenderer(renderer);
                column.setPreferredWidth(((int) (width * 0.84)));
            }
        }

        // Add a selection listener to the table model that will display the  
        // ingest options panel of the currently selected module model and 
        // enable or disable the global options panel invocation button.
        modulesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    int index = listSelectionModel.getMinSelectionIndex();
                    selectedModule = modules.get(index);
                    simplePanel.removeAll();
                    if (null != selectedModule.getIngestOptionsPanel()) {
                        simplePanel.add(selectedModule.getIngestOptionsPanel());
                    }
                    simplePanel.revalidate();
                    simplePanel.repaint();
                    advancedButton.setEnabled(null != selectedModule.getResourcesConfigPanel());
                }
            }
        });

        processUnallocCheckbox.setSelected(processUnallocatedSpace);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        timeGroup = new javax.swing.ButtonGroup();
        modulesScrollPane = new javax.swing.JScrollPane();
        modulesTable = new javax.swing.JTable();
        jPanel1 = new javax.swing.JPanel();
        advancedButton = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JSeparator();
        jScrollPane1 = new javax.swing.JScrollPane();
        simplePanel = new javax.swing.JPanel();
        processUnallocPanel = new javax.swing.JPanel();
        processUnallocCheckbox = new javax.swing.JCheckBox();

        setMaximumSize(new java.awt.Dimension(5750, 3000));
        setMinimumSize(new java.awt.Dimension(522, 257));
        setPreferredSize(new java.awt.Dimension(575, 300));

        modulesScrollPane.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(160, 160, 160)));
        modulesScrollPane.setPreferredSize(new java.awt.Dimension(160, 160));

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

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(160, 160, 160)));
        jPanel1.setPreferredSize(new java.awt.Dimension(338, 257));

        advancedButton.setText(org.openide.util.NbBundle.getMessage(IngestConfigurationPanel.class, "IngestConfigurationPanel.advancedButton.text")); // NOI18N
        advancedButton.setEnabled(false);
        advancedButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                advancedButtonActionPerformed(evt);
            }
        });

        jScrollPane1.setBorder(null);
        jScrollPane1.setPreferredSize(new java.awt.Dimension(250, 180));

        simplePanel.setLayout(new javax.swing.BoxLayout(simplePanel, javax.swing.BoxLayout.PAGE_AXIS));
        jScrollPane1.setViewportView(simplePanel);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 326, Short.MAX_VALUE)
            .addComponent(jSeparator2, javax.swing.GroupLayout.Alignment.TRAILING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(advancedButton)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(advancedButton)
                .addContainerGap())
        );

        processUnallocPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(160, 160, 160)));

        processUnallocCheckbox.setText(org.openide.util.NbBundle.getMessage(IngestConfigurationPanel.class, "IngestConfigurationPanel.processUnallocCheckbox.text")); // NOI18N
        processUnallocCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(IngestConfigurationPanel.class, "IngestConfigurationPanel.processUnallocCheckbox.toolTipText")); // NOI18N
        processUnallocCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                processUnallocCheckboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout processUnallocPanelLayout = new javax.swing.GroupLayout(processUnallocPanel);
        processUnallocPanel.setLayout(processUnallocPanelLayout);
        processUnallocPanelLayout.setHorizontalGroup(
            processUnallocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(processUnallocPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(processUnallocCheckbox)
                .addContainerGap(60, Short.MAX_VALUE))
        );
        processUnallocPanelLayout.setVerticalGroup(
            processUnallocPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, processUnallocPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(processUnallocCheckbox)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(modulesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(processUnallocPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, 328, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 278, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(modulesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 233, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(processUnallocPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void advancedButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_advancedButtonActionPerformed
        final AdvancedConfigurationDialog dialog = new AdvancedConfigurationDialog();

        dialog.addApplyButtonListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (selectedModule.hasResourcesConfigPanel()) {
                        selectedModule.saveResourcesConfig();
                    }
                } catch (IngestModuleFactory.InvalidOptionsException ex) {
                    // RJCTODO: Error message box
                    // Return without closing to allow user to correct error.
                    return;
                }
                dialog.close();
            }
        });

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dialog.close();
            }
        });

        dialog.display(selectedModule.getResourcesConfigPanel());
    }//GEN-LAST:event_advancedButtonActionPerformed

    private void processUnallocCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_processUnallocCheckboxActionPerformed
        processUnallocatedSpace = processUnallocCheckbox.isSelected();
    }//GEN-LAST:event_processUnallocCheckboxActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton advancedButton;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JScrollPane modulesScrollPane;
    private javax.swing.JTable modulesTable;
    private javax.swing.JCheckBox processUnallocCheckbox;
    private javax.swing.JPanel processUnallocPanel;
    private javax.swing.JPanel simplePanel;
    private javax.swing.ButtonGroup timeGroup;
    // End of variables declaration//GEN-END:variables

    /**
     * A decorator for an ingest module template that adds ingest and global
     * options panels with lifetimes equal to that of the ingest configuration
     * panel.
     */
    static private class IngestModuleModel {

        private final IngestModuleTemplate moduleTemplate;
        private IngestModuleOptionsPanel ingestOptionsPanel = null;
        private IngestModuleResourcesConfigPanel resourcesConfigPanel = null;

        IngestModuleModel(IngestModuleTemplate moduleTemplate) {
            this.moduleTemplate = moduleTemplate;

            IngestModuleFactory moduleFactory = moduleTemplate.getIngestModuleFactory();
            if (moduleFactory.providesIngestOptionsPanels()) {
                try {
                    ingestOptionsPanel = moduleFactory.getIngestOptionsPanel(moduleTemplate.getIngestOptions());
                } catch (IngestModuleFactory.InvalidOptionsException ex) {
                    // RJCTODO: This is messy, maybe the template should be more capable, not expose factory? 
                    // RJCTODO: Need a solution
                     logger.log(Level.SEVERE, "The ingest options for " + moduleTemplate.getIngestModuleFactory().getModuleDisplayName() + " are invalid", ex);
                }
            }

            if (moduleFactory.providesResourcesConfigPanels()) {
                resourcesConfigPanel = moduleFactory.getResourcesConfigPanel();
            }
        }

        IngestModuleTemplate getIngestModuleTemplate() {
            return moduleTemplate;
        }

        String getName() {
            return moduleTemplate.getIngestModuleFactory().getModuleDisplayName();
        }

        String getDescription() {
            return moduleTemplate.getIngestModuleFactory().getModuleDescription();
        }

        void setEnabled(boolean enabled) {
            moduleTemplate.setEnabled(enabled);
        }

        boolean isEnabled() {
            return moduleTemplate.isEnabled();
        }

        boolean hasIngestOptionsPanel() {
            return moduleTemplate.getIngestModuleFactory().providesIngestOptionsPanels();
        }

        IngestModuleOptionsPanel getIngestOptionsPanel() {
            return ingestOptionsPanel;
        }

        boolean hasResourcesConfigPanel() {
            return moduleTemplate.getIngestModuleFactory().providesResourcesConfigPanels();
        }

        IngestModuleResourcesConfigPanel getResourcesConfigPanel() {
            return resourcesConfigPanel;
        }

        void saveResourcesConfig() throws IngestModuleFactory.InvalidOptionsException {
            resourcesConfigPanel.store();
        }
    }

    /**
     * Custom table model to display ingest module names and enable/disable
     * ingest modules.
     */
    private class IngestModulesTableModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return modules.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IngestModuleModel module = modules.get(rowIndex);
            if (columnIndex == 0) {
                return module.isEnabled();
            } else {
                return module.getName();
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                modules.get(rowIndex).setEnabled((boolean) aValue);
            }
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
    }

    /**
     * Custom cell renderer to create tool tips displaying ingest module
     * descriptions.
     */
    private class IngestModulesTableRenderer extends DefaultTableCellRenderer {

        List<String> tooltips = new ArrayList<>();

        public IngestModulesTableRenderer() {
            for (IngestModuleModel moduleTemplate : modules) {
                tooltips.add(moduleTemplate.getDescription());
            }
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (1 == column) {
                setToolTipText(tooltips.get(row));
            }
            return this;
        }
    }
}
