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

/*
 * IngestDialogPanel.java
 *
 * Created on Feb 1, 2012, 3:02:15 PM
 */
package org.sleuthkit.autopsy.ingest;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.sleuthkit.datamodel.Image;

/**
 *
 * @author dfickling
 */
public class IngestDialogPanel extends javax.swing.JPanel {
    
    private IngestManager manager = null;
    private List<IngestServiceAbstract> services;
    private String current;
    private Map<String, ConfigurationInterface> simpleConfigures;
    private Map<String, ConfigurationInterface> advancedConfigures;
    private JLabel defaultLabel;
    private Map<String, Boolean> serviceStates;
    private ServicesTableModel tableModel;
    private static final Logger logger = Logger.getLogger(IngestDialogPanel.class.getName());

    /** Creates new form IngestDialogPanel */
    IngestDialogPanel() {
        tableModel = new ServicesTableModel();
        services = new ArrayList<IngestServiceAbstract>();
        simpleConfigures = new HashMap<String, ConfigurationInterface>();
        advancedConfigures = new HashMap<String, ConfigurationInterface>();
        serviceStates = new HashMap<String, Boolean>();
        initComponents();
        customizeComponents();
    }
    
    private void customizeComponents(){
        servicesTable.setModel(tableModel);
        this.manager = IngestManager.getDefault();
        defaultLabel = new JLabel("This ingest service has no user-configurable settings");
        defaultLabel.setOpaque(true);
        defaultLabel.setBounds(0, 0, 300, 300);
        configurePane.add(defaultLabel, JLayeredPane.DEFAULT_LAYER);
        Collection<IngestServiceImage> imageServices = IngestManager.enumerateImageServices();
        for (final IngestServiceImage service : imageServices) {
            addService(service);
        }
        Collection<IngestServiceFsContent> fsServices = IngestManager.enumerateFsContentServices();
        for (final IngestServiceFsContent service : fsServices) {
            addService(service);
        }
        
        if (manager.isIngestRunning()) {
            freqSlider.setEnabled(false);
        }
        else {
            freqSlider.setEnabled(true);
        }
        freqSlider.setValue(manager.getUpdateFrequency());
        
        freqSlider.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
               int val = freqSlider.getValue();
               if (val<2)
                   freqSlider.setValue(2);
            }
            
        
        });
        
        servicesTable.setTableHeader(null);
        servicesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        //customize column witdhs
        final int width = servicesScrollPane.getPreferredSize().width;
        TableColumn column = null;
        for (int i = 0; i < servicesTable.getColumnCount(); i++) {
            column = servicesTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (width * 0.15)));
            } else {
                column.setPreferredWidth(((int) (width * 0.84)));
            }
        }
        
        servicesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    if(current != null)
                        simpleConfigures.get(current).save();
                    int index = listSelectionModel.getMinSelectionIndex();
                    String name = (String) tableModel.getValueAt(index, 1);
                    if(simpleConfigures.containsKey(name)){
                        configurePane.moveToFront(simpleConfigures.get(name));
                        current = name;
                    } else {
                        configurePane.moveToFront(defaultLabel);
                        current = null;
                    }
                    advancedButton.setEnabled(advancedConfigures.containsKey(name));
                } else {
                    current = null;
                }
            }
            
        });
        
    }

    private void addService(IngestServiceAbstract service) {
        final String serviceName = service.getName();
        services.add(service);
        serviceStates.put(serviceName, true);
        ConfigurationInterface serviceConfigure;
        if (service.hasSimpleConfiguration()) {
            serviceConfigure = service.getSimpleConfiguration();
            serviceConfigure.setOpaque(true);
            serviceConfigure.setBounds(0, 0, 300, 300);
            simpleConfigures.put(serviceName, serviceConfigure);
            configurePane.add(serviceConfigure, JLayeredPane.DEFAULT_LAYER);
        }
        if (service.hasAdvancedConfiguration()) {
            advancedConfigures.put(serviceName, service.getAdvancedConfiguration());
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        freqSlider = new javax.swing.JSlider();
        freqSliderLabel = new javax.swing.JLabel();
        servicesScrollPane = new javax.swing.JScrollPane();
        servicesTable = new javax.swing.JTable();
        configurePane = new javax.swing.JLayeredPane();
        advancedButton = new javax.swing.JButton();

        freqSlider.setMajorTickSpacing(5);
        freqSlider.setMaximum(30);
        freqSlider.setMinorTickSpacing(2);
        freqSlider.setPaintLabels(true);
        freqSlider.setPaintTicks(true);
        freqSlider.setSnapToTicks(true);
        freqSlider.setToolTipText(org.openide.util.NbBundle.getMessage(IngestDialogPanel.class, "IngestDialogPanel.freqSlider.toolTipText")); // NOI18N
        freqSlider.setValue(15);

        freqSliderLabel.setText(org.openide.util.NbBundle.getMessage(IngestDialogPanel.class, "IngestDialogPanel.freqSliderLabel.text")); // NOI18N
        freqSliderLabel.setToolTipText(org.openide.util.NbBundle.getMessage(IngestDialogPanel.class, "IngestDialogPanel.freqSliderLabel.toolTipText")); // NOI18N

        servicesScrollPane.setPreferredSize(new java.awt.Dimension(161, 201));

        servicesTable.setBackground(new java.awt.Color(240, 240, 240));
        servicesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        servicesTable.setShowHorizontalLines(false);
        servicesTable.setShowVerticalLines(false);
        servicesScrollPane.setViewportView(servicesTable);

        configurePane.setOpaque(true);
        configurePane.setPreferredSize(new java.awt.Dimension(300, 300));
        configurePane.setRequestFocusEnabled(false);

        advancedButton.setText(org.openide.util.NbBundle.getMessage(IngestDialogPanel.class, "IngestDialogPanel.advancedButton.text")); // NOI18N
        advancedButton.setEnabled(false);
        advancedButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                advancedButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(19, 19, 19)
                        .addComponent(freqSliderLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(freqSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(servicesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(configurePane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(advancedButton))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(servicesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(freqSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(configurePane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(advancedButton)
                    .addComponent(freqSliderLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void advancedButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_advancedButtonActionPerformed
        AdvancedConfigurationDialog dialog = new AdvancedConfigurationDialog();
        dialog.display(advancedConfigures.get(current));
    }//GEN-LAST:event_advancedButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton advancedButton;
    private javax.swing.JLayeredPane configurePane;
    private javax.swing.JSlider freqSlider;
    private javax.swing.JLabel freqSliderLabel;
    private javax.swing.JScrollPane servicesScrollPane;
    private javax.swing.JTable servicesTable;
    // End of variables declaration//GEN-END:variables

    private class ServicesTableModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return services.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            String name = services.get(rowIndex).getName();
            if(columnIndex == 0) {
                return serviceStates.get(name);
            }else {
                return name;
            }
        }
        
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if(columnIndex == 0){
                serviceStates.put((String)getValueAt(rowIndex, 1), (Boolean) aValue);
                    
            }
        }
        
        @Override
        public Class getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
    }
    
    List<IngestServiceAbstract> getServicesToStart() {
        List<IngestServiceAbstract> servicesToStart = new ArrayList<IngestServiceAbstract>();
        for (IngestServiceAbstract service : services) {
            boolean serviceEnabled = serviceStates.get(service.getName());
            if (serviceEnabled) {
                servicesToStart.add(service);
            }
        }
        return servicesToStart;
    }
    
    boolean freqSliderEnabled() {
        return freqSlider.isEnabled();
    }
    
    int sliderValue() {
        return freqSlider.getValue();
    }
}
