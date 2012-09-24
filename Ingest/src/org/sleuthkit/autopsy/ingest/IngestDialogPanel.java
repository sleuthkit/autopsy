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
package org.sleuthkit.autopsy.ingest;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JCheckBox;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.IngestConfigurator;
import org.sleuthkit.autopsy.corecomponents.AdvancedConfigurationDialog;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.Image;

/**
 * main configuration panel for all ingest modules, reusable JPanel component
 */
public class IngestDialogPanel extends javax.swing.JPanel implements IngestConfigurator {

    private IngestManager manager = null;
    private List<IngestModuleAbstract> modules;
    private IngestModuleAbstract currentModule;
    private Map<String, Boolean> moduleStates;
    private ModulesTableModel tableModel;
    private static final Logger logger = Logger.getLogger(IngestDialogPanel.class.getName());
    // The image that's just been added to the database
    private Image image;
    private static IngestDialogPanel instance = null;

    /** Creates new form IngestDialogPanel */
    private IngestDialogPanel() {
        tableModel = new ModulesTableModel();
        modules = new ArrayList<IngestModuleAbstract>();
        moduleStates = new HashMap<String, Boolean>();
        initComponents();
        customizeComponents();
    }

    synchronized static IngestDialogPanel getDefault() {
        if (instance == null) {
            instance = new IngestDialogPanel();
        }
        return instance;
    }
    
    private void loadModules() {
        this.modules.clear();
        //this.moduleStates.clear(); maintain the state
        Collection<IngestModuleImage> imageModules = manager.enumerateImageModules();
        for (final IngestModuleImage module : imageModules) {
            addModule(module);
        }
        Collection<IngestModuleAbstractFile> fsModules = manager.enumerateAbstractFileModules();
        for (final IngestModuleAbstractFile module : fsModules) {
            addModule(module);
        }
    }

    private void customizeComponents() {
        modulesTable.setModel(tableModel);
        this.manager = IngestManager.getDefault();
      
        loadModules();
        try {
            IngestModuleLoader.getDefault().addModulesReloadedListener(new PropertyChangeListener() {

                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getPropertyName().equals(IngestModuleLoader.Event.ModulesReloaded.toString())) {
                        loadModules();
                    }
                }
                
            });
        } catch (IngestModuleLoaderException ex) {
            logger.log(Level.SEVERE, "Could not initialize ingest module loader to listen for module config changes", ex);
        }

        modulesTable.setTableHeader(null);
        modulesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        //custom renderer for tooltips

        ModulesTableRenderer renderer = new ModulesTableRenderer();
        //customize column witdhs
        final int width = modulesScrollPane.getPreferredSize().width;
        TableColumn column = null;
        for (int i = 0; i < modulesTable.getColumnCount(); i++) {
            column = modulesTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (width * 0.15)));
            } else {
                column.setCellRenderer(renderer);
                column.setPreferredWidth(((int) (width * 0.84)));
            }
        }

        modulesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    save();
                    int index = listSelectionModel.getMinSelectionIndex();
                    currentModule = modules.get(index);
                    reload();
                    advancedButton.setEnabled(currentModule.hasAdvancedConfiguration());
                } else {
                    currentModule = null;
                }
            }
        });
        
        processUnallocCheckbox.setSelected(manager.getProcessUnallocSpace());

    }

   
    
    private void setProcessUnallocSpaceEnabled(boolean enabled) {
        processUnallocCheckbox.setEnabled(enabled);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (manager.isIngestRunning()) {
            setProcessUnallocSpaceEnabled(false);

        } else {
            setProcessUnallocSpaceEnabled(true);
        }
    }

    private void addModule(IngestModuleAbstract module) {
        final String moduleName = module.getName();
        modules.add(module);
        moduleStates.put(moduleName, true);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
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

        setPreferredSize(new java.awt.Dimension(522, 257));

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

        advancedButton.setText(org.openide.util.NbBundle.getMessage(IngestDialogPanel.class, "IngestDialogPanel.advancedButton.text")); // NOI18N
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

        processUnallocCheckbox.setText(org.openide.util.NbBundle.getMessage(IngestDialogPanel.class, "IngestDialogPanel.processUnallocCheckbox.text")); // NOI18N
        processUnallocCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(IngestDialogPanel.class, "IngestDialogPanel.processUnallocCheckbox.toolTipText")); // NOI18N
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
                .addContainerGap(7, Short.MAX_VALUE))
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
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 235, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(modulesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 190, Short.MAX_VALUE)
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
                dialog.close();
                currentModule.saveAdvancedConfiguration();
                reload();
            }
        });
        dialog.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                dialog.close();
                reload();
            }
        });
        save(); // save the simple panel
        dialog.display(currentModule.getAdvancedConfiguration());
    }//GEN-LAST:event_advancedButtonActionPerformed

    private void processUnallocCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_processUnallocCheckboxActionPerformed
        // TODO add your handling code here:
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

    private class ModulesTableModel extends AbstractTableModel {

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
            String name = modules.get(rowIndex).getName();
            if (columnIndex == 0) {
                return moduleStates.get(name);
            } else {
                return name;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                moduleStates.put((String) getValueAt(rowIndex, 1), (Boolean) aValue);

            }
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
    }

    List<IngestModuleAbstract> getModulesToStart() {
        List<IngestModuleAbstract> modulesToStart = new ArrayList<IngestModuleAbstract>();
        for (IngestModuleAbstract module : modules) {
            boolean moduleEnabled = moduleStates.get(module.getName());
            if (moduleEnabled) {
                modulesToStart.add(module);
            }
        }
        return modulesToStart;
    }


    
    private boolean processUnallocSpaceEnabled() {
        return processUnallocCheckbox.isEnabled();
    }



    /**
     * To be called whenever the next, close, or start buttons are pressed.
     * 
     */
    @Override
    public void save() {
        // Save the current module
        if (currentModule != null && currentModule.hasSimpleConfiguration()) {
            currentModule.saveSimpleConfiguration();
        }
        // Save this panel
        ArrayList<String> modulesEnabled = new ArrayList<String>();
        for(int i=0; i<modulesTable.getRowCount(); i++) {
            // Column 0 is always the module's checkbox (which is retreived as a boolean)
            Boolean enabled = (Boolean) modulesTable.getValueAt(i, 0);
            if(enabled) {
                // Column 1 is always the module name
                String moduleName = (String) modulesTable.getValueAt(i, 1);
                modulesEnabled.add(moduleName);
            }
        }
        // Add all the enabled modules to the properties separated by a coma
        String list = "";
        for(int i=0; i<modulesEnabled.size(); i++) {
            list += modulesEnabled.get(i);
            if(i+1 < modulesEnabled.size()) {
                list += ", ";
            }
        }
        ModuleSettings.setConfigSetting(IngestManager.MODULE_PROPERTIES, "Enabled Ingest Modules", list);
        String processUnalloc = Boolean.toString(processUnallocCheckbox.isSelected());
        ModuleSettings.setConfigSetting(IngestManager.MODULE_PROPERTIES, "Process Unallocated Space", processUnalloc);
        
    }
    
    /**
     * Called when the dialog needs to be reloaded. Most commonly used
     * to refresh the simple panel.
     * 
     * Called every time this panel is displayed.
     */
    @Override
    public void reload() {
        // Reload the simple panel
        if(this.modulesTable.getSelectedRow() != -1) {
            simplePanel.removeAll();
            if (currentModule.hasSimpleConfiguration()) {
                simplePanel.add(currentModule.getSimpleConfiguration());
            }
            simplePanel.revalidate();
            simplePanel.repaint();
        }
        // Reload this panel
        String list = ModuleSettings.getConfigSetting(IngestManager.MODULE_PROPERTIES, "Enabled Ingest Modules");
        if(list!=null) { // if no property is found, list will be null
            ArrayList<String> modulesEnabled = new ArrayList<String>(Arrays.asList(list.split(", ")));
            // For every row, see if that module name is in the ArrayList
            for(int i=0; i<modulesTable.getRowCount(); i++) {
                String moduleName = (String) modulesTable.getValueAt(i, 1);
                if(modulesEnabled.contains(moduleName)) {
                    modulesTable.setValueAt(true, i, 0); // we found it!
                } else {
                    modulesTable.setValueAt(false, i, 0); // not so lucky..
                }
            }
        }
        String processUnalloc = ModuleSettings.getConfigSetting(IngestManager.MODULE_PROPERTIES, "Process Unallocated Space");
        if(processUnalloc!=null) {
            processUnallocCheckbox.setSelected(Boolean.parseBoolean(processUnalloc));
        }
    }

    @Override
    public JPanel getIngestConfigPanel() {
        this.reload();
        return this;
    }

    @Override
    public void setImage(Image image) {
        this.image = image;
    }

    @Override
    public void start() {
        //pick the modules
        List<IngestModuleAbstract> modulesToStart = getModulesToStart();

        if (!modulesToStart.isEmpty()) {
            manager.execute(modulesToStart, image);
        }


        //update ingest proc. unalloc space
        if (processUnallocSpaceEnabled() ) {
            manager.setProcessUnallocSpace(processUnallocCheckbox.isSelected());
        }
    }

    @Override
    public boolean isIngestRunning() {
        return manager.isIngestRunning();
    }
    
    

    /**
     * Custom cell renderer for tooltips with module description
     */
    private class ModulesTableRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (column == 1) {
                //String moduleName = (String) table.getModel().getValueAt(row, column);
                IngestModuleAbstract module = modules.get(row);
                String moduleDescr = module.getDescription();
                setToolTipText(moduleDescr);
            }




            return this;
        }
    }
}
