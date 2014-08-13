/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.ingest.IngestScheduler.IngestJobSchedulerStats;

public class IngestProgressSnapshotPanel extends javax.swing.JPanel {

    private final JDialog parent;
    private final IngestThreadActivitySnapshotsTableModel threadActivityTableModel;
    private final IngestJobTableModel jobTableModel;

    IngestProgressSnapshotPanel(JDialog parent) {
        this.parent = parent;
        threadActivityTableModel = new IngestThreadActivitySnapshotsTableModel();
        jobTableModel = new IngestJobTableModel();
        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {
        threadActivitySnapshotsTable.setModel(threadActivityTableModel);
        jobsTable.setModel(jobTableModel);

        int width = snapshotsScrollPane.getPreferredSize().width;
        for (int i = 0; i < threadActivitySnapshotsTable.getColumnCount(); ++i) {
            TableColumn column = threadActivitySnapshotsTable.getColumnModel().getColumn(i);
            switch (i) {
                case 0:
                    column.setPreferredWidth(((int) (width * 0.02)));
                    break;
                case 1:
                    column.setPreferredWidth(((int) (width * 0.20)));
                    break;
                case 2:
                    column.setPreferredWidth(((int) (width * 0.15)));
                    break;
                case 3:
                    column.setPreferredWidth(((int) (width * 0.35)));
                    break;
                case 4:
                    column.setPreferredWidth(((int) (width * 0.18)));
                    break;
                case 5:
                    column.setPreferredWidth(((int) (width * 0.10)));
                    break;
            }
        }

        threadActivitySnapshotsTable.setFillsViewportHeight(true);

        fileProcessedPerSecondLabel.setText(NbBundle.getMessage(this.getClass(),
                "IngestProgressSnapshotPanel.fileProcessedPerSecondLabel.text", 0));
    }

    private class IngestThreadActivitySnapshotsTableModel extends AbstractTableModel {

        private final String[] columnNames = {NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.threadID"),
            NbBundle.getMessage(this.getClass(),        
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.activity"),
            NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.dataSource"),
            NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.file"),
            NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.startTime"),
            NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.elapsedTime"),
            "Job ID"};
        private List<IngestManager.IngestThreadActivitySnapshot> snapshots;

        private IngestThreadActivitySnapshotsTableModel() {
            refresh();
        }

        private void refresh() {
            snapshots = IngestManager.getInstance().getIngestThreadActivitySnapshots();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return snapshots.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IngestManager.IngestThreadActivitySnapshot snapshot = snapshots.get(rowIndex);
            Object cellValue;
            switch (columnIndex) {
                case 0:
                    cellValue = snapshot.getThreadId();
                    break;
                case 1:
                    cellValue = snapshot.getActivity();
                    break;
                case 2:
                    cellValue = snapshot.getDataSourceName();
                    break;
                case 3:
                    cellValue = snapshot.getFileName();
                    break;
                case 4:
                    cellValue = snapshot.getStartTime();
                    break;
                case 5:
                    Date now = new Date();
                    long elapsedTime = now.getTime() - snapshot.getStartTime().getTime();
                    cellValue = DurationFormatUtils.formatDurationHMS(elapsedTime);
                    break;
                case 6:
                    cellValue = snapshot.getJobId();
                    break;
                default:
                    cellValue = null;
                    break;
            }
            return cellValue;
        }
    }
    
    private class IngestJobTableModel extends AbstractTableModel {

        private final String[] columnNames = {"Job ID", 
            "Data Source", "Start", "Files/Sec", "In Progress", "Files Queued", "Dir Queued", "Root Queued", "DS Queued"};
        private List<IngestJobSchedulerStats> schedStats;

        private IngestJobTableModel() {
            refresh();
        }

        private void refresh() {
            schedStats = IngestScheduler.getInstance().getJobStats();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return schedStats.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IngestJobSchedulerStats schedStat = schedStats.get(rowIndex);
            Object cellValue;
            switch (columnIndex) {
                case 0:
                    cellValue = schedStat.getJobId();
                    break;
                case 1:
                    cellValue = schedStat.getDataSource();
                    break;
                case 2:
                    SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
                    cellValue = dateFormat.format(new Date(schedStat.ingestJobStats.getStartTime()));
                    break;
                case 3:
                    cellValue = schedStat.ingestJobStats.getSpeed();
                    break;
                case 4:
                    cellValue = schedStat.getRunningListSize();
                    break;
                case 5:
                    cellValue = schedStat.getFileQueueSize();
                    break;
                case 6:
                    cellValue = schedStat.getDirQueueSize();
                    break;
                case 7:
                    cellValue = schedStat.getRootQueueSize();
                    break;
                case 8:
                    cellValue = schedStat.getDsQueueSize();
                    break;
                default:
                    cellValue = null;
                    break;
            }
            return cellValue;
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

        snapshotsScrollPane = new javax.swing.JScrollPane();
        threadActivitySnapshotsTable = new javax.swing.JTable();
        jobsScrollPane = new javax.swing.JScrollPane();
        jobsTable = new javax.swing.JTable();
        refreshButton = new javax.swing.JButton();
        closeButton = new javax.swing.JButton();
        fileProcessedPerSecondLabel = new javax.swing.JLabel();

        threadActivitySnapshotsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        snapshotsScrollPane.setViewportView(threadActivitySnapshotsTable);

        jobsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        jobsScrollPane.setViewportView(jobsTable);

        org.openide.awt.Mnemonics.setLocalizedText(refreshButton, org.openide.util.NbBundle.getMessage(IngestProgressSnapshotPanel.class, "IngestProgressSnapshotPanel.refreshButton.text")); // NOI18N
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(closeButton, org.openide.util.NbBundle.getMessage(IngestProgressSnapshotPanel.class, "IngestProgressSnapshotPanel.closeButton.text")); // NOI18N
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(fileProcessedPerSecondLabel, org.openide.util.NbBundle.getMessage(IngestProgressSnapshotPanel.class, "IngestProgressSnapshotPanel.fileProcessedPerSecondLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(snapshotsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 881, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(fileProcessedPerSecondLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 173, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(refreshButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(closeButton))
                    .addComponent(jobsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 881, Short.MAX_VALUE))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {closeButton, refreshButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(snapshotsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 155, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jobsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 155, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(refreshButton)
                    .addComponent(closeButton)
                    .addComponent(fileProcessedPerSecondLabel))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {closeButton, refreshButton});

    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        parent.setVisible(false);
    }//GEN-LAST:event_closeButtonActionPerformed

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        threadActivityTableModel.refresh();
        jobTableModel.refresh();
        IngestManager.ProcessedFilesSnapshot snapshot = IngestManager.getInstance().getProcessedFilesSnapshot();
        Date now = new Date();
        long elapsedTime = now.getTime() - snapshot.getStartTime().getTime();
        double filesPerSecond = (double) snapshot.getProcessedFilesCount() / (double) elapsedTime * 1000.0;
        fileProcessedPerSecondLabel.setText(NbBundle.getMessage(this.getClass(),
                "IngestProgressSnapshotPanel.fileProcessedPerSecondLabel.text", filesPerSecond));
    }//GEN-LAST:event_refreshButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JLabel fileProcessedPerSecondLabel;
    private javax.swing.JScrollPane jobsScrollPane;
    private javax.swing.JTable jobsTable;
    private javax.swing.JButton refreshButton;
    private javax.swing.JScrollPane snapshotsScrollPane;
    private javax.swing.JTable threadActivitySnapshotsTable;
    // End of variables declaration//GEN-END:variables
}
