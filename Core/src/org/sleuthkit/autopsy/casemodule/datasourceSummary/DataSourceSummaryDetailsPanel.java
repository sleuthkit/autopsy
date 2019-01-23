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

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel to display additional details associated with a specific DataSource
 *
 */
public class DataSourceSummaryDetailsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private Map<Long, String> osDetailMap = new HashMap<>();
    private static final Logger logger = Logger.getLogger(DataSourceSummaryDetailsPanel.class.getName());

    /**
     * Creates new form DataSourceSummaryDetailsPanel
     */
    @Messages({"DataSourceSummaryDetailsPanel.getDataSources.error.text=Failed to get the list of datasources for the current case.",
        "DataSourceSummaryDetailsPanel.getDataSources.error.title=Load Failure"})
    public DataSourceSummaryDetailsPanel() {
        initComponents();
        getOperatingSystems();
    }

    /**
     * Update which DataSource this panel should display details about
     *
     * @param selectedDataSource the DataSource to display details about.
     */
    @Messages({"DataSourceSummaryDetailsPanel.detail.name=Name",
        "DataSourceSummaryDetailsPanel.detail.displayName=Display Name",
        "DataSourceSummaryDetailsPanel.detail.deviceId=Device ID",
        "DataSourceSummaryDetailsPanel.detail.operatingSystem=Operating System",
        "DataSourceSummaryDetailsPanel.detail.timeZone=Time Zone",
        "DataSourceSummaryDetailsPanel.detail.filePath=File Path",
        "DataSourceSummaryDetailsPanel.detail.size=Size",
        "DataSourceSummaryDetailsPanel.detail.sectorSize=Sector Size",
        "DataSourceSummaryDetailsPanel.detail.md5=MD5",
        "DataSourceSummaryDetailsPanel.detail.sha1=SHA1",
        "DataSourceSummaryDetailsPanel.detail.sha256=SHA256",
        "DataSourceSummaryDetailsPanel.detail.aquisitionDetails=Aquisition Details",
        "DataSourceSummaryDetailsPanel.units.bytes= bytes"})
    void updateDetailsPanelData(DataSource selectedDataSource) {
        clearTableValues();
        if (selectedDataSource != null) {
            updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_name(), selectedDataSource.getName());
            updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_displayName(), selectedDataSource.getName());
            updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_deviceId(), selectedDataSource.getDeviceId());
            updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_timeZone(), selectedDataSource.getTimeZone());
            updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_operatingSystem(), osDetailMap.get(selectedDataSource.getId()));
            if (selectedDataSource instanceof Image) {
                updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_filePath(), StringUtils.join(((Image) selectedDataSource).getPaths()));
                updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_size(), String.valueOf(selectedDataSource.getSize()) + Bundle.DataSourceSummaryDetailsPanel_units_bytes());
                updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_sectorSize(), String.valueOf(((Image) selectedDataSource).getSsize()) + Bundle.DataSourceSummaryDetailsPanel_units_bytes());
                try {
                    updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_md5(), ((Image) selectedDataSource).getMd5());
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Unable to get MD5 for selected data source", ex);
                }
                try {
                    updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_sha1(), ((Image) selectedDataSource).getSha1());
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Unable to get SHA1 for selected data source", ex);
                }
                try {
                    updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_sha256(), ((Image) selectedDataSource).getSha256());
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Unable to get SHA256 for selected data source", ex);
                }
            }
            try {
                updateTableValue(Bundle.DataSourceSummaryDetailsPanel_detail_aquisitionDetails(), selectedDataSource.getAcquisitionDetails());
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to get aquisition details for selected data source", ex);
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
    }

    /**
     * Map the names of operating systems joined in a comma seperated list to
     * the Data Source they exist on.
     *
     */
    private void getOperatingSystems() {
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            ArrayList<BlackboardArtifact> osInfoArtifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO);
            for (BlackboardArtifact osInfo : osInfoArtifacts) {
                BlackboardAttribute programName = osInfo.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME));
                if (programName != null) {
                    String currentOsString = osDetailMap.get(osInfo.getDataSource().getId());
                    if (currentOsString == null || currentOsString.isEmpty()) {
                        currentOsString = programName.getValueString();
                    } else {
                        currentOsString = currentOsString + ", " + programName.getValueString();;
                    }
                    osDetailMap.put(osInfo.getDataSource().getId(), currentOsString);
                }
            }
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to load OS info artifacts.", ex);
            JOptionPane.showMessageDialog(this, Bundle.DataSourceSummaryDetailsPanel_getDataSources_error_text(), Bundle.DataSourceSummaryDetailsPanel_getDataSources_error_title(), JOptionPane.ERROR_MESSAGE);
        }
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
