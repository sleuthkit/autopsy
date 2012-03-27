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

import java.awt.Color;
import java.awt.Component;
import org.sleuthkit.autopsy.corecomponents.AdvancedConfigurationDialog;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import org.sleuthkit.autopsy.casemodule.IngestConfigurator;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestMessagePanel.IngestMessageGroup;
import org.sleuthkit.datamodel.Image;

/**
 * main configuration panel for all ingest services, reusable JPanel component
 */
public class IngestDialogPanel extends javax.swing.JPanel implements IngestConfigurator {

    private IngestManager manager = null;
    private List<IngestServiceAbstract> services;
    private IngestServiceAbstract currentService;
    private Map<String, Boolean> serviceStates;
    private ServicesTableModel tableModel;
    private static final Logger logger = Logger.getLogger(IngestDialogPanel.class.getName());
    // The image that's just been added to the database
    private Image image;
    private static IngestDialogPanel instance = null;

    /** Creates new form IngestDialogPanel */
    private IngestDialogPanel() {
        tableModel = new ServicesTableModel();
        services = new ArrayList<IngestServiceAbstract>();
        serviceStates = new HashMap<String, Boolean>();
        initComponents();
        customizeComponents();
    }

    synchronized static IngestDialogPanel getDefault() {
        if (instance == null) {
            instance = new IngestDialogPanel();
        }
        return instance;
    }

    private void customizeComponents() {
        servicesTable.setModel(tableModel);
        this.manager = IngestManager.getDefault();
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
        } else {
            freqSlider.setEnabled(true);
        }
        freqSlider.setValue(manager.getUpdateFrequency());

        freqSlider.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                int val = freqSlider.getValue();
                if (val < 2) {
                    freqSlider.setValue(2);
                }
            }
        });


        servicesTable.setTableHeader(null);
        servicesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        //custom renderer for tooltips

        ServiceTableRenderer renderer = new ServiceTableRenderer();
        //customize column witdhs
        final int width = servicesScrollPane.getPreferredSize().width;
        TableColumn column = null;
        for (int i = 0; i < servicesTable.getColumnCount(); i++) {
            column = servicesTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (width * 0.15)));
            } else {
                column.setCellRenderer(renderer);
                column.setPreferredWidth(((int) (width * 0.84)));
            }
        }

        servicesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    save();
                    int index = listSelectionModel.getMinSelectionIndex();
                    currentService = services.get(index);
                    reloadSimpleConfiguration();
                    advancedButton.setEnabled(currentService.hasAdvancedConfiguration());
                } else {
                    currentService = null;
                }
            }
        });

    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (manager.isIngestRunning()) {
            freqSlider.setEnabled(false);
        } else {
            freqSlider.setEnabled(true);
        }
    }

    private void addService(IngestServiceAbstract service) {
        final String serviceName = service.getName();
        services.add(service);
        serviceStates.put(serviceName, true);
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
        jPanel1 = new javax.swing.JPanel();
        advancedButton = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JSeparator();
        jScrollPane1 = new javax.swing.JScrollPane();
        simplePanel = new javax.swing.JPanel();

        setPreferredSize(new java.awt.Dimension(522, 257));

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

        servicesScrollPane.setPreferredSize(new java.awt.Dimension(160, 160));

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
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 336, Short.MAX_VALUE)
            .addComponent(jSeparator2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 336, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(245, Short.MAX_VALUE)
                .addComponent(advancedButton)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 183, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(advancedButton)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(freqSlider, 0, 0, Short.MAX_VALUE)
                            .addComponent(servicesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGap(37, 37, 37)
                        .addComponent(freqSliderLabel)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 235, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(servicesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 170, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(freqSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addComponent(freqSliderLabel)))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void advancedButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_advancedButtonActionPerformed
        final AdvancedConfigurationDialog dialog = new AdvancedConfigurationDialog();
        dialog.addApplyButtonListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.close();
                currentService.saveAdvancedConfiguration();
                reloadSimpleConfiguration();
            }
        });
        dialog.display(currentService.getAdvancedConfiguration());
    }//GEN-LAST:event_advancedButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton advancedButton;
    private javax.swing.JSlider freqSlider;
    private javax.swing.JLabel freqSliderLabel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JScrollPane servicesScrollPane;
    private javax.swing.JTable servicesTable;
    private javax.swing.JPanel simplePanel;
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
            if (columnIndex == 0) {
                return serviceStates.get(name);
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
                serviceStates.put((String) getValueAt(rowIndex, 1), (Boolean) aValue);

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

    private void reloadSimpleConfiguration() {
        simplePanel.removeAll();
        if (currentService.hasSimpleConfiguration()) {
            simplePanel.add(currentService.getSimpleConfiguration());
        }
        simplePanel.revalidate();
        simplePanel.repaint();
    }

    /**
     * To be called whenever the next, close, or start buttons are pressed.
     * 
     */
    @Override
    public void save() {
        if (currentService != null && currentService.hasSimpleConfiguration()) {
            currentService.saveSimpleConfiguration();
        }
    }

    @Override
    public JPanel getIngestConfigPanel() {
        return this;
    }

    @Override
    public void setImage(Image image) {
        this.image = image;
    }

    @Override
    public void start() {
        //pick the services
        List<IngestServiceAbstract> servicesToStart = getServicesToStart();

        if (!servicesToStart.isEmpty()) {
            manager.execute(servicesToStart, image);
        }

        //update ingest freq. refresh
        if (freqSlider.isEnabled()) {
            manager.setUpdateFrequency(freqSlider.getValue());
        }
    }

    /**
     * Custom cell renderer for tooltips with service description
     */
    private class ServiceTableRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (column == 1) {
                //String serviceName = (String) table.getModel().getValueAt(row, column);
                IngestServiceAbstract service = services.get(row);
                String serviceDescr = service.getDescription();
                setToolTipText(serviceDescr);
            }




            return this;
        }
    }
}
