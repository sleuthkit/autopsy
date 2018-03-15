/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.beans.PropertyChangeEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestModuleInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel for displaying ingest job history.
 */
public final class IngestJobInfoPanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(IngestJobInfoPanel.class.getName());
    private List<IngestJobInfo> ingestJobs;
    private IngestJobTableModel ingestJobTableModel = new IngestJobTableModel();
    private IngestModuleTableModel ingestModuleTableModel = new IngestModuleTableModel(null);
    private final DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    /**
     * Creates new form IngestJobInfoPanel
     */
    public IngestJobInfoPanel() {
        initComponents();
        customizeComponents();
    }

    @Messages({"IngestJobInfoPanel.loadIngestJob.error.text=Failed to load ingest jobs.",
        "IngestJobInfoPanel.loadIngestJob.error.title=Load Failure"})
    private void customizeComponents() {
        refresh();
        this.ingestJobTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            this.ingestModuleTableModel = new IngestModuleTableModel(this.ingestJobs.get(ingestJobTable.getSelectedRow()));
            this.ingestModuleTable.setModel(this.ingestModuleTableModel);
        });

        IngestManager.getInstance().addIngestJobEventListener((PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(IngestManager.IngestJobEvent.STARTED.toString())
                    || evt.getPropertyName().equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                    || evt.getPropertyName().equals(IngestManager.IngestJobEvent.COMPLETED.toString())) {
                refresh();
            }
        });
    }

    private void refresh() {
        try {
            SleuthkitCase skCase = Case.getOpenCase().getSleuthkitCase();
            List<IngestJobInfo> ingestJobs = skCase.getIngestJobs();
            this.ingestJobs = ingestJobs;
            this.repaint();
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to load ingest jobs.", ex);
            JOptionPane.showMessageDialog(this, Bundle.IngestJobInfoPanel_loadIngestJob_error_text(), Bundle.IngestJobInfoPanel_loadIngestJob_error_title(), JOptionPane.ERROR_MESSAGE);
        }
    }

    @Messages({"IngestJobInfoPanel.IngestJobTableModel.DataSource.header=Data Source",
        "IngestJobInfoPanel.IngestJobTableModel.StartTime.header=Start Time",
        "IngestJobInfoPanel.IngestJobTableModel.EndTime.header=End Time",
        "IngestJobInfoPanel.IngestJobTableModel.IngestStatus.header=Ingest Status"})
    private class IngestJobTableModel extends AbstractTableModel {

        private List<String> columnHeaders = new ArrayList<>();

        IngestJobTableModel() {
            columnHeaders.add(Bundle.IngestJobInfoPanel_IngestJobTableModel_DataSource_header());
            columnHeaders.add(Bundle.IngestJobInfoPanel_IngestJobTableModel_StartTime_header());
            columnHeaders.add(Bundle.IngestJobInfoPanel_IngestJobTableModel_EndTime_header());
            columnHeaders.add(Bundle.IngestJobInfoPanel_IngestJobTableModel_IngestStatus_header());
        }

        @Override
        public int getRowCount() {
            return ingestJobs.size();
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IngestJobInfo currIngestJob = ingestJobs.get(rowIndex);
            if (columnIndex == 0) {
                try {
                    SleuthkitCase skCase = Case.getOpenCase().getSleuthkitCase();
                    return skCase.getContentById(currIngestJob.getObjectId()).getName();
                } catch (TskCoreException | NoCurrentCaseException ex) {
                    logger.log(Level.SEVERE, "Failed to get content from db", ex);
                    return "";
                }
            } else if (columnIndex == 1) {
                return datetimeFormat.format(currIngestJob.getStartDateTime());
            } else if (columnIndex == 2) {
                Date endDate = currIngestJob.getEndDateTime();
                if (endDate.getTime() == 0) {
                    return "N/A";
                }
                return datetimeFormat.format(currIngestJob.getEndDateTime());
            } else if (columnIndex == 3) {
                return currIngestJob.getStatus().getDisplayName();
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
        }

    }

    @Messages({"IngestJobInfoPanel.IngestModuleTableModel.ModuleName.header=Module Name",
        "IngestJobInfoPanel.IngestModuleTableModel.ModuleVersion.header=Module Version"})
    private class IngestModuleTableModel extends AbstractTableModel {

        private List<String> columnHeaders = new ArrayList<>();
        private IngestJobInfo currJob;

        IngestModuleTableModel(IngestJobInfo currJob) {
            columnHeaders.add(Bundle.IngestJobInfoPanel_IngestModuleTableModel_ModuleName_header());
            columnHeaders.add(Bundle.IngestJobInfoPanel_IngestModuleTableModel_ModuleVersion_header());
            this.currJob = currJob;
        }

        @Override
        public int getRowCount() {
            if (currJob == null) {
                return 0;
            }
            return currJob.getIngestModuleInfo().size();
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (currJob != null) {
                IngestModuleInfo currIngestModule = currJob.getIngestModuleInfo().get(rowIndex);
                if (columnIndex == 0) {
                    return currIngestModule.getDisplayName();
                } else if (columnIndex == 1) {
                    return currIngestModule.getVersion();
                }
                return null;
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
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

        jScrollPane1 = new javax.swing.JScrollPane();
        ingestJobTable = new javax.swing.JTable();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        ingestModuleTable = new javax.swing.JTable();

        jScrollPane1.setBorder(null);

        ingestJobTable.setModel(ingestJobTableModel);
        ingestJobTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(ingestJobTable);
        ingestJobTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(IngestJobInfoPanel.class, "IngestJobInfoPanel.jLabel1.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(IngestJobInfoPanel.class, "IngestJobInfoPanel.jLabel2.text")); // NOI18N

        ingestModuleTable.setModel(ingestModuleTableModel);
        jScrollPane2.setViewportView(ingestModuleTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 485, Short.MAX_VALUE))
                .addGap(8, 8, 8)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 254, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 162, Short.MAX_VALUE)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(10, 10, 10))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTable ingestJobTable;
    private javax.swing.JTable ingestModuleTable;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    // End of variables declaration//GEN-END:variables
}
