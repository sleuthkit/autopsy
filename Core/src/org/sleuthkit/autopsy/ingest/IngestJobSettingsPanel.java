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
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import org.sleuthkit.autopsy.corecomponents.AdvancedConfigurationDialog;

/**
 * User interface component to allow a user to make ingest job settings.
 */
public final class IngestJobSettingsPanel extends javax.swing.JPanel {

    private final IngestJobSettings settings;
    private final List<IngestModuleModel> modules;
    private IngestModuleModel selectedModule;

    /**
     * Construct a user interface component to allow a user to make ingest job
     * settings.
     *
     * @param settings The initial settings for the ingest job.
     */
    public IngestJobSettingsPanel(IngestJobSettings settings) {
        this.settings = settings;
        this.modules = new ArrayList<>();
        for (IngestModuleTemplate moduleTemplate : settings.getIngestModuleTemplates()) {
            this.modules.add(new IngestModuleModel(moduleTemplate));
        }
        initComponents();
        customizeComponents();
    }

    /**
     * Gets the ingest settings made using this panel.
     *
     * @return The settings.
     */
    public IngestJobSettings getSettings() {
        List<IngestModuleTemplate> moduleTemplates = new ArrayList<>();
        for (IngestModuleModel module : modules) {
            IngestModuleTemplate moduleTemplate = module.getIngestModuleTemplate();
            if (module.hasModuleSettingsPanel()) {
                IngestModuleIngestJobSettings moduleSettings = module.getModuleSettingsPanel().getSettings();
                moduleTemplate.setModuleSettings(moduleSettings);
            }
            moduleTemplates.add(moduleTemplate);
        }
        this.settings.setIngestModuleTemplates(moduleTemplates);
        return this.settings;
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
        // ingest job options panel of the currently selected module model and 
        // enable or disable the resources configuration panel invocation button.
        modulesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    int index = listSelectionModel.getMinSelectionIndex();
                    selectedModule = modules.get(index);
                    simplePanel.removeAll();
                    if (null != selectedModule.getModuleSettingsPanel()) {
                        simplePanel.add(selectedModule.getModuleSettingsPanel());
                    }
                    simplePanel.revalidate();
                    simplePanel.repaint();
                    advancedButton.setEnabled(null != selectedModule.getGlobalSettingsPanel());
                    descriptionLabel.setText(selectedModule.getDescription());
                    descriptionLabel.setToolTipText(selectedModule.getDescription());
                }
            }
        });

        processUnallocCheckbox.setSelected(this.settings.getProcessUnallocatedSpace());
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
        descriptionLabel = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        simplePanel = new javax.swing.JPanel();
        jButtonSelectAll = new javax.swing.JButton();
        jButtonDeselectAll = new javax.swing.JButton();
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

        advancedButton.setText(org.openide.util.NbBundle.getMessage(IngestJobSettingsPanel.class, "IngestJobSettingsPanel.advancedButton.text")); // NOI18N
        advancedButton.setActionCommand(org.openide.util.NbBundle.getMessage(IngestJobSettingsPanel.class, "IngestJobSettingsPanel.advancedButton.actionCommand")); // NOI18N
        advancedButton.setEnabled(false);
        advancedButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                advancedButtonActionPerformed(evt);
            }
        });

        descriptionLabel.setText(org.openide.util.NbBundle.getMessage(IngestJobSettingsPanel.class, "IngestJobSettingsPanel.descriptionLabel.text")); // NOI18N

        jScrollPane1.setBorder(null);
        jScrollPane1.setPreferredSize(new java.awt.Dimension(250, 180));

        simplePanel.setLayout(new javax.swing.BoxLayout(simplePanel, javax.swing.BoxLayout.PAGE_AXIS));
        jScrollPane1.setViewportView(simplePanel);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSeparator2, javax.swing.GroupLayout.Alignment.TRAILING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(descriptionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(advancedButton)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 211, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(descriptionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(advancedButton))
                .addContainerGap())
        );

        jButtonSelectAll.setText(org.openide.util.NbBundle.getMessage(IngestJobSettingsPanel.class, "IngestJobSettingsPanel.jButtonSelectAll.text")); // NOI18N
        jButtonSelectAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSelectAllActionPerformed(evt);
            }
        });

        jButtonDeselectAll.setText(org.openide.util.NbBundle.getMessage(IngestJobSettingsPanel.class, "IngestJobSettingsPanel.jButtonDeselectAll.text")); // NOI18N
        jButtonDeselectAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonDeselectAllActionPerformed(evt);
            }
        });

        processUnallocCheckbox.setText(org.openide.util.NbBundle.getMessage(IngestJobSettingsPanel.class, "IngestJobSettingsPanel.processUnallocCheckbox.text")); // NOI18N
        processUnallocCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(IngestJobSettingsPanel.class, "IngestJobSettingsPanel.processUnallocCheckbox.toolTipText")); // NOI18N
        processUnallocCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                processUnallocCheckboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(modulesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(10, 10, 10))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(24, 24, 24)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(processUnallocCheckbox)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jButtonSelectAll, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jButtonDeselectAll)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 30, Short.MAX_VALUE)))
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 310, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 278, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(modulesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jButtonDeselectAll)
                            .addComponent(jButtonSelectAll))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(processUnallocCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void advancedButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_advancedButtonActionPerformed
        final AdvancedConfigurationDialog dialog = new AdvancedConfigurationDialog();

        dialog.addApplyButtonListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedModule.hasGlobalSettingsPanel()) {
                    selectedModule.saveResourcesConfig();
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

        dialog.display(selectedModule.getGlobalSettingsPanel());
    }//GEN-LAST:event_advancedButtonActionPerformed

    private void jButtonSelectAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSelectAllActionPerformed
        SelectAllModules(true);
    }//GEN-LAST:event_jButtonSelectAllActionPerformed

    private void jButtonDeselectAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonDeselectAllActionPerformed
        SelectAllModules(false);
    }//GEN-LAST:event_jButtonDeselectAllActionPerformed

    private void processUnallocCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_processUnallocCheckboxActionPerformed
        this.settings.setProcessUnallocatedSpace(processUnallocCheckbox.isSelected());
    }//GEN-LAST:event_processUnallocCheckboxActionPerformed

    private void SelectAllModules(boolean set) {
        for (IngestModuleModel module : modules) {
            module.setEnabled(set);
        }
        modulesTable.repaint();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton advancedButton;
    private javax.swing.JLabel descriptionLabel;
    private javax.swing.JButton jButtonDeselectAll;
    private javax.swing.JButton jButtonSelectAll;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JScrollPane modulesScrollPane;
    private javax.swing.JTable modulesTable;
    private javax.swing.JCheckBox processUnallocCheckbox;
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
        private IngestModuleGlobalSettingsPanel globalSettingsPanel = null;
        private IngestModuleIngestJobSettingsPanel moduleSettingsPanel = null;

        IngestModuleModel(IngestModuleTemplate moduleTemplate) {
            this.moduleTemplate = moduleTemplate;
            if (moduleTemplate.hasModuleSettingsPanel()) {
                moduleSettingsPanel = moduleTemplate.getModuleSettingsPanel();
            }
            if (moduleTemplate.hasGlobalSettingsPanel()) {
                globalSettingsPanel = moduleTemplate.getGlobalSettingsPanel();
            }
        }

        IngestModuleTemplate getIngestModuleTemplate() {
            return moduleTemplate;
        }

        String getName() {
            return moduleTemplate.getModuleName();
        }

        String getDescription() {
            return moduleTemplate.getModuleDescription();
        }

        void setEnabled(boolean enabled) {
            moduleTemplate.setEnabled(enabled);
        }

        boolean isEnabled() {
            return moduleTemplate.isEnabled();
        }

        boolean hasModuleSettingsPanel() {
            return moduleTemplate.hasModuleSettingsPanel();
        }

        IngestModuleIngestJobSettingsPanel getModuleSettingsPanel() {
            return moduleSettingsPanel;
        }

        boolean hasGlobalSettingsPanel() {
            return moduleTemplate.hasGlobalSettingsPanel();
        }

        IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
            return globalSettingsPanel;
        }

        void saveResourcesConfig() {
            globalSettingsPanel.saveSettings();
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
