/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014-2018 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.JDialog;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.openide.util.NbBundle;

/**
 * A panel that displays ingest task progress snapshots.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class IngestProgressSnapshotPanel extends javax.swing.JPanel {

    private final JDialog parent;
    private final IngestProgressSnapshotProvider snapshotProvider;
    private final IngestThreadActivitySnapshotsTableModel threadActivityTableModel;
    private final IngestJobTableModel jobTableModel;
    private final ModuleTableModel moduleTableModel;

    IngestProgressSnapshotPanel(JDialog parent, IngestProgressSnapshotProvider snapshotProvider) {
        this.parent = parent;
        this.snapshotProvider = snapshotProvider;
        threadActivityTableModel = new IngestThreadActivitySnapshotsTableModel();
        jobTableModel = new IngestJobTableModel();
        moduleTableModel = new ModuleTableModel();
        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {
        threadActivitySnapshotsTable.setModel(threadActivityTableModel);
        jobTable.setModel(jobTableModel);
        moduleTable.setModel(moduleTableModel);

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
            NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.jobID")};
        private List<IngestManager.IngestThreadActivitySnapshot> snapshots;

        private IngestThreadActivitySnapshotsTableModel() {
            refresh();
        }

        private void refresh() {
            snapshots = snapshotProvider.getIngestThreadActivitySnapshots();
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
                    cellValue = snapshot.getIngestJobId();
                    break;
                default:
                    cellValue = null;
                    break;
            }
            return cellValue;
        }
    }

    private class IngestJobTableModel extends AbstractTableModel {

        private final String[] columnNames = {NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.jobID"),
            NbBundle.getMessage(this.getClass(),
            "IngestJobTableModel.colName.dataSource"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.start"),
            NbBundle.getMessage(this.getClass(),
            "IngestJobTableModel.colName.numProcessed"),
            NbBundle.getMessage(this.getClass(),
            "IngestJobTableModel.colName.filesPerSec"),
            NbBundle.getMessage(this.getClass(),
            "IngestJobTableModel.colName.inProgress"),
            NbBundle.getMessage(this.getClass(),
            "IngestJobTableModel.colName.filesQueued"),
            NbBundle.getMessage(this.getClass(),
            "IngestJobTableModel.colName.dirQueued"),
            NbBundle.getMessage(this.getClass(),
            "IngestJobTableModel.colName.rootQueued"),
            NbBundle.getMessage(this.getClass(),
            "IngestJobTableModel.colName.streamingQueued"),
            NbBundle.getMessage(this.getClass(),
            "IngestJobTableModel.colName.dsQueued")};
        private List<Snapshot> jobSnapshots;

        private IngestJobTableModel() {
            refresh();
        }

        private void refresh() {
            jobSnapshots = snapshotProvider.getIngestJobSnapshots();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return jobSnapshots.size();
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
            Snapshot snapShot = jobSnapshots.get(rowIndex);
            Object cellValue;
            switch (columnIndex) {
                case 0:
                    cellValue = snapShot.getJobId();
                    break;
                case 1:
                    cellValue = snapShot.getDataSource();
                    break;
                case 2:
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    cellValue = dateFormat.format(new Date(snapShot.getJobStartTime()));
                    break;
                case 3:
                    cellValue = snapShot.getFilesProcessed();
                    break;
                case 4:
                    cellValue = snapShot.getSpeed();
                    break;
                case 5:
                    cellValue = snapShot.getRunningListSize();
                    break;
                case 6:
                    cellValue = snapShot.getFileQueueSize();
                    break;
                case 7:
                    cellValue = snapShot.getDirQueueSize();
                    break;
                case 8:
                    cellValue = snapShot.getRootQueueSize();
                    break;
                case 9:
                    cellValue = snapShot.getStreamingQueueSize();
                    break;
                case 10:
                    cellValue = snapShot.getDsQueueSize();
                    break;
                default:
                    cellValue = null;
                    break;
            }
            return cellValue;
        }
    }

    private class ModuleTableModel extends AbstractTableModel {

        private class ModuleStats implements Comparable<ModuleStats> {

            private final String name;
            private final long duration;

            ModuleStats(String name, long duration) {
                this.name = name;
                this.duration = duration;
            }

            /**
             * @return the name
             */
            protected String getName() {
                return name;
            }

            /**
             * @return the duration
             */
            protected long getDuration() {
                return duration;
            }

            @Override
            public int compareTo(ModuleStats o) {
                if (duration > o.getDuration()) {
                    return -1;
                } else if (duration == o.getDuration()) {
                    return 0;
                } else {
                    return 1;
                }
            }

        }
        private final String[] columnNames = {NbBundle.getMessage(this.getClass(), "ModuleTableModel.colName.module"),
            NbBundle.getMessage(this.getClass(),
            "ModuleTableModel.colName.duration")};
        private final List<ModuleStats> moduleStats = new ArrayList<>();
        private long totalTime;

        private ModuleTableModel() {
            refresh();
        }

        private void refresh() {
            Map<String, Long> moduleStatMap = snapshotProvider.getModuleRunTimes();
            moduleStats.clear();
            totalTime = 0;
            for (String k : moduleStatMap.keySet()) {
                moduleStats.add(new ModuleStats(k, moduleStatMap.get(k)));
                totalTime += moduleStatMap.get(k);
            }
            Collections.sort(moduleStats);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return moduleStats.size();
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
            ModuleStats moduleStat = moduleStats.get(rowIndex);
            Object cellValue;
            switch (columnIndex) {
                case 0:
                    cellValue = moduleStat.getName();
                    break;
                case 1:
                    cellValue = DurationFormatUtils.formatDurationHMS(moduleStat.getDuration()) + " (" + (moduleStat.getDuration() * 100) / totalTime + "%)";
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
        jobScrollPane = new javax.swing.JScrollPane();
        jobTable = new javax.swing.JTable();
        refreshButton = new javax.swing.JButton();
        closeButton = new javax.swing.JButton();
        moduleScrollPane = new javax.swing.JScrollPane();
        moduleTable = new javax.swing.JTable();

        threadActivitySnapshotsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        snapshotsScrollPane.setViewportView(threadActivitySnapshotsTable);

        jobTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        jobScrollPane.setViewportView(jobTable);

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

        moduleTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        moduleScrollPane.setViewportView(moduleTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(snapshotsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 881, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(refreshButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(closeButton))
                    .addComponent(jobScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 881, Short.MAX_VALUE)
                    .addComponent(moduleScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 881, Short.MAX_VALUE))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {closeButton, refreshButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(snapshotsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 102, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jobScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 102, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(moduleScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 100, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(refreshButton)
                    .addComponent(closeButton))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {closeButton, refreshButton});

    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        parent.dispose();
    }//GEN-LAST:event_closeButtonActionPerformed

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        threadActivityTableModel.refresh();
        jobTableModel.refresh();
        moduleTableModel.refresh();
    }//GEN-LAST:event_refreshButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JScrollPane jobScrollPane;
    private javax.swing.JTable jobTable;
    private javax.swing.JScrollPane moduleScrollPane;
    private javax.swing.JTable moduleTable;
    private javax.swing.JButton refreshButton;
    private javax.swing.JScrollPane snapshotsScrollPane;
    private javax.swing.JTable threadActivitySnapshotsTable;
    // End of variables declaration//GEN-END:variables
}
