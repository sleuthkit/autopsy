/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.datasourceSummary;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import org.apache.commons.lang.StringUtils;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.OSInfo;
import org.sleuthkit.datamodel.OSUtility;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel to display additional details associated with a specific DataSource
 *
 */
public class DataSourceSummaryDetailsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private List<OSInfo> osInfoList;
    private static final Logger logger = Logger.getLogger(DataSourceSummaryDetailsPanel.class.getName());

    /**
     * Creates new form DataSourceSummaryDetailsPanel
     */
    @Messages({"DataSourceSummaryDetailsPanel.getDataSources.error.text=Failed to get the list of datasources for the current case.",
        "DataSourceSummaryDetailsPanel.getDataSources.error.title=Load Failure"})
    public DataSourceSummaryDetailsPanel() {
        initComponents();
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            osInfoList = OSUtility.getOSInfo(skCase);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to load ingest jobs.", ex);
            JOptionPane.showMessageDialog(this, Bundle.DataSourceSummaryDetailsPanel_getDataSources_error_text(), Bundle.DataSourceSummaryDetailsPanel_getDataSources_error_title(), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Update which DataSource this panel should display details about
     *
     * @param selectedDataSource the DataSource to display details about.
     */
    void updateDetailsPanelData(DataSource selectedDataSource) {
        clearTableValues();
        if (selectedDataSource != null) {
            updateTableValue("Name", selectedDataSource.getName());
            updateTableValue("Display Name", selectedDataSource.getName());
            updateTableValue("Device ID", selectedDataSource.getDeviceId());
            updateTableValue("Operating System", getOSName(selectedDataSource));
            updateTableValue("Time Zone", selectedDataSource.getTimeZone());
            if (selectedDataSource instanceof Image) {
                updateTableValue("File Path", StringUtils.join(((Image) selectedDataSource).getPaths()));
                updateTableValue("Size", String.valueOf(selectedDataSource.getSize()) + " bytes");
                updateTableValue("Sector Size", String.valueOf(((Image) selectedDataSource).getSsize()) + " bytes");
                try {
                    updateTableValue("MD5", ((Image) selectedDataSource).getMd5());
                } catch (TskCoreException ex) {

                }
                try {
                    updateTableValue("SHA1", ((Image) selectedDataSource).getSha1());
                } catch (TskCoreException ex) {

                }
                try {
                    updateTableValue("SHA256", ((Image) selectedDataSource).getSha256());
                } catch (TskCoreException ex) {

                }   
            }
            try {
                updateTableValue("Aquisition Details", selectedDataSource.getAcquisitionDetails());
            } catch (TskCoreException ex) {

            }
            this.repaint();
        }
    }

    void clearTableValues() {
        ((DefaultTableModel) jTable1.getModel()).setRowCount(0);
    }

    void updateTableValue(String valueName, String value) {
        if (value != null && !value.isEmpty()) {
            ((DefaultTableModel) jTable1.getModel()).addRow(new Object[]{valueName, value});
        }
//        for (int row = 0; row < jTable1.getRowCount(); row++) {
//            if (jTable1.getModel().getValueAt(row, 0).equals(valueName)) {
//                jTable1.getModel().setValueAt(value, row, 1);
//                return;
//            }
//        }
    }

    /**
     * Get the name of the operating system if it is available. Otherwise get
     * and empty string.
     *
     * @param selectedDataSource the datasource to get the OS information for
     *
     * @return the name of the operating system on the specified datasource,
     *         empty string if no operating system info found
     */
    private String getOSName(DataSource selectedDataSource) {
        String osName = "";
        if (selectedDataSource != null) {
            for (OSInfo osInfo : osInfoList) {
                try {
                    //assumes only one Operating System per datasource
                    //get the datasource id from the OSInfo's first artifact if it has artifacts
                    if (!osInfo.getArtifacts().isEmpty() && osInfo.getArtifacts().get(0).getDataSource().getId() == selectedDataSource.getId()) {
                        if (!osName.isEmpty()) {
                            osName += ", ";
                        }
                        osName += osInfo.getOSName();
                        //if this OSInfo object has a name use it otherwise keep checking OSInfo objects
                    }
                } catch (TskCoreException ignored) {
                    //unable to get datasource for the OSInfo Object 
                    //continue checking for OSInfo objects to try and get get the desired information
                }
            }
        }
        return osName;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane2 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Detail", "Value"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jTable1.setCellSelectionEnabled(true);
        jScrollPane2.setViewportView(jTable1);
        if (jTable1.getColumnModel().getColumnCount() > 0) {
            jTable1.getColumnModel().getColumn(0).setHeaderValue(org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.jTable1.columnModel.title0")); // NOI18N
            jTable1.getColumnModel().getColumn(1).setHeaderValue(org.openide.util.NbBundle.getMessage(DataSourceSummaryDetailsPanel.class, "DataSourceSummaryDetailsPanel.jTable1.columnModel.title1")); // NOI18N
        }

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 545, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 259, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable jTable1;
    // End of variables declaration//GEN-END:variables
}
